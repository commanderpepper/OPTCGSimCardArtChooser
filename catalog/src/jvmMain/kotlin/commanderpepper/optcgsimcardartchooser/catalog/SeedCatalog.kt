package commanderpepper.optcgsimcardartchooser.catalog

import commanderpepper.optcgsimcardartchooser.catalog.model.Catalog
import commanderpepper.optcgsimcardartchooser.catalog.optcgapi.OptcgApiClient
import commanderpepper.optcgsimcardartchooser.catalog.optcgapi.OptcgApiSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

private const val SEED_RESOURCE = "/seed-catalog.json"

/** The catalog shipped inside the app, so first launch works without a network. */
object BundledCatalog {
    fun load(): Catalog? =
        BundledCatalog::class.java.getResourceAsStream(SEED_RESOURCE)?.use { CatalogJson.decode(it.readBytes().decodeToString()) }
}

fun createHttpClient(): HttpClient = HttpClient(Java) {
    install(UserAgent) { agent = "OPTCGSimCardArtChooser" }
    install(HttpTimeout) { requestTimeoutMillis = 60_000 }
}

/** Entry point for `./gradlew :catalog:generateSeedCatalog`. Argument: output file path. */
fun main(args: Array<String>) = runBlocking {
    val output = Path(args.singleOrNull() ?: error("Usage: SeedCatalog <output.json>"))
    val result = createHttpClient().use { http ->
        OptcgApiSource(OptcgApiClient(http)).fetch(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString())
    }
    output.createParentDirectories()
    output.writeText(CatalogJson.encode(result.catalog))

    val catalog = result.catalog
    println("Wrote $output")
    println("  ${catalog.sets.size} sets, ${catalog.cards.size} cards, ${catalog.cards.sumOf { it.variants.size }} variants")
    if (result.warnings.isNotEmpty()) {
        println("  ${result.warnings.size} warnings:")
        result.warnings.forEach { println("    $it") }
    }
}
