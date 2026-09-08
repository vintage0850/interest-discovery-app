from __future__ import annotations

import json
import os
from typing import Any

from google import genai
from google.genai import types
from pydantic import BaseModel, Field, field_validator

from discovery.models import (
    BehaviorCategory,
    DomainType,
    Evidence,
    Experiment,
    ExperimentResult,
    ExperimentStatus,
    InterestSignal,
)


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


class BehaviorCategoryClassification(BaseModel):
    """1件のエビデンスに対する行動分類（Action Taxonomy）結果。"""

    evidence_id: int
    categories: dict[str, float] = Field(
        ..., description="許可された行動分類とその強度（0.0〜1.0）"
    )

    @field_validator("categories")
    @classmethod
    def _validate_categories(cls, value: dict[str, float]) -> dict[str, float]:
        valid_categories = {c.value for c in BehaviorCategory}
        for category, score in value.items():
            if category not in valid_categories:
                raise ValueError(f"invalid behavior category: {category}")
            if not isinstance(score, (int, float)) or isinstance(score, bool):
                raise ValueError(
                    f"behavior category score must be numeric: {score}"
                )
            if score < 0.0 or score > 1.0:
                raise ValueError(
                    f"behavior category score must be between 0.0 and 1.0: {score}"
                )
        return value


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

    def classify_behavior_categories(
        self,
        evidences: list[Evidence],
        experiments: list[Experiment],
        results: list[ExperimentResult],
    ) -> dict[int, dict[str, float]]:
        """エビデンスごとに行動分類（Action Taxonomy）を Gemini で判定する。

        許可される分類は EXPLORE/COMPARE/ANALYZE/CREATE/IMPROVE/ORGANIZE/PRACTICE/
        COMMUNICATE/DECIDE/REFLECT の10種類のみ。強度は 0.0〜1.0。
        """
        from google.genai.errors import APIError

        client = self._ensure_client()
        prompt = self._build_behavior_category_prompt(evidences, experiments, results)
        try:
            response = client.models.generate_content(
                model="gemini-2.5-flash",
                contents=prompt,
                config=types.GenerateContentConfig(
                    system_instruction=_BEHAVIOR_CATEGORY_SYSTEM_INSTRUCTION,
                    response_mime_type="application/json",
                    response_schema=list[BehaviorCategoryClassification],
                ),
            )
        except APIError as exc:
            raise RuntimeError(_sanitize_gemini_error_message(exc)) from exc
        classifications = self._parse_behavior_category_response(response.text or "")
        return {c.evidence_id: c.categories for c in classifications}

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

    def _build_behavior_category_prompt(
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
        categories_text = ", ".join(c.value for c in BehaviorCategory)
        return (
            "以下は高校生のエビデンス（興味シグナルの集計・要約）と行動実験結果です。\n\n"
            "【エビデンス】\n"
            f"{evidence_text}\n\n"
            "【実験結果】\n"
            f"{experiment_text}\n\n"
            "各エビデンスに対し、行動分類を 0.0〜1.0 の強度で付与してください。\n"
            f"許可される分類は次の10種類のみです: {categories_text}\n"
            "- 複数の分類を付与してよいですが、関連性の低い分類は 0.0 に近い小さな値にしてください。\n"
            "- 分類名は上記の文字列をそのまま使用してください。\n"
            "- 値は必ず 0.0 から 1.0 の範囲内にしてください。"
        )

    def _parse_behavior_category_response(
        self, raw: str
    ) -> list[BehaviorCategoryClassification]:
        if not raw:
            raise ValueError("Gemini から空の応答が返りました")
        try:
            data = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise ValueError(f"Gemini 応答の JSON パースに失敗しました: {exc}") from exc

        if not isinstance(data, list):
            raise ValueError("Gemini 応答が配列ではありません")
        return [BehaviorCategoryClassification.model_validate(item) for item in data]

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


_EXPERIMENT_SYSTEM_INSTRUCTION = """\
あなたは高校生の興味発見を支援するアシスタントです。
生徒の弱い興味シグナルから、5〜15分で試せる小さな行動実験を提案してください。

【提案の条件】
- 所要時間は必ず 5〜15 分の範囲内
- ドメインは tech / art / music / sports / science / social / making / nature / business / other のいずれか
- スマートフォン1台または身近な道具だけでできる
- 具体性があり、すぐに始められる

【絶対にやらないこと】
- データに基づかない断定はしない
- 生徒の能力や将来を決めつけない
- 精神疾患、発達障害、IQ、性的指向、政治思想、宗教、医療状態について、推論・言及・示唆をしない

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
- 精神疾患、発達障害、IQ、性的指向、政治思想、宗教、医療状態について、推論・言及・示唆をしない

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
- 精神疾患、発達障害、IQ、性的指向、政治思想、宗教、医療状態について、推論・言及・示唆をしない

【出力形式】
以下の JSON スキーマに厳密に従ってください。余計な説明は不要です。
- weekly_insights: 直近1週間の気づき（200文字以内）
- change_from_past: 前週からの変化（200文字以内）
"""

_BEHAVIOR_CATEGORY_SYSTEM_INSTRUCTION = """\
あなたは高校生の興味発見を支援するアシスタントです。
生徒のエビデンス（興味シグナルの集計・要約）と行動実験結果を読み、
各エビデンスが示す行動パターンを分類してください。

【分類のルール】
- 許可される分類は次の10種類のみ:
  EXPLORE, COMPARE, ANALYZE, CREATE, IMPROVE, ORGANIZE, PRACTICE, COMMUNICATE, DECIDE, REFLECT
- 分類名は上記の英字をそのまま使用すること
- 強度は 0.0〜1.0 の数値で表すこと
- 複数の分類を付与してよいが、値はエビデンスとの関連性を反映すること

【絶対にやらないこと】
- データに基づかない断定はしない
- 生徒の能力や将来を決めつけない
- 精神疾患、発達障害、IQ、性的指向、政治思想、宗教、医療状態について、推論・言及・示唆をしない

【出力形式】
以下の JSON スキーマに厳密に従ってください。余計な説明は不要です。
- evidence_id: エビデンスのID（整数）
- categories: {分類名: 強度} のオブジェクト
"""
