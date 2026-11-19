package commanderpepper.optcgsimcardartchooser.catalog.conversion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class AppDirectoriesTest {

    @Test
    fun debugVersionUsesItsOwnFolders() {
        val release = AppDirectories.forCurrentHost(AppDirectories.APP)
        val debug = AppDirectories.forCurrentHost(AppDirectories.APP + "-Debug")
        assertNotEquals(release.data, debug.data)
        assertNotEquals(release.cache, debug.cache)
        assertFalse(debug.data.startsWith(release.data))
        assertEquals("OPTCGSimCardArtChooser-Debug", debug.data.fileName.toString())
    }

    @Test
    fun testsRunAsTheReleaseVersion() {
        // Only the app's run tasks set the flavor; tests and installers don't.
        assertFalse(AppFlavor.isDebug)
        assertEquals(AppDirectories.APP, AppDirectories.forCurrentHost().data.fileName.toString())
    }
}
