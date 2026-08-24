package com.example.myapplication.data.calendar

import android.content.Context
import android.content.ContextWrapper
import com.example.myapplication.data.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * [GoogleCalendarSync] の 401 再試行・トークン無効化まわりの単体テスト。
 *
 * 実際の Play Services やネットワークに頼らず、認可と API 応答をテスト用の
 * 偽装クラスで置き換えて動作を確認する。
 */
class GoogleCalendarSyncTest {

    @Test
    fun `401 で使用したトークンとキャッシュが一致すれば破棄して再試行する`() = runBlocking {
        // 1回目は tokenA、再取得後は tokenB を返すようにする
        val authManager = TestAuthManager().apply {
            queuedTokens.addAll(listOf("tokenA", "tokenB"))
            cachedToken = "tokenA"
        }

        // 1回目 401、2回目成功
        val api = FakeCalendarApi { _ ->
            when (this.apiCallCount++) {
                0 -> Response.error<CalendarEventResponse>(
                    401,
                    "{}".toResponseBody("application/json".toMediaType())
                )
                else -> Response.success(CalendarEventResponse(id = "event123"))
            }
        }

        val sync = GoogleCalendarSync(authManager, api, NoOpCalendarLogger)
        val result = sync.insertEvent(createTask(), "仕事")

        // 再試行に成功し、イベント ID が返る
        assertTrue(result is CalendarResult.Success)
        assertEquals("event123", (result as CalendarResult.Success).value)

        // tokenA が無効化対象になった
        assertEquals("tokenA", authManager.invalidatedToken)
        assertTrue(authManager.invalidateCleared)

        // 2回 API を呼んでおり、2回目は新しいトークンを使っている
        assertEquals(2, api.authorizations.size)
        assertEquals("Bearer tokenA", api.authorizations[0])
        assertEquals("Bearer tokenB", api.authorizations[1])
    }

    @Test
    fun `並行処理が新しいトークンを取得済みの場合古い401でキャッシュを破棄しない`() = runBlocking {
        val authManager = TestAuthManager().apply {
            // このリクエストが使うトークンは tokenA だが、
            // 並行処理によってキャッシュは既に tokenB に更新されている
            queuedTokens.add("tokenA")
            cachedToken = "tokenB"
        }

        val api = FakeCalendarApi { authorization ->
            this.apiCallCount++
            // tokenA で来た古い 401 を返す
            if (authorization == "Bearer tokenA") {
                Response.error<CalendarEventResponse>(
                    401,
                    "{}".toResponseBody("application/json".toMediaType())
                )
            } else {
                Response.success(CalendarEventResponse(id = "event999"))
            }
        }

        val sync = GoogleCalendarSync(authManager, api, NoOpCalendarLogger)
        val result = sync.insertEvent(createTask(), "仕事")

        // キャッシュの tokenB を破棄せず、tokenB で再試行して成功する
        assertTrue(result is CalendarResult.Success)
        assertEquals("event999", (result as CalendarResult.Success).value)
        assertEquals("tokenA", authManager.invalidatedToken)
        assertFalse(authManager.invalidateCleared)
        assertEquals("tokenB", authManager.cachedToken)
    }

    @Test
    fun `2回目も401ならUnauthorizedを返す`() = runBlocking {
        val authManager = TestAuthManager().apply {
            queuedTokens.addAll(listOf("tokenA", "tokenB"))
            cachedToken = "tokenA"
        }

        val api = FakeCalendarApi { _ ->
            this.apiCallCount++
            Response.error<CalendarEventResponse>(
                401,
                "{}".toResponseBody("application/json".toMediaType())
            )
        }

        val sync = GoogleCalendarSync(authManager, api, NoOpCalendarLogger)
        val result = sync.insertEvent(createTask(), "仕事")

        assertTrue(result is CalendarResult.Unauthorized)
        // 再試行は 1 回までなので API は 2 回呼ばれる
        assertEquals(2, api.apiCallCount)
    }

