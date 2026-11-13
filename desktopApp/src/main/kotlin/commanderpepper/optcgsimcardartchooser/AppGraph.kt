package commanderpepper.optcgsimcardartchooser

import commanderpepper.optcgsimcardartchooser.simfolder.FolderPicker
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metrox.viewmodel.ViewModelGraph

/**
 * The app's root dependency graph. Bindings are contributed to [AppScope] from `:shared`
 * (`DesktopBindings`, `AppViewModelFactory`, `@ContributesIntoMap` ViewModels).
 */
@DependencyGraph(AppScope::class)
interface AppGraph : ViewModelGraph {
    val folderPicker: FolderPicker
}
