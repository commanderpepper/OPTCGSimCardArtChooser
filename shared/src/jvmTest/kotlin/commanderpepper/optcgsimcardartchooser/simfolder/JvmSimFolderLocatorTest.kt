package commanderpepper.optcgsimcardartchooser.simfolder

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JvmSimFolderLocatorTest {
    private val root: Path = Files.createTempDirectory("simfolder-test")
    private val locator = JvmSimFolderLocator(unityLogs = emptyList(), installParents = emptyList())

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun windowsInstall(base: Path = root.resolve("Builds_Windows")): Path {
        val cards = base.resolve("OPTCGSim_Data/StreamingAssets/Cards")
        cards.resolve("OP01").createDirectories().resolve("OP01-001.png").createFile()
        cards.resolve("ST01").createDirectories().resolve("ST01-001.png").createFile()
        cards.resolve("Don").createDirectories().resolve("Don.png").createFile()
        return base
    }

    private fun macApp(base: Path = root.resolve("OPTCGSim.app")): Path {
        base.resolve("Contents/Resources/Data/StreamingAssets/Cards/OP01").createDirectories()
            .resolve("OP01-001.png").createFile()
        return base
    }

    @Test
    fun windowsGameFolderIsValidAndCountsOnlyCardSets() {
        val install = windowsInstall()
        val check = assertIs<FolderCheck.Valid>(locator.check(install.toString()))
        assertEquals(2, check.setCount)
        assertTrue(check.writable)
        assertTrue(check.cardsPath.endsWith("Cards"))
    }

    @Test
    fun versionFolderAboveBuildsWindowsIsValid() {
        windowsInstall(root.resolve("1.25c_Windows/Builds_Windows"))
        assertIs<FolderCheck.Valid>(locator.check(root.resolve("1.25c_Windows").toString()))
    }

    @Test
    fun selectingTheCardsFolderDirectlyIsValid() {
        val install = windowsInstall()
        assertIs<FolderCheck.Valid>(locator.check(install.resolve("OPTCGSim_Data/StreamingAssets/Cards").toString()))
    }

    @Test
    fun macAppBundleAndItsParentAreValid() {
        val app = macApp()
        assertIs<FolderCheck.Valid>(locator.check(app.toString()))
        assertIs<FolderCheck.Valid>(locator.check(root.toString()))
    }

    @Test
    fun quotedPathsFromExplorerCopyAsPathAreAccepted() {
        val install = windowsInstall()
        assertIs<FolderCheck.Valid>(locator.check("\"$install\""))
    }

    @Test
    fun unrelatedMissingOrEmptyFoldersAreInvalid() {
        assertIs<FolderCheck.Invalid>(locator.check(""))
        assertIs<FolderCheck.Invalid>(locator.check(root.resolve("missing").toString()))
        assertIs<FolderCheck.Invalid>(locator.check(root.toString()))
        root.resolve("Empty/OPTCGSim_Data/StreamingAssets/Cards/OP01").createDirectories()
        assertIs<FolderCheck.Invalid>(locator.check(root.resolve("Empty").toString()))
    }

    @Test
    fun suggestionsComeFromUnityLogAndInstallParents() {
        val fromLog = windowsInstall(root.resolve("logged/Builds_Windows"))
        val log = root.resolve("Player.log")
        log.writeText(
            "Mono path[0] = '${fromLog.toString().replace('\\', '/')}/OPTCGSim_Data/Managed'\nOther line\n",
        )
        windowsInstall(root.resolve("One Piece Sim/1.30a_Windows/Builds_Windows"))

        val suggestions = JvmSimFolderLocator(
            unityLogs = listOf(log, root.resolve("missing.log")),
            installParents = listOf(root.resolve("One Piece Sim")),
        ).suggestions()

        assertEquals(2, suggestions.size, "one per install, not per folder level: $suggestions")
        assertEquals(fromLog.toString(), suggestions.first())
    }
}
