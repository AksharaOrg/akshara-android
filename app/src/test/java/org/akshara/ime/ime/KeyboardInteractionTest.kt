package org.akshara.ime.ime

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import org.akshara.ime.engine.InputMode
import org.akshara.ime.settings.KeyboardPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyboardInteractionTest {
    @Test @org.robolectric.annotation.Config(qualifiers = "land")
    fun landscapePanelsAlsoKeepTheSameHeight() = allPanelsKeepTheSameHeightIncludingTallAndOptionalRows()
    @Test fun emojiSearchTypingDoesNotWriteIntoHostEditor() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var hostText = ""
        val actions = object : KeyboardActions by idleActions() {
            override fun onCharacter(value: String) { hostText += value }
        }
        val view = KeyboardView(context, actions, KeyboardPreferences(context))
        view.configure(InputMode.SMART_PHONETIC, false, "↵")
        findButton(view, "Emoji")!!.performClick()
        findButton(view, "Search emoji")!!.performClick()
        "heart".forEach { findButton(view, it.toString())!!.performClick() }
        assertEquals("", hostText)
        assertNotNull(findButton(view, "Back to emoji"))
    }
    @Test fun allPanelsKeepTheSameHeightIncludingTallAndOptionalRows() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = KeyboardPreferences(context)
        for (size in listOf("compact", "standard", "tall")) {
            for (top in listOf("none", "numbers", "emoji")) {
                prefs.keyboardSize = size; prefs.topRow = top; prefs.clipboardHistory = true
                val view = KeyboardView(context, idleActions(), prefs)
                view.configure(InputMode.SMART_PHONETIC, false, "↵")
                fun height(): Int {
                    view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    view.layout(0, 0, 1080, view.measuredHeight)
                    return view.measuredHeight
                }
                val typing = height()
                findButton(view, "Clipboard history")!!.performClick()
                assertEquals("Clipboard $size $top", typing, height())
                findButton(view, "Clipboard history")!!.performClick()
                findButton(view, "Emoji")!!.performClick()
                assertEquals("Emoji $size $top", typing, height())
                findButton(view, "Search emoji")!!.performClick()
                assertEquals("Search $size $top", typing, height())
                findButton(view, "Back to emoji")!!.performClick()
                assertEquals(typing, height())
            }
        }
        prefs.reset()
    }
    @Test fun globeIsLeftToTheSystemAndLayerTransitionWorks() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var switches = 0
        val actions = object : KeyboardActions {
            override fun onCharacter(value: String) = Unit; override fun onBackspace(word: Boolean) = Unit
            override fun onSpace() = Unit; override fun onEnter() = Unit; override fun onCandidate(value: String) = Unit
            override fun onGlobe() { switches++ }; override fun onModeRequested(mode: InputMode) = Unit
            override fun onHide() = Unit; override fun onCursorDelta(delta: Int) = Unit
        }
        val view = KeyboardView(context, actions, KeyboardPreferences(context))
        view.configure(InputMode.PHONETIC, true, "↵")
        layoutKeyboard(view)
        assertNull(findButton(view, "Next keyboard")); assertEquals(0, switches)
        findButton(view, "Numbers and symbols")!!.performClick()
        assertNotNull(findButton(view, "Letters"))
    }
    @Test fun phoneticKeysExposeSinhalaHintLegends() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val actions = object : KeyboardActions {
            override fun onCharacter(value: String) = Unit; override fun onBackspace(word: Boolean) = Unit
            override fun onSpace() = Unit; override fun onEnter() = Unit; override fun onCandidate(value: String) = Unit
            override fun onGlobe() = Unit; override fun onModeRequested(mode: InputMode) = Unit
            override fun onHide() = Unit; override fun onCursorDelta(delta: Int) = Unit
        }
        val view = KeyboardView(context, actions, KeyboardPreferences(context))
        view.configure(InputMode.SMART_PHONETIC, false, "↵")
        layoutKeyboard(view)
        assertTrue(view.typingLayout()!!.keys.any { it.hint == "ක" })
        view.configure(InputMode.PHONETIC, false, "↵")
        layoutKeyboard(view)
        assertTrue(view.typingLayout()!!.keys.any { it.hint == "ම" })
        view.configure(InputMode.WIJESEKARA, false, "↵")
        layoutKeyboard(view)
        assertTrue(view.typingLayout()!!.keys.none { it.hint == "ම" })
    }
    @Test fun emojiLayerHidesRailAndSearchReturnsResults() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val actions = object : KeyboardActions {
            override fun onCharacter(value: String) = Unit; override fun onBackspace(word: Boolean) = Unit
            override fun onSpace() = Unit; override fun onEnter() = Unit; override fun onCandidate(value: String) = Unit
            override fun onGlobe() = Unit; override fun onModeRequested(mode: InputMode) = Unit
            override fun onHide() = Unit; override fun onCursorDelta(delta: Int) = Unit
        }
        val view = KeyboardView(context, actions, KeyboardPreferences(context))
        view.configure(InputMode.SMART_PHONETIC, true, "↵")
        findButton(view, "Emoji")!!.performClick()
        assertEquals(0, (view.getChildAt(0).layoutParams as LinearLayout.LayoutParams).height)
        assertNotNull(findButton(view, "Smileys & People")); assertNotNull(findButton(view, "Search emoji"))
        assertNotNull(findButton(view, "Flags"))
        findButton(view, "Search emoji")!!.performClick()
        "heart".forEach { findButton(view, it.toString())!!.performClick() }
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 1080, 900)
        assertTrue(hasEmojiResult(view))
    }
    @Test fun wijesekaraFollowsIosPhoneLayoutAndExposesLongPressHints() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val actions = object : KeyboardActions {
            override fun onCharacter(value: String) = Unit; override fun onBackspace(word: Boolean) = Unit
            override fun onSpace() = Unit; override fun onEnter() = Unit; override fun onCandidate(value: String) = Unit
            override fun onGlobe() = Unit; override fun onModeRequested(mode: InputMode) = Unit
            override fun onHide() = Unit; override fun onCursorDelta(delta: Int) = Unit
        }
        val view = KeyboardView(context, actions, KeyboardPreferences(context))
        view.configure(InputMode.WIJESEKARA, false, "↵")
        layoutKeyboard(view)
        assertNotNull(findButton(view, "Rakaranshaya"))
        assertTrue(view.typingLayout()!!.keys.any { it.hint == "ඟ" })
        assertEquals(listOf("ඟ" to "ඟ"), KeyAlternates.extras(".", InputMode.WIJESEKARA, KeyboardLayer.LETTERS, false))
        assertTrue(KeyAlternates.extras("a", InputMode.PHONETIC, KeyboardLayer.LETTERS, false).any { it.first == "à" })
        assertNull(findButton(view, "z, '"))
    }
    @Test fun phoneticKeysMatchGboardProportionsAndOwnLeftoverHits() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = KeyboardView(context, idleActions(), KeyboardPreferences(context))
        view.configure(InputMode.PHONETIC, false, "↵")
        layoutKeyboard(view)
        val q = findTagged(view, "q")!!
        val a = findTagged(view, "a")!!
        assertEquals(q.width, a.width)
        assertTrue(a.left > q.left)
        val layout = view.typingLayout()!!
        val qKey = layout.keyById("q")!!
        val wKey = layout.keyById("w")!!
        val gutter = (qKey.logical.right + wKey.logical.left) / 2f
        assertEquals("q", layout.keyAtLogical(gutter - 1f, qKey.logical.centerY)?.id)
        assertEquals("w", layout.keyAtLogical(gutter + 1f, wKey.logical.centerY)?.id)
        assertEquals("a", layout.keyAtLogical(1f, layout.keyById("a")!!.logical.centerY)?.id)
        val shift = layout.keyById("shift")!!
        val z = layout.keyById("z")!!
        val mid = (shift.logical.right + z.logical.left) / 2f
        assertEquals("shift", layout.keyAtLogical(mid - 1f, shift.logical.centerY)?.id)
        assertEquals("z", layout.keyAtLogical(mid + 1f, z.logical.centerY)?.id)
    }

    @Test fun wijesekaraKeepsShiftHitsInsteadOfGivingLeftoverToRakaranshaya() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = KeyboardView(context, idleActions(), KeyboardPreferences(context))
        view.configure(InputMode.WIJESEKARA, false, "↵")
        layoutKeyboard(view)
        val layout = view.typingLayout()!!
        val shift = layout.keyById("shift")!!
        val rakaranshaya = layout.keyById("rakaranshaya")!!
        assertEquals("shift", layout.keyAtLogical(shift.logical.right - 2f, shift.logical.centerY)?.id)
        assertEquals("rakaranshaya", layout.keyAtLogical(rakaranshaya.logical.left + 2f, rakaranshaya.logical.centerY)?.id)
    }

    @Test fun suggestionRailKeepsPersistentChipsAndCentreWeight() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = KeyboardView(context, idleActions(), KeyboardPreferences(context))
        view.configure(InputMode.SMART_PHONETIC, false, "↵")
        layoutKeyboard(view)
        view.setCandidates(listOf("කතාව", "කතා", "ක"))
        layoutKeyboard(view)
        assertNotNull(findButton(view, "Suggestion කතාව"))
        findButton(view, "Numbers and symbols")!!.performClick()
        layoutKeyboard(view)
        assertTrue((view.getChildAt(0).layoutParams as LinearLayout.LayoutParams).height > 0)
        assertNull(findButton(view, "Suggestion කතාව"))
        assertNull(findButton(view, "Suggestion කතා"))
        assertNotNull(findButton(view, "Letters"))
    }

    @Test fun suggestionRailShowsTwoEmojiInTheRightColumn() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = KeyboardView(context, idleActions(), KeyboardPreferences(context))
        view.configure(InputMode.SMART_PHONETIC, false, "↵")
        layoutKeyboard(view)
        view.setCandidates(listOf("කතාව", "කතා", "ක"), listOf("😀", "😂"))
        layoutKeyboard(view)
        assertNotNull(findButton(view, "Suggestion කතාව"))
        assertNotNull(findButton(view, "Suggestion කතා"))
        assertNull(findButton(view, "Suggestion ක"))
        assertNotNull(findButton(view, "Suggestion 😀"))
        assertNotNull(findButton(view, "Suggestion 😂"))
        view.setCandidates(listOf("කතාව"), listOf("😀"))
        layoutKeyboard(view)
        assertNotNull(findButton(view, "Suggestion 😀"))
        assertNull(findButton(view, "Suggestion 😂"))
    }

    @Test fun clipboardHistoryOpensFromTheSuggestionRailWhenEnabled() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences(KeyboardPreferences.FILE, 0).edit().clear().commit()
        val prefs = KeyboardPreferences(context)
        prefs.clipboardHistory = true
        var pasted = ""
        val actions = object : KeyboardActions {
            override fun onCharacter(value: String) { pasted = value }
            override fun onBackspace(word: Boolean) = Unit
            override fun onSpace() = Unit; override fun onEnter() = Unit; override fun onCandidate(value: String) = Unit
            override fun onGlobe() = Unit; override fun onModeRequested(mode: InputMode) = Unit
            override fun onHide() = Unit; override fun onCursorDelta(delta: Int) = Unit
        }
        val view = KeyboardView(context, actions, prefs)
        view.configure(InputMode.PHONETIC, false, "↵")
        view.setClipboardItems(listOf("copied text"), emptyList())
        layoutKeyboard(view)
        assertEquals(View.VISIBLE, findButton(view, "Clipboard history")!!.visibility)
        findButton(view, "Clipboard history")!!.performClick()
        layoutKeyboard(view)
        assertNotNull(findButton(view, "Back"))
        assertTrue(findText(view, "Recent 1"))
        assertNull(findButton(view, "Hide keyboard"))
        assertNull(findButton(view, "Return to letters"))
        assertEquals(View.VISIBLE, findButton(view, "Clipboard history")!!.visibility)
        findButton(view, "Paste copied text")!!.performClick()
        layoutKeyboard(view)
        assertEquals("copied text", pasted)
        assertEquals(View.VISIBLE, findButton(view, "Clipboard history")!!.visibility)

        prefs.clipboardHistory = false
        val closed = KeyboardView(context, idleActions(), prefs)
        closed.configure(InputMode.PHONETIC, false, "↵")
        layoutKeyboard(closed)
        val hidden = findButton(closed, "Clipboard history")
        assertTrue(hidden == null || hidden.visibility != View.VISIBLE)
    }

    @Test fun clipboardPullExpandsAndCollapsesWhileCollapsedStaysEqualHeight() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences(KeyboardPreferences.FILE, 0).edit().clear().commit()
        val prefs = KeyboardPreferences(context)
        prefs.clipboardHistory = true
        val view = KeyboardView(context, idleActions(), prefs)
        view.configure(InputMode.PHONETIC, false, "↵")
        view.setClipboardItems(listOf("one", "two", "three", "four", "five"), emptyList())
        fun height(): Int {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            view.layout(0, 0, 1080, view.measuredHeight)
            return view.measuredHeight
        }
        val typing = height()
        findButton(view, "Clipboard history")!!.performClick()
        assertEquals(typing, height())
        assertEquals(0, view.clipboardExpansionPx())
        assertNotNull(findButton(view, "Expand clipboard"))

        dragClipboardHandle(view, startY = 12f, endY = -400f)
        val expanded = height()
        assertTrue("expanded=$expanded typing=$typing", expanded > typing)
        assertTrue(view.clipboardExpansionPx() > 0)
        assertNotNull(findButton(view, "Collapse clipboard"))
        val board = findClipboardBoard(view)!!
        assertTrue(board.height > 0)

        findButton(view, "Collapse clipboard")!!.performClick()
        assertEquals(typing, height())
        assertEquals(0, view.clipboardExpansionPx())

        view.setClipboardExpandedForTest(true)
        assertTrue(height() > typing)
        findButton(view, "Back")!!.performClick()
        assertEquals(typing, height())
        assertEquals(0, view.clipboardExpansionPx())
    }

    @Test fun clipboardRailButtonDoesNotLeaveKeyboardExpanded() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences(KeyboardPreferences.FILE, 0).edit().clear().commit()
        val prefs = KeyboardPreferences(context)
        prefs.clipboardHistory = true
        val view = KeyboardView(context, idleActions(), prefs)
        view.configure(InputMode.PHONETIC, false, "↵")
        view.setClipboardItems(listOf("clip"), emptyList())
        fun height(): Int {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            view.layout(0, 0, 1080, view.measuredHeight)
            return view.measuredHeight
        }
        val typing = height()
        findButton(view, "Clipboard history")!!.performClick()
        view.setClipboardExpandedForTest(true)
        assertTrue(height() > typing)
        findButton(view, "Clipboard history")!!.performClick()
        assertEquals(typing, height())
        assertEquals(0, view.clipboardExpansionPx())
        assertNull(findButton(view, "Expand clipboard"))
        assertNull(findButton(view, "Back"))
    }

    @Test fun spacebarUsesAksharaModeCaption() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = KeyboardView(context, idleActions(), KeyboardPreferences(context))
        view.configure(InputMode.SMART_PHONETIC, false, "↵")
        layoutKeyboard(view)
        assertEquals("Akshara - Smart Phonetic", view.typingLayout()!!.keyById("space")!!.label)
        view.configure(InputMode.WIJESEKARA, false, "↵")
        layoutKeyboard(view)
        assertEquals("Akshara - Wijesekara", view.typingLayout()!!.keyById("space")!!.label)
        view.configure(InputMode.PHONETIC, false, "↵")
        layoutKeyboard(view)
        assertEquals("Akshara - Phonetic", view.typingLayout()!!.keyById("space")!!.label)
    }

    @Test fun englishOneWordUsesSpaceCaption() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = KeyboardView(context, idleActions(), KeyboardPreferences(context))
        view.configure(InputMode.SMART_PHONETIC, false, "↵")
        layoutKeyboard(view)
        view.setEnglishOneWord(true)
        layoutKeyboard(view)
        assertEquals("English · one word", view.typingLayout()!!.keyById("space")!!.label)
    }

    @Test fun spaceOnSymbolsReturnsToLetters() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = KeyboardView(context, idleActions(), KeyboardPreferences(context))
        view.configure(InputMode.SMART_PHONETIC, false, "↵")
        layoutKeyboard(view)
        findButton(view, "Numbers and symbols")!!.performClick()
        layoutKeyboard(view)
        assertNotNull(findButton(view, "Letters"))
        findButton(view, "Space")!!.performClick()
        layoutKeyboard(view)
        assertNotNull(findButton(view, "Numbers and symbols"))
    }

    private fun idleActions() = object : KeyboardActions {
        override fun onCharacter(value: String) = Unit; override fun onBackspace(word: Boolean) = Unit
        override fun onSpace() = Unit; override fun onEnter() = Unit; override fun onCandidate(value: String) = Unit
        override fun onGlobe() = Unit; override fun onModeRequested(mode: InputMode) = Unit
        override fun onHide() = Unit; override fun onCursorDelta(delta: Int) = Unit
    }
    private fun layoutKeyboard(view: KeyboardView, width: Int = 1080) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, 900)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, 900)
    }
    private fun dragClipboardHandle(view: KeyboardView, startY: Float, endY: Float) {
        layoutKeyboard(view)
        val x = view.width / 2f
        val downTime = android.os.SystemClock.uptimeMillis()
        fun send(action: Int, y: Float, time: Long) {
            val event = android.view.MotionEvent.obtain(downTime, time, action, x, y, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
        send(android.view.MotionEvent.ACTION_DOWN, startY, downTime)
        val steps = 6
        for (i in 1..steps) {
            val y = startY + (endY - startY) * i / steps
            send(android.view.MotionEvent.ACTION_MOVE, y, downTime + 16L * i)
        }
        send(android.view.MotionEvent.ACTION_UP, endY, downTime + 16L * (steps + 1))
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
    }
    private fun findClipboardBoard(view: View): View? {
        if (view is ClipboardBoard) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findClipboardBoard(view.getChildAt(i))?.let { return it }
        }
        return null
    }
    private fun findTagged(view: View, tag: String): View? {
        if (view.tag == tag) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findTagged(view.getChildAt(i), tag)?.let { return it }
        return null
    }
    private fun findButton(view: View, description: String): View? {
        if (view.isClickable && view.contentDescription == description) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findButton(view.getChildAt(i), description)?.let { return it }
        return null
    }
    private fun findText(view: View, value: String): Boolean {
        if (view is TextView && view.text.toString() == value && view.textSize < 15f) return true
        if (view is ViewGroup) for (i in 0 until view.childCount) if (findText(view.getChildAt(i), value)) return true
        return false
    }
    private fun hasEmojiResult(view: View): Boolean {
        if (view.isClickable && view.contentDescription?.startsWith("Emoji ") == true) return true
        if (view is ViewGroup) for (i in 0 until view.childCount) if (hasEmojiResult(view.getChildAt(i))) return true
        return false
    }
}
