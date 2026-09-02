package org.akshara.ime.ime

/**
 * Gboard-style shift: a tap arms one letter, a rapid second tap holds
 * caps lock, and a later tap only turns the current state off.
 */
internal class ShiftLatch(private val doubleTapMs: Long = KeyboardGeometry.SHIFT_DOUBLE_MS) {
    var shifted = false
        private set
    var capsLock = false
        private set
    val active get() = shifted || capsLock

    private var lastTapAt = Long.MIN_VALUE / 2

    fun tap(now: Long) {
        val rapid = now - lastTapAt <= doubleTapMs
        lastTapAt = now
        when {
            capsLock -> {
                capsLock = false
                shifted = false
            }
            shifted && rapid -> {
                capsLock = true
                shifted = false
            }
            shifted -> shifted = false
            else -> shifted = true
        }
    }

    fun consumeOneShot() {
        if (shifted && !capsLock) shifted = false
    }

    fun reset() {
        shifted = false
        capsLock = false
        lastTapAt = Long.MIN_VALUE / 2
    }
}
