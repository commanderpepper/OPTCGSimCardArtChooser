package commanderpepper.optcgsimcardartchooser.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeViewModelTest {
    private class FakeThemeStore(
        var saved: ThemeMode = ThemeMode.System,
        var savedTextScale: Int = TextScale.DEFAULT,
    ) : ThemeStore {
        var saves = 0
        var textScaleSaves = 0
        override fun load() = saved
        override fun save(mode: ThemeMode) {
            saved = mode
            saves++
        }
        override fun loadTextScale() = savedTextScale
        override fun saveTextScale(percent: Int) {
            savedTextScale = percent
            textScaleSaves++
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

    @Test
    fun textSizeStepsUpToTheCapAndSaves() {
        val store = FakeThemeStore()
        val viewModel = ThemeViewModel(store)
        assertEquals(100, viewModel.textScale.value)

        viewModel.increaseTextSize()
        assertEquals(110, viewModel.textScale.value)
        assertEquals(110, store.savedTextScale)

        repeat(10) { viewModel.increaseTextSize() }
        assertEquals(135, viewModel.textScale.value, "capped at 135%")
        assertEquals(3, store.textScaleSaves, "110, 120, 135; no saves once at the cap")
    }

    @Test
    fun textSizeStepsDownToTheMinimumAndResets() {
        val store = FakeThemeStore()
        val viewModel = ThemeViewModel(store)
        repeat(10) { viewModel.decreaseTextSize() }
        assertEquals(85, viewModel.textScale.value)

        viewModel.resetTextSize()
        assertEquals(100, viewModel.textScale.value)
        assertEquals(100, store.savedTextScale)
    }

    @Test
    fun aSavedTextSizeOffTheStepsSnapsToTheNearestOne() {
        assertEquals(135, ThemeViewModel(FakeThemeStore(savedTextScale = 200)).textScale.value)
        assertEquals(120, ThemeViewModel(FakeThemeStore(savedTextScale = 118)).textScale.value)
    }
}
