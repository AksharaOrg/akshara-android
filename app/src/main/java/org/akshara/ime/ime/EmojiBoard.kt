package org.akshara.ime.ime

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

internal class EmojiCell(context: Context, ink: Int) : TextView(context) {
    init {
        gravity = Gravity.CENTER
        textSize = KeyboardGeometry.EMOJI_TEXT_SP
        includeFontPadding = false
        if (Build.VERSION.SDK_INT >= 28) isFallbackLineSpacing = false
        setTextColor(ink)
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        isClickable = true
        isFocusable = true
        background = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(ink, 40)),
            null,
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(size, size)
    }
}

internal class EmojiAdapter(
    private val values: List<String>,
    private val ink: Int,
    private val tone: String,
    private val onPick: (String) -> Unit
) : RecyclerView.Adapter<EmojiAdapter.Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val cell = EmojiCell(parent.context, ink)
        cell.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return Holder(cell)
    }

    override fun getItemCount() = values.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val emoji = values[position]
        val drawn = org.akshara.ime.data.EmojiRepository.withTone(emoji, tone)
        holder.cell.text = drawn
        holder.cell.contentDescription = "Emoji $emoji"
        holder.cell.setOnClickListener { onPick(drawn) }
    }

    class Holder(val cell: EmojiCell) : RecyclerView.ViewHolder(cell)
}

internal object EmojiBoard {
    fun scroller(
        context: Context,
        values: List<String>,
        ink: Int,
        tone: String,
        onPick: (String) -> Unit
    ): RecyclerView {
        val columns = columns(context)
        return RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, columns)
            adapter = EmojiAdapter(values, ink, tone, onPick)
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
            setHasFixedSize(true)
            clipChildren = true
            clipToPadding = true
            clipToOutline = true
            isNestedScrollingEnabled = true
            val pad = (4 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
    }

    fun columns(context: Context): Int {
        val landscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        return if (landscape) KeyboardGeometry.EMOJI_COLUMNS_LANDSCAPE else KeyboardGeometry.EMOJI_COLUMNS_PORTRAIT
    }

    fun gridHeight(context: Context, rows: Int, landscape: Boolean): Int {
        val width = context.resources.displayMetrics.widthPixels
        val cell = width / columns(context)
        val count = if (landscape) KeyboardGeometry.EMOJI_ROWS_LANDSCAPE else rows
        return cell * count
    }
}

/** One continuous catalog; category buttons jump to section headings. */
internal class EmojiCatalogView(
    context: Context,
    sections: List<Pair<String, List<String>>>,
    ink: Int,
    tone: String,
    onCategory: (Int) -> Unit,
    onPick: (String) -> Unit
) : RecyclerView(context) {
    private data class Entry(val text: String, val category: Int, val heading: Boolean = false, val empty: Boolean = false)
    private val columns = EmojiBoard.columns(context)
    private val entries = buildList {
        sections.forEachIndexed { category, (title, emoji) ->
            add(Entry(title, category, heading = true))
            if (emoji.isEmpty()) add(Entry("Recently used emoji appear here", category, empty = true))
            else {
                emoji.forEach { add(Entry(it, category)) }
                repeat((columns - emoji.size % columns) % columns) { add(Entry("", category)) }
            }
        }
    }
    private val manager = GridLayoutManager(context, columns).apply {
        spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (entries[position].heading || entries[position].empty) columns else 1
        }
    }
    init {
        layoutManager = manager
        adapter = object : Adapter<ViewHolder>() {
            override fun getItemCount() = entries.size
            override fun getItemViewType(position: Int) = if (entries[position].heading || entries[position].empty) 1 else 0
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                val cell = if (viewType == 0) EmojiCell(context, ink) else TextView(context).apply {
                    textSize = 12f
                    setTextColor(ColorUtils.setAlphaComponent(ink, 160))
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), 0, dp(8), 0)
                }
                cell.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, if (viewType == 0) LayoutParams.WRAP_CONTENT else dp(26))
                return object : ViewHolder(cell) {}
            }
            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                val entry = entries[position]
                val cell = holder.itemView as TextView
                cell.text = if (entry.heading || entry.empty) entry.text else org.akshara.ime.data.EmojiRepository.withTone(entry.text, tone)
                val pickable = !entry.heading && !entry.empty && entry.text.isNotEmpty()
                cell.setOnClickListener(if (pickable) View.OnClickListener { onPick(cell.text.toString()) } else null)
                cell.isClickable = pickable
                cell.isFocusable = pickable
                cell.contentDescription = if (pickable) "Emoji ${entry.text}" else null
                cell.importantForAccessibility = if (entry.text.isEmpty()) View.IMPORTANT_FOR_ACCESSIBILITY_NO else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                androidx.core.view.ViewCompat.setAccessibilityHeading(cell, entry.heading)
            }
        }
        itemAnimator = null
        overScrollMode = View.OVER_SCROLL_NEVER
        setPadding(dp(4), 0, dp(4), 0)
        clipToPadding = true
        addOnScrollListener(object : OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                entries.getOrNull(manager.findFirstVisibleItemPosition())?.let { onCategory(it.category) }
            }
        })
    }
    fun showCategory(category: Int) {
        val position = entries.indexOfFirst { it.category == category && it.heading }
        if (position >= 0) manager.scrollToPositionWithOffset(position, 0)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
