package org.slashboard.ime.ime

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vanniktech.emoji.EmojiTextView
import org.slashboard.ime.R
import org.slashboard.ime.data.EmojiCategory
import org.slashboard.ime.data.EmojiRepository

internal class EmojiCell(context: Context, ink: Int) : EmojiTextView(context) {
    init {
        gravity = Gravity.CENTER
        textSize = KeyboardGeometry.EMOJI_TEXT_SP
        val outValue = android.util.TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true) && outValue.resourceId != 0) {
            setBackgroundResource(outValue.resourceId)
        } else {
            background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(ink, 40)),
                null,
                null
            )
        }
        setTextColor(ink)
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        isClickable = true
        isFocusable = true
        maxLines = 1
        ellipsize = null
        val density = context.resources.displayMetrics.density
        setEmojiSize((KeyboardGeometry.EMOJI_TEXT_SP * density).toInt())
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(size, size)
    }
}

internal sealed class EmojiListItem {
    data class Header(val title: String, val categoryIndex: Int) : EmojiListItem()
    data class Item(val unicode: String) : EmojiListItem()
    data class EmptyPlaceholder(val message: String) : EmojiListItem()
}

internal class EmojiGroupAdapter(
    private val items: List<EmojiListItem>,
    private val ink: Int,
    private val tone: String,
    private val onPick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_EMOJI = 1
        const val TYPE_EMPTY = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is EmojiListItem.Header -> TYPE_HEADER
            is EmojiListItem.Item -> TYPE_EMOJI
            is EmojiListItem.EmptyPlaceholder -> TYPE_EMPTY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val density = parent.context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        return when (viewType) {
            TYPE_HEADER -> {
                val tv = TextView(parent.context).apply {
                    textSize = 11.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    setTextColor(ColorUtils.setAlphaComponent(ink, 170))
                    setPadding(dp(12), dp(10), dp(12), dp(4))
                    letterSpacing = 0.08f
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                HeaderHolder(tv)
            }
            TYPE_EMPTY -> {
                val tv = TextView(parent.context).apply {
                    textSize = 13f
                    typeface = Typeface.DEFAULT
                    setTextColor(ColorUtils.setAlphaComponent(ink, 130))
                    gravity = Gravity.CENTER
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                EmptyHolder(tv)
            }
            else -> {
                val cell = EmojiCell(parent.context, ink).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                EmojiHolder(cell)
            }
        }
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is EmojiListItem.Header -> {
                (holder as HeaderHolder).title.text = item.title
            }
            is EmojiListItem.EmptyPlaceholder -> {
                (holder as EmptyHolder).message.text = item.message
            }
            is EmojiListItem.Item -> {
                val cleanedUnicode = item.unicode.replace("default", "", ignoreCase = true).trim()
                val drawn = EmojiRepository.withTone(cleanedUnicode, tone)
                val cell = (holder as EmojiHolder).cell
                cell.text = drawn
                cell.contentDescription = "Emoji $cleanedUnicode"
                cell.setOnClickListener { onPick(drawn) }
            }
        }
    }

    class HeaderHolder(val title: TextView) : RecyclerView.ViewHolder(title)
    class EmptyHolder(val message: TextView) : RecyclerView.ViewHolder(message)
    class EmojiHolder(val cell: EmojiCell) : RecyclerView.ViewHolder(cell)
}

