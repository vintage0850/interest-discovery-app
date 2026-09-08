package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * 案件29：設定画面の「LINE連携」行タップ時に、MainActivity から
 * 公式 LINE 友だち追加 URL へ遷移する [Intent.ACTION_VIEW] が発行されることを検証する。
 *
 * Robolectric 上で実行し、実機/エミュレータ無しでも回帰を確認できるようにする。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class MainActivityLineLinkTest {

    private val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val workManagerInitRule = object : TestWatcher() {
        override fun starting(description: Description?) {
            val context = RuntimeEnvironment.getApplication()
            WorkManagerTestInitHelper.initializeTestWorkManager(context)
            // MainActivity はオンボーディング未完了時にウェルカム画面を表示するため、
            // 設定タブに到達できるよう完了済みにしておく。
            context.getSharedPreferences("onboarding", android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean("completed", true)
                .apply()
        }
    }

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(workManagerInitRule)
        .around(composeTestRule)

    @Test
    fun line連携行をタップすると公式アカウントUrlが開かれる() {
        composeTestRule.onNodeWithText("設定").performClick()
        composeTestRule.onNodeWithText("LINE連携").performClick()

        val application = RuntimeEnvironment.getApplication()
        val shadowApplication = Shadows.shadowOf(application)
        val startedIntent = shadowApplication.nextStartedActivity

        assertNotNull("LINE連携タップ後に Intent が発行されるべき", startedIntent)
        assertEquals(Intent.ACTION_VIEW, startedIntent?.action)
        assertEquals(Uri.parse("https://lin.ee/utP8awy"), startedIntent?.data)
    }
}
