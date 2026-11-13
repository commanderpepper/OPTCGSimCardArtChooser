package commanderpepper.optcgsimcardartchooser.simfolder

import java.util.prefs.Preferences

/** Stores the confirmed folder in the OS preference store (registry on Windows, plist on macOS). */
class PreferencesSimFolderStore(
    private val prefs: Preferences = Preferences.userRoot().node("commanderpepper/optcgsimcardartchooser"),
) : SimFolderStore {
    override fun load(): String? = prefs.get(KEY, null)

    override fun save(path: String) {
        prefs.put(KEY, path)
        prefs.flush()
    }

    override fun clear() {
        prefs.remove(KEY)
        prefs.flush()
    }

    private companion object {
        const val KEY = "simFolder"
    }
}
