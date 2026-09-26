package org.akshara.ime.ime

import org.akshara.ime.engine.InputMode
import org.akshara.ime.engine.SinhalaEngine
import kotlin.math.max

internal data class KeyboardLayout(
    val keys: List<KeySpec>,
    val width: Float,
    val height: Float,
    val rowHeight: Float,
    val letterWidth: Float,
    val rows: Int
) {
    fun keyAtLogical(x: Float, y: Float): KeySpec? = keys.firstOrNull { it.logical.contains(x, y) }
    fun keyById(id: String): KeySpec? = keys.firstOrNull { it.id == id }
    fun rowKeys(row: Int): List<KeySpec> = keys.filter { it.row == row }
}

internal object KeyboardLayoutFactory {
    fun place(
        rows: List<RowDef>,
        width: Float,
        rowHeight: Float,
        metrics: KeyboardMetrics,
        sliver: Float,
        insetH: Float,
        insetV: Float
    ): KeyboardLayout {
        val widthPx = width.toInt().coerceAtLeast(1)
        val halfGap = metrics.gap / 2f
        val specs = ArrayList<KeySpec>(48)
        rows.forEachIndexed { rowIndex, row ->
            val y = rowIndex * rowHeight
            val slots = packRow(row.keys, metrics, widthPx)
            row.keys.forEachIndexed { index, def ->
                val slot = slots[index]
                val cell = (slot.right - slot.left).toFloat().coerceAtLeast(1f)
                val letter = metrics.tenKeyWidth.toFloat().coerceAtLeast(1f)
                val scale = (cell / letter).coerceIn(0.55f, 1f)
                val ih = (insetH * scale).coerceAtMost(cell * 0.22f)
                val iv = insetV.coerceAtMost(rowHeight * 0.18f)
                var logicalLeft = slot.left - halfGap
                var logicalRight = slot.right + halfGap
                if (row.expandEdges && index == 0) logicalLeft = 0f
                if (row.expandEdges && index == row.keys.lastIndex) logicalRight = width
                val topSliver = if (row.sliverTop) sliver else 0f
                specs += KeySpec(
                    id = def.id,
                    label = def.label,
                    output = def.output,
                    action = def.action,
                    logical = Bounds(logicalLeft, y - topSliver, logicalRight, y + rowHeight),
                    visual = Bounds(slot.left + ih, y + iv, slot.right - ih, y + rowHeight - iv),
                    row = rowIndex,
                    hint = def.hint,
                    extras = def.extras,
                    flickOutput = def.flickOutput,
                    icon = def.icon,
                    utility = def.utility,
                    payload = def.payload
                )
            }
        }
        stealSpaceHits(specs)
        return KeyboardLayout(
            specs, width, rows.size * rowHeight, rowHeight, metrics.tenKeyWidth.toFloat(), rows.size
        )
    }

    fun place(
        rows: List<RowDef>,
        width: Float,
        rowHeight: Float,
        sliver: Float = 4f,
        spacing: String = "standard",
        density: Float = 1f,
        landscape: Boolean = false
    ): KeyboardLayout = place(
        rows,
        width,
        rowHeight,
        KeyboardMetrics.phonetic(width.toInt(), spacing, density, landscape),
        sliver,
        KeyboardGeometry.visualInsetH(density, spacing),
        KeyboardGeometry.visualInsetV(density, spacing)
    )

    private data class PackedKey(val left: Int, val right: Int)

    private fun packRow(keys: List<KeyDef>, metrics: KeyboardMetrics, widthPx: Int): List<PackedKey> {
        val n = keys.size
        if (n == 0) return emptyList()
        val first = keys.first().action
        val last = keys.last().action
        return when {
            last == KeyCode.ENTER -> packBottom(keys, metrics, widthPx)
            last == KeyCode.DELETE && (first == KeyCode.SHIFT || first == KeyCode.LAYER) ->
                packModifierLetterRow(n - 2, metrics, widthPx)
            keys.all { it.action == KeyCode.CHAR } -> packLetterRow(n, metrics, widthPx)
            else -> packLetterRow(n, metrics, widthPx)
        }
    }

    private fun packLetterRow(count: Int, metrics: KeyboardMetrics, widthPx: Int): List<PackedKey> {
        if (count == 9) {
            return packEqual(count, metrics.inset + metrics.secondRowInset, metrics.tenKeyWidth, metrics.gap)
        }
        if (count == 10) {
            val slots = packEqual(count, metrics.inset, metrics.tenKeyWidth, metrics.gap).toMutableList()
            val last = slots.last()
            slots[slots.lastIndex] = last.copy(right = max(last.right, widthPx - metrics.inset))
            return slots
        }
        return packEqualFill(count, metrics.inset, widthPx - metrics.inset, metrics.gap)
    }

