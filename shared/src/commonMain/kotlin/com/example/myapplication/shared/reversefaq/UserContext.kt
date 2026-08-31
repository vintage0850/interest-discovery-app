package com.example.myapplication.shared.reversefaq

/**
 * ユーザーの本人条件。AIが一般論ではなく本人向けの質問を作るための入力。
 * 現時点ではJSON文字列として保存し、Phase 2以降で構造化を検討する。
 */
data class UserContext(
    val caseId: Long,
    val attributesJson: String
)