    @Test
    fun `トークン取得中にキャンセルされたらCancellationExceptionを再throwする`() = runBlocking {
        val authManager = TestAuthManager().apply {
            getAccessTokenException = CancellationException()
        }

        val sync = GoogleCalendarSync(authManager, logger = NoOpCalendarLogger)

        // キャンセルは Failure として握り潰さず、呼び出し側に伝えることで
        // 画面を閉じたときに余計なエラーメッセージが出ない
        assertThrows(CancellationException::class.java) {
            runBlocking { sync.insertEvent(createTask(), "仕事") }
        }
        Unit
    }

    @Test
    fun `API通信中にキャンセルされたらCancellationExceptionを再throwする`() = runBlocking {
        val authManager = TestAuthManager().apply {
            queuedTokens.add("tokenA")
        }

        val api = FakeCalendarApi { _ ->
            throw CancellationException()
        }

        val sync = GoogleCalendarSync(authManager, api, NoOpCalendarLogger)

        assertThrows(CancellationException::class.java) {
            runBlocking { sync.insertEvent(createTask(), "仕事") }
        }
        Unit
    }

    @Test
    fun `401後のトークン無効化でキャンセルされたらCancellationExceptionを再throwする`() = runBlocking {
        val authManager = TestAuthManager().apply {
            queuedTokens.addAll(listOf("tokenA", "tokenB"))
            cachedToken = "tokenA"
            invalidateException = CancellationException()
        }

        val api = FakeCalendarApi { _ ->
            Response.error<CalendarEventResponse>(
                401,
                "{}".toResponseBody("application/json".toMediaType())
            )
        }

        val sync = GoogleCalendarSync(authManager, api, NoOpCalendarLogger)

        assertThrows(CancellationException::class.java) {
            runBlocking { sync.insertEvent(createTask(), "仕事") }
        }
        Unit
    }

    @Test
    fun `401後のトークン再取得でキャンセルされたらCancellationExceptionを再throwする`() = runBlocking {
        val authManager = TestAuthManager().apply {
            queuedTokens.add("tokenA")
            cachedToken = "tokenA"
            getAccessTokenExceptionOnSecondCall = CancellationException()
        }

        val api = FakeCalendarApi { _ ->
            Response.error<CalendarEventResponse>(
                401,
                "{}".toResponseBody("application/json".toMediaType())
            )
        }

        val sync = GoogleCalendarSync(authManager, api, NoOpCalendarLogger)

        assertThrows(CancellationException::class.java) {
            runBlocking { sync.insertEvent(createTask(), "仕事") }
        }
        Unit
    }

    @Test
    fun `listTodayEventsは予定一覧をCalendarEventSlotへ変換して返す`() = runBlocking {
        val authManager = TestAuthManager().apply {
            queuedTokens.add("tokenA")
        }
        val api = FakeCalendarApi { _ -> throw UnsupportedOperationException() }.apply {
            listEventsHandler = { _, _, _ ->
                Response.success(
                    CalendarEventListResponse(
                        items = listOf(
                            CalendarEventListItem(
                                start = CalendarEventDateTime(dateTime = "2026-08-23T10:00:00+09:00"),
                                end = CalendarEventDateTime(dateTime = "2026-08-23T11:00:00+09:00")
                            )
                        )
                    )
                )
            }
        }

        val sync = GoogleCalendarSync(authManager, api, NoOpCalendarLogger)
        val result = sync.listTodayEvents()

        assertTrue(result is CalendarResult.Success)
        val slots = (result as CalendarResult.Success).value
        assertEquals(1, slots.size)
        assertEquals(
            java.time.Instant.parse("2026-08-23T01:00:00Z"),
            slots[0].start
        )
    }

    private fun createTask(): Task = Task(
        title = "テストタスク",
        deadline = 1_700_000_000_000L,
        importance = 2,
        urgency = 2,
        categoryId = null,
        isCompleted = false
    )

