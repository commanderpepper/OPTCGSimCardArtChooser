package commanderpepper.optcgsimcardartchooser.theme

/** Light or dark colours; [System] follows the OS setting. */
enum class ThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

/** Remembers the chosen [ThemeMode] between launches. */
interface ThemeStore {
    fun load(): ThemeMode
    fun save(mode: ThemeMode)
}
