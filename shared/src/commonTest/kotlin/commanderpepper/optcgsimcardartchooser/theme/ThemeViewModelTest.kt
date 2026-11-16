package commanderpepper.optcgsimcardartchooser.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeViewModelTest {
    private class FakeThemeStore(var saved: ThemeMode = ThemeMode.System, var saves: Int = 0) : ThemeStore {
        override fun load() = saved
        override fun save(mode: ThemeMode) {
            saved = mode
            saves++
        }
    }

    @Test
    fun startsWithTheStoredMode() {
        assertEquals(ThemeMode.Dark, ThemeViewModel(FakeThemeStore(ThemeMode.Dark)).mode.value)
    }

    @Test
    fun setModeUpdatesStateAndSaves() {
        val store = FakeThemeStore()
        val viewModel = ThemeViewModel(store)
        viewModel.setMode(ThemeMode.Dark)
        assertEquals(ThemeMode.Dark, viewModel.mode.value)
        assertEquals(ThemeMode.Dark, store.saved)
    }

    @Test
    fun choosingTheSameModeDoesNotSaveAgain() {
        val store = FakeThemeStore(ThemeMode.Light)
        ThemeViewModel(store).setMode(ThemeMode.Light)
        assertEquals(0, store.saves)
    }
}
