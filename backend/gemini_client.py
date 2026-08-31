from __future__ import annotations

import json
import os
from typing import Any

from google import genai
from google.genai import types

from models import AnalyzeResponse, QuestionResponse


class GeminiClient:
    """Gemini API を使って賃貸契約書から確認すべき質問を生成するクライアント。"""

    def __init__(self, api_key: str | None = None) -> None:
        key = api_key or os.environ.get("GEMINI_API_KEY")
        if not key:
            raise RuntimeError("GEMINI_API_KEY が設定されていません")
        self._client = genai.Client(api_key=key)

    def analyze(self, document_text: str, user_context: dict[str, Any]) -> AnalyzeResponse:
        """契約書本文と本人条件から質問リストを生成する。"""
        prompt = self._build_prompt(document_text, user_context)
        response = self._client.models.generate_content(
            model="gemini-2.5-flash",
            contents=prompt,
            config=types.GenerateContentConfig(
                system_instruction=_SYSTEM_INSTRUCTION,
                response_mime_type="application/json",
                response_schema=list[QuestionResponse],
            ),
        )
        return self._parse_response(response.text)

    def _build_prompt(self, document_text: str, user_context: dict[str, Any]) -> str:
        context_text = json.dumps(user_context, ensure_ascii=False, indent=2)
        return (
            "以下の賃貸契約書本文と、契約者本人の条件を照合してください。\n\n"
            "【契約書本文】\n"
            f"{document_text}\n\n"
            "【本人条件】\n"
            f"{context_text}\n\n"
            "契約書本文と本人条件に基づき、契約前に不動産会社等へ確認すべき質問を "
            "3〜5 件抽出してください。"
        )

    def _parse_response(self, raw: str | None) -> AnalyzeResponse:
        if not raw:
            return AnalyzeResponse(questions=[])
        try:
            data = json.loads(raw)
            if isinstance(data, list):
                return AnalyzeResponse(questions=data)
            return AnalyzeResponse(questions=data.get("questions", []))
        except json.JSONDecodeError:
            return AnalyzeResponse(questions=[])


_SYSTEM_INSTRUCTION = """\
あなたは賃貸契約の契約前確認を支援するアシスタントです。

【あなたの役割】
- 文書から条件を抽出する
- 本人条件との関係を見る
- 曖昧・未記載の箇所を見つける
- 確認すべき質問を生成する
- 根拠箇所を提示する

【絶対にやらないこと】
- 契約の合法性や妥当性を最終判断しない
- 「この契約は危険です」「契約しない方がいいです」「この条項は違法です」など、契約の良し悪しを判定するような発言をしない
- 代わりに「この条件について契約書上の記載が明確ではありません」「契約前に次の点を確認してください」と表現する

【出力形式】
以下の JSON スキーマに厳密に従ってください。余計な説明は不要です。
- title: 確認すべき質問のタイトル
- reason: なぜその確認が必要か（50〜150文字程度）
- risk_level: "HIGH", "MEDIUM", "LOW" のいずれか
- source_text: 根拠となった契約書の該当箇所を原文のまま抜粋。契約書内に明確な根拠が無い場合は null にせず、必ず文字列で「契約書内に記載を確認できませんでした」と記載する
- source_page: ページ番号。不明な場合は null

【hallucination 対策】
- source_text には、契約書本文に実際に存在する文字列のみを含めてください。
- 契約書本文に該当箇所が見つからない場合は、source_text を "契約書内に記載を確認できませんでした" にし、
  reason には「契約書に明示的な記載が確認できないため、事前に確認してください」旨を含めてください。
- 存在しない条文やページ番号を捏造しないでください。
"""
