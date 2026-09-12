package com.mikke.discovery.shared.ui

import androidx.compose.ui.window.ComposeUIViewController
import com.mikke.discovery.shared.db.DatabaseDriverFactory
import platform.UIKit.UIViewController

/** iOS側（Swift）から `App(driverFactory)` を呼び出すためのブリッジ関数。 */
fun MainViewController(driverFactory: DatabaseDriverFactory): UIViewController =
    ComposeUIViewController { App(driverFactory, enableDiscoveryHttpLogging = false) }
