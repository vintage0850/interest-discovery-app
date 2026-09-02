from __future__ import annotations

import json
import os
from typing import Any

from google import genai
from google.genai import types
from pydantic import BaseModel, Field, field_validator

from discovery.models import DomainType, Experiment, ExperimentResult, ExperimentStatus, InterestSignal


def _sanitize_gemini_error_message(exc: Exception) -> str:
    """SDK例外から秘密情報を含まない固定メッセージを返す。"""
    return "Gemini API request failed"


class ExperimentCandidate(BaseModel):
    """Gemini が生成する1件の実験候補。"""

    title: str = Field(..., min_length=1, max_length=200)
    description: str = Field(..., min_length=1, max_length=1000)
    domain: str = Field(..., description="有効な興味ドメイン")
    planned_minutes: int = Field(..., ge=5, le=15)

    @field_validator("domain")
    @classmethod
    def _validate_domain(cls, value: str) -> str:
        if value not in {d.value for d in DomainType}:
            raise ValueError(f"invalid domain: {value}")
        return value


class SupportingEvidence(BaseModel):
    """興味仮説の根拠となる1件の観察。"""

    domain: str = Field(..., min_length=1, max_length=100)
    description: str = Field(..., min_length=1, max_length=500)


class HypothesisCandidate(BaseModel):
    """Gemini が生成する1件の興味仮説。"""

    summary: str = Field(..., min_length=1, max_length=1000)
    confidence: float = Field(..., ge=0.0, le=1.0)
    supporting_evidence: list[SupportingEvidence] = Field(default_factory=list)
    suggested_next_domains: list[str] = Field(default_factory=list)

    @field_validator("suggested_next_domains")
    @classmethod
    def _filter_suggested_domains(cls, value: list[str]) -> list[str]:
        valid_domains = {d.value for d in DomainType}
        return [d for d in value if d in valid_domains]