internal class EmojiBoardView(
    context: Context,
    private val kbColors: KeyboardColors,
    private val emojiRepo: EmojiRepository,
    private val recentEmojis: List<String>,
    private val skinTone: String = "",
    private val onPick: (String) -> Unit,
    private val onSearchClick: () -> Unit,
    private val onBackspace: () -> Unit,
    private val onClose: () -> Unit
) : LinearLayout(context) {

    private val categoryTabs = mutableListOf<View>()
    private val categoryHeaderPositions = mutableMapOf<Int, Int>()
    private var activeCategoryIndex = 0
    private var isUserScrolling = false
    private val tabsScrollView: HorizontalScrollView
    private val recyclerView: RecyclerView
    private val layoutManager: GridLayoutManager

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    init {
        orientation = VERTICAL
        clipChildren = true
        clipToPadding = true

        // Build flat items list for continuous vertical scrolling
        val flatItems = mutableListOf<EmojiListItem>()

        // 0: Recent
        categoryHeaderPositions[0] = flatItems.size
        flatItems.add(EmojiListItem.Header("RECENTLY USAGE", 0))
        if (recentEmojis.isEmpty()) {
            flatItems.add(EmojiListItem.EmptyPlaceholder("Recently used emoji will appear here"))
        } else {
            recentEmojis.forEach { flatItems.add(EmojiListItem.Item(it)) }
        }

        // 1..N: Standard Categories
        emojiRepo.categories.forEachIndexed { catIdx, category ->
            val tabIndex = catIdx + 1
            categoryHeaderPositions[tabIndex] = flatItems.size
            flatItems.add(EmojiListItem.Header(category.name.uppercase(), tabIndex))
            category.emoji.forEach { flatItems.add(EmojiListItem.Item(it)) }
        }

        // Columns: 8 in portrait, 12 in landscape
        val columns = columns(context)
        layoutManager = GridLayoutManager(context, columns).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return when (flatItems.getOrNull(position)) {
                        is EmojiListItem.Header, is EmojiListItem.EmptyPlaceholder -> columns
                        else -> 1
                    }
                }
            }
        }

        // 1. TOP BAR with Circular Back Button, Search Button, and Category Tabs
        val topBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(4), dp(4))
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Circular Purple Back Button (matching image)
        val actionColor = if (kbColors.action != 0 && kbColors.action != Color.TRANSPARENT) kbColors.action else Color.parseColor("#7C4DFF")
        val backBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_key_back)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dp(8)
            setPadding(pad, pad, pad, pad)
            val circle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(actionColor)
            }
            background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(Color.WHITE, 80)),
                circle,
                null
            )
            contentDescription = "Back to keyboard"
            isClickable = true
            isFocusable = true
            setOnClickListener { onClose() }
        }
        topBar.addView(backBtn, LayoutParams(dp(38), dp(38)).apply {
            marginEnd = dp(6)
        })

        // Search Button (Magnifier)
        val searchBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_key_search)
            imageTintList = ColorStateList.valueOf(kbColors.ink)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dp(8)
            setPadding(pad, pad, pad, pad)
            background = circularRippleBackground(kbColors.ink)
            contentDescription = "Search emoji"
            isClickable = true
            isFocusable = true
            setOnClickListener { onSearchClick() }
        }
        topBar.addView(searchBtn, LayoutParams(dp(38), dp(38)).apply {
            marginEnd = dp(4)
        })

        // Horizontal Category Tabs
        tabsScrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            isFillViewport = true
        }

        val tabsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val tabList = listOf("🕒" to "Recent") + emojiRepo.categories.map { it.icon to it.name }
        tabList.forEachIndexed { i, (icon, name) ->
            val tabView = createTabIcon(icon, name, i == 0) {
                activeCategoryIndex = i
                updateTabSelection(i)
                val targetPos = categoryHeaderPositions[i] ?: 0
                layoutManager.scrollToPositionWithOffset(targetPos, 0)
            }
            categoryTabs.add(tabView)
            tabsRow.addView(tabView, LayoutParams(dp(38), dp(38)).apply {
                val m = dp(2)
                setMargins(m, 0, m, 0)
            })
        }
        tabsScrollView.addView(tabsRow, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        topBar.addView(tabsScrollView, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))

        addView(topBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))

        // 2. RECYCLER VIEW (Continuous vertical scroll of all categories)
        recyclerView = RecyclerView(context).apply {
            this.layoutManager = this@EmojiBoardView.layoutManager
            adapter = EmojiGroupAdapter(flatItems, kbColors.ink, skinTone, onPick)
            itemAnimator = null
            overScrollMode = OVER_SCROLL_NEVER
            setHasFixedSize(true)
            clipChildren = true
            clipToPadding = false
            val pad = dp(4)
            setPadding(pad, 0, pad, pad)

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    isUserScrolling = newState != RecyclerView.SCROLL_STATE_IDLE
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy == 0) return
                    val firstVisible = this@EmojiBoardView.layoutManager.findFirstVisibleItemPosition()
                    if (firstVisible < 0) return

                    // Find which category header corresponds to this position
                    var currentCat = 0
                    for ((catIdx, pos) in categoryHeaderPositions) {
                        if (firstVisible >= pos) {
                            currentCat = catIdx
                        }
                    }
                    if (currentCat != activeCategoryIndex) {
                        activeCategoryIndex = currentCat
                        updateTabSelection(currentCat)
                    }
                }
            })
        }
        addView(recyclerView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        // 3. BOTTOM BAR (Keyboard return, Emoji indicator, Backspace)
        val bottomBar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setBackgroundColor(Color.TRANSPARENT)
        }

        // Keyboard button (left)
        val keyboardBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_keyboard)
            imageTintList = ColorStateList.valueOf(kbColors.ink)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dp(8)
            setPadding(pad, pad, pad, pad)
            val pill = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(kbColors.utility)
            }
            background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(kbColors.ink, 50)),
                pill,
                null
            )
            contentDescription = "Switch to keyboard"
            isClickable = true
            isFocusable = true
            setOnClickListener { onClose() }
        }
        bottomBar.addView(keyboardBtn, LayoutParams(dp(56), dp(40)).apply {
            marginEnd = dp(8)
        })

        // Center space / Emoji toggle indicator
        val centerIndicator = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            val pill = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(ColorUtils.setAlphaComponent(kbColors.utility, 180))
            }
            background = pill
            setPadding(dp(14), dp(4), dp(14), dp(4))
            val smileyText = TextView(context).apply {
                text = "😀"
                textSize = 18f
                gravity = Gravity.CENTER
            }
            addView(smileyText, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
        bottomBar.addView(centerIndicator, LayoutParams(0, dp(36), 1f))

        // Backspace button (right)
        val backspaceBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_key_backspace)
            imageTintList = ColorStateList.valueOf(kbColors.ink)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dp(8)
            setPadding(pad, pad, pad, pad)
            val pill = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(kbColors.utility)
            }
            background = RippleDrawable(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(kbColors.ink, 50)),
                pill,
                null
            )
            contentDescription = "Delete"
            isClickable = true
            isFocusable = true
            setOnClickListener { onBackspace() }
            setOnLongClickListener {
                onBackspace()
                true
            }
        }
        bottomBar.addView(backspaceBtn, LayoutParams(dp(56), dp(40)).apply {
            marginStart = dp(8)
        })

        addView(bottomBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
    }

    private fun createTabIcon(icon: String, name: String, isSelected: Boolean, onClick: () -> Unit): View {
        return TextView(context).apply {
            text = icon
            textSize = 17f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(kbColors.ink)
            contentDescription = name
            isClickable = true
            isFocusable = true
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            background = tabBackground(isSelected)
            setOnClickListener { onClick() }
        }
    }

    private fun tabBackground(isSelected: Boolean): android.graphics.drawable.Drawable {
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (isSelected) {
                setColor(ColorUtils.setAlphaComponent(kbColors.utility, 220))
                setStroke(dp(1), ColorUtils.setAlphaComponent(kbColors.ink, 60))
            } else {
                setColor(Color.TRANSPARENT)
            }
        }
        return RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(kbColors.ink, 60)),
            shape,
            null
        )
    }

    private fun updateTabSelection(selectedIdx: Int) {
        categoryTabs.forEachIndexed { index, tabView ->
            tabView.background = tabBackground(index == selectedIdx)
        }
        categoryTabs.getOrNull(selectedIdx)?.let { tab ->
            tabsScrollView.smoothScrollTo(tab.left - dp(40), 0)
        }
    }

    private fun circularRippleBackground(inkColor: Int): RippleDrawable {
        val bgShape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ColorUtils.setAlphaComponent(inkColor, 20))
        }
        return RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(inkColor, 70)),
            bgShape,
            null
        )
    }

    companion object {
        fun columns(context: Context): Int {
            val landscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            return if (landscape) KeyboardGeometry.EMOJI_COLUMNS_LANDSCAPE else KeyboardGeometry.EMOJI_COLUMNS_PORTRAIT
        }
    }
}

internal object EmojiBoard {
    fun columns(context: Context): Int = EmojiBoardView.columns(context)

    fun scroller(
        context: Context,
        values: List<String>,
        ink: Int,
        tone: String,
        onPick: (String) -> Unit
    ): RecyclerView {
        val items = values.map { EmojiListItem.Item(it) }
        val cols = columns(context)
        return RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, cols)
            adapter = EmojiGroupAdapter(items, ink, tone, onPick)
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
            setHasFixedSize(true)
            val pad = (4 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
    }

    fun gridHeight(context: Context, rows: Int, landscape: Boolean): Int {
        val width = context.resources.displayMetrics.widthPixels
        val cell = width / columns(context)
        val count = if (landscape) KeyboardGeometry.EMOJI_ROWS_LANDSCAPE else rows
        return cell * count
    }
}
