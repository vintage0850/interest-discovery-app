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

private val SUMMARY_BODY_WITH_DIVE_CANDIDATE = """
    {"session": {"id": 1, "student_label": "test_user", "status": "active",
     "created_at": "2026-09-02T00:00:00+00:00", "updated_at": "2026-09-02T00:00:00+00:00"},
     "behavior_summary": {
       "total_signals": 0, "action_type_counts": {}, "domain_counts": {},
       "total_experiments": 3, "completed_experiments": 3, "skipped_experiments": 0,
       "avg_enjoyment": 4.0, "avg_curiosity": 4.0, "avg_retry_intent": 4.0, "avg_confidence": 0.70,
       "duration_ratio_high": [1, 3], "duration_ratio_very_high": [], "discrepancies": [],
       "total_minutes_spent": 36,
       "domain_experiment_counts": {"tech": 2, "art": 1, "music": 1},
       "domain_completed_counts": {"tech": 2, "art": 1, "music": 0},
       "dive_candidate_domains": ["tech"]
     },
     "latest_hypothesis": null}
""".trimIndent()

private val SUMMARY_BODY_WITH_CANDIDATE_BUT_UNEXPLORED = """
    {"session": {"id": 1, "student_label": "test_user", "status": "active",
     "created_at": "2026-09-02T00:00:00+00:00", "updated_at": "2026-09-02T00:00:00+00:00"},
     "behavior_summary": {
       "total_signals": 0, "action_type_counts": {}, "domain_counts": {},
       "total_experiments": 0, "completed_experiments": 0, "skipped_experiments": 0,
       "avg_enjoyment": null, "avg_curiosity": null, "avg_retry_intent": null, "avg_confidence": null,
       "duration_ratio_high": [], "duration_ratio_very_high": [], "discrepancies": [],
       "total_minutes_spent": 0,
       "domain_experiment_counts": {},
       "domain_completed_counts": {},
       "dive_candidate_domains": ["tech"]
     },
     "latest_hypothesis": null}
""".trimIndent()

private val SUMMARY_BODY_WITH_CANDIDATE_BUT_EXPLORED = """
    {"session": {"id": 1, "student_label": "test_user", "status": "active",
     "created_at": "2026-09-02T00:00:00+00:00", "updated_at": "2026-09-02T00:00:00+00:00"},
     "behavior_summary": {
       "total_signals": 0, "action_type_counts": {}, "domain_counts": {},
       "total_experiments": 1, "completed_experiments": 0, "skipped_experiments": 0,
       "avg_enjoyment": null, "avg_curiosity": null, "avg_retry_intent": null, "avg_confidence": null,
       "duration_ratio_high": [], "duration_ratio_very_high": [], "discrepancies": [],
       "total_minutes_spent": 0,
       "domain_experiment_counts": {"tech": 1},
       "domain_completed_counts": {"tech": 0},
       "dive_candidate_domains": ["tech"]
     },
     "latest_hypothesis": null}
""".trimIndent()

