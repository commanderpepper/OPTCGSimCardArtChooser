package commanderpepper.optcgsimcardartchooser.theme

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The app-wide theme choice; owned by `App` so every page shares it. */
@Inject
@ViewModelKey(ThemeViewModel::class)
@ContributesIntoMap(AppScope::class)
class ThemeViewModel(private val store: ThemeStore) : ViewModel() {

    private val _mode = MutableStateFlow(store.load())
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun setMode(mode: ThemeMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        store.save(mode)
    }
}
