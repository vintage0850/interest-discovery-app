package com.mikke.discovery.data.calendar

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import com.mikke.discovery.BuildConfig
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google カレンダー REST API を叩くための OAuth 認可状態。UI はこれを見て表示を切り替える。
 */
sealed interface CalendarAuthState {
    /** BuildConfig に OAuth クライアント ID が設定されていない（機能自体を隠す想定）。 */
    data object NotConfigured : CalendarAuthState

    /** 未同意。同意画面（[GoogleAuthManager.authorizationIntentSender]）の起動が必要。 */
    data object NotAuthorized : CalendarAuthState

    /** 同意済み。[email] はアカウントのメールアドレス（取得できなければ null）。 */
    data class Authorized(val email: String?) : CalendarAuthState
}

/**
 * [GoogleAuthManager.authorizationIntentSender] の結果。
 *
 * 「同意画面が要る」「もう要らない」「そもそも設定が無い」「確認自体が失敗した」を
 * 呼び出し側が区別できるようにする。特に通信エラーを「ユーザーが同意を拒否した」と
 * 誤って表示しないために必要。
 */
sealed interface AuthorizationStep {
    /** 同意画面の起動が必要。[intentSender] を ActivityResultLauncher に渡すこと。 */
    data class Consent(val intentSender: IntentSender) : AuthorizationStep

    /** すでに同意済みで、同意画面は不要。 */
    data object AlreadyAuthorized : AuthorizationStep

    /** OAuth クライアント ID が未設定。 */
    data object NotConfigured : AuthorizationStep

    /** 通信エラーなどで認可状態を確認できなかった。[message] はそのまま画面に出せる文言。 */
    data class Failed(val message: String) : AuthorizationStep
}

/** [GoogleAuthManager.signOut] の結果。実態と食い違う案内を出さないために区別する。 */
enum class SignOutResult {
    /** Google 側の認可の取り消しまで成功した。 */
    REVOKED,

    /** アプリ側の連携情報は消せたが、Google 側の取り消しには失敗した可能性がある。 */
    LOCAL_ONLY
}

/**
 * Google Calendar API 用のアクセストークンを管理する。
 *
 * Credential Manager ではなく Play Services の Authorization API を使う。
 * [com.google.android.gms.auth.api.identity.AuthorizationClient.authorize] は
 * 「サインイン + スコープ同意」をまとめて扱い、成功すればアクセストークンをそのまま返す。
 * 既に同意済みのアカウントであればユーザー操作なしで解決されるため、
 * アクセストークンの期限切れ時も同じ呼び出しを繰り返すだけで再取得できる
 * （Android クライアントではリフレッシュトークンを扱えないため、この方式が唯一の選択肢）。
 *
 * 取得したトークンは短時間だけメモリに載せるだけで、永続化は一切しない。
 */
open class GoogleAuthManager(context: Context) {

    // Activity を握るとリークするので必ず Application context に落とす
    private val appContext = context.applicationContext

    private val authorizationClient by lazy { Identity.getAuthorizationClient(appContext) }

    /**
     * OAuth クライアント ID が設定されているか。
     *
     * Android の OAuth クライアントは GCP 側でパッケージ名 + SHA-1 に紐付くため、
     * [AuthorizationRequest] 自体にクライアント ID を渡す必要はない
     * （渡すのは requestOfflineAccess でサーバー用の認可コードが欲しい場合だけ）。
     * ここでは「GCP の設定が済んでいるか」の判定材料として使う。
     */
    private val isConfigured: Boolean
        get() = BuildConfig.GOOGLE_OAUTH_CLIENT_ID.isNotBlank()

    // 初期状態: 設定済みなら「未同意」から始め、refreshAuthState() で実態に合わせる
    private val _authState = MutableStateFlow<CalendarAuthState>(
        if (isConfigured) CalendarAuthState.NotAuthorized else CalendarAuthState.NotConfigured
    )

    /** 現在の認可状態。UI から collect する。 */
    val authState: StateFlow<CalendarAuthState> = _authState.asStateFlow()

    // authorize() の多重呼び出しとトークンキャッシュの競合を防ぐ
    private val requestMutex = Mutex()

    private var cachedToken: String? = null
    private var cachedTokenExpiresAt = 0L

    /** 起動時などに、ユーザー操作なしで認可済みかを確認して [authState] を更新する。 */
    open suspend fun refreshAuthState() {
        if (!ensureConfigured()) return
        withContext(Dispatchers.IO) {
            requestMutex.withLock { authorizeInternal() }
        }
    }

    /**
     * アクセストークンを返す。未認可・未設定なら null。
     * ユーザー操作が必要な場合も null を返すだけで、同意画面は出さない。
     */
    open suspend fun getAccessToken(): String? {
        if (!ensureConfigured()) return null
        return withContext(Dispatchers.IO) {
            requestMutex.withLock {
                validCachedToken()?.let { return@withLock it }
                val result = authorizeInternal().getOrNull() ?: return@withLock null
                // 同意が必要な状態ならトークンは発行されない
                if (result.hasResolution()) null else result.accessToken
            }
        }
    }