private val SUMMARY_BODY_OLD_FORMAT = """
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

private val SUMMARY_BODY_WITH_UNKNOWN_CANDIDATE = """
    {"session": {"id": 1, "student_label": "test_user", "status": "active",
     "created_at": "2026-09-02T00:00:00+00:00", "updated_at": "2026-09-02T00:00:00+00:00"},
     "behavior_summary": {
       "total_signals": 0, "action_type_counts": {},
       "total_experiments": 1, "completed_experiments": 1, "skipped_experiments": 0,
       "avg_enjoyment": null, "avg_curiosity": null, "avg_retry_intent": null, "avg_confidence": null,
       "duration_ratio_high": [], "duration_ratio_very_high": [], "discrepancies": [],
       "total_minutes_spent": 12,
       "domain_experiment_counts": {"tech": 1},
       "domain_completed_counts": {"tech": 1},
       "dive_candidate_domains": ["tech", "unknown_domain"]
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

private val WEEKLY_NARRATIVE_BODY = """
    {"weekly_insights": "今週は分析する活動に集中できました。",
     "change_from_past": "先週より試行回数が増えています。"}
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
                "/sessions/1/report/weekly-narrative" -> HttpStatusCode.OK to WEEKLY_NARRATIVE_BODY
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val report = repo.getReportData()

        assertEquals(1, report.totalCompletedCount)
        assertEquals(12, report.totalMinutesSpent)
    }

    @Test
    fun getDomainFields_mapsDiveCandidateStatusForMatchingCompletedDomain() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/summary" -> HttpStatusCode.OK to SUMMARY_BODY_WITH_DIVE_CANDIDATE
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val fields = repo.getDomainFields()

        val techField = fields.first { it.id == "tech" }
        val artField = fields.first { it.id == "art" }
        assertEquals(ExploreStatus.DIVE_CANDIDATE, techField.status)
        assertEquals(ExploreStatus.TRIED, artField.status)
    }

    @Test
    fun getDomainFields_diveCandidateIgnoredWhenExperimentCountZero() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/summary" -> HttpStatusCode.OK to SUMMARY_BODY_WITH_CANDIDATE_BUT_UNEXPLORED
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val fields = repo.getDomainFields()

        val techField = fields.first { it.id == "tech" }
        assertEquals(ExploreStatus.UNEXPLORED, techField.status)
    }

    @Test
    fun getDomainFields_diveCandidateIgnoredWhenTriedCountZero() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/summary" -> HttpStatusCode.OK to SUMMARY_BODY_WITH_CANDIDATE_BUT_EXPLORED
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val fields = repo.getDomainFields()

        val techField = fields.first { it.id == "tech" }
        assertEquals(ExploreStatus.EXPLORED, techField.status)
    }

    @Test
    fun getDomainFields_oldFormatWithoutDiveCandidateDefaultsToEmptyList() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/summary" -> HttpStatusCode.OK to SUMMARY_BODY_OLD_FORMAT
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val fields = repo.getDomainFields()

        val techField = fields.first { it.id == "tech" }
        assertEquals(ExploreStatus.TRIED, techField.status)
    }

    @Test
    fun getDomainFields_unknownCandidateDomainIgnored() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/summary" -> HttpStatusCode.OK to SUMMARY_BODY_WITH_UNKNOWN_CANDIDATE
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val fields = repo.getDomainFields()

        val techField = fields.first { it.id == "tech" }
        assertEquals(ExploreStatus.DIVE_CANDIDATE, techField.status)
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
                "/sessions/1/report/weekly-narrative" -> HttpStatusCode.OK to WEEKLY_NARRATIVE_BODY
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
                "/sessions/1/report/weekly-narrative" -> HttpStatusCode.OK to WEEKLY_NARRATIVE_BODY
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
                "/sessions/1/report/weekly-narrative" -> HttpStatusCode.OK to WEEKLY_NARRATIVE_BODY
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val report = repo.getReportData()

        assertEquals(BehaviorSignal.ANALYZE, report.topSignal)
        assertEquals(emptyMap<String, Int>(), report.signalDistribution)
    }

    @Test
    fun getReportData_fallsBackToDefaultNarrativeWhenWeeklyNarrativeFails() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/summary" -> HttpStatusCode.OK to SUMMARY_BODY_WITH_HYPOTHESIS
                "/sessions/1/report/weekly-narrative" -> HttpStatusCode.ServiceUnavailable to """{"detail":"Gemini unavailable"}"""
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val report = repo.getReportData()

        assertEquals(1, report.totalCompletedCount)
        assertEquals(12, report.totalMinutesSpent)
        assertEquals(BehaviorSignal.ANALYZE, report.topSignal)
        assertEquals(mapOf("分析する" to 1), report.signalDistribution)
        assertEquals("週次レポートは現在取得できません。", report.weeklyInsights)
        assertEquals("週次レポートは現在取得できません。", report.changeFromPast)
    }

    @Test
    fun getWeeklyNarrative_stillThrowsWhenServerReturnsError() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/report/weekly-narrative" -> HttpStatusCode.ServiceUnavailable to """{"detail":"Gemini unavailable"}"""
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val exception = runCatching { repo.getWeeklyNarrative() }.exceptionOrNull()

        assertTrue(exception is DiscoveryApiException)
        assertTrue(exception.message?.contains("503") == true)
    }

    @Test
    fun getWeeklyNarrative_deserializesSnakeCaseResponse() = runTest {
        val (client, paths) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/report/weekly-narrative" -> HttpStatusCode.OK to WEEKLY_NARRATIVE_BODY
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val narrative = repo.getWeeklyNarrative()

        assertEquals("今週は分析する活動に集中できました。", narrative.weeklyInsights)
        assertEquals("先週より試行回数が増えています。", narrative.changeFromPast)
        assertEquals(listOf("/sessions", "/sessions/1/report/weekly-narrative"), paths)
    }

    @Test
    fun getReportData_includesWeeklyNarrativeFromDedicatedEndpoint() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/summary" -> HttpStatusCode.OK to SUMMARY_BODY_WITH_ACTION_TYPE_COUNTS
                "/sessions/1/report/weekly-narrative" -> HttpStatusCode.OK to WEEKLY_NARRATIVE_BODY
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val report = repo.getReportData()

        assertEquals("今週は分析する活動に集中できました。", report.weeklyInsights)
        assertEquals("先週より試行回数が増えています。", report.changeFromPast)
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
    fun sendHypothesisFeedback_postsOnceAndReturnsOutcomeWithoutExtraGet() = runTest {
        val (client, paths) = mockClient { path ->
            when (path) {
                "/hypotheses/1/feedback" -> HttpStatusCode.Created to """
                    {"feedback": {"id": 1, "hypothesis_id": 1, "reaction": "agree",
                     "created_at": "2026-09-04T00:00:00+00:00"},
                     "updated_hypothesis": {"id": 1, "session_id": 1,
                     "summary": "比較してから決める傾向がある", "confidence": 0.65,
                     "supporting_evidence": [], "suggested_next_domains": [],
                     "created_at": "2026-09-04T00:00:00+00:00"},
                     "new_criterion": {"id": 5, "session_id": 1, "label": "比較してから決める傾向がある",
                     "description": "比較してから決める傾向がある", "confidence": 0.65,
                     "source_hypothesis_id": 1, "user_confirmed": true,
                     "created_at": "2026-09-04T00:00:00+00:00", "updated_at": "2026-09-04T00:00:00+00:00"}}
                """.trimIndent()
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val outcome = repo.sendHypothesisFeedback(1, HypothesisReaction.AGREE)

        // POSTレスポンスだけで状態が組み立てられ、追加のGETは発生しない（Gate4指摘の是正確認）。
        assertEquals(listOf("/hypotheses/1/feedback"), paths)
        assertEquals("比較してから決める傾向がある", outcome.hypothesisSummary)
        assertEquals(0.65f, outcome.hypothesisConfidence)
        assertEquals(5, outcome.criterion?.id)
        assertEquals("Appearing", outcome.criterion?.confidenceLabel)
    }

    @Test
    fun sendHypothesisFeedback_withoutPromotion_returnsNullCriterion() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/hypotheses/1/feedback" -> HttpStatusCode.Created to """
                    {"feedback": {"id": 2, "hypothesis_id": 1, "reaction": "unsure",
                     "created_at": "2026-09-04T00:00:00+00:00"},
                     "updated_hypothesis": {"id": 1, "session_id": 1,
                     "summary": "まだ判断中", "confidence": 0.4,
                     "supporting_evidence": [], "suggested_next_domains": [],
                     "created_at": "2026-09-04T00:00:00+00:00"},
                     "new_criterion": null}
                """.trimIndent()
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val outcome = repo.sendHypothesisFeedback(1, HypothesisReaction.UNSURE)

        assertEquals(0.4f, outcome.hypothesisConfidence)
        assertNull(outcome.criterion)
    }

    @Test
    fun sendHypothesisFeedback_throwsWhenServerReturns4xx() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/hypotheses/999/feedback" -> HttpStatusCode.NotFound to """{"detail":"not found"}"""
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val exception = runCatching {
            repo.sendHypothesisFeedback(999, HypothesisReaction.AGREE)
        }.exceptionOrNull()

        assertTrue(exception is DiscoveryApiException)
        assertTrue(exception.message?.contains("404") == true)
    }

    @Test
    fun updateHypothesis_postsEmptyBodyToUpdateEndpoint() = runTest {
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/sessions" -> respond(
                    content = SESSION_BODY,
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/sessions/1/hypothesis/update" -> {
                    capturedBody = (request.body as io.ktor.http.content.TextContent).text
                    respond(
                        content = """
                            {"id": 2, "session_id": 1, "summary": "更新後の仮説",
                             "confidence": 0.8, "supporting_evidence": [], "suggested_next_domains": [],
                             "created_at": "2026-09-02T00:00:00+00:00"}
                        """.trimIndent(),
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("unexpected path: ${request.url.encodedPath}")
            }
        }
        val customClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    namingStrategy = JsonNamingStrategy.SnakeCase
                })
            }
        }
        val repo = RealDiscoveryRepository(httpClient = customClient)

        repo.updateHypothesis()

        assertEquals("{}", capturedBody)
    }

    @Test
    fun updateHypothesis_succeedsWhenResponseIsNull() = runTest {
        val (client, paths) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/hypothesis/update" -> HttpStatusCode.Created to "null"
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        repo.updateHypothesis()

        assertTrue(paths.contains("/sessions/1/hypothesis/update"))
    }

    @Test
    fun updateHypothesis_throwsWhenServerReturns503() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/hypothesis/update" -> HttpStatusCode.ServiceUnavailable to """{"detail":"Gemini unavailable"}"""
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val exception = runCatching { repo.updateHypothesis() }.exceptionOrNull()

        assertTrue(exception is DiscoveryApiException)
        assertTrue(exception.message?.contains("503") == true)
    }

    @Test
    fun updateHypothesis_throwsWhenServerReturns4xx() = runTest {
        val (client, _) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/hypothesis/update" -> HttpStatusCode.BadRequest to """{"detail":"invalid"}"""
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val exception = runCatching { repo.updateHypothesis() }.exceptionOrNull()

        assertTrue(exception is DiscoveryApiException)
        assertTrue(exception.message?.contains("400") == true)
    }

    @Test
    fun completeOnboarding_patchesToOnboardingEndpoint() = runTest {
        var capturedMethod: io.ktor.http.HttpMethod? = null
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/sessions") {
                respond(
                    content = SESSION_BODY,
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else if (request.url.encodedPath == "/sessions/1/onboarding") {
                capturedMethod = request.method
                capturedBody = (request.body as io.ktor.http.content.TextContent).text
                respond(
                    content = """
                        {"id": 1, "student_label": "test_user", "nickname": "Taro", "status": "active",
                         "age_range": "16〜18歳", "school_stage": "高校", "optional_interests": ["tech"],
                         "initial_self_understanding_score": 4.2,
                         "created_at": "2026-09-02T00:00:00+00:00", "updated_at": "2026-09-02T00:00:00+00:00"}
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                error("unexpected path: ${request.url.encodedPath}")
            }
        }
        val customClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    namingStrategy = JsonNamingStrategy.SnakeCase
                })
            }
        }
        val repo = RealDiscoveryRepository(httpClient = customClient)

        repo.completeOnboarding(
            nickname = "Taro",
            ageRange = "16〜18歳",
            schoolStage = "高校",
            optionalInterests = listOf("tech"),
            initialSelfUnderstandingScore = 4.2f
        )

        assertEquals(io.ktor.http.HttpMethod.Patch, capturedMethod)
        assertNotNull(capturedBody)
        assertTrue(capturedBody.contains("\"nickname\":\"Taro\""))
        assertTrue(capturedBody.contains("\"age_range\":\"16〜18歳\""))
        assertTrue(capturedBody.contains("\"school_stage\":\"高校\""))
        assertTrue(capturedBody.contains("\"optional_interests\":[\"tech\"]"))
        assertTrue(capturedBody.contains("\"initial_self_understanding_score\":4.2"))
    }

    @Test
    fun completeOnboarding_throwsWhenServerReturnsError() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/sessions") {
                respond(
                    content = SESSION_BODY,
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else if (request.url.encodedPath == "/sessions/1/onboarding") {
                respond(
                    content = """{"detail":"invalid"}""",
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                error("unexpected path: ${request.url.encodedPath}")
            }
        }
        val customClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    namingStrategy = JsonNamingStrategy.SnakeCase
                })
            }
        }
        val repo = RealDiscoveryRepository(httpClient = customClient)

        val exception = runCatching {
            repo.completeOnboarding(
                nickname = null,
                ageRange = null,
                schoolStage = null,
                optionalInterests = emptyList(),
                initialSelfUnderstandingScore = 3.0f
            )
        }.exceptionOrNull()

        assertTrue(exception is DiscoveryApiException)
        assertTrue(exception.message?.contains("400") == true)
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

    // ---- 案件18：セッション復帰・一覧・心理軸・Reflection ----

    @Test
    fun ensureSession_restoresFromStorageWhenSummaryExists() = runTest {
        val storage = InMemorySessionStorage()
        storage.saveLastSessionId(42)
        val (client, paths) = mockClient { path ->
            when (path) {
                "/sessions/42/summary" -> HttpStatusCode.OK to SUMMARY_BODY_NO_HYPOTHESIS
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client, sessionStorage = storage)

        repo.getDiscovery()

        // ensureSession() で存在確認の GET、fetchSummary() で取得の GET の 2 回呼ばれる。
        assertEquals(listOf("/sessions/42/summary", "/sessions/42/summary"), paths)
        assertEquals(42, storage.getLastSessionId())
    }

    @Test
    fun ensureSession_createsNewSessionWhenStoredSessionNotFound() = runTest {
        val storage = InMemorySessionStorage()
        storage.saveLastSessionId(99)
        val (client, paths) = mockClient { path ->
            when (path) {
                "/sessions/99/summary" -> HttpStatusCode.NotFound to """{"detail":"not found"}"""
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/summary" -> HttpStatusCode.OK to SUMMARY_BODY_NO_HYPOTHESIS
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client, sessionStorage = storage)

        repo.getDiscovery()

        assertEquals(listOf("/sessions/99/summary", "/sessions", "/sessions/1/summary"), paths)
        assertEquals(1, storage.getLastSessionId())
    }

    @Test
    fun getSessionList_fetchesSessionsForStudentLabel() = runTest {
        val (client, paths) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.OK to """
                    [{"id": 2, "student_label": "test_user", "status": "active",
                      "created_at": "2026-09-02T00:00:00+00:00", "updated_at": "2026-09-03T00:00:00+00:00"},
                     {"id": 1, "student_label": "test_user", "status": "active",
                      "created_at": "2026-09-01T00:00:00+00:00", "updated_at": "2026-09-01T00:00:00+00:00"}]
                """.trimIndent()
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val sessions = repo.getSessionList()

        assertEquals(listOf("/sessions"), paths)
        assertEquals(2, sessions.size)
        assertEquals(2, sessions[0].id)
        assertEquals(1, sessions[1].id)
    }

    @Test
    fun switchToSession_persistsToStorageAndClearsCache() = runTest {
        val storage = InMemorySessionStorage()
        val (client, paths) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/experiments/generate" -> HttpStatusCode.Created to twoExperimentsBody()
                "/sessions/7/experiments/generate" -> HttpStatusCode.Created to twoExperimentsBody()
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client, sessionStorage = storage)
        repo.getSuggestedExperiments()
        paths.clear()

        repo.switchToSession(7)

        assertEquals(7, storage.getLastSessionId())
        // キャッシュがクリアされているため、次の取得で新しいセッション ID のエンドポイントが呼ばれる。
        repo.getSuggestedExperiments()
        assertEquals(listOf("/sessions/7/experiments/generate"), paths)
    }

    @Test
    fun submitPsychAxisSurvey_postsScores() = runTest {
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/sessions" -> respond(
                    content = SESSION_BODY,
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/sessions/1/psych-axis-survey" -> {
                    capturedBody = (request.body as io.ktor.http.content.TextContent).text
                    respond(
                        content = """
                            [{"id": 1, "session_id": 1, "axis": "INVESTIGATE", "score": 4.5,
                              "created_at": "2026-09-02T00:00:00+00:00", "updated_at": "2026-09-02T00:00:00+00:00"},
                             {"id": 2, "session_id": 1, "axis": "CREATE", "score": 3.0,
                              "created_at": "2026-09-02T00:00:00+00:00", "updated_at": "2026-09-02T00:00:00+00:00"},
                             {"id": 3, "session_id": 1, "axis": "EXECUTE", "score": 4.0,
                              "created_at": "2026-09-02T00:00:00+00:00", "updated_at": "2026-09-02T00:00:00+00:00"},
                             {"id": 4, "session_id": 1, "axis": "COMMUNICATE", "score": 2.5,
                              "created_at": "2026-09-02T00:00:00+00:00", "updated_at": "2026-09-02T00:00:00+00:00"}]
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("unexpected path: ${request.url.encodedPath}")
            }
        }
        val customClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    namingStrategy = JsonNamingStrategy.SnakeCase
                })
            }
        }
        val repo = RealDiscoveryRepository(httpClient = customClient)
        val scores = mapOf(
            PsychAxis.INVESTIGATE to 4.5f,
            PsychAxis.CREATE to 3.0f,
            PsychAxis.EXECUTE to 4.0f,
            PsychAxis.COMMUNICATE to 2.5f
        )

        val results = repo.submitPsychAxisSurvey(scores)

        assertNotNull(capturedBody)
        assertTrue(capturedBody!!.contains("\"scores\""))
        assertTrue(capturedBody!!.contains("\"INVESTIGATE\":4.5"))
        assertTrue(capturedBody!!.contains("\"COMMUNICATE\":2.5"))
        assertEquals(4, results.size)
        assertEquals(4.5f, results.first { it.axis == PsychAxis.INVESTIGATE }.score)
    }

    @Test
    fun addReflection_postsReflection() = runTest {
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/sessions" -> respond(
                    content = SESSION_BODY,
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/sessions/1/reflections" -> {
                    capturedBody = (request.body as io.ktor.http.content.TextContent).text
                    respond(
                        content = """
                            {"id": 1, "session_id": 1, "content": "今日は楽しかった", "mood": 4,
                             "created_at": "2026-09-02T00:00:00+00:00"}
                        """.trimIndent(),
                        status = HttpStatusCode.Created,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> error("unexpected path: ${request.url.encodedPath}")
            }
        }
        val customClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    namingStrategy = JsonNamingStrategy.SnakeCase
                })
            }
        }
        val repo = RealDiscoveryRepository(httpClient = customClient)

        repo.addReflection("今日は楽しかった", mood = 4)

        assertNotNull(capturedBody)
        assertTrue(capturedBody!!.contains("\"content\":\"今日は楽しかった\""))
        assertTrue(capturedBody!!.contains("\"mood\":4"))
    }

    @Test
    fun getReflections_fetchesReflections() = runTest {
        val (client, paths) = mockClient { path ->
            when (path) {
                "/sessions" -> HttpStatusCode.Created to SESSION_BODY
                "/sessions/1/reflections" -> HttpStatusCode.OK to """
                    [{"id": 1, "session_id": 1, "content": "今日は楽しかった", "mood": 4,
                      "created_at": "2026-09-02T00:00:00+00:00"}]
                """.trimIndent()
                else -> error("unexpected path: $path")
            }
        }
        val repo = RealDiscoveryRepository(httpClient = client)

        val reflections = repo.getReflections()

        assertTrue(paths.contains("/sessions/1/reflections"))
        assertEquals(1, reflections.size)
        assertEquals("今日は楽しかった", reflections[0].content)
        assertEquals(4, reflections[0].mood)
    }
}
