package commanderpepper.optcgsimcardartchooser.theme

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The app-wide appearance choices (theme and text size); owned by `App` so every page shares them. */
@Inject
@ViewModelKey(ThemeViewModel::class)
@ContributesIntoMap(AppScope::class)
class ThemeViewModel(private val store: ThemeStore) : ViewModel() {

    private val _mode = MutableStateFlow(store.load())
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    private val _textScale = MutableStateFlow(TextScale.nearest(store.loadTextScale()))

    /** Text size in percent, one of [TextScale.STEPS]. */
    val textScale: StateFlow<Int> = _textScale.asStateFlow()

    fun setMode(mode: ThemeMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        store.save(mode)
    }

    /** One step larger; does nothing at [TextScale.MAX]. */
    fun increaseTextSize() = step(+1)

    /** One step smaller; does nothing at [TextScale.MIN]. */
    fun decreaseTextSize() = step(-1)

    fun resetTextSize() = setTextScale(TextScale.DEFAULT)

    private fun step(by: Int) {
        val steps = TextScale.STEPS
        steps.getOrNull(steps.indexOf(_textScale.value) + by)?.let(::setTextScale)
    }

    private fun setTextScale(percent: Int) {
        if (_textScale.value == percent) return
        _textScale.value = percent
        store.saveTextScale(percent)
    }
}
