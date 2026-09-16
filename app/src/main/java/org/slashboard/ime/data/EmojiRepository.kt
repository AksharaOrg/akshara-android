package org.slashboard.ime.data

import android.content.Context
import org.slashboard.ime.R
import org.json.JSONObject

data class EmojiCategory(val name: String, val icon: String, val emoji: List<String>)

class EmojiRepository(context: Context) {
    private val index = mutableMapOf<String, MutableSet<String>>()
    private val catalog = linkedSetOf<String>()
    private val categorizedLists = linkedMapOf<String, MutableList<String>>()

    val categories: List<EmojiCategory>

    init {
        val smileysList = mutableListOf<String>()
        val peopleList = mutableListOf<String>()
        val natureList = mutableListOf<String>()
        val foodList = mutableListOf<String>()
        val activitiesList = mutableListOf<String>()
        val travelList = mutableListOf<String>()
        val objectsList = mutableListOf<String>()
        val symbolsList = mutableListOf<String>()
        val flagsList = mutableListOf<String>()

        categorizedLists["Smileys & Emotion"] = smileysList
        categorizedLists["People & Body"] = peopleList
        categorizedLists["Animals & Nature"] = natureList
        categorizedLists["Food & Drink"] = foodList
        categorizedLists["Activities"] = activitiesList
        categorizedLists["Travel & Places"] = travelList
        categorizedLists["Objects"] = objectsList
        categorizedLists["Symbols"] = symbolsList
        categorizedLists["Flags"] = flagsList

        runCatching {
            val provider = com.vanniktech.emoji.ios.IosEmojiProvider()
            provider.categories.forEach { cat ->
                val simpleName = cat.javaClass.simpleName.lowercase()
                cat.emojis.forEach { e ->
                    val unicode = e.unicode
                    catalog.add(unicode)

                    when {
                        simpleName.contains("flag") -> {
                            flagsList.add(unicode)
                        }
                        simpleName.contains("animal") || simpleName.contains("nature") -> {
                            natureList.add(unicode)
                        }
                        simpleName.contains("food") || simpleName.contains("drink") -> {
                            foodList.add(unicode)
                        }
                        simpleName.contains("activit") || simpleName.contains("sport") -> {
                            activitiesList.add(unicode)
                        }
                        simpleName.contains("travel") || simpleName.contains("place") -> {
                            travelList.add(unicode)
                        }
                        simpleName.contains("object") -> {
                            objectsList.add(unicode)
                        }
                        simpleName.contains("symbol") -> {
                            symbolsList.add(unicode)
                        }
                        simpleName.contains("smiley") || simpleName.contains("people") -> {
                            if (isSmileyOrEmotion(unicode)) {
                                smileysList.add(unicode)
                            } else {
                                peopleList.add(unicode)
                            }
                        }
                        else -> {
                            val assignedCat = categoryFor(unicode)
                            categorizedLists[assignedCat]?.add(unicode)
                        }
                    }
                }
            }
        }

        // Put Sri Lanka flag 🇱🇰 at top of flags
        flagsList.remove("🇱🇰")
        flagsList.add(0, "🇱🇰")

        // Load Sinhala search index
        runCatching {
            val text = context.resources.openRawResource(R.raw.sinhala_emoji_index).bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            root.keys().forEach { key ->
                val value = root.get(key)
                when (value) {
                    is String -> {
                        index.getOrPut(key.lowercase()) { linkedSetOf() }.add(value)
                        catalog.add(value)
                    }
                    is org.json.JSONArray -> repeat(value.length()) { i ->
                        val e = value.getString(i)
                        index.getOrPut(key.lowercase()) { linkedSetOf() }.add(e)
                        catalog.add(e)
                    }
                }
            }
        }

        // Build category objects matching WhatsApp layout
        categories = listOf(
            EmojiCategory("Smileys & Emotion", "😀", smileysList.distinct()),
            EmojiCategory("People & Body", "👋", peopleList.distinct()),
            EmojiCategory("Animals & Nature", "🐻", natureList.distinct()),
            EmojiCategory("Food & Drink", "🍔", foodList.distinct()),
            EmojiCategory("Activities", "⚽", activitiesList.distinct()),
            EmojiCategory("Travel & Places", "🚗", travelList.distinct()),
            EmojiCategory("Objects", "💡", objectsList.distinct()),
            EmojiCategory("Symbols", "❤️", symbolsList.distinct()),
            EmojiCategory("Flags", "🚩", flagsList.distinct())
        )
    }

