package commanderpepper.optcgsimcardartchooser.catalog.optcgapi

import commanderpepper.optcgsimcardartchooser.catalog.Fixtures
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OptcgApiSourceTest {
    private val requested = mutableListOf<String>()

    private fun client(status: HttpStatusCode = HttpStatusCode.OK) = OptcgApiClient(
        HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            requested += path
            val fixture = path.trim('/').substringAfterLast('/')
            respond(Fixtures.text(fixture), status, headersOf(HttpHeaders.ContentType, "application/json"))
        }),
    )

    @Test
    fun fetchUsesOnlyTheFourBulkEndpoints() = runTest {
        val result = OptcgApiSource(client()).fetch("t")
        assertEquals(
            setOf("/api/allSets/", "/api/allDecks/", "/api/allSetCards/", "/api/allSTCards/"),
            requested.toSet(),
        )
        assertEquals(4, requested.size)
        assertTrue(result.catalog.cards.any { it.id == "ST01-001" })
        assertTrue(result.catalog.cards.any { it.id == "EB01-006" })
    }

    @Test
    fun httpErrorsSurfaceAsExceptions() = runTest {
        assertFailsWith<OptcgApiException> { client(HttpStatusCode.NotFound).sets() }
    }
}