    private fun packModifierLetterRow(letters: Int, metrics: KeyboardMetrics, widthPx: Int): List<PackedKey> {
        val gap = metrics.gap
        val inset = metrics.inset
        val end = widthPx - inset
        val letterW = if (letters == 7) metrics.tenKeyWidth else metrics.equalKeyWidth(11)
        val side = if (letters == 7) {
            metrics.shiftWidth
        } else {
            val inner = letters * letterW + (letters - 1).coerceAtLeast(0) * gap
            max(1, (metrics.usable - inner - 2 * gap) / 2)
        }
        var x = inset
        val slots = ArrayList<PackedKey>(letters + 2)
        slots += PackedKey(x, x + side)
        x += side + gap
        repeat(letters) {
            slots += PackedKey(x, x + letterW)
            x += letterW + gap
        }
        slots += PackedKey(x, end)
        return slots
    }

    private fun packBottom(keys: List<KeyDef>, metrics: KeyboardMetrics, widthPx: Int): List<PackedKey> {
        val n = keys.size
        val gap = metrics.gap
        val ten = metrics.tenKeyWidth
        val shift = metrics.shiftWidth
        val widths = IntArray(n)
        var spaceIndex = -1
        keys.forEachIndexed { index, key ->
            when (key.action) {
                KeyCode.SPACE -> spaceIndex = index
                // Language is intentionally the same physical size as '.' and Emoji, not a wide modifier.
                KeyCode.CHAR, KeyCode.EMOJI, KeyCode.LANGUAGE -> widths[index] = ten
                else -> widths[index] = shift
            }
        }
        val gaps = gap * (n - 1).coerceAtLeast(0)
        val fixed = widths.sum()
        if (spaceIndex >= 0) widths[spaceIndex] = max(ten, metrics.usable - fixed - gaps)
        return packFrom(metrics.inset, widths, gap, widthPx - metrics.inset)
    }

    private fun packEqual(count: Int, start: Int, keyW: Int, gap: Int): List<PackedKey> {
        var x = start
        return List(count) {
            PackedKey(x, x + keyW).also { x += keyW + gap }
        }
    }

    private fun packEqualFill(count: Int, start: Int, end: Int, gap: Int): List<PackedKey> {
        val inner = (end - start).coerceAtLeast(count)
        val keyW = max(1, (inner - gap * (count - 1).coerceAtLeast(0)) / count)
        var rem = inner - (keyW * count + gap * (count - 1).coerceAtLeast(0))
        var x = start
        return List(count) {
            val extra = if (rem > 0) 1 else 0
            rem -= extra
            PackedKey(x, x + keyW + extra).also { x += keyW + extra + gap }
        }
    }

    private fun packFrom(start: Int, widths: IntArray, gap: Int, end: Int): List<PackedKey> {
        var x = start
        return widths.indices.map { index ->
            val left = x
            val right = if (index == widths.lastIndex) end else left + widths[index]
            x = right + gap
            PackedKey(left, right)
        }
    }

    /** Space is used far more than "." / emoji; give it the gutter and a sliver of each neighbour. */
    private fun stealSpaceHits(specs: ArrayList<KeySpec>, fraction: Float = KeyboardGeometry.SPACE_STEAL) {
        val spaces = specs.indices.filter { specs[it].action == KeyCode.SPACE }
        for (spaceIndex in spaces) {
            val space = specs[spaceIndex]
            val row = specs.mapIndexed { index, key -> index to key }
                .filter { it.second.row == space.row }
                .sortedBy { it.second.logical.left }
            val position = row.indexOfFirst { it.first == spaceIndex }
            if (position < 0) continue
            if (position > 0) stealFromNeighbor(specs, spaceIndex, row[position - 1].first, fromRight = true, fraction)
            if (position < row.lastIndex) stealFromNeighbor(specs, spaceIndex, row[position + 1].first, fromRight = false, fraction)
        }
    }

