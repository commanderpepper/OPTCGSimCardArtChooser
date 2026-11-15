package commanderpepper.optcgsimcardartchooser.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import commanderpepper.optcgsimcardartchooser.home.HomeScreen
import commanderpepper.optcgsimcardartchooser.home.HomeViewModel
import commanderpepper.optcgsimcardartchooser.cards.CardBrowserScreen
import commanderpepper.optcgsimcardartchooser.cards.CardBrowserViewModel
import commanderpepper.optcgsimcardartchooser.cards.CardDetailScreen
import commanderpepper.optcgsimcardartchooser.cards.CardDetailViewModel
import commanderpepper.optcgsimcardartchooser.cards.DeckListActions
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
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
                    val homeViewModel: HomeViewModel = metroViewModel()
                    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
                    // Runs each time Home is shown, so the modified count is current.
                    LaunchedEffect(homeViewModel) { homeViewModel.refresh() }
                    HomeScreen(
                        folder = confirmed,
                        state = homeState,
                        onBrowseCards = { backStack.add(Pages.CardBrowser) },
                        onChangeFolder = {
                            folderViewModel.changeFolder()
                            backStack.add(Pages.SimFolderSetup)
                        },
                        onReapply = homeViewModel::reapplyAll,
                        onDismissMessage = homeViewModel::dismissMessage,
                    )
                }
            }
            entry<Pages.CardBrowser> {
                // Scoped to this entry: created when the page opens, cleared when it's popped.
                val viewModel: CardBrowserViewModel = metroViewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                // Runs each time the page is shown again, e.g. after changing art on a card page.
                LaunchedEffect(viewModel) { viewModel.refreshModified() }
                CardBrowserScreen(
                    state = state,
                    onQueryChanged = viewModel::onQueryChanged,
                    onToggleColor = viewModel::toggleColor,
                    onToggleCategory = viewModel::toggleCategory,
                    onToggleSet = viewModel::toggleSet,
                    onToggleArtStatus = viewModel::toggleArtStatus,
                    deckActions = remember(viewModel) {
                        DeckListActions(viewModel::applyDeckList, viewModel::clearDeckList, viewModel::dismissDeckMessage)
                    },
                    onClearFilters = viewModel::clearFilters,
                    onRetry = viewModel::retry,
                    onBack = { backStack.removeLastOrNull() },
                    loadThumbnail = viewModel::thumbnail,
                    onCardSelected = { cardId -> backStack.add(Pages.CardDetail(cardId)) },
                )
            }
            entry<Pages.CardDetail> { page ->
                val viewModel = assistedMetroViewModel<CardDetailViewModel, CardDetailViewModel.Factory> { create(page.cardId) }
                val state by viewModel.state.collectAsStateWithLifecycle()
                CardDetailScreen(
                    state = state,
                    onSelect = viewModel::select,
                    onDismissMessage = viewModel::dismissMessage,
                    onBack = { backStack.removeLastOrNull() },
                )
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
