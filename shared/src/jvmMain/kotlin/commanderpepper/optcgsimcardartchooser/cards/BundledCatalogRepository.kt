package commanderpepper.optcgsimcardartchooser.cards

import commanderpepper.optcgsimcardartchooser.catalog.BundledCatalog
import commanderpepper.optcgsimcardartchooser.catalog.model.Catalog
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Serves the seed catalog bundled in the app, decoded once (≈3.5 MB of JSON) off the UI thread. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class BundledCatalogRepository : CatalogRepository {
    private val mutex = Mutex()
    private var cached: Catalog? = null

    override suspend fun load(): Catalog = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            BundledCatalog.load() ?: error("The bundled card catalog is missing from the app")
        }.also { cached = it }
    }
}
