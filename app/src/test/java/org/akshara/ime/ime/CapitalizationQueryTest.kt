package org.akshara.ime.ime

import android.text.InputType
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import org.akshara.ime.settings.KeyboardPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class CapitalizationQueryTest {
    @Test fun disabledCapitalizationDoesNotQueryTheEditor() {
        val controller = Robolectric.buildService(AksharaInputMethodService::class.java).create()
        val service = controller.get()
        try {
            service.onCreateInputView()
            val connection = CountingConnection(View(service))
            ReflectionHelpers.setField(service, "mInputConnection", connection)
            ReflectionHelpers.setField(service, "mStartedInputConnection", connection)
            for ((english, layout, enabled, secure) in listOf(
                Case(false, EditorLayout.TEXT, true, false),
                Case(true, EditorLayout.EMAIL, true, false),
                Case(true, EditorLayout.TEXT, false, false),
                Case(true, EditorLayout.TEXT, true, true))) {
                KeyboardPreferences(service).autoCapitalization = enabled
                ReflectionHelpers.setField(service, "persistentEnglish", english)
                ReflectionHelpers.setField(service, "editorLayout", layout)
                ReflectionHelpers.setField(service, "secureEditor", secure)
                ReflectionHelpers.callInstanceMethod<Any?>(service, "updateEnglishCapitalization")
            }
            assertEquals(0, connection.capsQueries)
            assertEquals(0, connection.textQueries)
        } finally { controller.destroy() }
    }

    @Test fun englishCapitalizationStillUsesTheEditorAndLocalFallback() {
        val controller = Robolectric.buildService(AksharaInputMethodService::class.java).create()
        val service = controller.get()
        try {
            service.onCreateInputView()
            val connection = CountingConnection(View(service))
            ReflectionHelpers.setField(service, "mInputConnection", connection)
            ReflectionHelpers.setField(service, "mStartedInputConnection", connection)
            ReflectionHelpers.setField(service, "mInputEditorInfo", EditorInfo().apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            })
            ReflectionHelpers.setField(service, "persistentEnglish", true)
            ReflectionHelpers.setField(service, "editorLayout", EditorLayout.TEXT)
            ReflectionHelpers.setField(service, "secureEditor", false)
            KeyboardPreferences(service).autoCapitalization = true
            ReflectionHelpers.callInstanceMethod<Any?>(service, "updateEnglishCapitalization")
            assertEquals(1, connection.capsQueries)
            assertEquals(1, connection.textQueries)
        } finally { controller.destroy() }
    }

    private data class Case(val english: Boolean, val layout: EditorLayout, val enabled: Boolean, val secure: Boolean)
    private class CountingConnection(view: View) : BaseInputConnection(view, false) {
        var capsQueries = 0
        var textQueries = 0
        override fun getCursorCapsMode(reqModes: Int): Int { capsQueries++; return 0 }
        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence { textQueries++; return "" }
    }
}
