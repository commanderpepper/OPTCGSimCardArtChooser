package commanderpepper.optcgsimcardartchooser.simfolder

import dev.zacsweers.metro.Inject

/**
 * The verified sim folder's `StreamingAssets/Cards` directory. Unscoped on purpose: each screen
 * gets a fresh instance, so a folder changed in setup is picked up the next time a page opens.
 */
@Inject
class SimCardsFolder(
    private val store: SimFolderStore,
    private val locator: SimFolderLocator,
) {
    /** Null when no folder is saved or the saved one no longer validates. */
    val cardsPath: String? by lazy {
        (store.load()?.let(locator::check) as? FolderCheck.Valid)?.cardsPath
    }
}