    val allEmoji: List<String> get() = catalog.toList()

    private val englishNames: Map<String, String> by lazy {
        catalog.associateWith { emoji ->
            emoji.codePoints().toArray().map { cp -> Character.getName(cp).orEmpty() }.filter { it.isNotEmpty() }.joinToString(" ").lowercase()
        }
    }

    fun search(query: String, max: Int = 48, scanNames: Boolean = true): List<String> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        val indexed = index.asSequence().filter { (key, _) -> key.contains(needle) }
            .sortedBy { (key, _) -> when { key == needle -> 0; key.startsWith(needle) -> 1; key.split(Regex("[^\\p{L}\\p{N}]+")).any { it.startsWith(needle) } -> 2; else -> 3 } }
            .flatMap { it.value.asSequence() }
        if (!scanNames) return indexed.distinct().take(max).toList()
        val unicodeNamed = englishNames.asSequence().filter { (_, name) -> name.contains(needle) }
            .sortedBy { (_, name) -> if (name.startsWith(needle)) 0 else 1 }.map { it.key }
        return (indexed + unicodeNamed).distinct().take(max).toList()
    }

    private fun isSmileyOrEmotion(emoji: String): Boolean {
        val cp = emoji.codePointAt(0)
        return cp in 0x1F600..0x1F64F || // Emoticons
               cp in 0x1F910..0x1F92F || // Supplemental symbols and faces
               cp in 0x1F970..0x1F978 || // Faces with hearts, party, freezing, etc.
               cp in 0x1FAE0..0x1FAE8 || // Melting, salute, dotted line face, etc.
               cp in 0x1F479..0x1F480 || // Goblin, ogre, ghost, alien, skull
               cp in 0x1F4A9..0x1F4AB || // Poop, dizzy
               cp in 0x1F916..0x1F917 || // Robot, hugging face
               cp == 0x1F383 ||          // Jack-o-lantern
               cp in 0x1F440..0x1F450 || // Eyes, tongue, hands, clapping, thumbs
               cp in 0x1F918..0x1F91F || // Hand signs, pinches
               cp in 0x1FAF0..0x1FAF8 || // Pointing, handshakes, heart hands
               cp in 0x270A..0x270D ||   // Fist, victory, write
               cp == 0x261D || cp in 0x1F590..0x1F596
    }

    private fun categoryFor(emoji: String): String {
        val cp = emoji.codePointAt(0)
        return when {
            isSmileyOrEmotion(emoji) -> "Smileys & Emotion"
            cp in 0x1F466..0x1F487 || cp in 0x1F645..0x1F64F || cp in 0x1F9B0..0x1F9DD -> "People & Body"
            cp in 0x1F32D..0x1F37F || cp in 0x1F950..0x1F96F -> "Food & Drink"
            cp in 0x1F3A0..0x1F3FF -> "Activities"
            cp in 0x1F680..0x1F6FF -> "Travel & Places"
            cp in 0x1F400..0x1F43E || cp in 0x1F980..0x1F9AE || cp in 0x1F300..0x1F32C -> "Animals & Nature"
            cp in 0x1F4A0..0x1F5FF -> "Objects"
            cp in 0x1F1E6..0x1F1FF || cp == 0x1F3F4 || cp == 0x1F3C1 || cp == 0x1F6A9 -> "Flags"
            else -> "Symbols"
        }
    }

    companion object {
        fun withTone(emoji: String, tone: String): String {
            if (tone.isEmpty() || tone.isBlank() || tone.equals("default", ignoreCase = true)) return emoji
            val validTone = tone.codePoints().toArray().firstOrNull { it in 0x1F3FB..0x1F3FF } ?: return emoji
            if (emoji.codePoints().anyMatch { it in 0x1F3FB..0x1F3FF }) return emoji
            val toneable = setOf(0x1F44D, 0x1F44E, 0x1F44F, 0x1F64F, 0x1F4AA, 0x1F44B, 0x1F91D, 0x1FAF6)
            val cps = emoji.codePoints().toArray()
            if (cps.none { it in toneable }) return emoji
            val out = StringBuilder()
            val toneStr = String(Character.toChars(validTone))
            cps.forEach { cp ->
                out.appendCodePoint(cp)
                if (cp in toneable) out.append(toneStr)
            }
            return out.toString()
        }
    }
}