    /**
     * 同意フローの次の一歩を返す。
     *
     * 同意画面が必要なら [AuthorizationStep.Consent]、既に認可済みなら
     * [AuthorizationStep.AlreadyAuthorized]、通信エラー等で確認できなければ
     * [AuthorizationStep.Failed] を返す。
     * 呼び出し側は Consent の場合だけ ActivityResultLauncher<IntentSenderRequest> で起動すること。
     */
    suspend fun authorizationIntentSender(): AuthorizationStep {
        if (!ensureConfigured()) return AuthorizationStep.NotConfigured
        return withContext(Dispatchers.IO) {
            requestMutex.withLock {
                val result = authorizeInternal().getOrElse { error ->
                    return@withLock AuthorizationStep.Failed(failureMessageOf(error))
                }
                if (!result.hasResolution()) return@withLock AuthorizationStep.AlreadyAuthorized

                // 同意が要るのに起動先が無い（通常ありえない）ケースも失敗として扱う
                result.pendingIntent?.intentSender
                    ?.let { AuthorizationStep.Consent(it) }
                    ?: AuthorizationStep.Failed(AUTHORIZATION_UNAVAILABLE_MESSAGE)
            }
        }
    }

    /** 同意画面から戻ってきた結果を処理する。成功したら true。 */
    suspend fun handleAuthorizationResult(data: Intent?): Boolean {
        if (!ensureConfigured()) return false
        if (data == null) {
            // ユーザーがキャンセルした場合など。未同意のまま
            _authState.value = CalendarAuthState.NotAuthorized
            return false
        }
        return withContext(Dispatchers.IO) {
            requestMutex.withLock {
                val result = runCatchingPreserveCancellation {
                    authorizationClient.getAuthorizationResultFromIntent(data)
                }.onFailure {
                    Log.w(TAG, "同意画面の結果の取得に失敗しました", it)
                }.getOrNull()

                if (result == null) {
                    clearCachedToken()
                    _authState.value = CalendarAuthState.NotAuthorized
                    return@withLock false
                }
                applyResult(result)
                !result.hasResolution()
            }
        }
    }

    /**
     * 認可を取り消してサインアウトする。
     *
     * Google 側の取り消し（revokeAccess）に失敗しても、アプリ側の状態は必ず未認可へ戻す。
     * ただし実態と食い違う案内を出さないよう、どこまで消せたかを戻り値で返す。
     */
    suspend fun signOut(): SignOutResult {
        return withContext(Dispatchers.IO) {
            requestMutex.withLock {
                val token = cachedToken
                clearCachedToken()

                // 端末側にキャッシュされたトークンを破棄する
                if (token != null) {
                    runCatchingPreserveCancellation {
                        authorizationClient.clearToken(
                            ClearTokenRequest.builder().setToken(token).build()
                        ).awaitOrNull()
                    }.onFailure { Log.w(TAG, "トークンの破棄に失敗しました", it) }
                }

                // 付与済みスコープの同意そのものを取り消す
                val revoked = runCatchingPreserveCancellation {
                    authorizationClient.revokeAccess(
                        RevokeAccessRequest.builder()
                            .setScopes(listOf(Scope(CALENDAR_EVENTS_SCOPE)))
                            .build()
                    ).awaitOrNull()
                }.onFailure { Log.w(TAG, "認可の取り消しに失敗しました", it) }.isSuccess

                _authState.value =
                    if (isConfigured) CalendarAuthState.NotAuthorized else CalendarAuthState.NotConfigured

                if (revoked) SignOutResult.REVOKED else SignOutResult.LOCAL_ONLY
            }
        }
    }

    /**
     * authorize() を呼んで結果を [authState] とトークンキャッシュに反映する。
     * 通信エラーなどで結果が得られなかった場合は失敗した [Result] を返し、状態は変更しない
     * （一時的な失敗で「未同意」に見せてしまわないため）。
     *
     * 必ず [requestMutex] を保持した状態で呼ぶこと。
     */
    private suspend fun authorizeInternal(): Result<AuthorizationResult> =
        runCatchingPreserveCancellation {
            authorizationClient.authorize(buildRequest()).awaitOrNull()
                ?: throw IllegalStateException("認可結果が取得できませんでした")
        }
            .onFailure { Log.w(TAG, "認可状態の確認に失敗しました", it) }
            .onSuccess { applyResult(it) }

    /**
     * 認可の確認に失敗した理由をユーザー向けの文言にする。
     * オフラインが圧倒的に多いため、断定できない失敗も「拒否された」ではなく接続の問題として伝える。
     */
    private fun failureMessageOf(error: Throwable): String = when {
        error is ApiException && error.statusCode in NETWORK_STATUS_CODES -> NETWORK_ERROR_MESSAGE
        error is IOException -> NETWORK_ERROR_MESSAGE
        else -> AUTHORIZATION_UNAVAILABLE_MESSAGE
    }

