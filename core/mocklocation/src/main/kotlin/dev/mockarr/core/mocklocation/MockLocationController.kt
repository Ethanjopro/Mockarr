package dev.mockarr.core.mocklocation

import dev.mockarr.core.model.SimulatedFix

/**
 * Feeds simulated fixes into Android's mock location facility. The Android
 * implementation registers test providers via LocationManager; this interface
 * keeps consumers testable and the sink swappable.
 */
interface MockLocationController {
    val isRunning: Boolean

    fun start(): MockStartResult

    /**
     * Sends [fix] to every mock sink. False when the platform refused it because Mockarr is
     * no longer the selected mock location app: the session can't hold anything any more.
     */
    fun push(fix: SimulatedFix): Boolean

    fun stop()
}

sealed interface MockStartResult {
    data object Ok : MockStartResult

    /** Mockarr is not selected as the mock location app in Developer Options. */
    data object NotSelectedAsMockApp : MockStartResult

    data class ProviderError(val message: String) : MockStartResult
}
