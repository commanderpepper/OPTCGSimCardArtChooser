package commanderpepper.optcgsimcardartchooser.cards

import commanderpepper.optcgsimcardartchooser.simfolder.HostOs
import java.awt.FileDialog
import java.awt.Frame
import javax.swing.JFileChooser
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Pick one or more JPG, PNG or WebP images. macOS: the native AWT file dialog. Windows: JFileChooser with
 * the system look and feel and a "JPG, PNG or WebP images" filter (AWT's filename filter doesn't work
 * on Windows). Must be called on the AWT event thread (Compose Desktop click handlers already are).
 */
class NativeImagePicker(private val os: HostOs = HostOs.current) : ImagePicker {

    override fun pick(): List<String> = if (os == HostOs.MacOS) pickMac() else pickSwing()

    private fun pickMac(): List<String> {
        val dialog = FileDialog(null as Frame?, TITLE, FileDialog.LOAD).apply {
            isMultipleMode = true
            setFilenameFilter { _, name -> CustomArt.isAcceptedFile(name) }
        }
        dialog.isVisible = true
        return dialog.files.map { it.absolutePath }
    }

    private fun pickSwing(): List<String> {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser().apply {
            dialogTitle = TITLE
            fileSelectionMode = JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = true
            fileFilter = FileNameExtensionFilter("JPG, PNG or WebP images", *CustomArt.ACCEPTED_EXTENSIONS.toTypedArray())
            isAcceptAllFileFilterUsed = false
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFiles.map { it.absolutePath }
        } else {
            emptyList()
        }
    }

    private companion object {
        const val TITLE = "Choose images for this card"
    }
}