    private fun stealFromNeighbor(
        specs: ArrayList<KeySpec>,
        spaceIndex: Int,
        neighborIndex: Int,
        fromRight: Boolean,
        fraction: Float
    ) {
        val space = specs[spaceIndex]
        val neighbor = specs[neighborIndex]
        val amount = neighbor.logical.width * fraction
        if (amount <= 0f) return
        if (fromRight) {
            specs[neighborIndex] = neighbor.copy(logical = neighbor.logical.copy(right = neighbor.logical.right - amount))
            specs[spaceIndex] = space.copy(logical = space.logical.copy(left = space.logical.left - amount))
        } else {
            specs[neighborIndex] = neighbor.copy(logical = neighbor.logical.copy(left = neighbor.logical.left + amount))
            specs[spaceIndex] = space.copy(logical = space.logical.copy(right = space.logical.right + amount))
        }
    }

        fun typingRows(
        mode: InputMode,
        layer: KeyboardLayer,
        shifted: Boolean,
        caps: Boolean,
        editor: EditorLayout,
        topRow: String,
        emojiPicker: Boolean,
        enterLabel: String,
        spaceLabel: String,
        offerGlobe: Boolean = false,
        languageSwitchLabel: String? = null,
        spacePunctuationKeys: Boolean = false,
        english: Boolean = false
    ): List<RowDef> = when (layer) {
        KeyboardLayer.LETTERS -> letterRows(mode, shifted, caps, editor, topRow, enterLabel, spaceLabel, offerGlobe, languageSwitchLabel, spacePunctuationKeys, english)
        KeyboardLayer.NUMBERS -> symbolRows(KeyboardView.numbers, KeyboardLayer.SYMBOLS, "=\\<", enterLabel, spaceLabel)
        KeyboardLayer.SYMBOLS -> symbolRows(KeyboardView.symbols, KeyboardLayer.NUMBERS, "?123", enterLabel, spaceLabel)
        else -> emptyList()
    }

    private fun letterRows(
        mode: InputMode,
        shifted: Boolean,
        caps: Boolean,
        editor: EditorLayout,
        topRow: String,
        enterLabel: String,
        spaceLabel: String,
        offerGlobe: Boolean,
        languageSwitchLabel: String?,
        spacePunctuationKeys: Boolean,
        english: Boolean
    ): List<RowDef> {
        val rows = ArrayList<RowDef>(6)
        val literal = english || editor !in setOf(EditorLayout.TEXT, EditorLayout.URI) || (editor == EditorLayout.URI && languageSwitchLabel == null)
        val wijesekara = !literal && mode == InputMode.WIJESEKARA
        // Persistent English is rendered as ASCII, but remains a full text keyboard.
        if ((!literal || editor == EditorLayout.ASCII || (english && editor == EditorLayout.TEXT)) && topRow == "numbers") {
            rows += RowDef("1234567890".map { charDef(it.toString(), it.toString()) }, expandEdges = true, sliverTop = true)
        }
        if ((!literal || editor == EditorLayout.ASCII || (english && editor == EditorLayout.TEXT)) && topRow == "emoji") {
            rows += RowDef(
                listOf("😀", "😂", "❤️", "👍", "🙏", "🔥", "✨", "🎉", "🇱🇰", "😊").map { charDef(it, it) },
                expandEdges = true,
                sliverTop = true
            )
        }
        val firstLetters = rows.isEmpty()
        if (wijesekara) {
            KeyboardView.slsRows.forEachIndexed { index, ids ->
                val keys = ids.map { id -> letterDef(id, mode, true, shifted, caps) }
                val rowKeys = if (index == 2) {
                    listOf(shiftDef(caps)) + keys + listOf(deleteDef())
                } else keys
                val fractions = if (index == 2) wijesekaraThirdFractions(keys.size) else List(rowKeys.size) { 1f / rowKeys.size }
                rows += RowDef(
                    rowKeys.mapIndexed { i, def -> def.copy(widthFraction = fractions[i]) },
                    expandEdges = true,
                    sliverTop = firstLetters && index == 0
                )
            }
        } else {
            fun key(id: String) = letterDef(id, mode, false, shifted, caps, KeyboardGeometry.LETTER).let {
                if (literal) it.copy(label = it.output, hint = null,
                    extras = if (english && editor == EditorLayout.TEXT) KeyAlternates.extras(id, InputMode.PHONETIC, KeyboardLayer.LETTERS, shifted || caps) else emptyList(),
                    flickOutput = null) else it
            }
            val q = KeyboardView.qwertyRows[0].mapIndexed { index, id ->
                val letter = key(id)
                if (english && topRow != "numbers") {
                    val number = "1234567890"[index].toString()
                    letter.copy(hint = number, extras = listOf(number to number) + letter.extras)
                } else letter
            }
            val a = KeyboardView.qwertyRows[1].map(::key)
            val z = KeyboardView.qwertyRows[2].map(::key)
            rows += RowDef(q, startFraction = 0f, expandEdges = true, sliverTop = firstLetters)
            rows += RowDef(a, startFraction = KeyboardGeometry.ROW2_OFFSET, expandEdges = true)
            rows += RowDef(
                listOf(shiftDef(caps, shifted).copy(widthFraction = KeyboardGeometry.SHIFT)) +
                    z +
                    listOf(deleteDef().copy(widthFraction = KeyboardGeometry.DELETE))
            )
        }
        rows += bottomRow(editor, enterLabel, spaceLabel, offerGlobe, ukComma = !wijesekara, languageSwitchLabel, spacePunctuationKeys)
        return rows
    }

