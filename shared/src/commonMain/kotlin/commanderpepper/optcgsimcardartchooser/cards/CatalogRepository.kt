package commanderpepper.optcgsimcardartchooser.cards

import commanderpepper.optcgsimcardartchooser.catalog.model.Catalog

/** Source of the card catalog. Implementations should cache: callers may load more than once. */
interface CatalogRepository {
    suspend fun load(): Catalog
}
