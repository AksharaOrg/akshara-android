cat << 'INNEREOF' >> app/src/main/java/org/slashboard/ime/ime/KeyboardView.kt
    fun openNotes() {
        layer = KeyboardLayer.NOTES
        render()
    }
    private fun bindNotes() {
        body.removeAllViews()
        val board = NotesBoard(
            context = context,
            palette = palette,
            actions = actions,
            prefs = prefs,
            onDismiss = {
                layer = KeyboardLayer.LETTERS
                render()
            }
        )
        body.addView(board)
    }

    fun openTemplates() {
        layer = KeyboardLayer.TEMPLATES
        render()
    }
    private fun bindTemplates() {
        body.removeAllViews()
        val templatesLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(if (palette.dark) Color.parseColor("#121212") else Color.parseColor("#FAFAFA"))

            val header = android.widget.RelativeLayout(context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48))
                val title = android.widget.TextView(context).apply {
                    text = "Templates"
                    setTextColor(if (palette.dark) Color.WHITE else Color.BLACK)
                    textSize = 16f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                val closeBtn = android.widget.ImageButton(context).apply {
                    setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    setBackgroundResource(android.R.color.transparent)
                    imageTintList = android.content.res.ColorStateList.valueOf(if (palette.dark) Color.WHITE else Color.BLACK)
                    setOnClickListener {
                        layer = KeyboardLayer.LETTERS
                        render()
                    }
                }
                addView(title, android.widget.RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    addRule(android.widget.RelativeLayout.CENTER_IN_PARENT)
                })
                addView(closeBtn, android.widget.RelativeLayout.LayoutParams(dp(48), dp(48)).apply {
                    addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                })
            }
            addView(header)

            val scroll = android.widget.ScrollView(context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            }
            val list = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(dp(16), dp(8), dp(16), dp(8))
            }
            
            val templates = listOf(
                "Can't talk now. Call you later." to "Quick Reply",
                "I'm in a meeting." to "Status",
                "On my way!" to "Status",
                "Please call me when you have a moment." to "Request",
                "Hi, I'd like to follow up on our previous conversation regarding..." to "Email Follow-up",
                "Thank you for your time and consideration." to "Email Closing",
                "Let me know if you need any further information." to "Email Professional"
            )
            
            templates.forEach { (text, category) ->
                val card = android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(palette.key)
                        cornerRadius = dp(8).toFloat()
                    }
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    layoutParams = android.widget.LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(8)
                    }
                    
                    val categoryView = android.widget.TextView(context).apply {
                        this.text = category
                        setTextColor(if (palette.dark) Color.LTGRAY else Color.DKGRAY)
                        textSize = 12f
                    }
                    val textView = android.widget.TextView(context).apply {
                        this.text = text
                        setTextColor(if (palette.dark) Color.WHITE else Color.BLACK)
                        textSize = 14f
                        setPadding(0, dp(4), 0, 0)
                    }
                    
                    addView(categoryView)
                    addView(textView)
                    
                    setOnClickListener {
                        actions.onPasteText(text)
                    }
                }
                list.addView(card)
            }
            
            scroll.addView(list)
            addView(scroll)
        }
        body.addView(templatesLayout)
        templatesLayout.alpha = 0f
        templatesLayout.animate().alpha(1f).setDuration(160).start()
    }
}
INNEREOF
# I appended an extra } to close the class, so I need to remove the previous } if there was one.
