package com.example.myapplication.shared.reversefaq

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Reverse FAQ バックエンド（FastAPI）を Ktor Client で呼び出すクライアント。
 *
 * @param baseUrl バックエンドのベース URL。エミュレータでは [DEFAULT_BASE_URL]、
 *                実機では開発機の LAN 内 IP（例: http://192.168.x.x:8000）を指定する。
 * @param httpClient テスト用に差し替え可能な [HttpClient]。省略時は共通設定で生成する。
 */
open class ReverseFaqApiClient(
    baseUrl: String = DEFAULT_BASE_URL,
    httpClient: HttpClient? = null
) {
    private val client = httpClient ?: defaultHttpClient(baseUrl)

    /**
     * 契約書本文と本人条件をバックエンドに送信し、AI 生成の質問リストを取得する。
     *
     * @throws ReverseFaqApiException バックエンドから 2xx 以外が返った場合
     */
    open suspend fun analyze(
        caseId: Long,
        documentText: String,
        userContext: Map<String, JsonElement>
    ): List<QuestionDto> {
        val response = client.post("/cases/analyze") {
            contentType(ContentType.Application.Json)
            setBody(AnalyzeRequest(caseId, documentText, userContext))
        }
        if (!response.status.isSuccess()) {
            throw ReverseFaqApiException(
                "バックエンドでエラーが発生しました (HTTP ${response.status.value})"
            )
        }
        return response.body<AnalyzeResponse>().questions
    }

    companion object {
        /** エミュレータから開発機 localhost を参照するための標準 URL。 */
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8000"
    }
}

/**
 * バックエンド API 呼び出し時のエラー。
 */
class ReverseFaqApiException(message: String) : Exception(message)

/**
 * バックエンドから返される質問データ。
 */
@Serializable
data class QuestionDto(
    val title: String,
    val reason: String,
    val riskLevel: String,
    val sourceText: String,
    val sourcePage: Int? = null
)

@Serializable
private data class AnalyzeRequest(
    val caseId: Long,
    val documentText: String,
    val userContext: Map<String, JsonElement>
)

@Serializable
private data class AnalyzeResponse(
    val questions: List<QuestionDto>
)

/**
 * [QuestionDto] をドメインモデル [Question] に変換する。
 */
fun QuestionDto.toQuestion(caseId: Long): Question = Question(
    caseId = caseId,
    title = title,
    reason = reason,
    riskLevel = runCatching { RiskLevel.valueOf(riskLevel) }.getOrDefault(RiskLevel.MEDIUM),
    sourceText = sourceText,
    sourcePage = sourcePage,
    status = QuestionStatus.UNCONFIRMED
)

private fun defaultHttpClient(baseUrl: String): HttpClient {
    return HttpClient {
        defaultRequest {
            url(baseUrl)
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                namingStrategy = JsonNamingStrategy.SnakeCase
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
    }
}
