package commanderpepper.optcgsimcardartchooser.catalog.conversion

/**
 * Debug when started from Gradle (`run`, `hotRun`; see desktopApp/build.gradle.kts), release when installed.
 * The debug version keeps its own data folder and settings so it can't disturb the installed app.
 */
object AppFlavor {
    val isDebug: Boolean = System.getProperty("optcg.flavor") == "debug"

    /** Appended to folder names, the preferences path and the window title. */
    val suffix: String get() = if (isDebug) "-Debug" else ""
}
