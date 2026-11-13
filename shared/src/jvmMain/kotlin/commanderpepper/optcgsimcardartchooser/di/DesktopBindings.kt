package commanderpepper.optcgsimcardartchooser.di

import commanderpepper.optcgsimcardartchooser.simfolder.FolderPicker
import commanderpepper.optcgsimcardartchooser.simfolder.JvmSimFolderLocator
import commanderpepper.optcgsimcardartchooser.simfolder.NativeFolderPicker
import commanderpepper.optcgsimcardartchooser.simfolder.PreferencesSimFolderStore
import commanderpepper.optcgsimcardartchooser.simfolder.SimFolderLocator
import commanderpepper.optcgsimcardartchooser.simfolder.SimFolderStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** Desktop (Windows/macOS) implementations of the shared interfaces. */
@ContributesTo(AppScope::class)
interface DesktopBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun provideSimFolderLocator(): SimFolderLocator = JvmSimFolderLocator.forCurrentHost()

    @Provides
    @SingleIn(AppScope::class)
    fun provideSimFolderStore(): SimFolderStore = PreferencesSimFolderStore()

    @Provides
    @SingleIn(AppScope::class)
    fun provideFolderPicker(): FolderPicker = NativeFolderPicker()
}
