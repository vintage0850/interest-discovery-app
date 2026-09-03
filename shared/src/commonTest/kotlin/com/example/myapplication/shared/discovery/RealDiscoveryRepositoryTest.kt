package com.example.myapplication.shared.discovery

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.pluginOrNull
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val SESSION_BODY = """
    {"id": 1, "student_label": "test_user", "status": "active",
     "created_at": "2026-09-02T00:00:00+00:00", "updated_at": "2026-09-02T00:00:00+00:00"}
""".trimIndent()

private fun experimentsBody(id: Int = 10, domain: String = "tech") = """
    [{"id": $id, "session_id": 1, "title": "t", "description": "d",
      "domain": "$domain", "planned_minutes": 5, "status": "generated",
      "selected_at": null, "started_at": null, "completed_at": null,
      "skipped_at": null, "selection_note": null, "skip_reason": null,
      "actual_minutes": null, "created_at": "2026-09-02T00:00:00+00:00"}]
""".trimIndent()

private fun twoExperimentsBody() = """
    [{"id": 10, "session_id": 1, "title": "t1", "description": "d1",
      "domain": "tech", "planned_minutes": 5, "status": "generated",
      "selected_at": null, "started_at": null, "completed_at": null,
      "skipped_at": null, "selection_note": null, "skip_reason": null,
      "actual_minutes": null, "created_at": "2026-09-02T00:00:00+00:00"},
     {"id": 11, "session_id": 1, "title": "t2", "description": "d2",
      "domain": "art", "planned_minutes": 10, "status": "generated",
      "selected_at": null, "started_at": null, "completed_at": null,
      "skipped_at": null, "selection_note": null, "skip_reason": null,
      "actual_minutes": null, "created_at": "2026-09-02T00:00:00+00:00"}]
""".trimIndent()

private val SUMMARY_BODY_WITH_HYPOTHESIS = """
    {"session": {"id": 1, "student_label": "test_user", "status": "active",
     "created_at": "2026-09-02T00:00:00+00:00", "updated_at": "2026-09-02T00:00:00+00:00"},
     "behavior_summary": {
       "total_signals": 0, "action_type_counts": {}, "domain_counts": {},
       "total_experiments": 2, "completed_experiments": 1, "skipped_experiments": 0,
       "avg_enjoyment": 4.0, "avg_curiosity": 5.0, "avg_retry_intent": 3.0, "avg_confidence": 0.8,
       "duration_ratio_high": [], "duration_ratio_very_high": [], "discrepancies": [],
       "total_minutes_spent": 12,
       "domain_experiment_counts": {"tech": 2},
       "domain_completed_counts": {"tech": 1}
     },
     "latest_hypothesis": {
       "id": 1, "session_id": 1, "summary": "コードを書くことに強い関心があります",
       "confidence": 0.8, "supporting_evidence": [], "suggested_next_domains": [],
       "created_at": "2026-09-02T00:00:00+00:00"
     }}
""".trimIndent()

private val SUMMARY_BODY_NO_HYPOTHESIS = """
    {"session": {"id": 1, "student_label": "test_user", "status": "active",
     "created_at": "2026-09-02T00:00:00+00:00", "updated_at": "2026-09-02T00:00:00+00:00"},
     "behavior_summary": {
       "total_signals": 0, "action_type_counts": {}, "domain_counts": {},
       "total_experiments": 0, "completed_experiments": 0, "skipped_experiments": 0,
       "avg_enjoyment": null, "avg_curiosity": null, "avg_retry_intent": null, "avg_confidence": null,
       "duration_ratio_high": [], "duration_ratio_very_high": [], "discrepancies": [],
       "total_minutes_spent": 0,
       "domain_experiment_counts": {},
       "domain_completed_counts": {}
     },
     "latest_hypothesis": null}
""".trimIndent()

private val SUMMARY_BODY_WITH_ACTION_TYPE_COUNTS = """
    {"session": {"id": 1, "student_label": "test_user", "status": "active",
     "created_at": "2026-09-02T00:00:00+00:00", "updated_at": "2026-09-02T00:00:00+00:00"},
     "behavior_summary": {
       "total_signals": 5, "action_type_counts": {"ANALYZE": 3, "CREATE": 2, "UNKNOWN": 1},
       "total_experiments": 0, "completed_experiments": 0, "skipped_experiments": 0,
       "avg_enjoyment": null, "avg_curiosity": null, "avg_retry_intent": null, "avg_confidence": null,
       "duration_ratio_high": [], "duration_ratio_very_high": [], "discrepancies": [],
       "total_minutes_spent": 0,
       "domain_experiment_counts": {},
       "domain_completed_counts": {}
     },
     "latest_hypothesis": null}
""".trimIndent()

