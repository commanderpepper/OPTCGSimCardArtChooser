package commanderpepper.optcgsimcardartchooser.cards

import commanderpepper.optcgsimcardartchooser.simfolder.HostOs
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * macOS: the native AWT file dialog, filtered to JPG/PNG. Windows: JFileChooser with the system
 * look and feel and a "JPG or PNG images" filter (AWT's filename filter doesn't work on Windows).
 * Must be called on the AWT event thread (Compose Desktop click handlers already are).
 */
class NativeImagePicker(private val os: HostOs = HostOs.current) : ImagePicker {

    override fun pick(): String? = if (os == HostOs.MacOS) pickMac() else pickSwing()

    private fun pickMac(): String? {
        val dialog = FileDialog(null as Frame?, TITLE, FileDialog.LOAD).apply {
            setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in CustomArt.ACCEPTED_EXTENSIONS }
        }
        dialog.isVisible = true
        val file = dialog.file ?: return null
        return File(dialog.directory, file).absolutePath
    }

    private fun pickSwing(): String? {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser().apply {
            dialogTitle = TITLE
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter("JPG or PNG images", *CustomArt.ACCEPTED_EXTENSIONS.toTypedArray())
            isAcceptAllFileFilterUsed = false
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
    }

    private companion object {
        const val TITLE = "Choose an image for this card"
    }
}
