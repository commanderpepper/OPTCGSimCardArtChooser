package commanderpepper.optcgsimcardartchooser.simfolder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeLocator(
    private val valid: Map<String, Boolean>, // path -> writable
    private val suggested: List<String> = emptyList(),
) : SimFolderLocator {
    override fun suggestions() = suggested
    override fun check(path: String): FolderCheck =
        valid[path]?.let { FolderCheck.Valid(path, "$path/Cards", setCount = 3, writable = it) }
            ?: FolderCheck.Invalid(path, "nope")
}

private class FakeStore(var saved: String? = null) : SimFolderStore {
    override fun load() = saved
    override fun save(path: String) { saved = path }
    override fun clear() { saved = null }
}

class SimFolderViewModelTest {

    @Test
    fun savedValidFolderSkipsSetup() {
        val vm = SimFolderViewModel(FakeLocator(mapOf("/sim" to true)), FakeStore("/sim"))
        assertEquals("/sim", vm.state.value.confirmed?.selectedPath)
    }

    @Test
    fun detectedFolderIsPrefilledButNotConfirmedUntilUserVerifies() {
        val store = FakeStore()
        val vm = SimFolderViewModel(FakeLocator(mapOf("/sim" to true), suggested = listOf("/sim")), store)

        assertNull(vm.state.value.confirmed)
        assertEquals("/sim", vm.state.value.pathInput)
        assertTrue(vm.state.value.canConfirm)
        assertNull(store.saved)

        vm.confirm()
        assertNotNull(vm.state.value.confirmed)
        assertEquals("/sim", store.saved)
    }

    @Test
    fun savedFolderThatNoLongerValidatesReturnsToSetupWithNotice() {
        val vm = SimFolderViewModel(FakeLocator(emptyMap()), FakeStore("/gone"))
        assertNull(vm.state.value.confirmed)
        assertNotNull(vm.state.value.notice)
    }

    @Test
    fun readOnlyFolderCannotBeConfirmed() {
        val store = FakeStore()
        val vm = SimFolderViewModel(FakeLocator(mapOf("/ro" to false)), store)
        vm.onPathChanged("/ro")
        assertFalse(vm.state.value.canConfirm)
        vm.confirm()
        assertNull(vm.state.value.confirmed)
        assertNull(store.saved)
    }

    @Test
    fun changeFolderReturnsToSetupWithCurrentPath() {
        val vm = SimFolderViewModel(FakeLocator(mapOf("/sim" to true)), FakeStore("/sim"))
        vm.changeFolder()
        assertNull(vm.state.value.confirmed)
        assertEquals("/sim", vm.state.value.pathInput)
    }
}
