package commanderpepper.optcgsimcardartchooser.catalog.conversion

import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Where the app keeps its files, outside the sim folder so sim updates can't touch them.
 * - [data]: kept files (converted art, backups, catalog, selections).
 * - [cache]: re-downloadable files; on macOS this is ~/Library/Caches so the OS may reclaim it.
 */
class AppDirectories(val data: Path, val cache: Path) {
    val downloads: Path get() = cache.resolve("downloads")
    val converted: Path get() = data.resolve("converted")
    val backups: Path get() = data.resolve("backups")

    fun convertedSet(setId: String): Path = converted.resolve(setId)

    companion object {
        private const val APP = "OPTCGSimCardArtChooser"

        fun forCurrentHost(): AppDirectories {
            val os = System.getProperty("os.name").orEmpty().lowercase()
            val home = Path(System.getProperty("user.home"))
            return when {
                os.startsWith("windows") -> {
                    val local = System.getenv("LOCALAPPDATA")?.let { Path(it) } ?: home.resolve("AppData/Local")
                    local.resolve(APP).let { AppDirectories(data = it, cache = it) }
                }
                os.startsWith("mac") -> AppDirectories(
                    data = home.resolve("Library/Application Support/$APP"),
                    cache = home.resolve("Library/Caches/$APP"),
                )
                else -> AppDirectories(data = home.resolve(".local/share/$APP"), cache = home.resolve(".cache/$APP"))
            }
        }
    }
}
