package commanderpepper.optcgsimcardartchooser.simfolder

/** Result of checking whether a user-supplied path is a usable OPTCGSim install. */
sealed interface FolderCheck {
    val selectedPath: String

    data class Valid(
        override val selectedPath: String,
        /** The resolved `StreamingAssets/Cards` directory the app will modify. */
        val cardsPath: String,
        val setCount: Int,
        val writable: Boolean,
    ) : FolderCheck

    data class Invalid(
        override val selectedPath: String,
        val reason: String,
    ) : FolderCheck
}

/** Finds and validates OPTCGSim installs on the host file system. */
interface SimFolderLocator {
    /** Install folders that look valid, in order of confidence. Suggestions only; the user must confirm. */
    fun suggestions(): List<String>

    fun check(path: String): FolderCheck
}

/** Persists the folder the user confirmed. */
interface SimFolderStore {
    fun load(): String?
    fun save(path: String)
    fun clear()
}

/** Opens a native folder chooser. Returns null if the user cancels. */
fun interface FolderPicker {
    fun pick(startAt: String?): String?
}
