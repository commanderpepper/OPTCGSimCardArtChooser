package commanderpepper.optcgsimcardartchooser.di

import commanderpepper.optcgsimcardartchooser.cards.ImagePicker
import commanderpepper.optcgsimcardartchooser.cards.NativeImagePicker
import commanderpepper.optcgsimcardartchooser.catalog.conversion.AppDirectories
import commanderpepper.optcgsimcardartchooser.catalog.conversion.SetConverter
import commanderpepper.optcgsimcardartchooser.catalog.createHttpClient
import commanderpepper.optcgsimcardartchooser.simfolder.FolderPicker
import commanderpepper.optcgsimcardartchooser.simfolder.JvmSimFolderLocator
import commanderpepper.optcgsimcardartchooser.simfolder.NativeFolderPicker
import commanderpepper.optcgsimcardartchooser.simfolder.PreferencesSimFolderStore
import commanderpepper.optcgsimcardartchooser.simfolder.SimFolderLocator
import commanderpepper.optcgsimcardartchooser.simfolder.SimFolderStore
import commanderpepper.optcgsimcardartchooser.theme.PreferencesThemeStore
import commanderpepper.optcgsimcardartchooser.theme.ThemeStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient

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
    fun provideThemeStore(): ThemeStore = PreferencesThemeStore()

    @Provides
    @SingleIn(AppScope::class)
    fun provideFolderPicker(): FolderPicker = NativeFolderPicker()

    @Provides
    @SingleIn(AppScope::class)
    fun provideImagePicker(): ImagePicker = NativeImagePicker()

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppDirectories(): AppDirectories = AppDirectories.forCurrentHost()

    @Provides
    @SingleIn(AppScope::class)
    fun provideHttpClient(): HttpClient = createHttpClient()

    @Provides
    @SingleIn(AppScope::class)
    fun provideSetConverter(http: HttpClient, dirs: AppDirectories): SetConverter = SetConverter(http, dirs)
}