    /**
     * テスト用の認可管理クラス。
     * 本物の Play Services やメモリキャッシュの挙動を、テストで制御しやすい形に再現する。
     */
    private class TestAuthManager : GoogleAuthManager(DummyContext()) {
        /** getAccessToken() が順番に返すトークン。空なら cachedToken を返す。 */
        val queuedTokens = mutableListOf<String>()

        /** 現在キャッシュされているトークン。並行処理による更新をシミュレートするために使う。 */
        var cachedToken: String? = null

        /** invalidateTokenIfMatches に渡されたトークン。 */
        var invalidatedToken: String? = null

        /** invalidateTokenIfMatches で実際にキャッシュを破棄したか。 */
        var invalidateCleared = false

        /** getAccessToken() の 1 回目の呼び出しで投げる例外。 */
        var getAccessTokenException: Throwable? = null

        /** getAccessToken() の 2 回目以降の呼び出しで投げる例外（401 再試行経路用）。 */
        var getAccessTokenExceptionOnSecondCall: Throwable? = null

        /** invalidateTokenIfMatches() で投げる例外。 */
        var invalidateException: Throwable? = null

        private var getAccessTokenCallCount = 0

        override suspend fun getAccessToken(): String? {
            getAccessTokenCallCount++
            if (getAccessTokenCallCount == 1 && getAccessTokenException != null) {
                throw getAccessTokenException!!
            }
            if (getAccessTokenCallCount >= 2 && getAccessTokenExceptionOnSecondCall != null) {
                throw getAccessTokenExceptionOnSecondCall!!
            }
            return if (queuedTokens.isNotEmpty()) {
                val token = queuedTokens.removeAt(0)
                // キューから取り出したトークンを「使用したトークン」として記録しておく
                token
            } else {
                cachedToken
            }
        }

        override suspend fun invalidateTokenIfMatches(token: String): Boolean {
            invalidateException?.let { throw it }
            invalidatedToken = token
            return if (cachedToken == token) {
                cachedToken = null
                invalidateCleared = true
                true
            } else {
                false
            }
        }
    }

    /**
     * 本物の Retrofit ではなく、呼び出された authorization ヘッダと任意のレスポンスを返す偽装 API。
     */
    private class FakeCalendarApi(
        private val handler: FakeCalendarApi.(authorization: String) -> Response<CalendarEventResponse>
    ) : GoogleCalendarApi {
        val authorizations = mutableListOf<String>()
        var apiCallCount = 0
        var listEventsHandler: ((authorization: String, timeMin: String, timeMax: String) -> Response<CalendarEventListResponse>)? =
            null

        override suspend fun insertEvent(
            authorization: String,
            event: CalendarEventRequest
        ): Response<CalendarEventResponse> {
            authorizations.add(authorization)
            return handler(authorization)
        }

        override suspend fun patchEvent(
            authorization: String,
            eventId: String,
            event: CalendarEventRequest
        ): Response<CalendarEventResponse> {
            throw UnsupportedOperationException("このテストでは patchEvent は使われない")
        }

        override suspend fun deleteEvent(
            authorization: String,
            eventId: String
        ): Response<Unit> {
            throw UnsupportedOperationException("このテストでは deleteEvent は使われない")
        }

        override suspend fun listEvents(
            authorization: String,
            timeMin: String,
            timeMax: String,
            singleEvents: Boolean,
            orderBy: String
        ): Response<CalendarEventListResponse> {
            authorizations.add(authorization)
            return listEventsHandler?.invoke(authorization, timeMin, timeMax)
                ?: throw UnsupportedOperationException("このテストでは listEvents は使われない")
        }
    }

    /**
     * [GoogleAuthManager] のコンストラクタに必要な Context のダミー。
     * テストでは Play Services を呼ばないので getApplicationContext() だけ実装すれば十分。
     */
    private class DummyContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    /**
     * 何もしないロガー。Unit テストでは Android の Log が動かないため、
     * [GoogleCalendarSync] に渡してログ出力を無効化する。
     */
    private object NoOpCalendarLogger : CalendarLogger {
        override fun d(tag: String, message: String) = Unit
        override fun w(tag: String, message: String, throwable: Throwable?) = Unit
    }
}
