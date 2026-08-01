package com.example.myapplication.data.calendar

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Google Calendar API v3 の Events エンドポイント。
 *
 * 対象は常にログインユーザーのプライマリカレンダー（`primary`）。
 * 認証トークンはリクエストごとに変わりうるため、Interceptor ではなく [Header] で都度渡す。
 *
 * 戻り値を [Response] にしているのは、401 / 404 / 410 を例外ではなくステータスコードとして
 * 受け取り、[CalendarResult] へ素直に振り分けるため。
 *
 * @see <a href="https://developers.google.com/workspace/calendar/api/v3/reference/events">Events リソース</a>
 */
interface GoogleCalendarApi {

    /** 予定を新規作成する。 */
    @POST("calendars/primary/events")
    suspend fun insertEvent(
        @Header("Authorization") authorization: String,
        @Body event: CalendarEventRequest
    ): Response<CalendarEventResponse>

    /**
     * 予定を部分更新する。
     *
     * Retrofit の `@PATCH` は body 付きで問題なく使えるが、一部の HTTP クライアント実装で
     * PATCH + body が落ちるのを避けるため [HTTP] で明示的に `hasBody = true` を指定している。
     */
    @HTTP(method = "PATCH", path = "calendars/primary/events/{eventId}", hasBody = true)
    suspend fun patchEvent(
        @Header("Authorization") authorization: String,
        @Path("eventId") eventId: String,
        @Body event: CalendarEventRequest
    ): Response<CalendarEventResponse>

    /** 予定を削除する。成功時は 204（本文なし）。 */
    @DELETE("calendars/primary/events/{eventId}")
    suspend fun deleteEvent(
        @Header("Authorization") authorization: String,
        @Path("eventId") eventId: String
    ): Response<Unit>
}

/**
 * 予定の作成・更新に送る本文。終日予定なので [start] / [end] は `date` のみを持つ。
 *
 * @param summary 予定のタイトル
 * @param description 予定の説明
 */
@Serializable
data class CalendarEventRequest(
    val summary: String,
    val description: String,
    val start: CalendarEventDate,
    val end: CalendarEventDate
)

/**
 * 終日予定の日付。`YYYY-MM-DD` 形式の文字列を入れる（`dateTime` ではない）。
 *
 * end 側は **排他的** なので、締切当日だけの予定にしたい場合は翌日を入れること。
 */
@Serializable
data class CalendarEventDate(
    val date: String
)

/**
 * 予定のレスポンス。必要なのは ID だけなので他のフィールドは読み飛ばす
 * （パーサ側で `ignoreUnknownKeys = true` を設定している）。
 */
@Serializable
data class CalendarEventResponse(
    val id: String? = null
)
