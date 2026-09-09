package com.example.myapplication.data.account

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.example.myapplication.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Google アカウント連携（Sign-In）の状態。
 *
 * これは Google カレンダー API の OAuth 同意とは別の概念。
 * [GoogleAuthManager] はカレンダー API スコープの同意を扱い、
 * こちらはプロフィール表示のみの軽量な「アカウント連携」を扱う。
 */
sealed interface GoogleAccountState {
    /** BuildConfig にクライアント ID が設定されていない（機能自体を隠す想定）。 */
    data object NotConfigured : GoogleAccountState

    /** 未連携。サインインフローの開始が可能。 */
    data object NotLinked : GoogleAccountState

    /**
     * 連携済み。
     *
     * @param displayName Google アカウントの表示名（取得できなければ null）
     * @param email Google アカウントのメールアドレス（取得できなければ null）
     * @param photoUrl プロフィール画像 URL（取得できなければ null）
     */
    data class Linked(
        val displayName: String?,
        val email: String?,
        val photoUrl: String?
    ) : GoogleAccountState
}

/**
 * Google アカウント連携（Sign-In）の結果。
 */
sealed interface GoogleAccountSignInResult {
    data object Success : GoogleAccountSignInResult
    data object NotConfigured : GoogleAccountSignInResult
    data object Cancelled : GoogleAccountSignInResult
    data class Failed(val message: String) : GoogleAccountSignInResult
}

/**
 * Google アカウント連携（Sign-In）の解除結果。
 */
sealed interface GoogleAccountSignOutResult {
    data object Success : GoogleAccountSignOutResult
}

/**
 * Credential Manager を使って Google アカウントのサインインを行う。
 *
 * 取得するのは身元情報（displayName / email / photoUrl）のみで、
 * アクセストークンや API スコープは要求しない。
 * 取得した情報は端末の SharedPreferences にのみ保存し、バックエンドへは送信しない。
 *
 * [GetSignInWithGoogleOption] に渡すクライアント ID は、原則として GCP の
 * 「Web アプリケーション」タイプの OAuth クライアント ID が必要。
 * 未設定の場合は [GoogleAccountState.NotConfigured] となり、機能が無効になる。
 */
open class GoogleAccountManager(context: Context) {

    // Activity を握るとリークするので必ず Application context に落とす
    private val appContext = context.applicationContext

    private val credentialManager by lazy { CredentialManager.create(appContext) }
    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val isConfigured: Boolean
        get() = BuildConfig.GOOGLE_OAUTH_CLIENT_ID.isNotBlank()

    private val _accountState = MutableStateFlow<GoogleAccountState>(
        if (isConfigured) GoogleAccountState.NotLinked else GoogleAccountState.NotConfigured
    )

    /** 現在のアカウント連携状態。UI から collect する。 */
    val accountState: StateFlow<GoogleAccountState> = _accountState.asStateFlow()

    private val requestMutex = Mutex()

    init {
        loadStoredAccount()
    }

    /**
     * Google サインインフローを開始する。
     *
     * 成功すれば [GoogleAccountState.Linked] に遷移し、アカウント情報を端末に保存する。
     * ユーザーがキャンセルした場合は [GoogleAccountSignInResult.Cancelled] を返す。
     */
    open suspend fun signIn(): GoogleAccountSignInResult {
        if (!isConfigured) return GoogleAccountSignInResult.NotConfigured
        return withContext(Dispatchers.IO) {
            requestMutex.withLock {
                try {
                    val request = buildSignInRequest()
                    val result = credentialManager.getCredential(appContext, request)
                    handleSignInResult(result)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: GetCredentialException) {
                    GoogleAccountSignInResult.Cancelled
                } catch (e: Exception) {
                    GoogleAccountSignInResult.Failed(
                        e.message ?: "Google アカウントとの連携に失敗しました"
                    )
                }
            }
        }
    }

    /**
     * 連携情報を端末から削除し、[GoogleAccountState.NotLinked] に戻す。
     *
     * Google 側のトークンや同意の取り消しは行わない（取得していないため）。
     */
    open suspend fun signOut(): GoogleAccountSignOutResult {
        return withContext(Dispatchers.IO) {
            requestMutex.withLock {
                clearStoredAccount()
                _accountState.value =
                    if (isConfigured) GoogleAccountState.NotLinked else GoogleAccountState.NotConfigured
                GoogleAccountSignOutResult.Success
            }
        }
    }

    private fun buildSignInRequest(): GetCredentialRequest {
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_OAUTH_CLIENT_ID)
            .build()
        return GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
    }

    private fun handleSignInResult(result: GetCredentialResponse): GoogleAccountSignInResult {
        val credential = result.credential
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleAccountSignInResult.Failed("無効な Google 認証情報です")
        }
        return try {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val displayName = googleIdTokenCredential.displayName
            val email = googleIdTokenCredential.id
            val photoUrl = googleIdTokenCredential.profilePictureUri?.toString()
            saveAccount(displayName, email, photoUrl)
            _accountState.value = GoogleAccountState.Linked(displayName, email, photoUrl)
            GoogleAccountSignInResult.Success
        } catch (e: GoogleIdTokenParsingException) {
            GoogleAccountSignInResult.Failed("Google ID トークンの解析に失敗しました")
        }
    }

    private fun loadStoredAccount() {
        if (!isConfigured) return
        val displayName = prefs.getString(KEY_DISPLAY_NAME, null)
        val email = prefs.getString(KEY_EMAIL, null)
        val photoUrl = prefs.getString(KEY_PHOTO_URL, null)
        if (displayName != null || email != null || photoUrl != null) {
            _accountState.value = GoogleAccountState.Linked(displayName, email, photoUrl)
        }
    }

    private fun saveAccount(displayName: String?, email: String?, photoUrl: String?) {
        prefs.edit()
            .putString(KEY_DISPLAY_NAME, displayName)
            .putString(KEY_EMAIL, email)
            .putString(KEY_PHOTO_URL, photoUrl)
            .apply()
    }

    private fun clearStoredAccount() {
        prefs.edit()
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_EMAIL)
            .remove(KEY_PHOTO_URL)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "google_account_link"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_PHOTO_URL = "photo_url"

        @Volatile
        private var instance: GoogleAccountManager? = null

        /** アプリ全体で 1 つだけ使い回す。渡された Context は Application context に落とされる。 */
        fun get(context: Context): GoogleAccountManager =
            instance ?: synchronized(this) {
                instance ?: GoogleAccountManager(context.applicationContext).also { instance = it }
            }
    }
}
