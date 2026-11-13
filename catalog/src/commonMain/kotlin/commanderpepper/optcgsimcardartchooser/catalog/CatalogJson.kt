package commanderpepper.optcgsimcardartchooser.catalog

import commanderpepper.optcgsimcardartchooser.catalog.model.Catalog
import kotlinx.serialization.json.Json

object CatalogJson {
    /** Lenient on input so older/newer catalogs and source responses still decode. */
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val prettyJson = Json(json) { prettyPrint = true }

    fun encode(catalog: Catalog, pretty: Boolean = true): String =
        (if (pretty) prettyJson else json).encodeToString(Catalog.serializer(), catalog)

    fun decode(text: String): Catalog = json.decodeFromString(Catalog.serializer(), text)
}
