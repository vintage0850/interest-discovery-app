package com.example.myapplication.shared.reversefaq

/**
 * [ReverseFaqApiClient]のインメモリFake。単体テストで固定された質問を返す。
 */
class FakeReverseFaqApiClient : ReverseFaqApiClient() {

    override suspend fun analyze(
        caseId: Long,
        documentText: String,
        userContext: Map<String, kotlinx.serialization.json.JsonElement>
    ): List<QuestionDto> = listOf(
        QuestionDto(
            title = "退去時のクリーニング費用は必ず発生しますか？",
            reason = "退去時の費用負担条件が不明確です。",
            riskLevel = "HIGH",
            sourceText = documentText.take(50),
            sourcePage = null
        ),
        QuestionDto(
            title = "更新料はいくらですか？",
            reason = "契約更新時の料金が記載されているか確認が必要です。",
            riskLevel = "MEDIUM",
            sourceText = documentText.take(50),
            sourcePage = null
        ),
        QuestionDto(
            title = "鍵交換費用の負担はどうなりますか？",
            reason = "鍵の紛失・交換時の費用負担について確認してください。",
            riskLevel = "LOW",
            sourceText = documentText.take(50),
            sourcePage = null
        )
    )
}
