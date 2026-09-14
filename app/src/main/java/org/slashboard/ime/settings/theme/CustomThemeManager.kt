package org.slashboard.ime.settings.theme

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.InputStream

data class CustomThemeData(
    val id: String,
    val name: String,
    val background: String,
    val key: String,
    val utility: String,
    val ink: String,
    val action: String,
    val actionText: String,
    val selected: String,
    val dark: Boolean,
    val highContrast: Boolean,
    val keyRadiusDp: Float? = null,
    val blurEffect: Boolean = false,
    val keyOpacity: Float = 1.0f,
    val backgroundImagePath: String? = null,
    val spaceKey: String? = null,
    val spaceBorder: String? = null,
    val keyStyle: String = "rounded", // rounded, sharp, circle, pill, minimal, material, ios, neumorphic
    val animationType: String = "scale", // scale, ripple, glow
    val gradientStart: String? = null,
    val gradientEnd: String? = null,
    val borderWidthDp: Float = 0f,
    val borderColor: String? = null,
    val shadowElevationDp: Float = 0f,
    val glowColor: String? = null
)

object CustomThemeManager {
    fun getThemesDir(context: Context): File {
        val dir = File(context.filesDir, "themes")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun saveTheme(context: Context, theme: CustomThemeData): Boolean {
        return try {
            val dir = getThemesDir(context)
            if (!dir.exists()) dir.mkdirs()
            val cleanId = theme.id.removeSuffix(".slashtheme").removeSuffix(".json")
            val file = File(dir, "$cleanId.slashtheme")
            file.writeText(toJson(theme).toString(2))
            val oldJson = File(dir, "$cleanId.json")
            if (oldJson.exists()) oldJson.delete()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getThemes(context: Context): List<CustomThemeData> {
        val dir = getThemesDir(context)
        val files = dir.listFiles { _, name -> name.endsWith(".json") || name.endsWith(".slashtheme") } ?: return emptyList()
        val sortedFiles = files.sortedByDescending { it.lastModified() }
        val themes = mutableListOf<CustomThemeData>()
        val seenIds = mutableSetOf<String>()
        for (file in sortedFiles) {
            val id = file.nameWithoutExtension
            if (seenIds.contains(id)) continue
            try {
                val json = JSONObject(file.readText())
                themes.add(parseTheme(id, json))
                seenIds.add(id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return themes
    }

    fun parseTheme(id: String, json: JSONObject): CustomThemeData {
        return CustomThemeData(
            id = id,
            name = json.optString("name", id),
            background = json.optString("background", "#0F172A"),
            key = json.optString("key", "#1E293B"),
            utility = json.optString("utility", "#1E293B"),
            ink = json.optString("ink", "#38BDF8"),
            action = json.optString("action", "#0284C7"),
            actionText = json.optString("actionText", "#FFFFFF"),
            selected = json.optString("selected", "#334155"),
            dark = json.optBoolean("dark", true),
            highContrast = json.optBoolean("highContrast", false),
            keyRadiusDp = if (json.has("keyRadiusDp")) json.optDouble("keyRadiusDp").toFloat() else null,
            blurEffect = json.optBoolean("blurEffect", false),
            keyOpacity = json.optDouble("keyOpacity", 1.0).toFloat(),
            backgroundImagePath = json.optString("backgroundImagePath").takeIf { it.isNotEmpty() },
            spaceKey = json.optString("spaceKey").takeIf { it.isNotEmpty() },
            spaceBorder = json.optString("spaceBorder").takeIf { it.isNotEmpty() },
            keyStyle = json.optString("keyStyle", "rounded"),
            animationType = json.optString("animationType", "scale"),
            gradientStart = json.optString("gradientStart").takeIf { it.isNotEmpty() },
            gradientEnd = json.optString("gradientEnd").takeIf { it.isNotEmpty() },
            borderWidthDp = json.optDouble("borderWidthDp", 0.0).toFloat(),
            borderColor = json.optString("borderColor").takeIf { it.isNotEmpty() },
            shadowElevationDp = json.optDouble("shadowElevationDp", 0.0).toFloat(),
            glowColor = json.optString("glowColor").takeIf { it.isNotEmpty() }
        )
    }

    fun toJson(theme: CustomThemeData): JSONObject {
        val json = JSONObject()
        json.put("name", theme.name)
        json.put("background", theme.background)
        json.put("key", theme.key)
        json.put("utility", theme.utility)
        json.put("ink", theme.ink)
        json.put("action", theme.action)
        json.put("actionText", theme.actionText)
        json.put("selected", theme.selected)
        json.put("dark", theme.dark)
        json.put("highContrast", theme.highContrast)
        theme.keyRadiusDp?.let { json.put("keyRadiusDp", it.toDouble()) }
        json.put("blurEffect", theme.blurEffect)
        json.put("keyOpacity", theme.keyOpacity.toDouble())
        theme.backgroundImagePath?.let { json.put("backgroundImagePath", it) }
        theme.spaceKey?.let { json.put("spaceKey", it) }
        theme.spaceBorder?.let { json.put("spaceBorder", it) }
        json.put("keyStyle", theme.keyStyle)
        json.put("animationType", theme.animationType)
        theme.gradientStart?.let { json.put("gradientStart", it) }
        theme.gradientEnd?.let { json.put("gradientEnd", it) }
        json.put("borderWidthDp", theme.borderWidthDp.toDouble())
        theme.borderColor?.let { json.put("borderColor", it) }
        json.put("shadowElevationDp", theme.shadowElevationDp.toDouble())
        theme.glowColor?.let { json.put("glowColor", it) }
        return json
    }

    fun importTheme(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val content = inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(content)
                val id = "custom_" + System.currentTimeMillis()
                val file = File(getThemesDir(context), "$id.slashtheme")
                file.writeText(content)
                id
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun shareThemeText(context: Context, id: String) {
        val theme = getTheme(context, id) ?: return
        val json = toJson(theme).toString(2)
        val sendIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, json)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Slashboard Theme: ${theme.name}")
            type = "text/plain"
        }
        val chooser = android.content.Intent.createChooser(sendIntent, "Share Theme: ${theme.name}")
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun duplicateTheme(context: Context, id: String): String? {
        val theme = getTheme(context, id)
        if (theme != null) {
            val newId = "custom_" + System.currentTimeMillis()
            val cloned = theme.copy(id = newId, name = "${theme.name} (Copy)")
            val file = File(getThemesDir(context), "$newId.slashtheme")
            file.writeText(toJson(cloned).toString(2))
            return newId
        } else {
            // Duplicate pre-installed theme into custom theme
            return createFromPreinstalled(context, id, id.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() })
        }
    }

    fun createFromPreinstalled(context: Context, themeId: String, name: String): String {
        val palette = org.slashboard.ime.ime.KeyboardPaletteResolver.resolve(context, themeId, false)
        val hex = { c: Int -> String.format("#%06X", (0xFFFFFF and c)) }
        val newId = "custom_" + System.currentTimeMillis()
        val custom = CustomThemeData(
            id = newId,
            name = "$name (Custom)",
            background = hex(palette.background),
            key = hex(palette.key),
            utility = hex(palette.utility),
            ink = hex(palette.ink),
            action = hex(palette.action),
            actionText = hex(palette.actionText),
            selected = hex(palette.utility),
            dark = palette.dark,
            highContrast = false,
            spaceKey = palette.spaceKey?.let { hex(it) },
            spaceBorder = palette.spaceBorder?.let { hex(it) },
            keyRadiusDp = palette.keyRadiusDp,
            keyStyle = palette.keyStyle,
            animationType = palette.animationType,
            borderWidthDp = palette.borderWidthDp,
            borderColor = palette.borderColor?.let { hex(it) },
            glowColor = palette.glowColor?.let { hex(it) }
        )
        val file = File(getThemesDir(context), "$newId.slashtheme")
        file.writeText(toJson(custom).toString(2))
        return newId
    }

    fun createPhotoWallpaperTheme(context: Context, uri: Uri, themeName: String = "Photo Wallpaper"): String? {
        return try {
            val wallpapersDir = File(context.filesDir, "wallpapers")
            if (!wallpapersDir.exists()) wallpapersDir.mkdirs()
            val destFile = File(wallpapersDir, "wp_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val newId = "custom_wp_" + System.currentTimeMillis()
            val theme = CustomThemeData(
                id = newId,
                name = themeName,
                background = "#0B0F17",
                key = "#1E293B",
                utility = "#0F172A",
                ink = "#FFFFFF",
                action = "#2563EB",
                actionText = "#FFFFFF",
                selected = "#334155",
                dark = true,
                highContrast = false,
                keyOpacity = 0.72f,
                keyRadiusDp = 8f,
                backgroundImagePath = destFile.absolutePath,
                borderWidthDp = 1f,
                borderColor = "#38BDF8",
                keyStyle = "rounded"
            )
            val file = File(getThemesDir(context), "$newId.slashtheme")
            file.writeText(toJson(theme).toString(2))
            newId
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getTheme(context: Context, id: String): CustomThemeData? {
        val cleanId = id.removeSuffix(".slashtheme").removeSuffix(".json")
        val file = File(getThemesDir(context), "$cleanId.slashtheme")
        val fileJson = File(getThemesDir(context), "$cleanId.json")
        val target = if (file.exists()) file else if (fileJson.exists()) fileJson else return null
        
        return try {
            val json = JSONObject(target.readText())
            parseTheme(cleanId, json)
        } catch (e: Exception) {
            null
        }
    }
}
