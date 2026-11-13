package commanderpepper.optcgsimcardartchooser.catalog.conversion

import commanderpepper.optcgsimcardartchooser.catalog.BundledCatalog
import commanderpepper.optcgsimcardartchooser.catalog.CatalogJson
import commanderpepper.optcgsimcardartchooser.catalog.createHttpClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.io.path.writeText
import kotlin.system.exitProcess

/** Entry point for `./gradlew :catalog:convertSet -Pset=OP01`. */
fun main(args: Array<String>) {
    val setId = args.singleOrNull()?.trim()?.uppercase() ?: error("Usage: ConvertSet <SET_ID>, e.g. OP01")
    val catalog = BundledCatalog.load() ?: error("Bundled seed catalog not found")
    if (catalog.sets.none { it.id == setId }) {
        System.err.println("Unknown set '$setId'. Known: ${catalog.sets.joinToString(" ") { it.id }}")
        exitProcess(1)
    }
    val dirs = AppDirectories.forCurrentHost()
    println("Converting $setId into ${dirs.convertedSet(setId)}")

    val report = runBlocking {
        createHttpClient().use { http ->
            SetConverter(http, dirs).convert(
                catalog, setId,
                finishedAt = { Instant.now().truncatedTo(ChronoUnit.SECONDS).toString() },
                onProgress = { i, total, r ->
                    val note = r.message?.let { " — $it" }.orEmpty()
                    println("  [$i/$total] ${r.variantId}: ${r.status}$note")
                },
            )
        }
    }
    val json = Json(CatalogJson.json) { prettyPrint = true }
    dirs.convertedSet(setId).resolve("report.json").writeText(json.encodeToString(SetConversionReport.serializer(), report))

    println(
        "$setId done: ${report.count(VariantResult.Status.Converted)} converted, " +
            "${report.count(VariantResult.Status.AlreadyConverted)} already done, " +
            "${report.count(VariantResult.Status.Failed)} failed (of ${report.results.size}).",
    )
    if (report.count(VariantResult.Status.Failed) > 0) exitProcess(2)
}
