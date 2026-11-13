package commanderpepper.optcgsimcardartchooser.catalog.optcgapi

import commanderpepper.optcgsimcardartchooser.catalog.CatalogJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/**
 * Bulk endpoints of https://optcgapi.com (no auth, GET only). The API asks users to keep call
 * volume low, so only the four `all*` endpoints are used.
 * Note: `/allPromoCards/` is documented but returns 404, so promos come only from set rows.
 */
class OptcgApiClient(
    private val http: HttpClient,
    private val baseUrl: String = "https://optcgapi.com/api",
) {
    suspend fun sets(): List<ApiSet> = get("allSets")
    suspend fun decks(): List<ApiDeck> = get("allDecks")
    suspend fun setCards(): List<ApiCardRow> = get("allSetCards")
    suspend fun starterDeckCards(): List<ApiCardRow> = get("allSTCards")

    private suspend inline fun <reified T> get(endpoint: String): T {
        val url = "$baseUrl/$endpoint/"
        val response = http.get(url)
        if (!response.status.isSuccess()) throw OptcgApiException("GET $url failed: ${response.status}")
        return CatalogJson.json.decodeFromString<T>(response.bodyAsText())
    }
}

class OptcgApiException(message: String) : Exception(message)