    private fun wijesekaraThirdFractions(letterCount: Int): List<Float> {
        val shift = 0.12f
        val rest = 1f - shift * 2
        return listOf(shift) + List(letterCount) { rest / letterCount } + listOf(shift)
    }

    private fun symbolRows(
        grid: List<List<String>>,
        alternate: KeyboardLayer,
        alternateLabel: String,
        enterLabel: String,
        spaceLabel: String
    ): List<RowDef> {
        val rows = grid.take(2).mapIndexed { index, labels ->
            val fraction = 1f / labels.size
            RowDef(labels.map { charDef(it, it, fraction) }, expandEdges = true, sliverTop = index == 0)
        }.toMutableList()

        val third = grid.getOrElse(2) { emptyList() }
        val characterWidth = (1f - KeyboardGeometry.SYMBOLS - KeyboardGeometry.DELETE) / third.size.coerceAtLeast(1)
        rows += RowDef(
            listOf(
                KeyDef(alternateLabel, alternateLabel, "", KeyCode.LAYER, KeyboardGeometry.SYMBOLS, utility = true, payload = alternate.name),
            ) + third.map { charDef(it, it, characterWidth) } +
                deleteDef().copy(widthFraction = KeyboardGeometry.DELETE)
        )

        val bottom = ArrayList<KeyDef>(6)
        bottom += KeyDef("ABC", "ABC", "", KeyCode.LAYER, KeyboardGeometry.SYMBOLS, utility = true, payload = KeyboardLayer.LETTERS.name)
        bottom += commaDef()
        bottom += spaceDef(1f - bottom.sumOf { it.widthFraction.toDouble() }.toFloat() - KeyboardGeometry.PUNCT - KeyboardGeometry.ENTER, spaceLabel)
        bottom += periodDef()
        bottom += enterDef(enterLabel)
        rows += RowDef(bottom, expandEdges = true)
        return rows
    }

    private fun bottomRow(
        editor: EditorLayout,
        enterLabel: String,
        spaceLabel: String,
        offerGlobe: Boolean,
        ukComma: Boolean,
        languageSwitchLabel: String?,
        spacePunctuationKeys: Boolean
    ): RowDef {
        val keys = ArrayList<KeyDef>(8)
        keys += KeyDef("?123", "?123", "", KeyCode.LAYER, KeyboardGeometry.SYMBOLS, utility = true, payload = KeyboardLayer.NUMBERS.name)
        when (editor) {
            EditorLayout.EMAIL -> keys += charDef("@", "@", KeyboardGeometry.PUNCT)
            EditorLayout.URI -> {
                if (languageSwitchLabel != null) keys += languageSwitchDef(languageSwitchLabel)
                keys += charDef("/", "/", KeyboardGeometry.PUNCT)
            }
            else -> if (languageSwitchLabel != null) keys += languageSwitchDef(languageSwitchLabel)
        }
        val textLike = editor in setOf(EditorLayout.TEXT, EditorLayout.ASCII, EditorLayout.EMAIL, EditorLayout.URI)
        if (spacePunctuationKeys && textLike) {
            keys += commaDef()
        } else if (editor !in setOf(EditorLayout.EMAIL, EditorLayout.URI) && languageSwitchLabel == null && ukComma) {
            keys += commaDef()
        }
        val trailing = ArrayList<KeyDef>(3)
        if (textLike) {
            trailing += periodDef()
        }
        trailing += enterDef(enterLabel)
        val used = keys.sumOf { it.widthFraction.toDouble() } + trailing.sumOf { it.widthFraction.toDouble() }
        keys += spaceDef((1.0 - used).toFloat().coerceAtLeast(0.30f), spaceLabel)
        keys += trailing
        return RowDef(keys, expandEdges = true)
    }

