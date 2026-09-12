package com.mikke.discovery.data.account

import android.content.Context
import android.os.Bundle
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.test.core.app.ApplicationProvider
import com.mikke.discovery.shared.discovery.GoogleAccountState
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 案件32：GoogleAccountManager の取得・保存・復元・解除・エラー分類を検証する。
 *
 * Robolectric 上で実行し、実機/エミュレータ無しでも回帰を確認できるようにする。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class GoogleAccountManagerTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @Test
    fun 未設定時の初期状態はNotConfigured() {
        val manager = createManager(configured = false)
        assertEquals(GoogleAccountState.NotConfigured, manager.accountState.value)
    }

    @Test
    fun 設定済みで保存データなしの初期状態はNotLinked() {
        val manager = createManager(configured = true)
        assertEquals(GoogleAccountState.NotLinked, manager.accountState.value)
    }

    @Test
    fun 保存データありの初期状態はLinked() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("google_account_link", Context.MODE_PRIVATE)
            .edit()
            .putString("display_name", "Test User")
            .putString("email", "test@example.com")
            .putString("photo_url", "https://example.com/photo.jpg")
            .apply()

        val manager = createManager(configured = true, context = context)
        val state = manager.accountState.value as GoogleAccountState.Linked
        assertEquals("Test User", state.displayName)
        assertEquals("test@example.com", state.email)
        assertEquals("https://example.com/photo.jpg", state.photoUrl)
    }

    @Test
    fun displayNameのみnullの保存データもLinkedとして復元する() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("google_account_link", Context.MODE_PRIVATE)
            .edit()
            .putString("email", "only-email@example.com")
            .apply()

        val manager = createManager(configured = true, context = context)
        val state = manager.accountState.value
        assertTrue(state is GoogleAccountState.Linked)
        assertNull((state as GoogleAccountState.Linked).displayName)
        assertEquals("only-email@example.com", state.email)
    }

    @Test
    fun signIn成功でLinkedになり情報が保存される() = runTest(testDispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fakeProvider = FakeCredentialProvider()
        fakeProvider.response = GetCredentialResponse(
            CustomCredential(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, Bundle())
        )
        val fakeParser = FakeIdTokenParser()
        fakeParser.info = GoogleAccountInfo(
            displayName = "Sign In User",
            email = "signin@example.com",
            photoUrl = "https://example.com/signin.jpg"
        )
        val manager = createManager(
            configured = true,
            context = context,
            provider = fakeProvider,
            idTokenParser = fakeParser
        )

        val result = manager.signIn(context)

        assertEquals(GoogleAccountSignInResult.Success, result)
        val state = manager.accountState.value as GoogleAccountState.Linked
        assertEquals("Sign In User", state.displayName)
        assertEquals("signin@example.com", state.email)
        assertEquals("https://example.com/signin.jpg", state.photoUrl)

        val prefs = context.getSharedPreferences("google_account_link", Context.MODE_PRIVATE)
        assertEquals("Sign In User", prefs.getString("display_name", null))
        assertEquals("signin@example.com", prefs.getString("email", null))
        assertEquals("https://example.com/signin.jpg", prefs.getString("photo_url", null))
    }

    @Test
    fun signInキャンセルでCancelledになりstateはNotLinkedに戻る() = runTest(testDispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fakeProvider = FakeCredentialProvider()
        fakeProvider.error = GetCredentialCancellationException("cancelled")
        val manager = createManager(configured = true, context = context, provider = fakeProvider)

        val result = manager.signIn(context)

        assertEquals(GoogleAccountSignInResult.Cancelled, result)
        assertEquals(GoogleAccountState.NotLinked, manager.accountState.value)
    }

    @Test
    fun signInキャンセルでLinkFailedからNotLinkedに戻る() = runTest(testDispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = FakeCredentialProvider()
        provider.error = GetCredentialUnknownException("previous failure")
        val manager = createManager(configured = true, context = context, provider = provider)
        manager.signIn(context)
        assertTrue(manager.accountState.value is GoogleAccountState.LinkFailed)

        provider.error = GetCredentialCancellationException("cancelled")
        val result = manager.signIn(context)

        assertEquals(GoogleAccountSignInResult.Cancelled, result)
        assertEquals(GoogleAccountState.NotLinked, manager.accountState.value)
    }

    @Test
    fun signInのGetCredentialExceptionはFailedになりLinkFailedになる() = runTest(testDispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fakeProvider = FakeCredentialProvider()
        fakeProvider.error = GetCredentialProviderConfigurationException("no provider")
        val manager = createManager(configured = true, context = context, provider = fakeProvider)

        val result = manager.signIn(context)

        assertTrue(result is GoogleAccountSignInResult.Failed)
        assertTrue(manager.accountState.value is GoogleAccountState.LinkFailed)
    }

    @Test
    fun signInの一般例外はFailedになりLinkFailedになる() = runTest(testDispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fakeProvider = FakeCredentialProvider()
        fakeProvider.error = RuntimeException("unexpected")
        val manager = createManager(configured = true, context = context, provider = fakeProvider)

        val result = manager.signIn(context)

        assertTrue(result is GoogleAccountSignInResult.Failed)
        assertTrue(manager.accountState.value is GoogleAccountState.LinkFailed)
    }

    @Test
    fun signOutでNotLinkedになり保存データが削除される() = runTest(testDispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("google_account_link", Context.MODE_PRIVATE)
            .edit()
            .putString("display_name", "Test User")
            .putString("email", "test@example.com")
            .putString("photo_url", "https://example.com/photo.jpg")
            .apply()

        val manager = createManager(configured = true, context = context)
        assertTrue(manager.accountState.value is GoogleAccountState.Linked)

        val result = manager.signOut()

        assertEquals(GoogleAccountSignOutResult.Success, result)
        assertEquals(GoogleAccountState.NotLinked, manager.accountState.value)

        val prefs = context.getSharedPreferences("google_account_link", Context.MODE_PRIVATE)
        assertNull(prefs.getString("display_name", null))
        assertNull(prefs.getString("email", null))
        assertNull(prefs.getString("photo_url", null))
    }

    @Test
    fun signOutでLinkFailedからもNotLinkedに戻る() = runTest(testDispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = FakeCredentialProvider()
        provider.error = GetCredentialUnknownException("previous failure")
        val manager = createManager(configured = true, context = context, provider = provider)
        manager.signIn(context)
        assertTrue(manager.accountState.value is GoogleAccountState.LinkFailed)

        manager.signOut()

        assertEquals(GoogleAccountState.NotLinked, manager.accountState.value)
    }

    @Test
    fun 無効なcredentialTypeはFailedになる() = runTest(testDispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val invalidCredential = CustomCredential("invalid_type", Bundle())
        val fakeProvider = FakeCredentialProvider()
        fakeProvider.response = GetCredentialResponse(invalidCredential)
        val manager = createManager(configured = true, context = context, provider = fakeProvider)

        val result = manager.signIn(context)

        assertTrue(result is GoogleAccountSignInResult.Failed)
        assertTrue(manager.accountState.value is GoogleAccountState.LinkFailed)
    }

    private fun createManager(
        configured: Boolean,
        context: Context = ApplicationProvider.getApplicationContext(),
        provider: GoogleCredentialProvider = FakeCredentialProvider(),
        idTokenParser: GoogleIdTokenParser = DefaultGoogleIdTokenParser()
    ): GoogleAccountManager {
        val clientId = if (configured) "test-client-id" else ""
        return GoogleAccountManager(context, provider, clientId = clientId, idTokenParser = idTokenParser)
    }

    private class FakeCredentialProvider : GoogleCredentialProvider {
        var response: GetCredentialResponse? = null
        var error: Throwable? = null

        override suspend fun getCredential(activityContext: Context, request: GetCredentialRequest): GetCredentialResponse {
            error?.let { throw it }
            return response ?: throw GetCredentialUnknownException("not configured")
        }
    }

    private class FakeIdTokenParser : GoogleIdTokenParser {
        var info: GoogleAccountInfo? = null
        var error: Throwable? = null

        override fun parse(data: Bundle): GoogleAccountInfo {
            error?.let { throw it }
            return info ?: throw GoogleIdTokenParsingException(RuntimeException("not configured"))
        }
    }
}
