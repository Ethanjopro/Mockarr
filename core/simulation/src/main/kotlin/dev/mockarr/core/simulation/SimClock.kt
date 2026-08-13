package dev.mockarr.core.simulation

/**
 * Monotonic time source for the simulation engine. Injected so tests can drive
 * playback with a fake clock instead of real time.
 */
fun interface SimClock {
    fun elapsedNanos(): Long
}
