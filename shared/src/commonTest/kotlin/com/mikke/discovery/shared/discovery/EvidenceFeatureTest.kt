package com.mikke.discovery.shared.discovery

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EvidenceFeatureTest {

    private fun TestScope.createState(repository: DiscoveryRepository) = DiscoveryState(
        repository = repository,
        coroutineScope = this,
        useSupervisorJob = false,
        autoLoad = false
    )

    private val sessionBody = """
        {"id": 1, "student_label": "test_user", "status": "active",
         "created_at": "2026-09-02T00:00:00+00:00", "updated_at": "2026-09-02T00:00:00+00:00"}
    """.trimIndent()

    private val evidenceListJson = """
        [
            {
                "id": 1,
                "session_id": 1,
                "domain": "tech",
                "signal_count": 3,
                "summary_text": "techに関するシグナル3件",
                "created_at": "2026-09-02T10:00:00+00:00"
            },
            {
                "id": 2,
                "session_id": 1,
                "domain": "art",
                "signal_count": 2,
                "summary_text": "artに関するシグナル2件",
                "created_at": "2026-09-02T09:00:00+00:00"
            }
        ]
    """.trimIndent()

    @Test
    fun evidenceUiModel_hasRequiredProperties() {
        val instant = Instant.fromEpochMilliseconds(1725624000000L)
        val model = EvidenceUiModel(
            id = 10,
            domain = "science",
            signalCount = 5,
            summaryText = "scienceの実験記録5件",
            createdAt = instant
        )
        assertEquals(10, model.id)
        assertEquals("science", model.domain)
        assertEquals(5, model.signalCount)
        assertEquals("scienceの実験記録5件", model.summaryText)
        assertEquals(instant, model.createdAt)
    }

    @Test
    fun fakeDiscoveryRepository_getEvidenceList_returnsDummyEvidences() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val evidences = repo.getEvidenceList(sessionId = 1)
        assertTrue(evidences.isNotEmpty(), "Default scenario should return non-empty evidence list")
        val first = evidences.first()
        assertTrue(first.id > 0)
        assertTrue(first.domain.isNotBlank())
        assertTrue(first.signalCount > 0)
        assertTrue(first.summaryText.isNotBlank())
    }

    @Test
    fun fakeDiscoveryRepository_getEvidenceList_inEmptyScenario_returnsEmptyList() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.EMPTY_DISCOVERY, enableArtificialDelay = false)
        val evidences = repo.getEvidenceList(sessionId = 1)
        assertTrue(evidences.isEmpty())
    }

    @Test
    fun realDiscoveryRepository_getEvidenceList_callsApiAndMapsToUiModels() = runTest {
        val requestedPaths = mutableListOf<String>()
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            requestedPaths.add(path)
            when (path) {
                "/sessions" -> respond(
                    content = sessionBody,
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/sessions/1/evidence" -> respond(
                    content = evidenceListJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> error("Unexpected path: $path")
            }
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
        val evidences = repo.getEvidenceList(sessionId = 1)

        assertTrue(requestedPaths.contains("/sessions/1/evidence"))
        assertEquals(2, evidences.size)
        assertEquals(1, evidences[0].id)
        assertEquals("tech", evidences[0].domain)
        assertEquals(3, evidences[0].signalCount)
        assertEquals("techに関するシグナル3件", evidences[0].summaryText)
        assertEquals(2, evidences[1].id)
        assertEquals("art", evidences[1].domain)
    }

    @Test
    fun realDiscoveryRepository_getDiscovery_parsesHypothesisWithSupportingEvidenceIds() = runTest {
        val summaryJsonWithEvidenceIds = """
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
               "confidence": 0.8, "supporting_evidence": [101, 102], "suggested_next_domains": [],
               "created_at": "2026-09-02T00:00:00+00:00"
             }}
        """.trimIndent()

        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/sessions" -> respond(
                    content = sessionBody,
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/sessions/1/summary" -> respond(
                    content = summaryJsonWithEvidenceIds,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                "/sessions/1/experiments/generate" -> respond(
                    content = "[]",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
                else -> error("Unexpected path: ${request.url.encodedPath}")
            }
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
        val discovery = repo.getDiscovery()

        assertEquals("コードを書くことに強い関心があります", discovery.hypothesis)
        assertEquals(1, discovery.hypothesisId)
    }

    @Test
    fun discoveryState_loadEvidenceList_updatesState() = runTest {
        val repo = FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false)
        val state = createState(repo)
        try {
            state.loadEvidenceList()
            advanceUntilIdle()

            val uiState = state.evidenceListState.value
            assertFalse(uiState.isLoading)
            assertNotNull(uiState.evidences)
            assertTrue(uiState.evidences.isNotEmpty())
            assertEquals(null, uiState.errorMessage)
        } finally {
            state.close()
        }
    }

    @Test
    fun discoveryState_loadEvidenceList_onFailure_updatesErrorMessage() = runTest {
        val repo = object : DiscoveryRepository by FakeDiscoveryRepository(FakeScenario.NORMAL, enableArtificialDelay = false) {
            override suspend fun getEvidenceList(sessionId: Int): List<EvidenceUiModel> {
                throw DiscoveryApiException("エビデンス取得失敗")
            }
        }
        val state = createState(repo)
        try {
            state.loadEvidenceList()
            advanceUntilIdle()

            val uiState = state.evidenceListState.value
            assertFalse(uiState.isLoading)
            assertEquals("エビデンス取得失敗", uiState.errorMessage)
            assertTrue(uiState.evidences.isEmpty())
        } finally {
            state.close()
        }
    }
}
