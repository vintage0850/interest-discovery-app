package com.example.myapplication.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myapplication.shared.db.DatabaseDriverFactory
import com.example.myapplication.shared.discovery.DiscoverySettingsStorage
import com.example.myapplication.shared.discovery.DiscoveryState
import com.example.myapplication.shared.discovery.InMemoryDiscoverySettingsStorage
import com.example.myapplication.shared.discovery.InMemoryOnboardingStorage
import com.example.myapplication.shared.discovery.OnboardingStorage
import com.example.myapplication.shared.discovery.RealDiscoveryRepository
import com.example.myapplication.shared.ui.discovery.DiscoveryMainScaffold
import com.example.myapplication.shared.ui.discovery.DiscoveryResultScreen
import com.example.myapplication.shared.ui.discovery.ExperimentDetailScreen
import com.example.myapplication.shared.ui.discovery.ExperimentRunningScreen
import com.example.myapplication.shared.ui.discovery.ReflectionScreen
import com.example.myapplication.shared.ui.onboarding.OnboardingBasicInfo as OnboardingBasicInfoData
import com.example.myapplication.shared.ui.onboarding.OnboardingBasicInfoScreen
import com.example.myapplication.shared.ui.onboarding.OnboardingSelfCheckScreen
import com.example.myapplication.shared.ui.onboarding.OnboardingWelcomeScreen
import kotlinx.serialization.Serializable

// オンボーディング（Onboarding Flow）のルート
@Serializable object OnboardingWelcome
@Serializable object OnboardingBasicInfo
@Serializable object OnboardingSelfCheck

// 興味発見（Discovery Flow）のルート
@Serializable object DiscoveryHome
@Serializable object DiscoveryDetail
@Serializable object DiscoveryRunning
@Serializable object DiscoveryReflection
@Serializable object DiscoveryResult

@Composable
fun App(
    driverFactory: DatabaseDriverFactory? = null,
    discoverySettingsStorage: DiscoverySettingsStorage = InMemoryDiscoverySettingsStorage(),
    onboardingStorage: OnboardingStorage = InMemoryOnboardingStorage(),
    enableDiscoveryHttpLogging: Boolean = false,
    discoveryBaseUrl: String = "http://localhost:8000",
    modifier: Modifier = Modifier
) {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        val discoveryState = remember {
            DiscoveryState(
                RealDiscoveryRepository(
                    baseUrl = discoveryBaseUrl,
                    settingsStorage = discoverySettingsStorage,
                    enableHttpLogging = enableDiscoveryHttpLogging
                ),
                scope
            )
        }

        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        var savedBasicInfo by remember { mutableStateOf<OnboardingBasicInfoData?>(null) }

        LaunchedEffect(Unit) {
            discoveryState.messages.collect { message ->
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
        }

        val startDestination = if (onboardingStorage.hasCompletedOnboarding()) {
            DiscoveryHome
        } else {
            OnboardingWelcome
        }

        Box(modifier = modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize()
            ) {
            // 0. オンボーディング画面群
            composable<OnboardingWelcome> {
                OnboardingWelcomeScreen(
                    onNext = { navController.navigate(OnboardingBasicInfo) }
                )
            }

            composable<OnboardingBasicInfo> {
                OnboardingBasicInfoScreen(
                    onNext = { info ->
                        savedBasicInfo = info
                        navController.navigate(OnboardingSelfCheck)
                    }
                )
            }

            composable<OnboardingSelfCheck> {
                OnboardingSelfCheckScreen(
                    onComplete = { score ->
                        val info = savedBasicInfo
                        discoveryState.completeOnboarding(
                            nickname = info?.nickname?.takeIf { it.isNotBlank() },
                            ageRange = info?.ageRange?.takeIf { it.isNotBlank() },
                            schoolStage = info?.schoolStage?.takeIf { it.isNotBlank() },
                            optionalInterests = info?.optionalInterests ?: emptyList(),
                            initialSelfUnderstandingScore = score
                        ) {
                            onboardingStorage.markCompleted()
                            navController.navigate(DiscoveryHome) {
                                popUpTo(OnboardingWelcome) { inclusive = true }
                            }
                        }
                    }
                )
            }

            // 1. メイン画面（ボトムナビゲーション付き: Home, Discover, Explore, Report, Settings）
            composable<DiscoveryHome> {
                DiscoveryMainScaffold(
                    discoveryState = discoveryState,
                    onStartExperiment = { experiment ->
                        discoveryState.selectExperiment(experiment) {
                            navController.navigate(DiscoveryDetail) { launchSingleTop = true }
                        }
                    },
                    onViewDiscoveryDetail = {
                        discoveryState.loadDiscovery()
                        navController.navigate(DiscoveryResult) { launchSingleTop = true }
                    }
                )
            }

            // 2. 実験詳細画面
            composable<DiscoveryDetail> {
                val selectedExperiment by discoveryState.selectedExperiment.collectAsState()
                if (selectedExperiment != null) {
                    ExperimentDetailScreen(
                        experiment = selectedExperiment!!,
                        onStartExperiment = {
                            discoveryState.startExperiment {
                                navController.navigate(DiscoveryRunning) { launchSingleTop = true }
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            // 3. 実験中（タイマー稼働）画面
            composable<DiscoveryRunning> {
                val runningState by discoveryState.runningState.collectAsState()
                ExperimentRunningScreen(
                    runningState = runningState,
                    onFinish = {
                        discoveryState.finishExperiment()
                        navController.navigate(DiscoveryReflection) { launchSingleTop = true }
                    },
                    onCancel = {
                        discoveryState.finishExperiment()
                        navController.popBackStack(DiscoveryHome, inclusive = false)
                    }
                )
            }

            // 4. 振り返り画面
            composable<DiscoveryReflection> {
                val reflectionState by discoveryState.reflectionState.collectAsState()
                ReflectionScreen(
                    reflectionState = reflectionState,
                    onEnjoymentChange = { rating: Int -> discoveryState.setReflectionEnjoyment(rating) },
                    onCuriosityChange = { rating: Int -> discoveryState.setReflectionCuriosity(rating) },
                    onRetryIntentChange = { rating: Int -> discoveryState.setReflectionRetryIntent(rating) },
                    onSubmit = {
                        discoveryState.submitReflection {
                            navController.navigate(DiscoveryResult) { launchSingleTop = true }
                        }
                    }
                )
            }

            // 5. 発見結果画面
            composable<DiscoveryResult> {
                val discoveryUiState by discoveryState.discoveryState.collectAsState()
                DiscoveryResultScreen(
                    discoveryState = discoveryUiState,
                    onTryNext = { nextExp ->
                        discoveryState.selectExperiment(nextExp) {
                            navController.navigate(DiscoveryDetail) { launchSingleTop = true }
                        }
                    },
                    onBackHome = {
                        navController.popBackStack(DiscoveryHome, inclusive = false)
                    },
                    onRetry = { discoveryState.loadDiscovery() }
                )
            }
            }

            // §16: 反応送信の失敗など、discoveryState.messagesで流れるエラー通知を実際に画面へ表示する。
            // 以前は snackbarHostState.showSnackbar() が呼ばれるだけで、描画するSnackbarHostが
            // どこにも配置されておらず、ユーザーには何も見えていなかった（実バグ）。
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
