package com.example.myapplication.data.calendar

import android.util.Log
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.Task
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * ログ出力を抽象化するインターフェース。
 * Unit テストでは Android の [Log] が動作しないため、本番用とテスト用を差し替えられるようにする。
 */
interface CalendarLogger {
    fun d(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
}

/**
 * 本番用ロガー。Android の [Log] にそのまま委譲する。
 */
object AndroidCalendarLogger : CalendarLogger {
    override fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }
}

/**
 * カレンダー操作の結果。UI 側が理由別にメッセージを出し分けられるようにする。
 */
sealed interface CalendarResult<out T> {
    /** 成功。[value] は呼び出しごとの戻り値（イベント ID など）。 */
    data class Success<T>(val value: T) : CalendarResult<T>

    /** 401、またはトークンが取得できなかった。ユーザーに再認可してもらう必要がある。 */
    data object Unauthorized : CalendarResult<Nothing>

    /** 404 / 410。予定がカレンダー側で消えている。 */
    data object NotFound : CalendarResult<Nothing>

    /** ネットワーク障害やその他のエラー。 */
    data class Failure(val message: String) : CalendarResult<Nothing>
}

/**
 * Google Calendar REST API (v3) 経由でタスクの締切を終日予定として書き出す。
 *
 * かつて使っていた端末の CalendarProvider 版と違い、カレンダーの読み書き権限は不要で、
 * 代わりに OAuth のアクセストークンが要る。
 * トークンは有効期限があるためリクエストのたびに [GoogleAuthManager] から取り直す。
 *
 * 通信はすべて suspend 関数。内部で [Dispatchers.IO] に切り替えるので呼び出し側は
 * スレッドを気にしなくてよい。
 */