    /** 同意済みなら無操作で解決される認可リクエスト。 */
    private fun buildRequest(): AuthorizationRequest =
        AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(CALENDAR_EVENTS_SCOPE)))
            .build()

    private fun applyResult(result: AuthorizationResult) {
        if (result.hasResolution()) {
            // 同意画面を出さないと先に進めない状態
            clearCachedToken()
            _authState.value = CalendarAuthState.NotAuthorized
        } else {
            cacheToken(result.accessToken)
            _authState.value = CalendarAuthState.Authorized(emailOf(result))
        }
    }

    /**
     * アカウントのメールアドレス。取得できなくても致命的ではないので null を返す。
     *
     * 認可レスポンスに本人情報が含まれることは公式には保証されていないため、
     * ここで null になるのは異常ではない（UI は「連携中の Google アカウント」表示に落ちる）。
     */
    private fun emailOf(result: AuthorizationResult): String? =
        runCatchingPreserveCancellation { result.toGoogleSignInAccount()?.email }
            .onFailure { Log.w(TAG, "アカウント情報の取得に失敗しました", it) }
            .getOrNull()

    private fun validCachedToken(): String? =
        cachedToken?.takeIf { System.currentTimeMillis() < cachedTokenExpiresAt }

    /**
     * トークンを短時間だけメモリに保持する。
     * 実際の有効期限は API から取れないため、実期限（約 1 時間）より十分短い値で切り上げ、
     * 期限が来たら authorize() を呼び直す（同意済みならユーザー操作は発生しない）。
     */
    private fun cacheToken(token: String?) {
        cachedToken = token
        cachedTokenExpiresAt =
            if (token == null) 0L else System.currentTimeMillis() + TOKEN_CACHE_MILLIS
    }

    private fun clearCachedToken() {
        cachedToken = null
        cachedTokenExpiresAt = 0L
    }

    /**
     * 今回の通信に使ったトークンがキャッシュと一致する場合だけ、キャッシュを破棄する。
     *
     * 並行して別の処理が新しいトークンを取得済みのとき、後から返ってきた古い 401 応答で
     * 新トークンを巻き添えで消さないため。一致しなければ false を返す。
     */
    open suspend fun invalidateTokenIfMatches(token: String): Boolean =
        requestMutex.withLock {
            val current = cachedToken
            if (current != null && current == token) {
                clearCachedToken()
                true
            } else {
                false
            }
        }

    /** クライアント ID 未設定なら状態を [CalendarAuthState.NotConfigured] にして false を返す。 */
    private fun ensureConfigured(): Boolean {
        if (isConfigured) return true
        Log.w(TAG, "OAuth クライアント ID が未設定です（BuildConfig.GOOGLE_OAUTH_CLIENT_ID が空）")
        _authState.value = CalendarAuthState.NotConfigured
        return false
    }

    /**
     * Play Services の [Task] を suspend 化する。
     * kotlinx-coroutines-play-services の await() と同等だが、依存を増やさないため自前で実装する。
     */
    private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cont.resume(task.result)
            } else {
                cont.resumeWithException(
                    task.exception ?: IllegalStateException("Task が結果なしで終了しました")
                )
            }
        }
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

    companion object {
        private const val TAG = "GoogleAuthManager"

        /** カレンダーの予定の読み書きに必要な最小スコープ。 */
        const val CALENDAR_EVENTS_SCOPE = "https://www.googleapis.com/auth/calendar.events"

        /** トークンをメモリに保持する時間（実際の有効期限より十分短くしておく）。 */
        private const val TOKEN_CACHE_MILLIS = 5L * 60 * 1000

        /** 通信ができていないと判断できる Play Services のステータスコード。 */
        private val NETWORK_STATUS_CODES = setOf(
            CommonStatusCodes.NETWORK_ERROR,
            CommonStatusCodes.TIMEOUT,
            CommonStatusCodes.API_NOT_CONNECTED
        )

        /** オフラインなど、通信できなかったときの文言。 */
        private const val NETWORK_ERROR_MESSAGE =
            "ネットワークに接続できませんでした。通信環境を確認して再度お試しください"

        /** 原因を特定できない失敗。「拒否された」と誤解させない文言にする。 */
        private const val AUTHORIZATION_UNAVAILABLE_MESSAGE =
            "Google カレンダーとの連携状態を確認できませんでした。時間をおいて再度お試しください"

        @Volatile
        private var instance: GoogleAuthManager? = null

        /** アプリ全体で 1 つだけ使い回す。渡された Context は Application context に落とされる。 */
        fun get(context: Context): GoogleAuthManager =
            instance ?: synchronized(this) {
                instance ?: GoogleAuthManager(context.applicationContext).also { instance = it }
            }
    }
}
