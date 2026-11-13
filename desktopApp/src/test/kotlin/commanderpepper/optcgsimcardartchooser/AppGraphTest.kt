package commanderpepper.optcgsimcardartchooser

import androidx.lifecycle.viewmodel.CreationExtras
import commanderpepper.optcgsimcardartchooser.simfolder.NativeFolderPicker
import commanderpepper.optcgsimcardartchooser.simfolder.SimFolderViewModel
import dev.zacsweers.metro.createGraph
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

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
    fun appScopedBindingsAreSingletons() {
        assertIs<NativeFolderPicker>(graph.folderPicker)
        assertSame(graph.folderPicker, graph.folderPicker)
        assertSame(graph.metroViewModelFactory, graph.metroViewModelFactory)
    }
}
