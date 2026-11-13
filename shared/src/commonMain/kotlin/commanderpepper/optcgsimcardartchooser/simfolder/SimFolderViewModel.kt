package commanderpepper.optcgsimcardartchooser.simfolder

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SimFolderState(
    /** Set once the user has verified a folder; null means the setup screen is shown. */
    val confirmed: FolderCheck.Valid? = null,
    val pathInput: String = "",
    val check: FolderCheck? = null,
    val suggestions: List<String> = emptyList(),
    /** Shown when a previously saved folder no longer validates (sim moved or uninstalled). */
    val notice: String? = null,
) {
    val canConfirm: Boolean
        get() = (check as? FolderCheck.Valid)?.writable == true
}

@Inject
@ViewModelKey(SimFolderViewModel::class)
@ContributesIntoMap(AppScope::class)
class SimFolderViewModel(
    private val locator: SimFolderLocator,
    private val store: SimFolderStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SimFolderState())
    val state: StateFlow<SimFolderState> = _state.asStateFlow()

    init {
        val saved = store.load()
        val savedCheck = saved?.let(locator::check)
        if (savedCheck is FolderCheck.Valid && savedCheck.writable) {
            _state.value = SimFolderState(confirmed = savedCheck, pathInput = saved, check = savedCheck)
        } else {
            startSetup(
                notice = saved?.let { "The saved OPTCGSim folder is no longer usable. Please select it again." },
            )
        }
    }

    fun onPathChanged(path: String) {
        _state.update { it.copy(pathInput = path, check = if (path.isBlank()) null else locator.check(path)) }
    }

    fun onPicked(path: String?) {
        if (path != null) onPathChanged(path)
    }

    fun confirm() {
        val valid = _state.value.check as? FolderCheck.Valid ?: return
        if (!valid.writable) return
        store.save(valid.selectedPath)
        _state.update { it.copy(confirmed = valid, notice = null) }
    }

    fun changeFolder() {
        startSetup(notice = null, keepInput = _state.value.confirmed?.selectedPath)
    }

    private fun startSetup(notice: String?, keepInput: String? = null) {
        val suggestions = locator.suggestions()
        // Pre-fill the best guess, but never confirm it automatically.
        val initial = keepInput ?: suggestions.firstOrNull().orEmpty()
        _state.value = SimFolderState(
            pathInput = initial,
            check = if (initial.isBlank()) null else locator.check(initial),
            suggestions = suggestions,
            notice = notice,
        )
    }
}
