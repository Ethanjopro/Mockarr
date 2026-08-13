package dev.mockarr.app.ui.screens

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mockarr.core.mocklocation.SetupStatus
import dev.mockarr.core.mocklocation.SetupStatusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val setupStatusRepository: SetupStatusRepository,
) : ViewModel() {

    private val _status = MutableStateFlow<SetupStatus?>(null)
    val status: StateFlow<SetupStatus?> = _status.asStateFlow()

    fun refresh() {
        _status.value = setupStatusRepository.check()
    }

    /** One-shot check for launch-time routing decisions. */
    fun isReadyToMock(): Boolean = setupStatusRepository.check().readyToMock
}
