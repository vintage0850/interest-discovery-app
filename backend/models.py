from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class QuestionResponse(BaseModel):
    """AI が生成する1件の確認質問。"""

    title: str = Field(..., description="確認すべき質問のタイトル")
    reason: str = Field(..., description="なぜその確認が必要か")
    risk_level: str = Field(..., description="リスクレベル: HIGH / MEDIUM / LOW")
    source_text: str = Field(
        ...,
        description="根拠となった契約書該当箇所。根拠が無い場合は「契約書内に記載を確認できませんでした」",
    )
    source_page: int | None = Field(None, description="根拠ページ番号。不明なら null")


class AnalyzeRequest(BaseModel):
    """POST /cases/analyze のリクエストボディ。"""

    case_id: int = Field(..., description="案件 ID")
    document_text: str = Field(..., description="契約書の全文テキスト")
    user_context: dict[str, Any] = Field(default_factory=dict, description="本人条件")


class AnalyzeResponse(BaseModel):
    """POST /cases/analyze のレスポンスボディ。"""

    questions: list[QuestionResponse]
