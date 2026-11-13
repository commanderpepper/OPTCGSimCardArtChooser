package commanderpepper.optcgsimcardartchooser.catalog

import commanderpepper.optcgsimcardartchooser.catalog.model.Catalog

/** A place card data comes from (optcgapi, the sim's own files, manual entries, ...). */
interface CatalogSource {
    val name: String

    suspend fun fetch(generatedAt: String): CatalogBuildResult
}

/** [warnings] lists source rows that were skipped or adjusted, for logging. */
data class CatalogBuildResult(val catalog: Catalog, val warnings: List<String>)
