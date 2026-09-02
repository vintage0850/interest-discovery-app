package com.example.myapplication.shared.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.shared.discovery.AppTab
import com.example.myapplication.shared.discovery.DiscoveryState
import com.example.myapplication.shared.discovery.Experiment

/**
 * 興味発見アプリのメイン画面（ボトムナビゲーションバー付き）。
 * Home / Discover / Explore / Report / Settings の5つの主要タブを統合。
 */
@Composable
fun DiscoveryMainScaffold(
    discoveryState: DiscoveryState,
    onStartExperiment: (Experiment) -> Unit,
    onViewDiscoveryDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTab by discoveryState.currentTab.collectAsState()
    val homeState by discoveryState.homeState.collectAsState()
    val discoveryUiState by discoveryState.discoveryState.collectAsState()
    val exploreUiState by discoveryState.exploreState.collectAsState()
    val reportUiState by discoveryState.reportState.collectAsState()
    val settingsUiState by discoveryState.settingsState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            // ボトムナビゲーションバー
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DiscoveryColors.Surface)
                    .border(1.dp, DiscoveryColors.BorderSubtle)
                    .navigationBarsPadding()
                    .height(64.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppTab.entries.forEach { tab ->
                        val isSelected = currentTab == tab
                        val textColor = if (isSelected) DiscoveryColors.Accent else DiscoveryColors.TextSecondary

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    role = Role.Tab,
                                    onClick = { discoveryState.selectTab(tab) }
                                )
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = tab.iconEmoji,
                                fontSize = if (isSelected) 20.sp else 18.sp
                            )
                            Text(
                                text = tab.title,
                                color = textColor,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (currentTab) {
                AppTab.HOME -> DiscoveryHomeScreen(
                    uiState = homeState,
                    onStartExperiment = onStartExperiment,
                    onViewDiscovery = {
                        discoveryState.selectTab(AppTab.DISCOVER)
                    },
                    onSelectScenario = { discoveryState.changeScenario(it) },
                    onRetry = { discoveryState.loadHomeData() }
                )
                AppTab.DISCOVER -> DiscoverTabScreen(
                    discoveryState = discoveryUiState,
                    onToggleEvidence = { discoveryState.toggleEvidenceExpanded() },
                    onTryNext = onStartExperiment,
                    onRetry = { discoveryState.loadDiscovery() }
                )
                AppTab.EXPLORE -> ExploreTabScreen(
                    exploreState = exploreUiState,
                    onFilterChange = { discoveryState.setExploreFilter(it) },
                    onFieldClick = { field ->
                        // 分野タップ時、最初の実験を開始
                        val exp = homeState.homeData?.todayExperiments?.firstOrNull()
                        if (exp != null) onStartExperiment(exp)
                    },
                    onRetry = { discoveryState.loadExploreData() }
                )
                AppTab.REPORT -> ReportTabScreen(
                    reportState = reportUiState,
                    onRetry = { discoveryState.loadReportData() }
                )
                AppTab.SETTINGS -> SettingsTabScreen(
                    settingsState = settingsUiState,
                    onToggleNotifications = { discoveryState.toggleNotifications(it) },
                    onResetData = { discoveryState.resetAllData { discoveryState.selectTab(AppTab.HOME) } }
                )
            }
        }
    }
}
