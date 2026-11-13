package commanderpepper.optcgsimcardartchooser.catalog.optcgapi

import commanderpepper.optcgsimcardartchooser.catalog.CatalogBuildResult
import commanderpepper.optcgsimcardartchooser.catalog.CatalogSource

class OptcgApiSource(
    private val client: OptcgApiClient,
    private val builder: OptcgApiCatalogBuilder = OptcgApiCatalogBuilder(),
) : CatalogSource {
    override val name: String = OptcgApiCatalogBuilder.ORIGIN

    override suspend fun fetch(generatedAt: String): CatalogBuildResult =
        builder.build(
            sets = client.sets(),
            decks = client.decks(),
            rows = client.setCards() + client.starterDeckCards(),
            generatedAt = generatedAt,
        )
}
