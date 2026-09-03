package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.myapplication.shared.db.AndroidDatabaseDriverFactory
import com.example.myapplication.shared.discovery.AndroidDiscoverySettingsStorage
import com.example.myapplication.shared.discovery.AndroidOnboardingStorage
import com.example.myapplication.shared.ui.App

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val driverFactory = AndroidDatabaseDriverFactory(application)
        val discoverySettingsStorage = AndroidDiscoverySettingsStorage.get(application)
        val onboardingStorage = AndroidOnboardingStorage.get(application)
        setContent {
            App(
                driverFactory,
                discoverySettingsStorage,
                onboardingStorage = onboardingStorage,
                enableDiscoveryHttpLogging = BuildConfig.DEBUG,
                discoveryBaseUrl = BuildConfig.DISCOVERY_BASE_URL
            )
        }
    }
}
