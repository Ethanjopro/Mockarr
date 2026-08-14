package dev.mockarr.app.ui

import dev.mockarr.core.mocklocation.SetupStatus
import dev.mockarr.core.mocklocation.SetupStatusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of setup status for every screen. Screens call [refresh] on
 * resume; a short cache keeps overlapping refreshes (e.g. app launch + map
 * resume) from probing the system twice back to back.
 */
@Singleton
class SetupStatusHolder @Inject constructor(
    private val repository: SetupStatusRepository,
) {
    private val _status = MutableStateFlow<SetupStatus?>(null)
    val status: StateFlow<SetupStatus?> = _status.asStateFlow()

    private var lastRefreshMillis = 0L

    fun refresh(): SetupStatus {
        val now = System.currentTimeMillis()
        val cached = _status.value
        if (cached != null && now - lastRefreshMillis < MIN_REFRESH_INTERVAL_MILLIS) {
            return cached
        }
        lastRefreshMillis = now
        return repository.check().also { _status.value = it }
    }

    private companion object {
        const val MIN_REFRESH_INTERVAL_MILLIS = 500L
    }
}
