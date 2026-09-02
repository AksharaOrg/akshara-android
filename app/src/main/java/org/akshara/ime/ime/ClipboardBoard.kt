package org.akshara.ime.ime

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

internal class ClipboardBoard(
    context: Context,
    private val colors: KeyboardColors,
    private val onPaste: (String) -> Unit,
    private val onBack: () -> Unit,
    private val onHide: () -> Unit,
    private val onClearRecent: () -> Unit,
    private val onPinRecent: (Int) -> Unit,
    private val onRemoveRecent: (Int) -> Unit,
    private val onRemovePinned: (Int) -> Unit
) : LinearLayout(context) {
    private enum class Tab { RECENT, PINNED }

    private var recent = emptyList<String>()
    private var pinned = emptyList<String>()
    private var tab = Tab.RECENT
    private val recentTab = tabChip("Recent") { select(Tab.RECENT) }
    private val pinnedTab = tabChip("Pinned") { select(Tab.PINNED) }
    private val clear = toolbarIcon(org.akshara.ime.R.drawable.ic_delete, "Clear recent clips") { onClearRecent() }
    private val empty = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 15f
        setTextColor(ColorUtils.setAlphaComponent(colors.ink, 170))
        setPadding(dp(24), dp(16), dp(24), dp(16))
    }
    private val list = RecyclerView(context).apply {
        layoutManager = LinearLayoutManager(context)
        adapter = Adapter()
        itemAnimator = null
        overScrollMode = OVER_SCROLL_NEVER
        clipToPadding = false
        setPadding(dp(12), dp(4), dp(12), dp(8))
    }

    init {
        orientation = VERTICAL
        addView(toolbar(), LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        addView(tabs(), LayoutParams(LayoutParams.MATCH_PARENT, dp(40)).apply {
            topMargin = dp(4)
            marginStart = dp(12)
            marginEnd = dp(12)
            bottomMargin = dp(4)
        })
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(empty, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(bottomBar(), LayoutParams(LayoutParams.MATCH_PARENT, dp(54)))
        bind()
    }

    fun configure(recentItems: List<String>, pinnedItems: List<String>) {
        recent = recentItems
        pinned = pinnedItems
        bind()
    }

    private fun displayed() = if (tab == Tab.RECENT) recent else pinned

    private fun bind() {
        recentTab.text = "Recent ${recent.size}"
        pinnedTab.text = "Pinned ${pinned.size}"
        recentTab.isSelected = tab == Tab.RECENT
        pinnedTab.isSelected = tab == Tab.PINNED
        styleTab(recentTab)
        styleTab(pinnedTab)
        clear.visibility = if (tab == Tab.RECENT) VISIBLE else INVISIBLE
        clear.isEnabled = recent.isNotEmpty()
        clear.alpha = if (clear.isEnabled) 1f else 0.38f
        val items = displayed()
        empty.text = if (tab == Tab.RECENT) {
            "Copy text while Akshara is open to save it here. Pin a clip to keep it."
        } else {
            "Pinned clips stay here until you delete them. Tap a clip to paste it."
        }
        empty.visibility = if (items.isEmpty()) VISIBLE else GONE
        list.visibility = if (items.isEmpty()) GONE else VISIBLE
        list.adapter?.notifyDataSetChanged()
    }

    private fun select(next: Tab) {
        if (tab == next) return
        tab = next
        bind()
    }

    private fun toolbar() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            toolbarIcon(org.akshara.ime.R.drawable.ic_key_back, "Back") { onBack() },
            LayoutParams(dp(48), LayoutParams.MATCH_PARENT)
        )
        addView(TextView(context).apply {
            text = "Clipboard"
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setTextColor(colors.ink)
        }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(clear, LayoutParams(dp(48), LayoutParams.MATCH_PARENT))
    }

    private fun tabs() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        background = pill(ColorUtils.setAlphaComponent(colors.ink, 18), dp(20).toFloat())
        setPadding(dp(3), dp(3), dp(3), dp(3))
        addView(recentTab, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(pinnedTab, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
    }

    private fun bottomBar() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(actionKey("ABC", "Return to letters") { onBack() }, LayoutParams(0, dp(48), 1f).apply {
            setMargins(dp(6), dp(4), dp(6), dp(4))
        })
        addView(View(context), LayoutParams(0, dp(48), 3f))
        addView(toolbarIcon(org.akshara.ime.R.drawable.ic_key_hide, "Hide keyboard") { onHide() }.apply {
            background = keySurface(colors.utility)
        }, LayoutParams(0, dp(48), 1f).apply { setMargins(dp(6), dp(4), dp(6), dp(4)) })
    }

    private fun actionKey(label: String, description: String, click: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(colors.ink)
        contentDescription = description
        isClickable = true
        isFocusable = true
        background = keySurface(colors.utility)
        setOnClickListener { click() }
    }

    private fun tabChip(label: String, click: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 13f
        gravity = Gravity.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
    }

    private fun styleTab(view: TextView) {
        view.setTextColor(colors.ink)
        view.background = if (view.isSelected) pill(colors.key, dp(16).toFloat()) else null
    }

    private fun toolbarIcon(icon: Int, description: String, click: () -> Unit) = ImageButton(context).apply {
        setImageResource(icon)
        setColorFilter(colors.ink)
        scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(10), dp(10), dp(10), dp(10))
        contentDescription = description
        background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        setOnClickListener { click() }
    }

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = LinearLayout(parent.context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = keySurface(colors.key)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
                minimumHeight = dp(56)
            }
            val preview = TextView(parent.context).apply {
                textSize = 15f
                setTextColor(colors.ink)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(16), dp(12), dp(8), dp(12))
            }
            val pin = ImageButton(parent.context).apply {
                setImageResource(org.akshara.ime.R.drawable.ic_key_pin)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            }
            row.addView(preview, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(pin, LinearLayout.LayoutParams(dp(44), dp(44)))
            return Holder(row, preview, pin)
        }

        override fun getItemCount() = displayed().size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val text = displayed()[position]
            val pinnedTab = tab == Tab.PINNED
            holder.preview.text = text
            holder.row.contentDescription = "Paste ${text.take(40)}"
            holder.pin.contentDescription = if (pinnedTab) "Remove pin" else "Pin clip"
            holder.pin.setColorFilter(
                if (pinnedTab) ColorUtils.blendARGB(colors.ink, 0xFFFF9800.toInt(), 0.65f) else ColorUtils.setAlphaComponent(colors.ink, 140)
            )
            holder.row.setOnClickListener { onPaste(text) }
            holder.pin.setOnClickListener {
                val index = holder.bindingAdapterPosition
                if (index == RecyclerView.NO_POSITION) return@setOnClickListener
                if (pinnedTab) onRemovePinned(index) else onPinRecent(index)
            }
            holder.row.setOnLongClickListener {
                val index = holder.bindingAdapterPosition
                if (index == RecyclerView.NO_POSITION) return@setOnLongClickListener true
                val menu = PopupMenu(context, holder.row)
                menu.menu.add(if (pinnedTab) "Delete pin" else "Delete").setOnMenuItemClickListener {
                    if (pinnedTab) onRemovePinned(index) else onRemoveRecent(index)
                    true
                }
                if (!pinnedTab) {
                    menu.menu.add("Pin").setOnMenuItemClickListener {
                        onPinRecent(index)
                        true
                    }
                }
                menu.show()
                true
            }
        }
    }

    private class Holder(
        val row: LinearLayout,
        val preview: TextView,
        val pin: ImageButton
    ) : RecyclerView.ViewHolder(row)

    private fun keySurface(color: Int) = RippleDrawable(
        ColorStateList.valueOf(ColorUtils.setAlphaComponent(colors.ink, 40)),
        pill(color, dp(12).toFloat()),
        pill(Color.WHITE, dp(12).toFloat())
    )

    private fun pill(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
