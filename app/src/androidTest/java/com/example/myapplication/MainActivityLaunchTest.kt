package com.example.myapplication

import android.Manifest
import android.os.Build
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [MainActivity] が起動時にクラッシュしないことを確認する回帰テスト。
 *
 * [TaskViewModel] はテスト容易性のため repository/authManager/calendarSync を
 * デフォルト引数で受け取れるコンストラクタになっており、標準の
 * AndroidViewModelFactory（Application 型 1 引数のみを想定）ではインスタンス化に失敗して
 * 実機でアプリが即クラッシュしていた。このテストはその再発を検知する。
 */
@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {

    /**
     * 「空き時間です」通知のための POST_NOTIFICATIONS 権限リクエストを事前に許可しておく。
     * 許可済みなら初回起動時のダイアログが出ないため、ダイアログにフォーカスを奪われて
     * Activity が RESUMED に到達できない誤検知を防げる（API 33 未満では対象権限が無いので無害）。
     */
    @get:Rule
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    @Test
    fun mainActivityがクラッシュせずRESUMEDまで到達する() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun coldStart_週次レポートIntentで起動してもRESUMEDまで到達する() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_REPORT_TYPE, "weekly")
            putExtra(MainActivity.EXTRA_REPORT_SESSION_ID, 1)
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun warmStart_月次レポートIntentでonNewIntentを呼び出してもRESUMEDを維持する() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val newIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_REPORT_TYPE, "monthly")
                putExtra(MainActivity.EXTRA_REPORT_SESSION_ID, 1)
            }
            scenario.onActivity { activity ->
                activity.onNewIntent(newIntent)
            }
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
