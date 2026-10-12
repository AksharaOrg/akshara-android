package org.akshara.ime.ime

import android.app.Activity
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/** Suggestion text morphs after a pause, but swaps instantly while typing fast (drawing the morphs was most of the keyboard's CPU). */
@RunWith(RobolectricTestRunner::class)
class MorphLabelTypingTest {
    private fun label(): MorphLabel {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val label = MorphLabel(activity, Color.WHITE)
        activity.setContentView(FrameLayout(activity).apply { addView(label, FrameLayout.LayoutParams(600, 120)) })
        shadowOf(android.os.Looper.getMainLooper()).idle()
        return label
    }

    private fun MorphLabel.animating() = (0 until childCount).any { getChildAt(it).alpha < 1f }
    private fun advance(ms: Long) {
        SystemClock.setCurrentTimeMillis(SystemClock.uptimeMillis() + ms)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }

    @Test fun morphsAfterAPauseButNotWhileTypingFast() {
        val label = label()
        advance(1_000)
        label.setText("කොහොම", true)
        assertTrue("a change after a pause morphs", label.animating())

        label.setText("කොහොමද", true)   // the next key, right away
        assertTrue("fast typing swaps the text instantly", !label.animating() && label.childCount > 0)

        advance(SuggestionMorph.QUICK_SUCCESSION_MS + 100)
        label.setText("ඔයා", true)
        assertTrue("morphs again once typing pauses", label.animating())
        assertTrue(label.visibility == View.VISIBLE)
    }
}
