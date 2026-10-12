package org.akshara.ime.ime

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.akshara.ime.engine.InputMode
import org.akshara.ime.settings.KeyboardPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Touch the installed keyboard and accept the PR's typed-English suggestion. */
@RunWith(AndroidJUnit4::class)
class TypedLatinDeviceTest {
    @Test fun tappingTheTypedLatinSuggestionCommitsEnglish() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        val prefs = KeyboardPreferences(instrumentation.targetContext)
        val previousMode = prefs.mode
        val previousEnglish = prefs.persistentEnglish
        val previousSuggestions = prefs.suggestions
        val previousIme = android.provider.Settings.Secure.getString(
            instrumentation.targetContext.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD)
        fun shell(command: String) {
            automation.executeShellCommand(command).use {
                ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
        }
        try {
            prefs.mode = InputMode.SMART_PHONETIC
            prefs.persistentEnglish = false
            prefs.suggestions = true
            val intent = Intent(instrumentation.targetContext, ImeTestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<ImeTestActivity>(intent).use { scenario ->
                shell("ime set lk.org.akshara.keyboard/org.akshara.ime.ime.AksharaInputMethodService")
                scenario.onActivity { activity ->
                    val editor = activity.findViewById<EditText>(ImeTestActivity.EDITOR_ID)
                    editor.requestFocus()
                    activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                        .showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
                fun tap(description: String) {
                    val deadline = SystemClock.uptimeMillis() + 10_000
                    var bounds: Rect? = null
                    var stable = 0
                    while (SystemClock.uptimeMillis() < deadline && stable < 2) {
                        val next = automation.windows.asSequence()
                            .filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                            .mapNotNull { find(it.root, description) }.firstOrNull()
                        stable = if (next != null && next == bounds) stable + 1 else 0
                        bounds = next
                        SystemClock.sleep(80)
                    }
                    assertTrue("visible, stable $description", stable == 2 && bounds != null)
                    val point = requireNotNull(bounds)
                    val down = SystemClock.uptimeMillis()
                    for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                        val event = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                            point.exactCenterX(), point.exactCenterY(), 0)
                        event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                        assertTrue(automation.injectInputEvent(event, true))
                        event.recycle()
                        SystemClock.sleep(30)
                    }
                }
                "meeting".forEach { tap(it.toString()) }
                scenario.onActivity {
                    assertTrue("the preview is Sinhala", it.findViewById<EditText>(ImeTestActivity.EDITOR_ID)
                        .text.any { char -> char in '\u0D80'..'\u0DFF' })
                }
                tap("Suggestion meeting")
                var actual = ""
                val deadline = SystemClock.uptimeMillis() + 5_000
                do {
                    scenario.onActivity { actual = it.findViewById<EditText>(ImeTestActivity.EDITOR_ID).text.toString() }
                    if (actual == "meeting ") break
                    SystemClock.sleep(50)
                } while (SystemClock.uptimeMillis() < deadline)
                assertEquals("meeting ", actual)
            }
        } finally {
            prefs.mode = previousMode
            prefs.persistentEnglish = previousEnglish
            prefs.suggestions = previousSuggestions
            if (!previousIme.isNullOrBlank()) shell("ime set $previousIme")
        }
    }

    private fun find(node: AccessibilityNodeInfo?, description: String): Rect? {
        node ?: return null
        if (node.contentDescription?.toString() == description) {
            return Rect().also(node::getBoundsInScreen).takeUnless { it.isEmpty }
        }
        for (i in 0 until node.childCount) find(node.getChild(i), description)?.let { return it }
        return null
    }
}
