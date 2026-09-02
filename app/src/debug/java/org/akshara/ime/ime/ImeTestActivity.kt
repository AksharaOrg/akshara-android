package org.akshara.ime.ime

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.app.Activity
import org.akshara.ime.engine.InputMode
import org.akshara.ime.settings.KeyboardPreferences

/** Debug-build-only, empty editor used for real-device IME acceptance checks. */
class ImeTestActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        intent.getStringExtra("mode")?.let { value ->
            runCatching { KeyboardPreferences(this).mode = InputMode.valueOf(value) }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 80, 32, 32)
        }
        root.addView(TextView(this).apply { text = "Akshara device test"; textSize = 24f })
        val editor = EditText(this).apply {
            id = EDITOR_ID
            hint = "Type here"
            textSize = 24f
            minLines = 4
            gravity = Gravity.TOP
            setSingleLine(false)
            inputType = when (intent.getStringExtra("input")) {
                "number" -> InputType.TYPE_CLASS_NUMBER
                "decimal" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                "phone" -> InputType.TYPE_CLASS_PHONE
                "email" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                "uri" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                "password" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
        }
        root.addView(editor, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 420))
        setContentView(root)
        editor.requestFocus()
        editor.postDelayed({ (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT) }, 350)
    }
    companion object { const val EDITOR_ID = 0xA51 }
}
