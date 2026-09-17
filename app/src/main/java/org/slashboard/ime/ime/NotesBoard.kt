package org.slashboard.ime.ime

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import org.slashboard.ime.settings.KeyboardPreferences

class NotesBoard(
    context: Context,
    private val palette: org.slashboard.ime.settings.theme.KeyboardPalette,
    private val actions: KeyboardActions,
    private val prefs: KeyboardPreferences,
    private val onDismiss: () -> Unit
) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setBackgroundColor(if (palette.dark) Color.parseColor("#121212") else Color.parseColor("#FAFAFA"))

        val header = RelativeLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(48))
            val title = TextView(context).apply {
                text = "Encrypted Vault"
                setTextColor(if (palette.dark) Color.WHITE else Color.BLACK)
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            val closeBtn = ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setBackgroundResource(android.R.color.transparent)
                imageTintList = android.content.res.ColorStateList.valueOf(if (palette.dark) Color.WHITE else Color.BLACK)
                setOnClickListener { onDismiss() }
            }
            addView(title, RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.CENTER_IN_PARENT)
            })
            addView(closeBtn, RelativeLayout.LayoutParams(dp(48), dp(48)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
            })
        }
        addView(header)

        val inputRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val inputField = EditText(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            hint = "New secure note..."
            setTextColor(if (palette.dark) Color.WHITE else Color.BLACK)
            setHintTextColor(if (palette.dark) Color.GRAY else Color.LTGRAY)
            background = GradientDrawable().apply {
                setColor(if (palette.dark) Color.DKGRAY else Color.WHITE)
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        
        val addBtn = Button(context).apply {
            text = "Save"
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            }
        }
        
        inputRow.addView(inputField)
        inputRow.addView(addBtn)
        addView(inputRow)

        val scroll = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val list = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        
        val updateList = { 
            list.removeAllViews()
            val savedNotes = prefs.secureNotes.split("|||").filter { it.isNotBlank() }
            if (savedNotes.isEmpty()) {
                val emptyText = TextView(context).apply {
                    text = "No notes saved yet."
                    setTextColor(Color.GRAY)
                    setPadding(0, dp(16), 0, 0)
                    gravity = Gravity.CENTER
                }
                list.addView(emptyText)
            }
            savedNotes.forEach { noteText ->
                val card = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    background = GradientDrawable().apply {
                        setColor(palette.key)
                        cornerRadius = dp(8).toFloat()
                    }
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(8)
                    }
                    
                    val textView = TextView(context).apply {
                        text = noteText
                        setTextColor(if (palette.dark) Color.WHITE else Color.BLACK)
                        textSize = 14f
                        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                        setOnClickListener {
                            actions.onPasteText(noteText)
                        }
                    }
                    
                    val deleteBtn = ImageButton(context).apply {
                        setImageResource(android.R.drawable.ic_menu_delete)
                        setBackgroundResource(android.R.color.transparent)
                        imageTintList = android.content.res.ColorStateList.valueOf(Color.RED)
                        setOnClickListener {
                            prefs.secureNotes = savedNotes.filter { it != noteText }.joinToString("|||")
                            // trigger self update
                            list.removeAllViews()
                        }
                    }
                    
                    addView(textView)
                    addView(deleteBtn)
                }
                list.addView(card)
            }
        }
        
        addBtn.setOnClickListener {
            val text = inputField.text.toString()
            if (text.isNotBlank()) {
                val current = prefs.secureNotes
                prefs.secureNotes = if (current.isEmpty()) text else "$text|||$current"
                inputField.setText("")
                updateList()
            }
        }
        
        updateList()
        scroll.addView(list)
        addView(scroll)
    }
    
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
