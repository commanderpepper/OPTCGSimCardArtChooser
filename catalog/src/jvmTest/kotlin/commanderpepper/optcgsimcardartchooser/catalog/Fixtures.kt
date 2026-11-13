package commanderpepper.optcgsimcardartchooser.catalog

import commanderpepper.optcgsimcardartchooser.catalog.optcgapi.ApiCardRow
import commanderpepper.optcgsimcardartchooser.catalog.optcgapi.ApiDeck
import commanderpepper.optcgsimcardartchooser.catalog.optcgapi.ApiSet

/** Real optcgapi rows (trimmed) covering each cleanup case. */
object Fixtures {
    fun text(name: String): String =
        Fixtures::class.java.getResource("/optcgapi/$name.json")?.readText() ?: error("Missing fixture $name")

    val sets: List<ApiSet> get() = CatalogJson.json.decodeFromString(text("allSets"))
    val decks: List<ApiDeck> get() = CatalogJson.json.decodeFromString(text("allDecks"))
    val rows: List<ApiCardRow>
        get() = CatalogJson.json.decodeFromString<List<ApiCardRow>>(text("allSetCards")) +
            CatalogJson.json.decodeFromString<List<ApiCardRow>>(text("allSTCards"))
}