private val SUMMARY_BODY_WITH_DOMAIN_COUNTS = """
    {"session": {"id": 1, "student_label": "test_user", "status": "active",
     "created_at": "2026-09-02T00:00:00+00:00", "updated_at": "2026-09-02T00:00:00+00:00"},
     "behavior_summary": {
       "total_signals": 0, "action_type_counts": {},
       "total_experiments": 0, "completed_experiments": 2, "skipped_experiments": 0,
       "avg_enjoyment": null, "avg_curiosity": null, "avg_retry_intent": null, "avg_confidence": null,
       "duration_ratio_high": [], "duration_ratio_very_high": [], "discrepancies": [],
       "total_minutes_spent": 12,
       "domain_experiment_counts": {"tech": 2, "art": 1},
       "domain_completed_counts": {"tech": 2, "art": 1}
     },
     "latest_hypothesis": null}
""".trimIndent()

private val SUMMARY_BODY_NO_DATA = """
    {"session": {"id": 1, "student_label": "test_user", "status": "active",
     "created_at": "2026-09-02T00:00:00+00:00", "updated_at": "2026-09-02T00:00:00+00:00"},
     "behavior_summary": {
       "total_signals": 7, "action_type_counts": {},
       "total_experiments": 0, "completed_experiments": 0, "skipped_experiments": 0,
       "avg_enjoyment": null, "avg_curiosity": null, "avg_retry_intent": null, "avg_confidence": null,
       "duration_ratio_high": [], "duration_ratio_very_high": [], "discrepancies": [],
       "total_minutes_spent": 0,
       "domain_experiment_counts": {},
       "domain_completed_counts": {}
     },
     "latest_hypothesis": null}
""".trimIndent()

private fun mockClient(handler: (path: String) -> Pair<HttpStatusCode, String>): Pair<HttpClient, MutableList<String>> {
    val requestedPaths = mutableListOf<String>()
    val engine = MockEngine { request ->
        val path = request.url.encodedPath
        requestedPaths.add(path)
        val (status, body) = handler(path)
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
    val client = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                namingStrategy = JsonNamingStrategy.SnakeCase
            })
        }
    }
    return client to requestedPaths
}

class RealDiscoveryRepositoryTest {

    @Test
    fun cycleNextExperiment_advancesWithoutExtraHttpCall() = runTest {
        val (client, paths) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/experiments/generate" -> HttpStatusCode.Created to twoExperimentsBody()
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)
        repo.getSuggestedExperiments()
        paths.clear()

        val next = repo.cycleNextExperiment()

