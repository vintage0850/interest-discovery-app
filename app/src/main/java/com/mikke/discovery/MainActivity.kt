package com.mikke.discovery

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.mikke.discovery.data.account.GoogleAccountManager
import com.mikke.discovery.shared.discovery.GoogleAccountState
import com.mikke.discovery.data.calendar.AuthorizationStep
import com.mikke.discovery.data.calendar.CalendarAuthState
import com.mikke.discovery.data.calendar.GoogleAuthManager
import com.mikke.discovery.shared.db.AndroidDatabaseDriverFactory
import com.mikke.discovery.shared.discovery.AndroidDiscoverySettingsStorage
import com.mikke.discovery.shared.discovery.AndroidOnboardingStorage
import com.mikke.discovery.shared.discovery.AndroidSessionStorage
import com.mikke.discovery.shared.ui.App
import com.mikke.discovery.shared.ui.LocalGoogleAccountLinkHandler
import com.mikke.discovery.shared.ui.LocalGoogleAccountSignOutHandler
import com.mikke.discovery.shared.ui.LocalGoogleCalendarLinkHandler
import com.mikke.discovery.shared.ui.LocalLineLinkHandler
import com.mikke.discovery.work.DiscoveryNotificationScheduler
import com.mikke.discovery.work.DiscoveryPeriodicReportScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private const val LINE_ADD_FRIEND_URL = "https://lin.ee/utP8awy"

class MainActivity : ComponentActivity() {

    private val reportIntentState = MutableStateFlow<Pair<String?, Int?>?>(null)

    private val googleAuthManager by lazy { GoogleAuthManager.get(application) }
    private val googleAccountManager by lazy { GoogleAccountManager.get(application) }

    private val authorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        lifecycleScope.launch {
            googleAuthManager.handleAuthorizationResult(result.data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val driverFactory = AndroidDatabaseDriverFactory(application)
        val discoverySettingsStorage = AndroidDiscoverySettingsStorage.get(application)
        val onboardingStorage = AndroidOnboardingStorage.get(application)
        val sessionStorage = AndroidSessionStorage.get(application)
        val notificationExperimentId = intent.getStringExtra(EXTRA_EXPERIMENT_ID)

        val initialReportType = intent.getStringExtra(EXTRA_REPORT_TYPE)
        val initialSessionId = if (intent.hasExtra(EXTRA_REPORT_SESSION_ID)) {
            intent.getIntExtra(EXTRA_REPORT_SESSION_ID, -1).takeIf { it >= 0 }
        } else null
        if (initialReportType != null || initialSessionId != null) {
            reportIntentState.value = initialReportType to initialSessionId
        }

        // Google Calendar 連携は Android ホスト側で完結する。
        // 認可状態を KMP UI へ反映し、設定行タップで OAuth 同意画面を起動する。
        setContent {
            val authState by googleAuthManager.authState.collectAsState()
            val isCalendarLinked = authState is CalendarAuthState.Authorized
            val accountState by googleAccountManager.accountState.collectAsState()
            val reportIntent by reportIntentState.collectAsState()

            CompositionLocalProvider(
                LocalGoogleCalendarLinkHandler provides {
                    lifecycleScope.launch {
                        when (val step = googleAuthManager.authorizationIntentSender()) {
                            is AuthorizationStep.Consent -> {
                                val request = IntentSenderRequest.Builder(step.intentSender).build()
                                authorizationLauncher.launch(request)
                            }
                            else -> Unit
                        }
                    }
                },
                LocalGoogleAccountLinkHandler provides {
                    lifecycleScope.launch {
                        googleAccountManager.signIn(this@MainActivity)
                    }
                },
                LocalGoogleAccountSignOutHandler provides {
                    lifecycleScope.launch {
                        googleAccountManager.signOut()
                    }
                },
                LocalLineLinkHandler provides {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(LINE_ADD_FRIEND_URL))
                    )
                }
            ) {
                App(
                    driverFactory,
                    discoverySettingsStorage,
                    onboardingStorage = onboardingStorage,
                    sessionStorage = sessionStorage,
                    enableDiscoveryHttpLogging = BuildConfig.DEBUG,
                    discoveryBaseUrl = BuildConfig.DISCOVERY_BASE_URL,
                    notificationExperimentId = notificationExperimentId,
                    notificationReportType = reportIntent?.first,
                    notificationSessionId = reportIntent?.second,
                    isGoogleCalendarLinked = isCalendarLinked,
                    googleAccountState = accountState
                )
            }
        }

        // 定期 Worker は通知設定が ON のユーザーに対して 1 度登録すればよい。
        // Worker 自身が ON/OFF・権限・認可を都度判定するため、ここでは無条件でスケジュールする。
        DiscoveryNotificationScheduler.schedule(application)
        DiscoveryPeriodicReportScheduler.schedule(application)
    }

    public override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val reportType = intent.getStringExtra(EXTRA_REPORT_TYPE)
        val sessionId = if (intent.hasExtra(EXTRA_REPORT_SESSION_ID)) {
            intent.getIntExtra(EXTRA_REPORT_SESSION_ID, -1).takeIf { it >= 0 }
        } else null
        if (reportType != null || sessionId != null) {
            reportIntentState.value = reportType to sessionId
        }
    }

    companion object {
        /** 通知タップ時に [MainActivity] へ渡す experiment_id のキー。 */
        const val EXTRA_EXPERIMENT_ID = "extra_experiment_id"

        /** 気付きレポート通知タップ時に [MainActivity] へ渡す report_type のキー。 */
        const val EXTRA_REPORT_TYPE = "report_type"

        /** 気付きレポート通知タップ時に [MainActivity] へ渡す session_id のキー。 */
        const val EXTRA_REPORT_SESSION_ID = "session_id"
    }
}
