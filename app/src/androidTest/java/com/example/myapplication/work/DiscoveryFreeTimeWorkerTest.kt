package com.example.myapplication.work

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.myapplication.data.calendar.CalendarAuthState
import com.example.myapplication.data.calendar.CalendarEventListResponse
import com.example.myapplication.data.calendar.CalendarResult
import com.example.myapplication.data.calendar.GoogleAuthManager
import com.example.myapplication.data.calendar.GoogleCalendarApi
import com.example.myapplication.data.calendar.GoogleCalendarSync
import com.example.myapplication.shared.discovery.DiscoveryRepository
import com.example.myapplication.shared.discovery.Experiment
import com.example.myapplication.shared.discovery.FakeDiscoveryRepository
import com.example.myapplication.shared.discovery.NotificationCandidate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * [DiscoveryFreeTimeWorker] の分岐（未連携／時間帯外／空き無し／正常系）を
 * [TestListenableWorkerBuilder] で検証する。
 *
 * 実際の Play Services やカレンダー API・backend には依存せず、フェイクに差し替える。
 */
@RunWith(AndroidJUnit4::class)
class DiscoveryFreeTimeWorkerTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val zone: ZoneId = ZoneOffset.UTC

    /** 8:00〜21:00 の範囲内。テストの基準時刻。 */
    private fun clockAt(hour: Int): Clock =
        Clock.fixed(Instant.parse("2026-08-23T%02d:00:00Z".format(hour)), zone)

    private fun buildWorker(
        authState: CalendarAuthState,
        calendarSync: GoogleCalendarSync,
        repository: DiscoveryRepository,
        clock: Clock,
        notifier: DiscoveryNotifier
    ): DiscoveryFreeTimeWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = DiscoveryFreeTimeWorker(
                context = appContext,
                params = workerParameters,
                authManager = GoogleAuthManager.get(appContext),
                repository = repository,
                calendarSync = calendarSync,
                notifier = notifier,
                authState = { authState },
                notificationPermissionGranted = { true },
                clock = clock
            )
        }
        return TestListenableWorkerBuilder<DiscoveryFreeTimeWorker>(context)
            .setWorkerFactory(factory)
            .build()
    }

    @Test
    fun 未連携ならスキップする() = runBlocking {
        val notifier = FakeNotifier()
        val worker = buildWorker(
            authState = CalendarAuthState.NotAuthorized,
            calendarSync = FakeCalendarSync(events = emptyList()),
            repository = FakeDiscoveryRepository(enableArtificialDelay = false),
            clock = clockAt(10),
            notifier = notifier
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue("未連携なのに通知が発行された", notifier.notifiedCandidates.isEmpty())
    }

    @Test
    fun 時間帯外ならスキップする() = runBlocking {
        val notifier = FakeNotifier()
        val worker = buildWorker(
            authState = CalendarAuthState.Authorized(null),
            calendarSync = FakeCalendarSync(events = emptyList()),
            repository = FakeDiscoveryRepository(enableArtificialDelay = false),
            clock = clockAt(7), // 8:00 より前
            notifier = notifier
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue("時間帯外なのに通知が発行された", notifier.notifiedCandidates.isEmpty())
    }

    @Test
    fun 空き時間が無ければスキップする() = runBlocking {
        val notifier = FakeNotifier()
        val now = Instant.parse("2026-08-23T10:00:00Z")
        val windowEnd = Instant.parse("2026-08-23T21:00:00Z")
        val worker = buildWorker(
            authState = CalendarAuthState.Authorized(null),
            calendarSync = FakeCalendarSync(
                events = listOf(
                    com.example.myapplication.data.calendar.CalendarEventSlot(now, windowEnd)
                )
            ),
            repository = FakeDiscoveryRepository(enableArtificialDelay = false),
            clock = Clock.fixed(now, zone),
            notifier = notifier
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue("空き時間が無いのに通知が発行された", notifier.notifiedCandidates.isEmpty())
    }

    @Test
    fun 正常系で通知が発行される() = runBlocking {
        val notifier = FakeNotifier()
        val now = Instant.parse("2026-08-23T10:00:00Z")
        val repository = FakeDiscoveryRepository(enableArtificialDelay = false)

        val worker = buildWorker(
            authState = CalendarAuthState.Authorized(null),
            calendarSync = FakeCalendarSync(events = emptyList()),
            repository = repository,
            clock = Clock.fixed(now, zone),
            notifier = notifier
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, notifier.notifiedCandidates.size)
        val candidate = notifier.notifiedCandidates.first()
        assertTrue(
            "DIVE_CANDIDATE または TRIED/EXPLORED のいずれかの候補が通知される",
            candidate.experiment.id.isNotBlank()
        )
    }

    /** listTodayEvents だけ差し替える。他のメソッドはこのテストで使わない。 */
    private class FakeCalendarSync(
        private val events: List<com.example.myapplication.data.calendar.CalendarEventSlot>
    ) : GoogleCalendarSync(GoogleAuthManager.get(dummyContext()), DummyApi, NoOpLogger) {
        override suspend fun listTodayEvents(
            now: Instant,
            zoneId: ZoneId
        ): CalendarResult<List<com.example.myapplication.data.calendar.CalendarEventSlot>> =
            CalendarResult.Success(events)

        companion object {
            fun dummyContext(): Context = InstrumentationRegistry.getInstrumentation().targetContext
        }
    }

    private object DummyApi : GoogleCalendarApi {
        override suspend fun insertEvent(
            authorization: String,
            event: com.example.myapplication.data.calendar.CalendarEventRequest
        ) = throw UnsupportedOperationException()

        override suspend fun patchEvent(
            authorization: String,
            eventId: String,
            event: com.example.myapplication.data.calendar.CalendarEventRequest
        ) = throw UnsupportedOperationException()

        override suspend fun deleteEvent(authorization: String, eventId: String): Response<Unit> =
            throw UnsupportedOperationException()

        override suspend fun listEvents(
            authorization: String,
            timeMin: String,
            timeMax: String,
            singleEvents: Boolean,
            orderBy: String
        ): Response<CalendarEventListResponse> = throw UnsupportedOperationException()
    }

    private object NoOpLogger : com.example.myapplication.data.calendar.CalendarLogger {
        override fun d(tag: String, message: String) = Unit
        override fun w(tag: String, message: String, throwable: Throwable?) = Unit
    }

    private class FakeNotifier : DiscoveryNotifier {
        val notifiedCandidates = mutableListOf<NotificationCandidate>()
        override fun notifyDiscoveryCandidate(candidate: NotificationCandidate) {
            notifiedCandidates.add(candidate)
        }
    }
}