class DiscoveryGeminiClient:
    """Gemini API を使って実験候補と興味仮説を生成するクライアント。"""

    def __init__(self, api_key: str | None = None) -> None:
        # 遅延評価: 依存関係解決時ではなく、API呼び出し時にキー有無を検証する
        self._api_key = api_key or os.environ.get("GEMINI_API_KEY")
        self._client: genai.Client | None = None

    def _ensure_client(self) -> genai.Client:
        if not self._api_key:
            raise RuntimeError("Gemini API key is not configured")
        if self._client is None:
            self._client = genai.Client(api_key=self._api_key)
        return self._client

    def generate_experiments(
        self,
        signals: list[InterestSignal],
        n_candidates: int = 3,
    ) -> list[Experiment]:
        """興味シグナルから行動実験候補を生成する。"""
        from google.genai.errors import APIError

        client = self._ensure_client()
        prompt = self._build_experiment_prompt(signals, n_candidates)
        try:
            response = client.models.generate_content(
                model="gemini-2.5-flash",
                contents=prompt,
                config=types.GenerateContentConfig(
                    system_instruction=_EXPERIMENT_SYSTEM_INSTRUCTION,
                    response_mime_type="application/json",
                    response_schema=list[ExperimentCandidate],
                ),
            )
        except APIError as exc:
            raise RuntimeError(_sanitize_gemini_error_message(exc)) from exc
        candidates = self._parse_experiment_response(response.text or "")
        experiments: list[Experiment] = []
        for candidate in candidates:
            experiments.append(
                Experiment(
                    session_id=0,  # router で上書き
                    title=candidate.title,
                    description=candidate.description,
                    domain=candidate.domain,
                    planned_minutes=candidate.planned_minutes,
                    status=ExperimentStatus.GENERATED.value,
                )
            )
        return experiments

    def update_hypothesis(
        self,
        signals: list[InterestSignal],
        experiments: list[Experiment],
        results: list[ExperimentResult],
    ) -> dict[str, Any]:
        """行動データから興味仮説を生成する。"""
        from google.genai.errors import APIError

        client = self._ensure_client()
        prompt = self._build_hypothesis_prompt(signals, experiments, results)
        try:
            response = client.models.generate_content(
                model="gemini-2.5-flash",
                contents=prompt,
                config=types.GenerateContentConfig(
                    system_instruction=_HYPOTHESIS_SYSTEM_INSTRUCTION,
                    response_mime_type="application/json",
                    response_schema=HypothesisCandidate,
                ),
            )
        except APIError as exc:
            raise RuntimeError(_sanitize_gemini_error_message(exc)) from exc
        candidate = self._parse_hypothesis_response(response.text or "")
        return candidate.model_dump()

    def _build_experiment_prompt(
        self, signals: list[InterestSignal], n_candidates: int
    ) -> str:
        signal_text = json.dumps(
            [
                {
                    "action_type": s.action_type,
                    "domain": s.domain,
                    "content_summary": s.content_summary,
                    "source": s.source,
                }
                for s in signals
            ],
            ensure_ascii=False,
            indent=2,
        )
        return (
            "以下は高校生が示した弱い興味シグナルです。\n\n"
            f"{signal_text}\n\n"
            f"これらのシグナルから、5〜15分で試せる行動実験を {n_candidates} 件提案してください。\n"
            "提案は実現可能で、好奇心を刺激し、次のステップにつながるものにしてください。"
        )

    def _build_hypothesis_prompt(
        self,
        signals: list[InterestSignal],
        experiments: list[Experiment],
        results: list[ExperimentResult],
    ) -> str:
        signal_text = json.dumps(
            [
                {
                    "action_type": s.action_type,
                    "domain": s.domain,
                    "content_summary": s.content_summary,
                }
                for s in signals
            ],
            ensure_ascii=False,
            indent=2,
        )
        experiment_text = json.dumps(
            [
                {
                    "title": e.title,
                    "domain": e.domain,
                    "planned_minutes": e.planned_minutes,
                    "actual_minutes": e.actual_minutes,
                    "status": e.status,
                    "result": (
                        {
                            "enjoyment": r.enjoyment,
                            "curiosity": r.curiosity,
                            "retry_intent": r.retry_intent,
                            "confidence": r.confidence,
                        }
                        if (r := next((x for x in results if x.experiment_id == e.id), None))
                        else None
                    ),
                }
                for e in experiments
            ],
            ensure_ascii=False,
            indent=2,
        )
        return (
            "以下は高校生の興味シグナルと行動実験結果です。\n\n"
            "【シグナル】\n"
            f"{signal_text}\n\n"
            "【実験結果】\n"
            f"{experiment_text}\n\n"
            "これらを基に、生徒の興味に関する簡潔な仮説を生成してください。\n"
            "根拠は実データに基づき、次に試すべき別ドメインを提案してください。"
        )

    def _parse_experiment_response(self, raw: str) -> list[ExperimentCandidate]:
        if not raw:
            raise ValueError("Gemini から空の応答が返りました")
        try:
            data = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise ValueError(f"Gemini 応答の JSON パースに失敗しました: {exc}") from exc

        if not isinstance(data, list):
            raise ValueError("Gemini 応答が配列ではありません")
        return [ExperimentCandidate.model_validate(item) for item in data]

    def _parse_hypothesis_response(self, raw: str) -> HypothesisCandidate:
        if not raw:
            raise ValueError("Gemini から空の応答が返りました")
        try:
            data = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise ValueError(f"Gemini 応答の JSON パースに失敗しました: {exc}") from exc

        if isinstance(data, list):
            raise ValueError("Gemini 応答がオブジェクトではありません")
        return HypothesisCandidate.model_validate(data)


_EXPERIMENT_SYSTEM_INSTRUCTION = """\
あなたは高校生の興味発見を支援するアシスタントです。
生徒の弱い興味シグナルから、5〜15分で試せる小さな行動実験を提案してください。

【提案の条件】
- 所要時間は必ず 5〜15 分の範囲内
- ドメインは tech / art / music / sports / science / social / making / nature / business / other のいずれか
- スマートフォン1台または身近な道具だけでできる
- 具体性があり、すぐに始められる

【出力形式】
以下の JSON スキーマに厳密に従ってください。余計な説明は不要です。
- title: 実験タイトル（50文字以内）
- description: 実験の具体的な手順（200文字以内）
- domain: 興味ドメイン
- planned_minutes: 予定所要時間（5〜15）
"""

_HYPOTHESIS_SYSTEM_INSTRUCTION = """\
あなたは高校生の興味発見を支援するアシスタントです。
生徒の興味シグナルと行動実験結果から、仮の興味仮説を生成してください。

【あなたの役割】
- 行動データから興味の傾向を読み取る
- 実データに基づく簡潔な仮説を述べる
- 次に試すべき別ドメインを提案する

【絶対にやらないこと】
- データに基づかない断定はしない
- 生徒の能力や将来を決めつけない

【出力形式】
以下の JSON スキーマに厳密に従ってください。
- summary: 興味仮説（200文字以内）
- confidence: 確信度（0.0〜1.0）
- supporting_evidence: 根拠となる観察のリスト
- suggested_next_domains: 次に試すべきドメインのリスト
"""
