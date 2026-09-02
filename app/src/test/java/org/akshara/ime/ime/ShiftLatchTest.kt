package org.akshara.ime.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftLatchTest {
    @Test fun singleTapArmsAndALaterTapClearsWithoutCaps() {
        val latch = ShiftLatch(400)
        latch.tap(0)
        assertTrue(latch.shifted)
        assertFalse(latch.capsLock)
        latch.tap(500)
        assertFalse(latch.shifted)
        assertFalse(latch.capsLock)
    }

    @Test fun rapidSecondTapHoldsCapsLock() {
        val latch = ShiftLatch(400)
        latch.tap(0)
        latch.tap(200)
        assertFalse(latch.shifted)
        assertTrue(latch.capsLock)
        latch.tap(800)
        assertFalse(latch.capsLock)
        assertFalse(latch.active)
    }

    @Test fun oneShotIsConsumedAfterALetter() {
        val latch = ShiftLatch(400)
        latch.tap(0)
        latch.consumeOneShot()
        assertFalse(latch.shifted)
        assertFalse(latch.capsLock)
    }
}
