package org.akshara.ime.ime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import org.akshara.ime.settings.KeyboardPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class ClipboardPreviewIntegrationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun reset() {
        context.getSharedPreferences(KeyboardPreferences.FILE, 0).edit().clear().commit()
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).clearPrimaryClip()
    }

    @Test fun quickPasteWorksInUrlEditors() {
        KeyboardPreferences(context).clipboardPreview = true
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val controller = Robolectric.buildService(AksharaInputMethodService::class.java).create()
        val service = controller.get()
        try {
            val editor = EditText(context)
            val info = EditorInfo().apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                imeOptions = EditorInfo.IME_ACTION_GO
            }
            val connection = editor.onCreateInputConnection(info)!!
            ReflectionHelpers.setField(service, "mStartedInputConnection", connection)
            ReflectionHelpers.setField(service, "mInputEditorInfo", info)
            service.onCreateInputView()
            service.onStartInput(info, false)
            service.onStartInputView(info, false)
            clipboard.setPrimaryClip(ClipData.newPlainText("URL", "https://akshara.org.lk"))
            service.onClipboardPreviewPaste()
            assertEquals("https://akshara.org.lk", editor.text.toString())
        } finally {
            service.onFinishInputView(true)
            controller.destroy()
        }
    }

    @Test fun passwordEditorsNeverReceiveQuickPaste() {
        KeyboardPreferences(context).clipboardPreview = true
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Secret", "do-not-paste"))
        val controller = Robolectric.buildService(AksharaInputMethodService::class.java).create()
        val service = controller.get()
        try {
            val editor = EditText(context)
            val info = EditorInfo().apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            val connection = editor.onCreateInputConnection(info)!!
            ReflectionHelpers.setField(service, "mStartedInputConnection", connection)
            ReflectionHelpers.setField(service, "mInputEditorInfo", info)
            service.onCreateInputView()
            service.onStartInput(info, false)
            service.onStartInputView(info, false)
            service.onClipboardPreviewPaste()
            assertEquals("", editor.text.toString())
        } finally {
            service.onFinishInputView(true)
            controller.destroy()
        }
    }

    @Test fun layoutAndThemeSettingsApplyWithoutRestartingTheIme() {
        val controller = Robolectric.buildService(AksharaInputMethodService::class.java).create()
        val service = controller.get()
        try {
            val editor = EditText(context)
            val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
            val connection = editor.onCreateInputConnection(info)!!
            ReflectionHelpers.setField(service, "mStartedInputConnection", connection)
            ReflectionHelpers.setField(service, "mInputEditorInfo", info)
            val original = service.onCreateInputView() as KeyboardView
            service.onStartInput(info, false)
            service.onStartInputView(info, false)

            KeyboardPreferences(context).spacePunctuationKeys = true
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            val updated = ReflectionHelpers.getField<KeyboardView>(service, "keyboard")
            updated.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(900, android.view.View.MeasureSpec.EXACTLY)
            )
            updated.layout(0, 0, 1080, 900)
            val bottom = updated.typingLayout()!!.rowKeys(updated.typingLayout()!!.rows - 1)
            val space = bottom.indexOfFirst { it.action == KeyCode.SPACE }
            assertEquals(",", bottom[space - 1].id)
            assertEquals(".", bottom[space + 1].id)

            KeyboardPreferences(context).theme = "dark"
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertNotSame(original, ReflectionHelpers.getField<KeyboardView>(service, "keyboard"))
        } finally {
            service.onFinishInputView(true)
            controller.destroy()
        }
    }
}
