package com.example.myapplication.shared.discovery

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
