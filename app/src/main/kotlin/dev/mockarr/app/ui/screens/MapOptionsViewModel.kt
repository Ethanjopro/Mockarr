package dev.mockarr.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mockarr.core.data.MockarrSettings
import dev.mockarr.core.data.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
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
