package org.akshara.ime.ime

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.akshara.ime.R

/**
 * Recent and pinned clips. Recent rows offer an outlined pin to keep a clip; pinned rows show a filled pin on an
 * accent badge, which unpins the clip back to Recent. Long-press either for Pin or Unpin and Delete.
 */
internal class ClipboardBoard(
    context: Context,
    private val theme: KeyboardTheme,
    private val onPaste: (String) -> Unit,
    private val onBack: () -> Unit,
    private val onClearRecent: () -> Unit,
    private val onPin: (String) -> Unit,
    private val onUnpin: (String) -> Unit,
    private val onRemoveRecent: (String) -> Unit,
    private val onRemovePinned: (String) -> Unit
) : LinearLayout(context) {
    private enum class Tab { RECENT, PINNED }

    private var recent = emptyList<String>()
    private var pinned = emptyList<String>()
    private var tab = Tab.RECENT
    private var confirmingClear = false
    private val recentTab = tabChip { select(Tab.RECENT) }
    private val pinnedTab = tabChip { select(Tab.PINNED) }
    private val clear = toolbarIcon(R.drawable.ic_delete, string(R.string.clip_clear)) { askToClear() }
    private val tabs = tabs()
    private val confirmBar = confirmBar()
    private val empty = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 15f
        setTextColor(ColorUtils.setAlphaComponent(theme.ink, 170))
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
        // The clear confirmation takes the tabs' place, so the question sits right under the button that asked it
        addView(FrameLayout(context).apply {
            addView(tabs, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(confirmBar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(40)).apply {
            topMargin = dp(4)
            marginStart = dp(12)
            marginEnd = dp(12)
            bottomMargin = dp(4)
        })
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(empty, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        bind()
    }

    fun configure(recentItems: List<String>, pinnedItems: List<String>) {
        recent = recentItems
        pinned = pinnedItems
        if (recent.isEmpty()) confirmingClear = false
        bind()
    }

    private fun displayed() = if (tab == Tab.RECENT) recent else pinned

    private fun bind() {
        recentTab.text = context.getString(R.string.clip_recent, recent.size)
        pinnedTab.text = context.getString(R.string.clip_pinned, pinned.size)
        recentTab.isSelected = tab == Tab.RECENT
        pinnedTab.isSelected = tab == Tab.PINNED
        styleTab(recentTab)
        styleTab(pinnedTab)
        tabs.visibility = if (confirmingClear) INVISIBLE else VISIBLE
        confirmBar.visibility = if (confirmingClear) VISIBLE else GONE
        clear.visibility = if (tab == Tab.RECENT && !confirmingClear) VISIBLE else INVISIBLE
        clear.isEnabled = recent.isNotEmpty()
        clear.alpha = if (clear.isEnabled) 1f else 0.38f
        val items = displayed()
        empty.text = string(if (tab == Tab.RECENT) R.string.clip_empty_recent else R.string.clip_empty_pinned)
        empty.visibility = if (items.isEmpty()) VISIBLE else GONE
        list.visibility = if (items.isEmpty()) GONE else VISIBLE
        list.adapter?.notifyDataSetChanged()
    }

    private fun select(next: Tab) {
        if (tab == next) return
        tab = next
        confirmingClear = false
        bind()
    }

    private fun askToClear() {
        if (recent.isEmpty()) return
        confirmingClear = true
        bind()
    }

    private fun toolbar() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            toolbarIcon(R.drawable.ic_key_back, string(R.string.clip_back)) { onBack() },
            LayoutParams(dp(48), LayoutParams.MATCH_PARENT)
        )
        addView(TextView(context).apply {
            text = string(R.string.clip_title)
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setTextColor(theme.ink)
        }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(clear, LayoutParams(dp(48), LayoutParams.MATCH_PARENT))
    }

    private fun tabs() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        background = pill(ColorUtils.setAlphaComponent(theme.ink, 18), dp(20).toFloat())
        setPadding(dp(3), dp(3), dp(3), dp(3))
        addView(recentTab, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(pinnedTab, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
    }

    private fun confirmBar() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = pill(theme.surface, dp(20).toFloat())
        setPadding(dp(16), dp(3), dp(3), dp(3))
        addView(TextView(context).apply {
            text = string(R.string.clip_clear_question)
            textSize = 14f
            setTextColor(theme.ink)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(textButton(string(R.string.clip_cancel), filled = false) {
            confirmingClear = false
            bind()
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        addView(textButton(string(R.string.clip_clear_confirm), filled = true) {
            confirmingClear = false
            onClearRecent()
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply { marginStart = dp(4) })
    }

    private fun textButton(label: String, filled: Boolean, click: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(if (filled) theme.accentInk else theme.ink)
        setPadding(dp(16), 0, dp(16), 0)
        background = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(theme.ink, 40)),
            pill(if (filled) theme.accent else Color.TRANSPARENT, dp(16).toFloat()),
            pill(Color.WHITE, dp(16).toFloat())
        )
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
    }

    private fun tabChip(click: () -> Unit) = TextView(context).apply {
        textSize = 13f
        gravity = Gravity.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isClickable = true
        isFocusable = true
        setOnClickListener { click() }
    }

    private fun styleTab(view: TextView) {
        view.setTextColor(theme.ink)
        view.background = if (view.isSelected) pill(theme.surface, dp(16).toFloat()) else null
    }

    private fun toolbarIcon(icon: Int, description: String, click: () -> Unit) = ImageButton(context).apply {
        setImageResource(icon)
        setColorFilter(theme.ink)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(10), dp(10), dp(10), dp(10))
        contentDescription = description
        background = ColorDrawable(Color.TRANSPARENT)
        setOnClickListener { click() }
    }

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = LinearLayout(parent.context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = keySurface(theme.surface)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
                minimumHeight = dp(56)
            }
            val preview = TextView(parent.context).apply {
                textSize = 15f
                setTextColor(theme.ink)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(16), dp(12), dp(8), dp(12))
            }
            val pin = ImageButton(parent.context).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            row.addView(preview, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(pin, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(10) })
            return Holder(row, preview, pin)
        }

        override fun getItemCount() = displayed().size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val text = displayed()[position]
            val isPinned = tab == Tab.PINNED
            holder.preview.text = text
            holder.row.contentDescription = context.getString(R.string.clip_paste, text.take(40))
            stylePin(holder.pin, isPinned)
            holder.row.setOnClickListener { onPaste(text) }
            holder.pin.setOnClickListener { if (isPinned) onUnpin(text) else onPin(text) }
            holder.row.setOnLongClickListener {
                PopupMenu(context, holder.row).apply {
                    menu.add(string(if (isPinned) R.string.clip_unpin else R.string.clip_pin)).setOnMenuItemClickListener {
                        if (isPinned) onUnpin(text) else onPin(text)
                        true
                    }
                    menu.add(string(R.string.clip_delete)).setOnMenuItemClickListener {
                        if (isPinned) onRemovePinned(text) else onRemoveRecent(text)
                        true
                    }
                    show()
                }
                true
            }
        }
    }

    /** Off: a faint outlined pin (an action). On: a filled pin on an accent badge (a state). */
    private fun stylePin(pin: ImageButton, pinned: Boolean) {
        pin.setImageResource(if (pinned) R.drawable.ic_key_pin else R.drawable.ic_key_pin_outline)
        pin.contentDescription = string(if (pinned) R.string.clip_unpin else R.string.clip_pin)
        pin.setColorFilter(if (pinned) theme.accentInk else ColorUtils.setAlphaComponent(theme.ink, 150))
        val mask = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
        val badge = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (pinned) theme.accent else Color.TRANSPARENT)
        }
        pin.background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(theme.ink, 40)), badge, mask)
    }

    private class Holder(
        val row: LinearLayout,
        val preview: TextView,
        val pin: ImageButton
    ) : RecyclerView.ViewHolder(row)

    private fun keySurface(color: Int) = RippleDrawable(
        ColorStateList.valueOf(ColorUtils.setAlphaComponent(theme.ink, 40)),
        pill(color, dp(12).toFloat()),
        pill(Color.WHITE, dp(12).toFloat())
    )

    private fun pill(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
    }

    private fun string(id: Int) = context.getString(id)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
