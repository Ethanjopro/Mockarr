package dev.mockarr.core.mocklocation

import dev.mockarr.core.model.SimulatedFix

/**
 * Feeds simulated fixes into Android's mock location facility. The Android
 * implementation registers test providers via LocationManager; this interface
 * keeps consumers testable and the sink swappable.
 */
interface MockLocationController {
    fun start(): MockStartResult

    fun push(fix: SimulatedFix)

    fun stop()
}

sealed interface MockStartResult {
    data object Ok : MockStartResult

    /** Mockarr is not selected as the mock location app in Developer Options. */
    data object NotSelectedAsMockApp : MockStartResult

    data class ProviderError(val message: String) : MockStartResult
}
