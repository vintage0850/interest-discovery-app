package com.example.myapplication.work

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.myapplication.shared.discovery.DiscoveryRepository
import com.example.myapplication.shared.discovery.FakeDiscoveryRepository
import com.example.myapplication.shared.discovery.InMemoryDiscoverySettingsStorage
import com.example.myapplication.shared.discovery.InMemorySessionStorage
import com.example.myapplication.shared.discovery.MyDataSettings
import com.example.myapplication.shared.discovery.ReportType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class DiscoveryPeriodicReportWorkerTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val clock = Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ZoneOffset.UTC)

    private fun buildWorker(
        repository: DiscoveryRepository,
        settingsStorage: InMemoryDiscoverySettingsStorage = InMemoryDiscoverySettingsStorage(),
        sessionStorage: InMemorySessionStorage = InMemorySessionStorage().apply { saveLastSessionId(1) },
        notifier: FakeDiscoveryReportNotifier,
        permissionGranted: Boolean = true
    ): DiscoveryPeriodicReportWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = DiscoveryPeriodicReportWorker(
                context = appContext,
                params = workerParameters,
                settingsStorage = settingsStorage,
                sessionStorage = sessionStorage,
                repository = repository,
                notifier = notifier,
                notificationPermissionGranted = { permissionGranted },
                clock = clock
            )
        }
        return TestListenableWorkerBuilder<DiscoveryPeriodicReportWorker>(context)
            .setWorkerFactory(factory)
            .build()
    }

    @Test
    fun 通知設定OFFならスキップする() = runBlocking {
        val settingsStorage = InMemoryDiscoverySettingsStorage().apply {
            save(MyDataSettings(notificationsEnabled = false))
        }
        val notifier = FakeDiscoveryReportNotifier()
        val worker = buildWorker(
            repository = FakeDiscoveryRepository(enableArtificialDelay = false),
            settingsStorage = settingsStorage,
            notifier = notifier
        )

        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(notifier.notifiedReports.isEmpty())
    }

    @Test
    fun 権限なしならスキップする() = runBlocking {
        val notifier = FakeDiscoveryReportNotifier()
        val worker = buildWorker(
            repository = FakeDiscoveryRepository(enableArtificialDelay = false),
            notifier = notifier,
            permissionGranted = false
        )

        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(notifier.notifiedReports.isEmpty())
    }

    @Test
    fun セッションなしならスキップする() = runBlocking {
        val repo = FakeDiscoveryRepository(enableArtificialDelay = false).apply {
            activeSessionId = null
        }
        val sessionStorage = InMemorySessionStorage()
        val notifier = FakeDiscoveryReportNotifier()
        val worker = buildWorker(
            repository = repo,
            sessionStorage = sessionStorage,
            notifier = notifier
        )

        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(notifier.notifiedReports.isEmpty())
    }

    @Test
    fun 正常系で週次と月次の両方が通知されキーが保存される() = runBlocking {
        val settingsStorage = InMemoryDiscoverySettingsStorage().apply {
            save(MyDataSettings(notificationsEnabled = true))
        }
        val notifier = FakeDiscoveryReportNotifier()
        val worker = buildWorker(
            repository = FakeDiscoveryRepository(enableArtificialDelay = false),
            settingsStorage = settingsStorage,
            notifier = notifier
        )

        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(2, notifier.notifiedReports.size)
        assertEquals(ReportType.WEEKLY to 1, notifier.notifiedReports[0])
        assertEquals(ReportType.MONTHLY to 1, notifier.notifiedReports[1])

        assertEquals("2026-09-07", settingsStorage.getLastNotifiedWeekKey(1))
        assertEquals("2026-09", settingsStorage.getLastNotifiedMonthKey(1))
    }

    @Test
    fun 既に通知済みの期間ならスキップされる() = runBlocking {
        val settingsStorage = InMemoryDiscoverySettingsStorage().apply {
            save(MyDataSettings(notificationsEnabled = true))
            saveLastNotifiedWeekKey(1, "2026-09-07")
            saveLastNotifiedMonthKey(1, "2026-09")
        }
        val notifier = FakeDiscoveryReportNotifier()
        val worker = buildWorker(
            repository = FakeDiscoveryRepository(enableArtificialDelay = false),
            settingsStorage = settingsStorage,
            notifier = notifier
        )

        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(notifier.notifiedReports.isEmpty())
    }

    @Test
    fun 週次APIが失敗しても月次は通知され月次キーのみ更新される() = runBlocking {
        val settingsStorage = InMemoryDiscoverySettingsStorage().apply {
            save(MyDataSettings(notificationsEnabled = true))
        }
        val failingRepo = object : DiscoveryRepository by FakeDiscoveryRepository(enableArtificialDelay = false) {
            override suspend fun getWeeklyNarrative(): com.example.myapplication.shared.discovery.WeeklyNarrative {
                throw RuntimeException("Weekly narrative failed (e.g. 503)")
            }
        }
        val notifier = FakeDiscoveryReportNotifier()
        val worker = buildWorker(
            repository = failingRepo,
            settingsStorage = settingsStorage,
            notifier = notifier
        )

        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, notifier.notifiedReports.size)
        assertEquals(ReportType.MONTHLY to 1, notifier.notifiedReports[0])

        assertNull(settingsStorage.getLastNotifiedWeekKey(1))
        assertEquals("2026-09", settingsStorage.getLastNotifiedMonthKey(1))
    }

    @Test
    fun 月次APIが失敗しても週次は通知され週次キーのみ更新される() = runBlocking {
        val settingsStorage = InMemoryDiscoverySettingsStorage().apply {
            save(MyDataSettings(notificationsEnabled = true))
        }
        val failingRepo = object : DiscoveryRepository by FakeDiscoveryRepository(enableArtificialDelay = false) {
            override suspend fun getMonthlyNarrative(): com.example.myapplication.shared.discovery.MonthlyNarrative {
                throw RuntimeException("Monthly narrative failed (e.g. 503)")
            }
        }
        val notifier = FakeDiscoveryReportNotifier()
        val worker = buildWorker(
            repository = failingRepo,
            settingsStorage = settingsStorage,
            notifier = notifier
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(listOf(ReportType.WEEKLY to 1), notifier.notifiedReports)
        assertEquals("2026-09-07", settingsStorage.getLastNotifiedWeekKey(1))
        assertNull(settingsStorage.getLastNotifiedMonthKey(1))
    }

    @Test
    fun 通知発行例外では該当キーを保存せず他方は継続する() = runBlocking {
        val settingsStorage = InMemoryDiscoverySettingsStorage().apply {
            save(MyDataSettings(notificationsEnabled = true))
        }
        val notifier = FakeDiscoveryReportNotifier(failingType = ReportType.WEEKLY)
        val worker = buildWorker(
            repository = FakeDiscoveryRepository(enableArtificialDelay = false),
            settingsStorage = settingsStorage,
            notifier = notifier
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(listOf(ReportType.MONTHLY to 1), notifier.notifiedReports)
        assertNull(settingsStorage.getLastNotifiedWeekKey(1))
        assertEquals("2026-09", settingsStorage.getLastNotifiedMonthKey(1))
    }

    @Test
    fun 通知発行が失敗した場合は該当キーを保存しない() = runBlocking {
        val settingsStorage = InMemoryDiscoverySettingsStorage().apply {
            save(MyDataSettings(notificationsEnabled = true))
        }
        val notifier = FakeDiscoveryReportNotifier(refuseType = ReportType.WEEKLY)
        val worker = buildWorker(
            repository = FakeDiscoveryRepository(enableArtificialDelay = false),
            settingsStorage = settingsStorage,
            notifier = notifier
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(listOf(ReportType.MONTHLY to 1), notifier.notifiedReports)
        assertNull(settingsStorage.getLastNotifiedWeekKey(1))
        assertEquals("2026-09", settingsStorage.getLastNotifiedMonthKey(1))
    }

    private class FakeDiscoveryReportNotifier(
        private val failingType: ReportType? = null,
        private val refuseType: ReportType? = null
    ) : DiscoveryReportNotifier {
        val notifiedReports = mutableListOf<Pair<ReportType, Int>>()
        override fun notifyReport(reportType: ReportType, sessionId: Int): Boolean {
            if (reportType == failingType) throw IllegalStateException("notification failed")
            if (reportType == refuseType) return false
            notifiedReports.add(reportType to sessionId)
            return true
        }
    }
}
