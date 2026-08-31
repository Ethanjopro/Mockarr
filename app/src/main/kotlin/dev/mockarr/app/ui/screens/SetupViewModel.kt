package dev.mockarr.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mockarr.app.ui.SetupStatusHolder
import dev.mockarr.core.data.SettingsRepository
import dev.mockarr.core.mocklocation.SetupStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val statusHolder: SetupStatusHolder,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val status: StateFlow<SetupStatus?> = statusHolder.status

    fun refresh() {
        statusHolder.refresh()
    }

    /** A fresh probe: can mocking work right now? */
    fun readyNow(): Boolean = statusHolder.refresh().readyToMock

    /** True exactly once: the very first app launch shows the setup checklist. */
    suspend fun isFirstRun(): Boolean = !settingsRepository.awaitLoaded().setupSeen

    fun markSetupSeen() {
        viewModelScope.launch { settingsRepository.setSetupSeen(true) }
    }
}
