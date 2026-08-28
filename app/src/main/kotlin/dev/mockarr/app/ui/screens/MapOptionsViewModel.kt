package dev.mockarr.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mockarr.core.data.MockarrSettings
import dev.mockarr.core.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The quick options under the Map tab's action row (drag the sheet up):
 * the same DataStore settings the Settings screen edits, no separate state.
 */
@HiltViewModel
class MapOptionsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<MockarrSettings> = repository.settings

    /** True once DataStore has loaded and the sheet hint has never been dismissed. */
    private val _sheetHintPending = MutableStateFlow(false)
    val sheetHintPending: StateFlow<Boolean> = _sheetHintPending.asStateFlow()

    init {
        // Read from disk, not the in-memory default: the default would flash
        // the hint at every existing user for a frame.
        viewModelScope.launch { _sheetHintPending.value = !repository.awaitLoaded().sheetHintSeen }
    }

    fun markSheetHintSeen() {
        _sheetHintPending.value = false
        viewModelScope.launch { repository.setSheetHintSeen(true) }
    }

    fun setStayAtDestination(value: Boolean) {
        viewModelScope.launch { repository.setStayAtDestination(value) }
    }

    fun setTrafficSimEnabled(value: Boolean) {
        viewModelScope.launch { repository.setTrafficSimEnabled(value) }
    }

    fun setJitterEnabled(value: Boolean) {
        viewModelScope.launch { repository.setJitterEnabled(value) }
    }
}
