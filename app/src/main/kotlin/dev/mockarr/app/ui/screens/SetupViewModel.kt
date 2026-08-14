package dev.mockarr.app.ui.screens

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mockarr.app.ui.SetupStatusHolder
import dev.mockarr.core.mocklocation.SetupStatus
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val statusHolder: SetupStatusHolder,
) : ViewModel() {

    val status: StateFlow<SetupStatus?> = statusHolder.status

    fun refresh() {
        statusHolder.refresh()
    }

    /** One-shot check for launch-time routing decisions. */
    fun isReadyToMock(): Boolean = statusHolder.refresh().readyToMock
}
