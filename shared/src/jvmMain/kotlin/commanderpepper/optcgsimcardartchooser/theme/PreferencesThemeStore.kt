package commanderpepper.optcgsimcardartchooser.theme

import java.util.prefs.Preferences

/** Stores the theme choice next to the sim folder in the OS preference store. */
class PreferencesThemeStore(
    private val prefs: Preferences = Preferences.userRoot().node("commanderpepper/optcgsimcardartchooser"),
) : ThemeStore {
    override fun load(): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == prefs.get(KEY, null) } ?: ThemeMode.System

    override fun save(mode: ThemeMode) {
        prefs.put(KEY, mode.name)
        prefs.flush()
    }

    private companion object {
        const val KEY = "themeMode"
    }
}