    private fun letterDef(
        id: String,
        mode: InputMode,
        wijesekara: Boolean,
        shifted: Boolean,
        caps: Boolean,
        width: Float = KeyboardGeometry.LETTER
    ): KeyDef {
        val label = letterLabel(id, wijesekara, shifted, caps)
        val output = if (wijesekara) {
            SinhalaEngine.slsCharacter(id, shifted || caps)
        } else if (shifted || caps) {
            id.uppercase()
        } else {
            id
        }
        val extras = KeyAlternates.extras(id, mode, KeyboardLayer.LETTERS, shifted || caps)
        val hint = KeyAlternates.hint(id, mode, KeyboardLayer.LETTERS) ?: phoneticHint(id, mode, wijesekara, shifted, caps)
        val flick = extras.firstOrNull()?.second
        return KeyDef(id, label, output, KeyCode.CHAR, width, hint, extras, flick)
    }

    private fun charDef(id: String, output: String, width: Float = KeyboardGeometry.LETTER): KeyDef {
        val extras = KeyAlternates.extras(id, InputMode.PHONETIC, KeyboardLayer.NUMBERS, false)
        return KeyDef(id, id, output, KeyCode.CHAR, width, extras = extras, flickOutput = extras.firstOrNull()?.second)
    }

    private fun commaDef(): KeyDef {
        val extras = KeyAlternates.extras(",", InputMode.PHONETIC, KeyboardLayer.NUMBERS, false)
        return KeyDef(
            ",", ",", ",", KeyCode.CHAR, KeyboardGeometry.PUNCT,
            extras = extras, flickOutput = extras.firstOrNull()?.second
        )
    }

    private fun languageSwitchDef(label: String) = KeyDef(
        "language", label, "", KeyCode.LANGUAGE, KeyboardGeometry.PUNCT,
        utility = true
    )

    private fun periodDef(): KeyDef {
        val extras = KeyAlternates.extras(".", InputMode.PHONETIC, KeyboardLayer.NUMBERS, false)
        return KeyDef(
            ".", ".", ".", KeyCode.CHAR, KeyboardGeometry.PUNCT,
            extras = extras, flickOutput = extras.firstOrNull()?.second
        )
    }

    private fun shiftDef(caps: Boolean, shifted: Boolean = false) = KeyDef(
        KeyRow.SHIFT, "", "", KeyCode.SHIFT, KeyboardGeometry.SHIFT,
        icon = when {
            caps -> org.akshara.ime.R.drawable.ic_key_caps
            shifted -> org.akshara.ime.R.drawable.ic_key_shift_active
            else -> org.akshara.ime.R.drawable.ic_key_shift
        },
        utility = true
    )

    private fun deleteDef() = KeyDef(
        KeyRow.DELETE, "", "", KeyCode.DELETE, KeyboardGeometry.DELETE,
        icon = org.akshara.ime.R.drawable.ic_key_backspace, utility = true
    )

    private fun spaceDef(width: Float, label: String) = KeyDef("space", label, " ", KeyCode.SPACE, width)

    private fun enterDef(enterLabel: String) = KeyDef(
        "enter",
        if (enterLabel == "↵" || enterLabel == "⌕") "" else enterLabel,
        "",
        KeyCode.ENTER,
        KeyboardGeometry.ENTER,
        icon = when (enterLabel) {
            "↵" -> org.akshara.ime.R.drawable.ic_key_enter
            "⌕" -> org.akshara.ime.R.drawable.ic_key_search
            else -> null
        },
        utility = true
    )

    fun letterLabel(id: String, wijesekara: Boolean, shifted: Boolean, caps: Boolean) = when {
        id == "rakaranshaya" -> if (shifted || caps) "ZWJ" else "්‍ර"
        id == "h" && wijesekara && (shifted || caps) -> "්‍ය"
        wijesekara -> SinhalaEngine.slsKeyLabel(id.single(), shifted || caps)
        else -> id.uppercase()
    }

    fun phoneticHint(id: String, mode: InputMode, wijesekara: Boolean, shifted: Boolean, caps: Boolean): String? {
        if (wijesekara || id.length != 1) return null
        val source = if (shifted || caps) id.uppercase() else id
        val rendered = SinhalaEngine.transliterate(source, mode)
        val hint = StringBuilder()
        rendered.codePoints().forEach { cp -> if (cp in 0x0D80..0x0DFF && cp != 0x0DCA) hint.appendCodePoint(cp) }
        return hint.toString().takeIf { it.isNotEmpty() }
    }
}