        assertEquals(emptyList<String>(), paths)
        assertEquals("11", next.id)
    }

    @Test
    fun getExperiment_returnsFromCache() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/experiments/generate" -> HttpStatusCode.Created to twoExperimentsBody()
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)
        repo.getSuggestedExperiments()

        val experiment = repo.getExperiment("11")

        assertEquals("11", experiment.id)
    }

    @Test
    fun completeExperiment_computesConfidenceFromThreeScores() = runTest {
        var capturedBody = ""
        val engine = MockEngine { request ->
            capturedBody = (request.body as io.ktor.http.content.TextContent).text
            respond(
                content = """
                    {"id": 1, "experiment_id": 10, "enjoyment": 5, "curiosity": 4,
                     "retry_intent": 3, "confidence": 0.8, "reflection": null,
                     "created_at": "2026-09-02T00:00:00+00:00"}
                """.trimIndent(),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    namingStrategy = JsonNamingStrategy.SnakeCase
                })
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        repo.completeExperiment(experimentId = "10", enjoyment = 5, curiosity = 4, retryIntent = 3)

        assertEquals(true, capturedBody.contains("\"confidence\":0.8"))
    }

    @Test
    fun getSuggestedExperiments_createsSessionThenGeneratesExperiments() = runTest {
        val (client, paths) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/experiments/generate" -> HttpStatusCode.Created to experimentsBody()
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val experiments = repo.getSuggestedExperiments()

        assertEquals(listOf("/sessions", "/sessions/1/experiments/generate"), paths)
        assertEquals(1, experiments.size)
        assertEquals("10", experiments.first().id)
        assertEquals(BehaviorSignal.ANALYZE, experiments.first().actionType)
    }

    @Test
    fun getSuggestedExperiments_secondCallReusesSessionAndCache() = runTest {
        val (client, paths) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/experiments/generate" -> HttpStatusCode.Created to experimentsBody()
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        repo.getSuggestedExperiments()
        repo.getSuggestedExperiments()

        assertEquals(listOf("/sessions", "/sessions/1/experiments/generate"), paths)
    }

    @Test
    fun selectExperiment_postsToSelectEndpoint() = runTest {
        val (client, paths) = mockClient { path ->
            when (path) {
                "/experiments/10/select" -> HttpStatusCode.OK to experimentsBody().removeSurrounding("[", "]")
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        repo.selectExperiment("10")

        assertEquals(listOf("/experiments/10/select"), paths)
    }

    @Test
    fun startExperiment_postsToStartEndpoint() = runTest {
        val (client, paths) = mockClient { path ->
            when (path) {
                "/experiments/10/start" -> HttpStatusCode.OK to experimentsBody().removeSurrounding("[", "]")
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        repo.startExperiment("10")

        assertEquals(listOf("/experiments/10/start"), paths)
    }

    @Test
    fun skipExperiment_postsToSkipEndpoint() = runTest {
        val (client, paths) = mockClient { path ->
            when (path) {
                "/experiments/10/skip" -> HttpStatusCode.OK to experimentsBody().removeSurrounding("[", "]")
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        repo.skipExperiment("10")

        assertEquals(listOf("/experiments/10/skip"), paths)
    }

    @Test
    fun completeExperiment_invalidatesCache_soNextGetSuggestedExperimentsRefetches() = runTest {
        val (client, paths) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/experiments/generate" -> HttpStatusCode.Created to experimentsBody()
                "/experiments/10/complete" -> HttpStatusCode.Created to """
                    {"id": 1, "experiment_id": 10, "enjoyment": 5, "curiosity": 4,
                     "retry_intent": 3, "confidence": 0.8, "reflection": null,
                     "created_at": "2026-09-02T00:00:00+00:00"}
                """.trimIndent()
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)
        repo.getSuggestedExperiments()
        paths.clear()

        repo.completeExperiment(experimentId = "10", enjoyment = 5, curiosity = 4, retryIntent = 3)
        repo.getSuggestedExperiments()

        assertEquals(listOf("/experiments/10/complete", "/sessions/1/experiments/generate"), paths)
    }

    @Test
    fun getDiscovery_mapsBehaviorSummaryAndHypothesis() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/summary" -> HttpStatusCode.OK to SUMMARY_BODY_WITH_HYPOTHESIS
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val data = repo.getDiscovery()

        assertEquals(true, data.observation.contains("1"))
        assertEquals("コードを書くことに強い関心があります", data.hypothesis)
    }

    @Test
    fun getDiscovery_withNoHypothesisYet_returnsFallbackText() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/summary" -> HttpStatusCode.OK to SUMMARY_BODY_NO_HYPOTHESIS
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val data = repo.getDiscovery()

        assertEquals(false, data.hypothesis.isBlank())
    }

    @Test
    fun getReportData_mapsCompletedCountAndMinutesSpent() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/summary" -> HttpStatusCode.OK to SUMMARY_BODY_WITH_HYPOTHESIS
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val report = repo.getReportData()

        assertEquals(1, report.totalCompletedCount)
        assertEquals(12, report.totalMinutesSpent)
    }

    @Test
    fun getDomainFields_mapsDomainCountsFromSummary() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/summary" -> HttpStatusCode.OK to SUMMARY_BODY_WITH_HYPOTHESIS
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val fields = repo.getDomainFields()
        val techField = fields.first { it.id == "tech" }
        val artField = fields.first { it.id == "art" }

        assertEquals(2, techField.experimentCount)
        assertEquals(1, techField.triedCount)
        assertEquals(ExploreStatus.TRIED, techField.status)
        assertEquals(0, artField.experimentCount)
        assertEquals(ExploreStatus.UNEXPLORED, artField.status)
    }

    @Test
    fun getSettings_returnsFromInjectedStorage() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/summary" -> HttpStatusCode.OK to SUMMARY_BODY_NO_HYPOTHESIS
                else -> error("unexpected path: $path")
            }
        }
        val storage = InMemoryDiscoverySettingsStorage()
        storage.save(MyDataSettings(notificationsEnabled = false, reminderTime = "07:30"))
        val repo = RealDiscoveryRepository(httpClient = client, settingsStorage = storage)

        val settings = repo.getSettings()

        assertEquals(false, settings.notificationsEnabled)
        assertEquals("07:30", settings.reminderTime)
    }

    @Test
    fun getReportData_usesActionTypeCountsWhenPresent() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/summary" -> HttpStatusCode.OK to SUMMARY_BODY_WITH_ACTION_TYPE_COUNTS
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val report = repo.getReportData()

        assertEquals(BehaviorSignal.ANALYZE, report.topSignal)
        assertEquals(mapOf("分析する" to 3, "つくる" to 2), report.signalDistribution)
    }

    @Test
    fun getReportData_fallsBackToDomainCompletedCountsWhenActionTypeCountsEmpty() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/summary" -> HttpStatusCode.OK to SUMMARY_BODY_WITH_DOMAIN_COUNTS
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val report = repo.getReportData()

        assertEquals(BehaviorSignal.ANALYZE, report.topSignal)
        assertEquals(mapOf("分析する" to 2, "つくる" to 1), report.signalDistribution)
    }

    @Test
    fun getReportData_returnsEmptyDistributionAndDefaultTopSignalWhenNoData() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/summary" -> HttpStatusCode.OK to SUMMARY_BODY_NO_DATA
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val report = repo.getReportData()

        assertEquals(BehaviorSignal.ANALYZE, report.topSignal)
        assertEquals(emptyMap<String, Int>(), report.signalDistribution)
    }

    @Test
    fun getReportData_returnsEmptyWeeklyInsightsAndChangeFromPast() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/summary" -> HttpStatusCode.OK to SUMMARY_BODY_WITH_ACTION_TYPE_COUNTS
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val report = repo.getReportData()

        assertEquals("", report.weeklyInsights)
        assertEquals("", report.changeFromPast)
    }

    @Test
    fun getSettings_overridesSavedSignalCountFromTotalSignals() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/summary" -> HttpStatusCode.OK to SUMMARY_BODY_NO_DATA
                else -> error("unexpected path: $path")
            }
        }
        val storage = InMemoryDiscoverySettingsStorage()
        storage.save(MyDataSettings(savedSignalCount = 10))
        val repo = RealDiscoveryRepository(httpClient = client, settingsStorage = storage)

        val settings = repo.getSettings()

        assertEquals(7, settings.savedSignalCount)
    }

    @Test
    fun updateSettings_persistsToInjectedStorage() = runTest {
        val (client, _) = mockClient { path -> error("unexpected path: $path") }
        val storage = InMemoryDiscoverySettingsStorage()
        val repo = RealDiscoveryRepository(httpClient = client, settingsStorage = storage)

        repo.updateSettings(MyDataSettings(notificationsEnabled = false, reminderTime = "21:00"))

        assertEquals("21:00", storage.load().reminderTime)
        assertEquals(false, storage.load().notificationsEnabled)
    }

    @Test
    fun skipExperiment_invalidatesCache_soNextGetSuggestedExperimentsRefetches() = runTest {
        val (client, paths) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/experiments/generate" -> HttpStatusCode.Created to experimentsBody()
                "/experiments/10/skip" -> HttpStatusCode.OK to experimentsBody().removeSurrounding("[", "]")
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)
        repo.getSuggestedExperiments()
        paths.clear()

        repo.skipExperiment("10")
        repo.getSuggestedExperiments()

        assertEquals(listOf("/experiments/10/skip", "/sessions/1/experiments/generate"), paths)
    }

    @Test
    fun getHomeState_returnsGeneratedExperimentsFromBackend() = runTest {
        val (client, paths) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/experiments/generate" -> HttpStatusCode.Created to twoExperimentsBody()
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val home = repo.getHomeState()

        assertEquals(2, home.todayExperiments.size)
        assertEquals("10", home.featuredExperiment?.id)
        assertTrue(paths.contains("/sessions"))
        assertTrue(paths.contains("/sessions/1/experiments/generate"))
    }

    @Test
    fun selectExperiment_throwsWhenServerReturns4xx() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/experiments/10/select" -> HttpStatusCode.BadRequest to """{"detail":"invalid"}"""
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val exception = runCatching { repo.selectExperiment("10") }.exceptionOrNull()

        assertTrue(exception is DiscoveryApiException)
        assertTrue(exception.message?.contains("400") == true)
    }

    @Test
    fun startExperiment_throwsWhenServerReturns5xx() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/experiments/10/start" -> HttpStatusCode.InternalServerError to """{"detail":"error"}"""
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val exception = runCatching { repo.startExperiment("10") }.exceptionOrNull()

        assertTrue(exception is DiscoveryApiException)
        assertTrue(exception.message?.contains("500") == true)
    }

    @Test
    fun getSuggestedExperiments_throwsWhenSessionCreationFails() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.ServiceUnavailable to """{"detail":"busy"}"""
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val exception = runCatching { repo.getSuggestedExperiments() }.exceptionOrNull()

        assertTrue(exception is DiscoveryApiException)
        assertTrue(exception.message?.contains("503") == true)
    }

    @Test
    fun defaultHttpClient_doesNotInstallLoggingWhenDisabled() = runTest {
        val client = defaultDiscoveryHttpClient("", enableHttpLogging = false)

        assertNull(client.pluginOrNull(Logging))

        client.close()
    }

    @Test
    fun defaultHttpClient_installsLoggingWhenEnabled() = runTest {
        val client = defaultDiscoveryHttpClient("", enableHttpLogging = true)

        assertNotNull(client.pluginOrNull(Logging))

        client.close()
    }
}
