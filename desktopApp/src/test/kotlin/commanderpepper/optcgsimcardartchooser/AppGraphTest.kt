package commanderpepper.optcgsimcardartchooser

import androidx.lifecycle.viewmodel.CreationExtras
import commanderpepper.optcgsimcardartchooser.cards.CardBrowserViewModel
import commanderpepper.optcgsimcardartchooser.cards.CardDetailViewModel
import commanderpepper.optcgsimcardartchooser.home.HomeViewModel
import commanderpepper.optcgsimcardartchooser.simfolder.NativeFolderPicker
import commanderpepper.optcgsimcardartchooser.simfolder.SimFolderViewModel
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppGraphTest {
    private val graph = createGraph<AppGraph>()

    @Test
    fun viewModelFactoryCreatesInjectedViewModels() {
        val factory = graph.metroViewModelFactory
        val first = factory.create(SimFolderViewModel::class, CreationExtras.Empty)
        val second = factory.create(SimFolderViewModel::class, CreationExtras.Empty)
        assertIs<SimFolderViewModel>(first)
        assertNotSame(first, second, "the factory makes a new ViewModel; Compose's store keeps it per screen")
    }

    @Test
    fun cardBrowserViewModelIsInTheFactory() {
        val vm = graph.metroViewModelFactory.create(CardBrowserViewModel::class, CreationExtras.Empty)
        assertIs<CardBrowserViewModel>(vm)
    }

    @Test
    fun homeViewModelIsInTheFactory() {
        assertIs<HomeViewModel>(graph.metroViewModelFactory.create(HomeViewModel::class, CreationExtras.Empty))
    }

    @Test
    fun cardDetailViewModelIsCreatedWithItsCardId() {
        val factory = graph.metroViewModelFactory.createManuallyAssistedFactory(CardDetailViewModel.Factory::class)()
        assertIs<CardDetailViewModel>(factory.create("OP01-001"))
    }

    @Test
    fun bundledCatalogLoadsThroughTheGraph() = runBlocking {
        val catalog = graph.catalogRepository.load()
        assertTrue(catalog.cards.size > 2_000, "seed catalog has ${catalog.cards.size} cards")
        assertSame(catalog, graph.catalogRepository.load(), "decoded once, then cached")
    }

    @Test
    fun appScopedBindingsAreSingletons() {
        assertIs<NativeFolderPicker>(graph.folderPicker)
        assertSame(graph.folderPicker, graph.folderPicker)
        assertSame(graph.metroViewModelFactory, graph.metroViewModelFactory)
    }
}
