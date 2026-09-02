package org.akshara.ime.ime

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TouchPersonalizationTest {
    @Test fun ewmaOffsetsClampAndReset() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = TouchPersonalizationStore(context)
        store.reset()
        val key = KeySpec("q", "q", "q", KeyCode.CHAR, Bounds(0f, 0f, 40f, 50f), Bounds(4f, 4f, 36f, 46f), 0)
        repeat(40) { store.learn(key, 20f + 12f, 25f, true) }
        val offset = store.offset("q")
        assertTrue(offset.samples > 0)
        assertTrue(kotlin.math.abs(offset.x) <= key.logical.width * KeyboardGeometry.PERSONALIZATION_CLAMP + 0.01f)
        store.reset()
        assertEquals(0, store.offset("q").samples)
    }
}
