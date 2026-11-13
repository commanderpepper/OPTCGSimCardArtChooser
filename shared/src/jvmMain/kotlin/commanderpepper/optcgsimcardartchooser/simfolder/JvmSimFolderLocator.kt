package commanderpepper.optcgsimcardartchooser.simfolder

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.useLines

/**
 * Validates OPTCGSim installs. OPTCGSim is a Unity game, so card art lives in
 * `<Product>_Data/StreamingAssets/Cards/<SET>/<CARD>.png` on Windows and
 * `<Product>.app/Contents/Resources/Data/StreamingAssets/Cards/...` on macOS.
 *
 * @param unityLogs Unity Player.log files; their first line records the install path.
 * @param installParents Folders whose children may be installs (e.g. `Program Files/One Piece Sim`).
 */
class JvmSimFolderLocator(
    private val unityLogs: List<Path>,
    private val installParents: List<Path>,
) : SimFolderLocator {

    override fun suggestions(): List<String> {
        val fromLogs = unityLogs.mapNotNull(::installFromUnityLog)
        val fromParents = installParents.flatMap { parent -> childDirs(parent) + childDirs(parent).flatMap(::childDirs) }
        return (fromLogs + fromParents)
            .map { it.toAbsolutePath().normalize() }
            .distinct()
            .mapNotNull { check(it.toString()) as? FolderCheck.Valid }
            // A version folder and its Builds_* subfolder resolve to the same install.
            .distinctBy { it.cardsPath }
            .map { it.selectedPath }
    }

    override fun check(path: String): FolderCheck {
        val input = path.trim().removeSurrounding("\"")
        if (input.isEmpty()) return FolderCheck.Invalid(path, "Enter or browse to a folder.")
        val selected = try {
            Path(input)
        } catch (_: InvalidPathException) {
            return FolderCheck.Invalid(input, "That isn't a valid path.")
        }
        if (!selected.isDirectory()) return FolderCheck.Invalid(input, "That folder doesn't exist.")

        val cards = cardsDirCandidates(selected).firstOrNull { it.isDirectory() }
            ?: return FolderCheck.Invalid(
                input,
                "Couldn't find OPTCGSim's card art here. Select the folder containing OPTCGSim.exe " +
                    "(Windows) or OPTCGSim.app (macOS).",
            )

        val setCount = childDirs(cards).count { SET_DIR.matches(it.name) && containsCardArt(it) }
        if (setCount == 0) {
            return FolderCheck.Invalid(input, "Found a Cards folder at $cards, but it contains no card art.")
        }
        return FolderCheck.Valid(
            selectedPath = input,
            cardsPath = cards.toString(),
            setCount = setCount,
            writable = canWrite(cards),
        )
    }

    private fun cardsDirCandidates(selected: Path): List<Path> = buildList {
        if (selected.name == CARDS) add(selected)
        add(selected.resolve(CARDS)) // StreamingAssets
        add(selected.resolve(STREAMING_ASSETS).resolve(CARDS)) // OPTCGSim_Data
        add(selected.resolve(MAC_DATA).resolve(STREAMING_ASSETS).resolve(CARDS)) // OPTCGSim.app
        // Folder containing the game: OPTCGSim_Data (Windows) or OPTCGSim.app (macOS), any product name.
        val children = childDirs(selected)
        children.filter { it.name.endsWith("_Data") }
            .forEach { add(it.resolve(STREAMING_ASSETS).resolve(CARDS)) }
        children.filter { it.name.endsWith(".app") }
            .forEach { add(it.resolve(MAC_DATA).resolve(STREAMING_ASSETS).resolve(CARDS)) }
        // Version folders such as "1.25c_Windows/Builds_Windows".
        children.filter { it.name.startsWith("Builds_") }
            .flatMap(::childDirs)
            .filter { it.name.endsWith("_Data") }
            .forEach { add(it.resolve(STREAMING_ASSETS).resolve(CARDS)) }
    }

    private fun containsCardArt(setDir: Path): Boolean =
        Files.newDirectoryStream(setDir).use { entries -> entries.any { it.isRegularFile() && CARD_FILE.matches(it.name) } }

    /** A real write probe; `Files.isWritable` is unreliable for ACL-protected folders on Windows. */
    private fun canWrite(dir: Path): Boolean = try {
        Files.deleteIfExists(Files.createTempFile(dir, ".artchooser-write-test", ".tmp"))
        true
    } catch (_: Exception) {
        false
    }

    /** Unity logs begin with `Mono path[0] = '<install>/<Product>_Data/Managed'`. */
    private fun installFromUnityLog(log: Path): Path? {
        if (!log.isRegularFile()) return null
        val monoPath = try {
            log.useLines { lines -> lines.take(20).firstNotNullOfOrNull { MONO_PATH.find(it)?.groupValues?.get(1) } }
        } catch (_: Exception) {
            null
        } ?: return null
        var current: Path? = try { Path(monoPath) } catch (_: InvalidPathException) { null }
        while (current != null) {
            val name = current.fileName?.toString().orEmpty()
            if (name.endsWith(".app")) return current
            if (name.endsWith("_Data")) return current.parent
            current = current.parent
        }
        return null
    }

    private fun childDirs(dir: Path): List<Path> = try {
        if (dir.isDirectory()) dir.listDirectoryEntries().filter { it.isDirectory() } else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        private const val CARDS = "Cards"
        private const val STREAMING_ASSETS = "StreamingAssets"
        private const val MAC_DATA = "Contents/Resources/Data"
        private val SET_DIR = Regex("""^[A-Z]+\d+$""")
        private val CARD_FILE = Regex("""^[A-Z]+\d*-\d{3}.*\.(png|jpg)$""", RegexOption.IGNORE_CASE)
        private val MONO_PATH = Regex("""Mono path\[0] = '(.+)'""")

        fun forCurrentHost(os: HostOs = HostOs.current): JvmSimFolderLocator {
            val home = Path(System.getProperty("user.home"))
            return when (os) {
                HostOs.Windows -> {
                    val logDir = home.resolve("AppData/LocalLow/Batsu/OPTCGSim")
                    val programFiles = listOfNotNull(System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)"))
                    JvmSimFolderLocator(
                        unityLogs = listOf(logDir.resolve("Player.log"), logDir.resolve("Player-prev.log")),
                        installParents = programFiles.map { Path(it, "One Piece Sim") } +
                            listOf(home.resolve("Desktop"), home.resolve("Downloads")),
                    )
                }
                HostOs.MacOS -> {
                    val logDir = home.resolve("Library/Logs/Batsu/OPTCGSim")
                    JvmSimFolderLocator(
                        unityLogs = listOf(logDir.resolve("Player.log"), logDir.resolve("Player-prev.log")),
                        installParents = listOf(Path("/Applications"), home.resolve("Applications"), home.resolve("Downloads")),
                    )
                }
                HostOs.Other -> JvmSimFolderLocator(emptyList(), emptyList())
            }
        }
    }
}
