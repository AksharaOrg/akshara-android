package org.slashboard.ime.ime

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import org.slashboard.ime.R

internal class CalculatorBoard(
    context: Context,
    private val kbColors: KeyboardColors,
    private val onInsert: (String) -> Unit,
    private val onClose: () -> Unit
) : LinearLayout(context) {

    private var expression = ""
    private val exprText = TextView(context)
    private val resultText = TextView(context)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    init {
        orientation = VERTICAL
        clipChildren = true
        clipToPadding = true

        // 1. Header with Back button, Display screen, and Insert button
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }

        // Back button to close calculator
        val backBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_key_back)
            imageTintList = ColorStateList.valueOf(kbColors.ink)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dp(8)
            setPadding(pad, pad, pad, pad)
            background = circularRippleBackground(kbColors.ink)
            contentDescription = "Back to keyboard"
            isClickable = true
            isFocusable = true
            setOnClickListener { onClose() }
        }
        header.addView(backBtn, LayoutParams(dp(42), dp(42)))

        // Expression & Result display
        val displayContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(2), dp(12), dp(2))
        }

        exprText.apply {
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(kbColors.ink)
            gravity = Gravity.END
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.START
            text = "0"
        }

        resultText.apply {
            textSize = 14f
            typeface = Typeface.DEFAULT
            setTextColor(ColorUtils.setAlphaComponent(kbColors.ink, 170))
            gravity = Gravity.END
            maxLines = 1
            text = ""
        }

        displayContainer.addView(exprText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        displayContainer.addView(resultText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        header.addView(displayContainer, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        // Paste/Insert result into document button
        val insertBtn = TextView(context).apply {
            text = "Insert"
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val actionColor = if (kbColors.action != 0 && kbColors.action != Color.TRANSPARENT) kbColors.action else Color.parseColor("#7C4DFF")
            val actionTextColor = if (kbColors.actionText != 0) kbColors.actionText else Color.WHITE
            setTextColor(actionTextColor)
            gravity = Gravity.CENTER
            val pill = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(actionColor)
            }
            background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(Color.WHITE, 80)),
                pill,
                null
            )
            val hPad = dp(12)
            val vPad = dp(6)
            setPadding(hPad, vPad, hPad, vPad)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val toInsert = if (resultText.text.isNotEmpty()) {
                    resultText.text.toString().removePrefix("= ").trim()
                } else if (expression.isNotEmpty()) {
                    expression
                } else "0"
                onInsert(toInsert)
            }
            setOnLongClickListener {
                // Insert full equation "1+4=5"
                val res = if (resultText.text.isNotEmpty()) resultText.text.toString().removePrefix("= ").trim() else ""
                val full = if (res.isNotEmpty() && expression.isNotEmpty()) "$expression = $res" else expression
                onInsert(full)
                true
            }
        }
        header.addView(insertBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, dp(56)))

        // 2. Calculator Keypad (5 rows x 4 columns)
        val rows = listOf(
            listOf("C" to "clear", "( )" to "parens", "%" to "percent", "÷" to "op"),
            listOf("7" to "num", "8" to "num", "9" to "num", "×" to "op"),
            listOf("4" to "num", "5" to "num", "6" to "num", "−" to "op"),
            listOf("1" to "num", "2" to "num", "3" to "num", "+" to "op"),
            listOf("0" to "num", "." to "num", "⌫" to "backspace", "=" to "equal")
        )

        val keypad = LinearLayout(context).apply {
            orientation = VERTICAL
            clipChildren = true
            clipToPadding = true
            setPadding(dp(4), 0, dp(4), dp(4))
        }

        for (rowKeys in rows) {
            val rowLayout = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
            }

            for ((label, type) in rowKeys) {
                val btn = createCalcKey(label, type)
                val lp = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                    val m = dp(3)
                    setMargins(m, m, m, m)
                }
                rowLayout.addView(btn, lp)
            }

            keypad.addView(rowLayout, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }

        addView(keypad, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private fun createCalcKey(label: String, type: String): View {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            includeFontPadding = false
            textSize = when (type) {
                "num" -> 21f
                "op", "equal" -> 22f
                else -> 17f
            }
            typeface = when (type) {
                "num", "equal" -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                else -> Typeface.DEFAULT
            }

            val actionColor = if (kbColors.action != 0 && kbColors.action != Color.TRANSPARENT) kbColors.action else Color.parseColor("#7C4DFF")
            val actionTextColor = if (kbColors.actionText != 0) kbColors.actionText else Color.WHITE

            val bgCol = when (type) {
                "equal" -> actionColor
                "op" -> kbColors.utility
                "clear", "backspace", "parens", "percent" -> ColorUtils.setAlphaComponent(kbColors.utility, 200)
                else -> kbColors.key
            }

            val textCol = when (type) {
                "equal" -> actionTextColor
                "op" -> if (kbColors.highContrast) kbColors.ink else actionColor
                else -> kbColors.ink
            }

            setTextColor(textCol)

            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                val radius = kbColors.keyRadiusDp?.let { dp(it.toInt()).toFloat() } ?: dp(8).toFloat()
                cornerRadius = radius
                setColor(bgCol)
            }

            val rippleCol = if (type == "equal") ColorUtils.setAlphaComponent(Color.WHITE, 80)
                            else ColorUtils.setAlphaComponent(kbColors.ink, 50)

            background = RippleDrawable(ColorStateList.valueOf(rippleCol), shape, null)
            isClickable = true
            isFocusable = true

            setOnClickListener {
                onKeyPress(label, type)
            }

            if (type == "clear" || type == "backspace") {
                setOnLongClickListener {
                    expression = ""
                    updateDisplay()
                    true
                }
            }
        }
    }

    private fun onKeyPress(label: String, type: String) {
        when (type) {
            "num" -> {
                if (label == ".") {
                    val lastNumber = expression.takeLastWhile { it.isDigit() || it == '.' }
                    if (!lastNumber.contains(".")) {
                        expression += if (expression.isEmpty() || !expression.last().isDigit()) "0." else "."
                    }
                } else {
                    if (expression == "0") expression = label
                    else expression += label
                }
            }
            "op" -> {
                val op = when (label) {
                    "÷" -> "÷"
                    "×" -> "×"
                    "−" -> "−"
                    "+" -> "+"
                    else -> label
                }
                if (expression.isNotEmpty() && isLastOp()) {
                    expression = expression.dropLast(1) + op
                } else if (expression.isNotEmpty()) {
                    expression += op
                } else if (op == "−") {
                    expression = "-"
                }
            }
            "percent" -> {
                if (expression.isNotEmpty() && !isLastOp()) {
                    expression += "%"
                }
            }
            "parens" -> {
                val openCount = expression.count { it == '(' }
                val closeCount = expression.count { it == ')' }
                if (openCount > closeCount && expression.isNotEmpty() && (expression.last().isDigit() || expression.last() == ')')) {
                    expression += ")"
                } else {
                    if (expression.isNotEmpty() && (expression.last().isDigit() || expression.last() == ')')) {
                        expression += "×("
                    } else {
                        expression += "("
                    }
                }
            }
            "clear" -> {
                expression = ""
            }
            "backspace" -> {
                if (expression.isNotEmpty()) {
                    expression = expression.dropLast(1)
                }
            }
            "equal" -> {
                computeFinal()
                return
            }
        }
        updateDisplay()
    }

    private fun isLastOp(): Boolean {
        if (expression.isEmpty()) return false
        val c = expression.last()
        return c == '+' || c == '−' || c == '-' || c == '×' || c == '*' || c == '÷' || c == '/'
    }

    private fun updateDisplay() {
        exprText.text = if (expression.isEmpty()) "0" else expression
        if (expression.isEmpty()) {
            resultText.text = ""
            return
        }

        // Live calculation preview
        try {
            val eval = MathEvaluator.evaluate(expression)
            if (!eval.isNaN() && !eval.isInfinite()) {
                resultText.text = "= " + MathEvaluator.formatResult(eval)
            } else {
                resultText.text = ""
            }
        } catch (_: Exception) {
            resultText.text = ""
        }
    }

    private fun computeFinal() {
        if (expression.isEmpty()) return
        try {
            val eval = MathEvaluator.evaluate(expression)
            if (!eval.isNaN() && !eval.isInfinite()) {
                val formatted = MathEvaluator.formatResult(eval)
                expression = formatted
                resultText.text = ""
                exprText.text = formatted
            }
        } catch (_: Exception) {
            resultText.text = "Error"
        }
    }

    private fun circularRippleBackground(inkColor: Int): RippleDrawable {
        val bgShape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ColorUtils.setAlphaComponent(inkColor, 20))
            setStroke(dp(1), ColorUtils.setAlphaComponent(inkColor, 50))
        }
        return RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(inkColor, 80)),
            bgShape,
            null
        )
    }
}
