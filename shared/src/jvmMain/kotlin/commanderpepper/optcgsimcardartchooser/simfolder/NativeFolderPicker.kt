package commanderpepper.optcgsimcardartchooser.simfolder

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

/**
 * macOS: the native AWT dialog in directory mode. Windows: AWT's FileDialog can't pick folders,
 * so this uses JFileChooser with the system look and feel.
 * Must be called on the AWT event thread (Compose Desktop click handlers already are).
 */
class NativeFolderPicker(private val os: HostOs = HostOs.current) : FolderPicker {

    override fun pick(startAt: String?): String? =
        if (os == HostOs.MacOS) pickMac(startAt) else pickSwing(startAt)

    private fun pickMac(startAt: String?): String? {
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        try {
            val dialog = FileDialog(null as Frame?, TITLE, FileDialog.LOAD)
            startAt?.let { dialog.directory = it }
            dialog.isVisible = true
            val file = dialog.file ?: return null
            return File(dialog.directory, file).absolutePath
        } finally {
            System.setProperty("apple.awt.fileDialogForDirectories", "false")
        }
    }

    private fun pickSwing(startAt: String?): String? {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser(startAt?.let(::File)).apply {
            dialogTitle = TITLE
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.absolutePath
        } else {
            null
        }
    }

    private companion object {
        const val TITLE = "Select the OPTCGSim folder"
    }
}
