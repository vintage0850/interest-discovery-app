package com.example.myapplication.data.account

import android.content.Context
import android.os.Bundle
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.myapplication.BuildConfig
import com.example.myapplication.shared.discovery.GoogleAccountState
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
 * Credential Manager の `getCredential` 呼び出しを抽象化する。
 *
 * 本番では [AndroidGoogleCredentialProvider] を使い、
 * テストでは偽装実装を注入して Android フレームワークを使わずに状態遷移を検証する。
 */
interface GoogleCredentialProvider {
    suspend fun getCredential(activityContext: Context, request: GetCredentialRequest): GetCredentialResponse
}

/**
 * 実際の Credential Manager 呼び出し。
 *
 * [CredentialManager] 自体は Application context で作成するが、
 * `getCredential()` には UI を起動する前面 Activity の context を渡す必要がある。
 */
class AndroidGoogleCredentialProvider(context: Context) : GoogleCredentialProvider {
    private val credentialManager = CredentialManager.create(context.applicationContext)

    override suspend fun getCredential(activityContext: Context, request: GetCredentialRequest): GetCredentialResponse {
        return credentialManager.getCredential(activityContext, request)
    }
}

/**
 * Google ID トークンから取り出すアカウント情報。
 */
data class GoogleAccountInfo(
    val displayName: String?,
    val email: String?,
    val photoUrl: String?
)

/**
 * [GoogleIdTokenCredential] の解析を抽象化する。
 *
 * 本番では [DefaultGoogleIdTokenParser] を使い、
 * テストでは偽装実装を注入して JWT 検証に依存せずに状態遷移を検証する。
 */
interface GoogleIdTokenParser {
    @Throws(GoogleIdTokenParsingException::class)
    fun parse(data: Bundle): GoogleAccountInfo
}

/**
 * 実際の Google ID トークン解析。
 */
class DefaultGoogleIdTokenParser : GoogleIdTokenParser {
    override fun parse(data: Bundle): GoogleAccountInfo {
        val credential = GoogleIdTokenCredential.createFrom(data)
        return GoogleAccountInfo(
            displayName = credential.displayName,
            email = credential.id,
            photoUrl = credential.profilePictureUri?.toString()
        )
    }
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
 *
 * @param credentialProvider テスト時に差し替え可能な Credential Manager 呼び出し層。
 * @param clientId GCP の OAuth クライアント ID。テスト時に差し替え可能。
 * @param idTokenParser テスト時に差し替え可能な Google ID トークン解析層。
 */
open class GoogleAccountManager(
    context: Context,
    private val credentialProvider: GoogleCredentialProvider = AndroidGoogleCredentialProvider(context.applicationContext),
    clientId: String = BuildConfig.GOOGLE_OAUTH_CLIENT_ID,
    private val idTokenParser: GoogleIdTokenParser = DefaultGoogleIdTokenParser()
) {

    private val clientId = clientId
    private val isConfigured = clientId.isNotBlank()

    // Activity を握るとリークするので必ず Application context に落とす。
    // getCredential() には呼び出し元から Activity context を渡す。
    private val appContext = context.applicationContext

    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

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
     *
     * @param activityContext 認証 UI を起動する前面 Activity の context。
     *   Application context では UI が表示されないため、呼び出し側は必ず Activity を渡すこと。
     */
    open suspend fun signIn(activityContext: Context): GoogleAccountSignInResult {
        if (!isConfigured) return GoogleAccountSignInResult.NotConfigured
        return withContext(Dispatchers.IO) {
            requestMutex.withLock {
                try {
                    val request = buildSignInRequest()
                    val result = credentialProvider.getCredential(activityContext, request)
                    handleSignInResult(result)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: GetCredentialCancellationException) {
                    // ユーザーがダイアログをキャンセルした。失敗表示が残っていれば消す。
                    resetLinkFailedToNotLinked()
                    GoogleAccountSignInResult.Cancelled
                } catch (e: GetCredentialException) {
                    // キャンセル以外の Credential Manager エラー（設定不備・通信失敗など）
                    val message = e.message ?: "Google アカウントとの連携に失敗しました"
                    _accountState.value = GoogleAccountState.LinkFailed(message)
                    GoogleAccountSignInResult.Failed(message)
                } catch (e: Exception) {
                    val message = e.message ?: "Google アカウントとの連携に失敗しました"
                    _accountState.value = GoogleAccountState.LinkFailed(message)
                    GoogleAccountSignInResult.Failed(message)
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
        val option = GetSignInWithGoogleOption.Builder(clientId)
            .build()
        return GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
    }

    private fun handleSignInResult(result: GetCredentialResponse): GoogleAccountSignInResult {
        val credential = result.credential
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val message = "無効な Google 認証情報です"
            _accountState.value = GoogleAccountState.LinkFailed(message)
            return GoogleAccountSignInResult.Failed(message)
        }
        return try {
            val info = idTokenParser.parse(credential.data)
            saveAccount(info.displayName, info.email, info.photoUrl)
            _accountState.value = GoogleAccountState.Linked(info.displayName, info.email, info.photoUrl)
            GoogleAccountSignInResult.Success
        } catch (e: GoogleIdTokenParsingException) {
            val message = "Google ID トークンの解析に失敗しました"
            _accountState.value = GoogleAccountState.LinkFailed(message)
            GoogleAccountSignInResult.Failed(message)
        }
    }

    private fun loadStoredAccount() {
        if (!isConfigured) return
        val displayName = prefs.getString(KEY_DISPLAY_NAME, null)
        val email = prefs.getString(KEY_EMAIL, null)
        val photoUrl = prefs.getString(KEY_PHOTO_URL, null)
        // 3 値のいずれかがあれば連携済みとみなす（displayName が null でも OK）。
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

    private fun resetLinkFailedToNotLinked() {
        if (_accountState.value is GoogleAccountState.LinkFailed) {
            _accountState.value =
                if (isConfigured) GoogleAccountState.NotLinked else GoogleAccountState.NotConfigured
        }
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
