package commanderpepper.optcgsimcardartchooser.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import commanderpepper.optcgsimcardartchooser.HomeScreen
import commanderpepper.optcgsimcardartchooser.simfolder.FolderPicker
import commanderpepper.optcgsimcardartchooser.simfolder.SimFolderSetupScreen
import commanderpepper.optcgsimcardartchooser.simfolder.SimFolderViewModel

/**
 * Hosts every [Pages] destination. The sim folder ViewModel is shared by the setup and home
 * pages, so it's owned here rather than by a single entry; page-specific ViewModels obtained with
 * `metroViewModel()` inside an entry are scoped to that entry and cleared when it's popped.
 */
@Composable
fun AppNavigation(
    folderViewModel: SimFolderViewModel,
    folderPicker: FolderPicker,
) {
    val folderState by folderViewModel.state.collectAsStateWithLifecycle()
    val start: Pages = if (folderState.confirmed != null) Pages.Home else Pages.SimFolderSetup
    val backStack = rememberNavBackStack(Pages.savedStateConfiguration, start)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Pages.SimFolderSetup> {
                SimFolderSetupScreen(
                    state = folderState,
                    onPathChanged = folderViewModel::onPathChanged,
                    onBrowse = { folderViewModel.onPicked(folderPicker.pick(folderState.pathInput.ifBlank { null })) },
                    onConfirm = { if (folderViewModel.confirm()) backStack.showHome() },
                    onCancel = if (backStack.size > 1) ({ backStack.removeLastOrNull() }) else null,
                )
            }
            entry<Pages.Home> {
                val confirmed = folderState.confirmed
                if (confirmed != null) {
                    HomeScreen(
                        folder = confirmed,
                        onChangeFolder = {
                            folderViewModel.changeFolder()
                            backStack.add(Pages.SimFolderSetup)
                        },
                    )
                }
            }
        },
    )
}

/** After confirming a folder: go back to Home if it's on the stack, otherwise make it the only page. */
internal fun MutableList<NavKey>.showHome() {
    if (Pages.Home in this) {
        while (lastOrNull() != Pages.Home) removeLastOrNull()
    } else {
        clear()
        add(Pages.Home)
    }
}
