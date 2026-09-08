from __future__ import annotations

import json
import os
from typing import Any

from google import genai
from google.genai import types
from pydantic import BaseModel, Field, field_validator

from discovery.models import DomainType, Evidence, Experiment, ExperimentResult, ExperimentStatus, InterestSignal


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
    supporting_evidence: list[int] = Field(default_factory=list)
    suggested_next_domains: list[str] = Field(default_factory=list)

    @field_validator("suggested_next_domains")
    @classmethod
    def _filter_suggested_domains(cls, value: list[str]) -> list[str]:
        valid_domains = {d.value for d in DomainType}
        return [d for d in value if d in valid_domains]


class WeeklyNarrativeCandidate(BaseModel):
    """Gemini が生成する週次ナラティブ。"""

    weekly_insights: str = Field(..., min_length=1, max_length=200)
    change_from_past: str = Field(..., min_length=1, max_length=200)


class MonthlyNarrativeCandidate(BaseModel):
    """Gemini が生成する月次ナラティブ。"""

    monthly_insights: str = Field(..., min_length=1, max_length=200)
    progress_wave: str = Field(..., min_length=1, max_length=200)
    continuity_insight: str = Field(..., min_length=1, max_length=200)

    @field_validator("monthly_insights", "progress_wave", "continuity_insight")
    @classmethod
    def _validate_text(cls, value: str) -> str:
        trimmed = value.strip()
        if not trimmed:
            raise ValueError("field must not be empty or whitespace only")
        if len(trimmed) > 200:
            raise ValueError("field must be 200 characters or less after trimming")
        if "\n" in trimmed or "\r" in trimmed:
            raise ValueError("field must not contain line breaks")
        return trimmed


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
        evidences: list[Evidence],
        experiments: list[Experiment],
        results: list[ExperimentResult],
    ) -> dict[str, Any]:
        """行動データから興味仮説を生成する。"""
        from google.genai.errors import APIError

        client = self._ensure_client()
        prompt = self._build_hypothesis_prompt(evidences, experiments, results)
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

    def generate_weekly_narrative(
        self,
        recent_summary: dict[str, Any],
        previous_summary: dict[str, Any],
        top_domain: str,
    ) -> dict[str, Any]:
        """直近7日とその前7日のサマリーを比較し、週次ナラティブを生成する。"""
        from google.genai.errors import APIError

        client = self._ensure_client()
        prompt = self._build_weekly_narrative_prompt(
            recent_summary, previous_summary, top_domain
        )
        try:
            response = client.models.generate_content(
                model="gemini-2.5-flash",
                contents=prompt,
                config=types.GenerateContentConfig(
                    system_instruction=_WEEKLY_NARRATIVE_SYSTEM_INSTRUCTION,
                    response_mime_type="application/json",
                    response_schema=WeeklyNarrativeCandidate,
                ),
            )
        except APIError as exc:
            raise RuntimeError(_sanitize_gemini_error_message(exc)) from exc
        candidate = self._parse_weekly_narrative_response(response.text or "")
        return candidate.model_dump()

    def generate_monthly_narrative(
        self,
        recent_metrics: dict[str, Any],
        previous_metrics: dict[str, Any],
    ) -> dict[str, Any]:
        """直近30日とその前30日のメトリクスを比較し、月次ナラティブを生成する。"""
        from google.genai.errors import APIError

        client = self._ensure_client()
        prompt = self._build_monthly_narrative_prompt(
            recent_metrics, previous_metrics
        )
        try:
            response = client.models.generate_content(
                model="gemini-2.5-flash",
                contents=prompt,
                config=types.GenerateContentConfig(
                    system_instruction=_MONTHLY_NARRATIVE_SYSTEM_INSTRUCTION,
                    response_mime_type="application/json",
                    response_schema=MonthlyNarrativeCandidate,
                ),
            )
        except APIError as exc:
            raise RuntimeError(_sanitize_gemini_error_message(exc)) from exc
        candidate = self._parse_monthly_narrative_response(response.text or "")
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
        evidences: list[Evidence],
        experiments: list[Experiment],
        results: list[ExperimentResult],
    ) -> str:
        evidence_text = json.dumps(
            [
                {
                    "id": e.id,
                    "domain": e.domain,
                    "signal_count": e.signal_count,
                    "summary_text": e.summary_text,
                }
                for e in evidences
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
            "以下は高校生のエビデンス（興味シグナルの集計・要約）と行動実験結果です。\n\n"
            "【エビデンス】\n"
            f"{evidence_text}\n\n"
            "【実験結果】\n"
            f"{experiment_text}\n\n"
            "これらを基に、生徒の興味に関する簡潔な仮説を生成してください。\n"
            "根拠となったエビデンスの id を supporting_evidence に指定し、次に試すべき別ドメインを提案してください。"
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

    def _build_weekly_narrative_prompt(
        self,
        recent_summary: dict[str, Any],
        previous_summary: dict[str, Any],
        top_domain: str,
    ) -> str:
        recent_text = json.dumps(recent_summary, ensure_ascii=False, indent=2)
        previous_text = json.dumps(previous_summary, ensure_ascii=False, indent=2)
        return (
            "以下は高校生の興味発見アクティビティの直近7日間と、その前の7日間のサマリーです。\n\n"
            "【直近7日】\n"
            f"{recent_text}\n\n"
            "【前の7日】\n"
            f"{previous_text}\n\n"
            f"最も活動が多かったドメイン: {top_domain}\n\n"
            "これらを比較して、直近1週間の気づきと、前週からの変化を簡潔に日本語で出力してください。"
        )

    def _parse_weekly_narrative_response(self, raw: str) -> WeeklyNarrativeCandidate:
        if not raw:
            raise ValueError("Gemini から空の応答が返りました")
        try:
            data = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise ValueError(f"Gemini 応答の JSON パースに失敗しました: {exc}") from exc

        if isinstance(data, list):
            raise ValueError("Gemini 応答がオブジェクトではありません")
        return WeeklyNarrativeCandidate.model_validate(data)

    def _build_monthly_narrative_prompt(
        self,
        recent_metrics: dict[str, Any],
        previous_metrics: dict[str, Any],
    ) -> str:
        recent_text = json.dumps(recent_metrics, ensure_ascii=False, indent=2)
        previous_text = json.dumps(previous_metrics, ensure_ascii=False, indent=2)
        return (
            "以下は高校生の興味発見アクティビティの直近30日間と、その前の30日間のメトリクスです。\n\n"
            "【直近30日】\n"
            f"{recent_text}\n\n"
            "【前の30日】\n"
            f"{previous_text}\n\n"
            "これらを比較して、直近30日間の全体気づき、進み方の波、継続できたペースを"
            "簡潔に日本語で出力してください。"
        )

    def _parse_monthly_narrative_response(self, raw: str) -> MonthlyNarrativeCandidate:
        if not raw:
            raise ValueError("Gemini から空の応答が返りました")
        try:
            data = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise ValueError(f"Gemini 応答の JSON パースに失敗しました: {exc}") from exc

        if isinstance(data, list):
            raise ValueError("Gemini 応答がオブジェクトではありません")
        return MonthlyNarrativeCandidate.model_validate(data)


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
あなたはお高校生の興味発見を支援するアシスタントです。
生徒のエビデンス（興味シグナルの集計・要約）と行動実験結果から、仮の興味仮説を生成してください。

【あなたの役割】
- 行動データから興味の傾向を読み取る
- 実データに基づく簡潔な仮説を述べる
- 根拠となったエビデンスのIDリスト (supporting_evidence) を指定する
- 次に試すべき別ドメインを提案する

【絶対にやらないこと】
- データに基づかない断定はしない
- 生徒の能力や将来を決めつけない

【出力形式】
以下の JSON スキーマに厳密に従ってください。
- summary: 興味仮説（200文字以内）
- confidence: 確信度（0.0〜1.0）
- supporting_evidence: 根拠となったエビデンスIDのリスト（整数のリスト）
- suggested_next_domains: 次に試すべきドメインのリスト
"""

_WEEKLY_NARRATIVE_SYSTEM_INSTRUCTION = """\
あなたは高校生の興味発見を支援するアシスタントです。
生徒の直近7日間とその前の7日間の行動データを比較し、簡潔な週次レポートを生成してください。

【あなたの役割】
- 直近1週間の活動から気づきを述べる
- 前週と比較した変化を述べる

【絶対にやらないこと】
- データに基づかない断定はしない
- 生徒の能力や将来を決めつけない

【出力形式】
以下の JSON スキーマに厳密に従ってください。余計な説明は不要です。
- weekly_insights: 直近1週間の気づき（200文字以内）
- change_from_past: 前週からの変化（200文字以内）
"""

_MONTHLY_NARRATIVE_SYSTEM_INSTRUCTION = """\
あなたは高校生の興味発見を支援するアシスタントです。
生徒の直近30日間とその前の30日間の行動データを比較し、簡潔な月次レポートを生成してください。

【あなたの役割】
- 直近30日間の全体気づきを述べる
- 直近30日間の進み方の波（3つの10日区間のペース変化）を述べる
- 完了率とアクティブ日数を根拠に、継続できた点と次に試せる小さな工夫を述べる

【絶対にやらないこと】
- データに基づかない断定はしない
- 生徒の能力や将来を決めつけない
- 診断、優劣評価、失敗扱い、他ユーザーとの比較をしない
- 件数・率・因果関係を捏造しない

【入力メトリクスの補足】
- completion_rate は「期間内に開始した実験のうち、期間内に完了した割合（百分率・小数1桁）」です。
- completion_rate が null の場合は、期間内に開始された実験が0件であることを意味します。0%とは解釈しないでください。
- active_day_rate は「期間内に活動があった日数を30日で割った値（百分率・小数1桁）」です。活動日数はシグナル発生・実験開始・実験完了の日付を重複なく数えたものです。
- progress_segments は直近30日を3つの10日区間に分けたものです。各区間に completed_experiment_count と active_days が含まれます。

【出力形式】
以下の JSON スキーマに厳密に従ってください。余計な説明は不要です。
- monthly_insights: 直近30日間の全体気づき（200文字以内、改行なし）
- progress_wave: 進み方の波（200文字以内、改行なし）
- continuity_insight: 継続のペースに関する気づき（200文字以内、改行なし）
"""
