package com.example.myapplication

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
import com.example.myapplication.data.calendar.AuthorizationStep
import com.example.myapplication.data.calendar.CalendarAuthState
import com.example.myapplication.data.calendar.GoogleAuthManager
import com.example.myapplication.shared.db.AndroidDatabaseDriverFactory
import com.example.myapplication.shared.discovery.AndroidDiscoverySettingsStorage
import com.example.myapplication.shared.discovery.AndroidOnboardingStorage
import com.example.myapplication.shared.ui.App
import com.example.myapplication.shared.ui.LocalGoogleCalendarLinkHandler
import com.example.myapplication.work.DiscoveryNotificationScheduler
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val googleAuthManager by lazy { GoogleAuthManager.get(application) }

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
        val notificationExperimentId = intent.getStringExtra(EXTRA_EXPERIMENT_ID)

        // Google Calendar 連携は Android ホスト側で完結する。
        // 認可状態を KMP UI へ反映し、設定行タップで OAuth 同意画面を起動する。
        setContent {
            val authState by googleAuthManager.authState.collectAsState()
            val isLinked = authState is CalendarAuthState.Authorized

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
                }
            ) {
                App(
                    driverFactory,
                    discoverySettingsStorage,
                    onboardingStorage = onboardingStorage,
                    enableDiscoveryHttpLogging = BuildConfig.DEBUG,
                    discoveryBaseUrl = BuildConfig.DISCOVERY_BASE_URL,
                    notificationExperimentId = notificationExperimentId,
                    isGoogleCalendarLinked = isLinked
                )
            }
        }

        // 定期 Worker は通知設定が ON のユーザーに対して 1 度登録すればよい。
        // Worker 自身が ON/OFF・権限・認可を都度判定するため、ここでは無条件でスケジュールする。
        DiscoveryNotificationScheduler.schedule(application)
    }

    companion object {
        /** 通知タップ時に [MainActivity] へ渡す experiment_id のキー。 */
        const val EXTRA_EXPERIMENT_ID = "extra_experiment_id"
    }
}