open class GoogleCalendarSync(
    private val authManager: GoogleAuthManager,
    /**
     * テストで API 応答を差し替えられるように、Retrofit インスタンスはデフォルトを持ちつつ
     * コンストラクタから注入できるようにする。
     */
    api: GoogleCalendarApi? = null,
    /**
     * テストでは Android の [Log] が動かないため、ロガーも差し替え可能にする。
     */
    private val logger: CalendarLogger = AndroidCalendarLogger
) {

    /** JSON パーサ。API のレスポンスは項目が多いので未知のキーは読み飛ばす。 */
    private val json = Json {
        ignoreUnknownKeys = true
    }

    /** Retrofit は生成コストが高いので lazy に 1 つだけ作って使い回す。 */
    @OptIn(ExperimentalSerializationApi::class)
    private val api: GoogleCalendarApi by lazy {
        api ?: createApi(json)
    }

    /**
     * 締切当日の終日予定を作成し、そのイベント ID を返す。
     */
    open suspend fun insertEvent(task: Task, categoryName: String): CalendarResult<String> =
        request("予定の作成") { authorization ->
            api.insertEvent(authorization, task.toEventRequest(categoryName))
        }.mapSuccess { body ->
            // 201 が返ったのに ID が無いことは通常ありえないが、念のため握り潰さず失敗にする
            body?.id?.let { CalendarResult.Success(it) }
                ?: CalendarResult.Failure("予定は作成されましたが ID を取得できませんでした")
        }

    /**
     * 既存の予定をタスクの現在の内容へ更新する。
     * 予定がユーザーに手動で削除されていた場合は [CalendarResult.NotFound]。
     */
    open suspend fun updateEvent(
        eventId: String,
        task: Task,
        categoryName: String
    ): CalendarResult<Unit> =
        request("予定の更新") { authorization ->
            api.patchEvent(authorization, eventId, task.toEventRequest(categoryName))
        }.mapSuccess { CalendarResult.Success(Unit) }

    /**
     * 予定を削除する。すでに存在しない（404 / 410）場合も、結果として
     * 「カレンダーに予定が無い」状態は達成できているので成功扱いにする。
     */
    open suspend fun deleteEvent(eventId: String): CalendarResult<Unit> {
        val result = request("予定の削除") { authorization ->
            api.deleteEvent(authorization, eventId)
        }
        return when (result) {
            is CalendarResult.NotFound -> CalendarResult.Success(Unit)
            else -> result.mapSuccess { CalendarResult.Success(Unit) }
        }
    }

    /**
     * 今日1日（端末のタイムゾーン基準、0:00〜24:00）の予定一覧を取得する。
     * FreeTimeCheckWorker の空き時間検知が使う。取得に失敗した場合の扱い（静かに終了するなど）は
     * 呼び出し側（Worker）の責務とする。
     */
    open suspend fun listTodayEvents(
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CalendarResult<List<CalendarEventSlot>> {
        val today = now.atZone(zoneId).toLocalDate()
        val timeMin = today.atStartOfDay(zoneId).toInstant()
        val timeMax = today.plusDays(1).atStartOfDay(zoneId).toInstant()
        return request("予定一覧の取得") { authorization ->
            api.listEvents(
                authorization,
                timeMin = DateTimeFormatter.ISO_INSTANT.format(timeMin),
                timeMax = DateTimeFormatter.ISO_INSTANT.format(timeMax)
            )
        }.mapSuccess { body ->
            val slots = body?.items.orEmpty().mapNotNull { it.toEventSlotOrNull(zoneId) }
            CalendarResult.Success(slots)
        }
    }

    /**
     * トークン取得 → 通信 → ステータスコードの振り分け、という共通の流れをまとめる。
     *
     * 401 を受けた場合は、使用したトークンを無効化してから 1 回だけ再試行する。
     * ただしトークンの破棄は「今回の通信に使ったトークンとキャッシュが一致する場合だけ」行い、
     * 並行処理が先に新しいトークンを取得済みのときに新トークンを巻き添えで消さない。
     *
     * @param action ログに出す操作名
     */
    private suspend fun <T> request(
        action: String,
        call: suspend (authorization: String) -> Response<T>
    ): CalendarResult<T?> = withContext(Dispatchers.IO) {
        // トークンが無いなら通信するだけ無駄。そのまま再認可を促す
        val token = runCatchingPreserveCancellation { authManager.getAccessToken() }
            .onFailure { logger.w(TAG, "$action: アクセストークンの取得に失敗しました", it) }
            .getOrNull()
            ?: return@withContext CalendarResult.Unauthorized

        val result = executeRequest(action, token, call)

        // 401 の場合はトークンを無効化して 1 回だけ再試行する。
        // 2 回目の 401 は Unauthorized として返す。
        if (result is CalendarResult.Unauthorized) {
            // 一致する場合だけキャッシュを破棄。並行処理の新トークンを巻き添えにしない。
            runCatchingPreserveCancellation { authManager.invalidateTokenIfMatches(token) }
                .onFailure { logger.w(TAG, "$action: トークンの無効化に失敗しました", it) }

            val newToken = runCatchingPreserveCancellation { authManager.getAccessToken() }
                .onFailure { logger.w(TAG, "$action: 再取得に失敗しました", it) }
                .getOrNull()
                ?: return@withContext CalendarResult.Unauthorized

            return@withContext executeRequest(action, newToken, call)
        }

        result
    }

    /**
     * 実際の HTTP 通信とステータスコードの振り分け。
     * 401 / 404 / 410 などはここで [CalendarResult] に変換する。
     */
    private suspend fun <T> executeRequest(
        action: String,
        token: String,
        call: suspend (authorization: String) -> Response<T>
    ): CalendarResult<T?> = try {
        val response = call("Bearer $token")
        when {
            response.isSuccessful -> CalendarResult.Success(response.body())
            response.code() == HTTP_UNAUTHORIZED -> {
                logger.w(TAG, "$action: 認証エラー(401)。再認可が必要です")
                CalendarResult.Unauthorized
            }
            response.code() == HTTP_NOT_FOUND || response.code() == HTTP_GONE -> {
                logger.w(TAG, "$action: 予定が見つかりません(${response.code()})")
                CalendarResult.NotFound
            }
            else -> {
                logger.w(TAG, "$action: 失敗しました(${response.code()})")
                CalendarResult.Failure("$action に失敗しました (HTTP ${response.code()})")
            }
        }
    } catch (e: CancellationException) {
        // コルーチンがキャンセルされたらそのまま再 throw する。
        // ここで握り潰すと画面を閉じた後もエラー処理が走ってしまう。
        throw e
    } catch (e: IOException) {
        logger.w(TAG, "$action: 通信に失敗しました", e)
        CalendarResult.Failure("ネットワークに接続できませんでした")
    } catch (e: Exception) {
        logger.w(TAG, "$action: 予期しないエラーが発生しました", e)
        CalendarResult.Failure("$action に失敗しました")
    }

    /**
     * コルーチンのキャンセル信号はそのまま呼び出し側へ伝える。
     * 画面を閉じたときなどに、処理を中断すべき例外を握り潰して
     * ユーザーに余計なエラーメッセージが出ないようにするため。
     */
    private inline fun <R> runCatchingPreserveCancellation(block: () -> R): Result<R> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }

    /**
     * 成功時の値だけを差し替える。失敗系（Unauthorized / NotFound / Failure）はそのまま通す。
     */
    private inline fun <T, R> CalendarResult<T>.mapSuccess(
        transform: (T) -> CalendarResult<R>
    ): CalendarResult<R> = when (this) {
        is CalendarResult.Success -> transform(value)
        is CalendarResult.Unauthorized -> CalendarResult.Unauthorized
        is CalendarResult.NotFound -> CalendarResult.NotFound
        is CalendarResult.Failure -> this
    }

    /** タイトルと説明。完了済みのタスクは一目で分かるよう印を付ける。 */
    private fun Task.toEventRequest(categoryName: String): CalendarEventRequest {
        val mark = if (isCompleted) "✓ " else ""
        val date = localDateOf(deadline)
        return CalendarEventRequest(
            summary = "$mark$title",
            description = "重要度: $importance / 緊急度: $urgency\nカテゴリ: $categoryName",
            start = CalendarEventDate(date.format(DATE_FORMATTER)),
            // end.date は排他的なので締切日の「翌日」を入れると締切当日だけの終日予定になる
            end = CalendarEventDate(date.plusDays(1).format(DATE_FORMATTER))
        )
    }

    /**
     * 締切のエポックミリ秒を、端末のタイムゾーンでの暦日に変換する。
     *
     * CalendarProvider 版と違い REST API は日付文字列をそのまま受け取るので、
     * UTC 深夜への読み替えは不要（むしろやると日付がずれる）。
     */
    private fun localDateOf(deadline: Long): LocalDate =
        Instant.ofEpochMilli(deadline).atZone(ZoneId.systemDefault()).toLocalDate()

    companion object {
        private const val TAG = "GoogleCalendarSync"
        private const val BASE_URL = "https://www.googleapis.com/calendar/v3/"
        private const val APPLICATION_JSON = "application/json"

        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_GONE = 410

        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 30L

        /** 終日予定用の日付書式（RFC3339 の日付部分のみ）。 */
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        /**
         * 本番用の Retrofit クライアントを生成する。
         * ログはデバッグビルドのみ出力し、アクセストークンは必ず伏せ字にする。
         */
        @OptIn(ExperimentalSerializationApi::class)
        private fun createApi(json: Json): GoogleCalendarApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .apply {
                    // ログはデバッグビルドのみ。リリースに通信内容を出さない
                    if (BuildConfig.DEBUG) addInterceptor(loggingInterceptor())
                }
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory(APPLICATION_JSON.toMediaType()))
                .build()
                .create(GoogleCalendarApi::class.java)
        }

        private fun loggingInterceptor(): HttpLoggingInterceptor =
            HttpLoggingInterceptor { message -> Log.d(TAG, message) }.apply {
                level = HttpLoggingInterceptor.Level.HEADERS
                redactHeader("Authorization")
            }
    }
}
