package org.slashboard.ime.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

data class KeyboardPalette(
    val background: Int,
    val key: Int,
    val utility: Int,
    val ink: Int,
    val action: Int,
    val actionText: Int,
    val selected: Int,
    val dark: Boolean,
    val highContrast: Boolean,
    val dynamic: Boolean,
    val keyRadiusDp: Float? = null,
    val blurEffect: Boolean = false,
    val keyOpacity: Float = 1.0f,
    val backgroundImagePath: String? = null,
    val spaceKey: Int? = null,
    val spaceBorder: Int? = null,
    val keyStyle: String = "rounded", // rounded, sharp, circle, pill, minimal, material, ios, neumorphic
    val animationType: String = "scale", // scale, ripple, glow
    val gradientStart: Int? = null,
    val gradientEnd: Int? = null,
    val borderWidthDp: Float = 0f,
    val borderColor: Int? = null,
    val shadowElevationDp: Float = 0f,
    val glowColor: Int? = null
)

object KeyboardPaletteResolver {
    private fun normalizeHex(hex: String?): String? {
        if (hex.isNullOrBlank()) return null
        val trimmed = hex.trim()
        return if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    }

    private fun safeParseColor(colorStr: String?, defaultColor: String): Int {
        val normalized = normalizeHex(colorStr) ?: defaultColor
        return try {
            Color.parseColor(normalized)
        } catch (e: Exception) {
            try {
                Color.parseColor(defaultColor)
            } catch (ex: Exception) {
                Color.BLACK
            }
        }
    }

    private fun safeParseColorOrNull(colorStr: String?): Int? {
        val normalized = normalizeHex(colorStr) ?: return null
        return try {
            Color.parseColor(normalized)
        } catch (e: Exception) {
            null
        }
    }

    fun resolve(context: Context, theme: String, highContrast: Boolean): KeyboardPalette {
        val dark = when (theme) {
            "light", "cyberpunk", "lavender", "rose_gold", "cherry", "solarized_light", "mint", "peach", "silver", "material_light", "ios_style", "neumorphic", "cherry_blossom", "transparent_glass_light", "minimal_white", "pastel_dream", "nordic_clean" -> false
            "system" -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            else -> true
        }
        
        if (theme == "system" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                return dynamic(context, dark, highContrast)
            } catch (e: Exception) {
                // Fallback if system colors are missing on this OEM
            }
        }
        
        if (theme.startsWith("custom_")) {
            val custom = org.slashboard.ime.settings.theme.CustomThemeManager.getTheme(context, theme)
            if (custom != null) {
                return KeyboardPalette(
                    background = safeParseColor(custom.background, "#0F172A"),
                    key = safeParseColor(custom.key, "#1E293B"),
                    utility = safeParseColor(custom.utility, "#1E293B"),
                    ink = safeParseColor(custom.ink, "#38BDF8"),
                    action = safeParseColor(custom.action, "#0284C7"),
                    actionText = safeParseColor(custom.actionText, "#FFFFFF"),
                    selected = safeParseColor(custom.selected, "#334155"),
                    dark = custom.dark,
                    highContrast = highContrast || custom.highContrast,
                    dynamic = false,
                    keyRadiusDp = custom.keyRadiusDp,
                    blurEffect = custom.blurEffect,
                    keyOpacity = custom.keyOpacity,
                    backgroundImagePath = custom.backgroundImagePath,
                    spaceKey = custom.spaceKey?.let { safeParseColor(it, custom.key) },
                    spaceBorder = custom.spaceBorder?.let { safeParseColor(it, custom.ink) },
                    keyStyle = custom.keyStyle,
                    animationType = custom.animationType,
                    gradientStart = safeParseColorOrNull(custom.gradientStart),
                    gradientEnd = safeParseColorOrNull(custom.gradientEnd),
                    borderWidthDp = custom.borderWidthDp,
                    borderColor = safeParseColorOrNull(custom.borderColor),
                    shadowElevationDp = custom.shadowElevationDp,
                    glowColor = safeParseColorOrNull(custom.glowColor)
                )
            }
        }
        
        return when (theme) {
            // Transparent Glass Themes (Translucent keys with clear background)
            "transparent_glass", "transparent" -> KeyboardPalette(
                background = Color.TRANSPARENT,
                key = Color.parseColor("#33FFFFFF"),
                utility = Color.parseColor("#22FFFFFF"),
                ink = Color.parseColor("#FFFFFF"),
                action = Color.parseColor("#0072FF"),
                actionText = Color.parseColor("#FFFFFF"),
                selected = Color.parseColor("#55FFFFFF"),
                dark = true,
                highContrast = highContrast,
                dynamic = false,
                keyOpacity = 0.70f,
                keyStyle = "rounded",
                keyRadiusDp = 10f,
                borderWidthDp = 1f,
                borderColor = Color.parseColor("#44FFFFFF")
            )
            "transparent_glass_light" -> KeyboardPalette(
                background = Color.TRANSPARENT,
                key = Color.parseColor("#2B000000"),
                utility = Color.parseColor("#1C000000"),
                ink = Color.parseColor("#111827"),
                action = Color.parseColor("#007AFF"),
                actionText = Color.parseColor("#FFFFFF"),
                selected = Color.parseColor("#44000000"),
                dark = false,
                highContrast = highContrast,
                dynamic = false,
                keyOpacity = 0.70f,
                keyStyle = "rounded",
                keyRadiusDp = 10f,
                borderWidthDp = 1f,
                borderColor = Color.parseColor("#22000000")
            )

            // 15 Core Featured Themes (LiyoBoard Inspired & Polished)
            "minimal_white" -> custom(bg="#FAFAFA", key="#FFFFFF", util="#F3F4F6", ink="#111827", action="#10B981", actText="#FFFFFF", sel="#E5E7EB", dark=false, hc=highContrast, keyStyle="rounded", keyRadiusDp=12f, borderWidthDp=0.5f, borderColor=Color.parseColor("#E5E7EB"))
            "pastel_dream" -> custom(bg="#FDF4F5", key="#FFFFFF", util="#F8E8E9", ink="#4A4A4A", action="#F4A261", actText="#FFFFFF", sel="#EFC3CA", dark=false, hc=highContrast, keyStyle="rounded", keyRadiusDp=14f)
            "nordic_clean" -> custom(bg="#F0F4F8", key="#FFFFFF", util="#E2E8F0", ink="#334155", action="#3B82F6", actText="#FFFFFF", sel="#CBD5E1", dark=false, hc=highContrast, keyStyle="rounded", keyRadiusDp=12f)
            "pure_dark" -> custom(bg="#09090B", key="#18181B", util="#27272A", ink="#F4F4F5", action="#3B82F6", actText="#FFFFFF", sel="#3F3F46", dark=true, hc=highContrast, keyStyle="rounded", keyRadiusDp=12f, borderWidthDp=0.5f, borderColor=Color.parseColor("#27272A"))
            
            "catppuccin_mocha" -> custom(bg="#1E1E2E", key="#313244", util="#181825", ink="#CDD6F4", action="#CBA6F7", actText="#11111B", sel="#45475A", dark=true, hc=highContrast, keyStyle="rounded", keyRadiusDp=12f)
            "dracula" -> custom(bg="#282A36", key="#44475A", util="#343746", ink="#F8F8F2", action="#BD93F9", actText="#282A36", sel="#6272A4", dark=true, hc=highContrast, keyStyle="rounded", keyRadiusDp=10f)
            "nord" -> custom(bg="#2E3440", key="#3B4252", util="#2E3440", ink="#ECEFF4", action="#88C0D0", actText="#2E3440", sel="#434C5E", dark=true, hc=highContrast, keyStyle="rounded", keyRadiusDp=10f)
            "tokyo_night" -> custom(bg="#1A1B26", key="#24283B", util="#16161E", ink="#C0CAF5", action="#7AA2F7", actText="#1A1B26", sel="#414868", dark=true, hc=highContrast, keyStyle="rounded", keyRadiusDp=12f)
            "solarized_dark" -> custom(bg="#002B36", key="#073642", util="#00212B", ink="#839496", action="#268BD2", actText="#FDF6E3", sel="#586E75", dark=true, hc=highContrast, keyStyle="rounded", keyRadiusDp=8f)
            "rose_pine" -> custom(bg="#191724", key="#26233A", util="#1F1D2E", ink="#E0DEF4", action="#EBBCBA", actText="#191724", sel="#393552", dark=true, hc=highContrast, keyStyle="rounded", keyRadiusDp=12f)
            "gruvbox_dark" -> custom(bg="#282828", key="#3C3836", util="#1D2021", ink="#EBDBB2", action="#FE8019", actText="#282828", sel="#504945", dark=true, hc=highContrast, keyStyle="rounded", keyRadiusDp=8f)
            "material_light" -> custom(bg="#F3F4F6", key="#FFFFFF", util="#E5E7EB", ink="#1F2937", action="#3B82F6", actText="#FFFFFF", sel="#D1D5DB", dark=false, hc=highContrast, keyStyle="material", keyRadiusDp=12f)
            "ios_style" -> custom(bg="#D1D5DB", key="#FFFFFF", util="#ADB5BD", ink="#000000", action="#007AFF", actText="#FFFFFF", sel="#B0B5BA", dark=false, hc=highContrast, keyStyle="ios", keyRadiusDp=6f)
            "amoled_black" -> custom(bg="#000000", key="#121212", util="#1C1C1C", ink="#FFFFFF", action="#00E676", actText="#000000", sel="#242424", dark=true, hc=highContrast, keyStyle="rounded", keyRadiusDp=12f)
            "neon_cyberpunk" -> custom(bg="#0D0221", key="#0F084B", util="#261447", ink="#00F0FF", action="#FF003C", actText="#FFFFFF", sel="#3D1E6D", dark=true, hc=highContrast, keyStyle="rounded", keyRadiusDp=14f, glowColor=Color.parseColor("#00F0FF"))
            "ocean_wave" -> custom(bg="#0A192F", key="#172A45", util="#0F1E36", ink="#64FFDA", action="#00B4D8", actText="#0A192F", sel="#303C55", dark=true, hc=highContrast, keyStyle="rounded", keyRadiusDp=12f)
            "neumorphic" -> custom(bg="#E0E5EC", key="#E0E5EC", util="#D1D9E6", ink="#31344B", action="#4D77FF", actText="#FFFFFF", sel="#CAD1DC", dark=false, hc=highContrast, keyStyle="neumorphic", keyRadiusDp=14f)
            "cherry_blossom" -> custom(bg="#FFF0F5", key="#FFE4E1", util="#FFD1DC", ink="#8B0046", action="#FF69B4", actText="#FFFFFF", sel="#FFB6C1", dark=false, hc=highContrast, keyStyle="pill", keyRadiusDp=20f)
            "emerald" -> custom(bg="#022C22", key="#064E3B", util="#022018", ink="#50C878", action="#059669", actText="#FFFFFF", sel="#047857", dark=true, hc=highContrast, keyStyle="rounded", keyRadiusDp=12f)

            // Additional Popular Palettes
            "ocean_blue" -> custom(bg="#0F172A", key="#1E293B", util="#1E293B", ink="#38BDF8", action="#0284C7", actText="#FFFFFF", sel="#334155", dark=true, hc=highContrast)
            "forest_green" -> custom(bg="#064E3B", key="#065F46", util="#065F46", ink="#A7F3D0", action="#059669", actText="#FFFFFF", sel="#047857", dark=true, hc=highContrast)
            "sunset" -> custom(bg="#450A0A", key="#7F1D1D", util="#7F1D1D", ink="#FDE047", action="#DC2626", actText="#FFFFFF", sel="#991B1B", dark=true, hc=highContrast)
            "cyberpunk" -> custom(bg="#FDFE00", key="#00F0FF", util="#FF003C", ink="#000000", action="#FF003C", actText="#FFFFFF", sel="#FFD700", dark=false, hc=highContrast)
            "monokai" -> custom(bg="#272822", key="#3E3D32", util="#3E3D32", ink="#F8F8F2", action="#F92672", actText="#FFFFFF", sel="#49483E", dark=true, hc=highContrast)
            "lavender" -> custom(bg="#F3E8FF", key="#FFFFFF", util="#E9D5FF", ink="#4C1D95", action="#7C3AED", actText="#FFFFFF", sel="#DDD6FE", dark=false, hc=highContrast)
            "rose_gold" -> custom(bg="#FFF1F2", key="#FFE4E6", util="#FFE4E6", ink="#881337", action="#E11D48", actText="#FFFFFF", sel="#FECDD3", dark=false, hc=highContrast)
            "midnight" -> custom(bg="#000000", key="#111111", util="#111111", ink="#FFFFFF", action="#FFFFFF", actText="#000000", sel="#222222", dark=true, hc=highContrast)
            "neon_green" -> custom(bg="#052E16", key="#064E3B", util="#064E3B", ink="#4ADE80", action="#22C55E", actText="#052E16", sel="#166534", dark=true, hc=highContrast)
            "cherry" -> custom(bg="#FDF2F8", key="#FCE7F3", util="#FCE7F3", ink="#831843", action="#DB2777", actText="#FFFFFF", sel="#FBCFE8", dark=false, hc=highContrast)
            "coffee" -> custom(bg="#3E2723", key="#4E342E", util="#4E342E", ink="#D7CCC8", action="#795548", actText="#FFFFFF", sel="#5D4037", dark=true, hc=highContrast)
            "deep_space" -> custom(bg="#0B0C10", key="#1F2833", util="#1F2833", ink="#66FCF1", action="#45A29E", actText="#0B0C10", sel="#2C353F", dark=true, hc=highContrast)
            "mint" -> custom(bg="#ECFDF5", key="#D1FAE5", util="#D1FAE5", ink="#064E3B", action="#10B981", actText="#FFFFFF", sel="#A7F3D0", dark=false, hc=highContrast)
            "crimson" -> custom(bg="#4C0519", key="#881337", util="#881337", ink="#FFE4E6", action="#E11D48", actText="#FFFFFF", sel="#9F1239", dark=true, hc=highContrast)
            "solarized_light" -> custom(bg="#FDF6E3", key="#EEE8D5", util="#EEE8D5", ink="#657B83", action="#2AA198", actText="#FDF6E3", sel="#93A1A1", dark=false, hc=highContrast)
            "matcha" -> custom(bg="#2E3C23", key="#3F4F34", util="#3F4F34", ink="#EAEFD3", action="#84A98C", actText="#2E3C23", sel="#52796F", dark=true, hc=highContrast)
            "coral" -> custom(bg="#4A151D", key="#6B222E", util="#6B222E", ink="#FFD3D6", action="#FF7F50", actText="#FFFFFF", sel="#8C2C3C", dark=true, hc=highContrast)
            "peach" -> custom(bg="#FFF6F1", key="#FFE8DE", util="#FFE8DE", ink="#5C2D1F", action="#FF9D7E", actText="#FFFFFF", sel="#FFD1C1", dark=false, hc=highContrast)
            "royal_purple" -> custom(bg="#2E0A47", key="#40175E", util="#40175E", ink="#E0C3FC", action="#7B2CBF", actText="#FFFFFF", sel="#5A189A", dark=true, hc=highContrast)
            "gold" -> custom(bg="#332701", key="#4D3B01", util="#4D3B01", ink="#FFD700", action="#D4AF37", actText="#000000", sel="#664E01", dark=true, hc=highContrast)
            "silver" -> custom(bg="#F8F9FA", key="#E9ECEF", util="#E9ECEF", ink="#212529", action="#ADB5BD", actText="#FFFFFF", sel="#CED4DA", dark=false, hc=highContrast)
            "ruby" -> custom(bg="#3F000F", key="#5C0016", util="#5C0016", ink="#E0115F", action="#9B111E", actText="#FFFFFF", sel="#7A001E", dark=true, hc=highContrast)
            "sapphire" -> custom(bg="#001433", key="#002255", util="#002255", ink="#0F52BA", action="#0047AB", actText="#FFFFFF", sel="#003380", dark=true, hc=highContrast)
            "amethyst" -> custom(bg="#291238", key="#3F1C55", util="#3F1C55", ink="#9966CC", action="#663399", actText="#FFFFFF", sel="#552B72", dark=true, hc=highContrast)
            "aquamarine" -> custom(bg="#002E29", key="#004840", util="#004840", ink="#7FFFD4", action="#20B2AA", actText="#002E29", sel="#006359", dark=true, hc=highContrast)
            "obsidian" -> custom(bg="#0B0B0B", key="#1C1C1C", util="#1C1C1C", ink="#A9A9A9", action="#4A4A4A", actText="#FFFFFF", sel="#2D2D2D", dark=true, hc=highContrast)
            "dark" -> custom(bg="#151A22", key="#222B38", util="#222B38", ink="#F1F5F9", action="#0072FF", actText="#FFFFFF", sel="#334155", dark=true, hc=highContrast)
            "light" -> custom(bg="#ECE6F0", key="#FFFFFF", util="#EADDFF", ink="#1C1B1F", action="#0B57D0", actText="#FFFFFF", sel="#D0D0D0", dark=false, hc=highContrast)
            else -> custom(bg="#151A22", key="#222B38", util="#222B38", ink="#F1F5F9", action="#0072FF", actText="#FFFFFF", sel="#334155", dark=true, hc=highContrast) // Dark as default fallback
        }
    }

    private fun custom(
        bg: String,
        key: String,
        util: String,
        ink: String,
        action: String,
        actText: String,
        sel: String,
        dark: Boolean,
        hc: Boolean,
        keyOpacity: Float = 1.0f,
        keyStyle: String = "rounded",
        keyRadiusDp: Float? = null,
        glowColor: Int? = null,
        borderWidthDp: Float = 0f,
        borderColor: Int? = null
    ) = KeyboardPalette(
        background = Color.parseColor(bg),
        key = Color.parseColor(key),
        utility = Color.parseColor(util),
        ink = Color.parseColor(ink),
        action = Color.parseColor(action),
        actionText = Color.parseColor(actText),
        selected = Color.parseColor(sel),
        dark = dark,
        highContrast = hc,
        dynamic = false,
        keyOpacity = keyOpacity,
        keyStyle = keyStyle,
        keyRadiusDp = keyRadiusDp,
        glowColor = glowColor,
        borderWidthDp = borderWidthDp,
        borderColor = borderColor
    )

    @RequiresApi(Build.VERSION_CODES.S)
    private fun dynamic(context: Context, dark: Boolean, highContrast: Boolean): KeyboardPalette {
        fun color(resource: Int): Int {
            val c = ContextCompat.getColor(context, resource)
            if (Color.alpha(c) == 0) throw IllegalStateException("Transparent system color")
            return c
        }
        
        return if (dark) {
            KeyboardPalette(
                background = color(android.R.color.system_neutral1_900),
                key = color(android.R.color.system_neutral1_800),
                utility = color(android.R.color.system_neutral2_700),
                ink = color(android.R.color.system_neutral1_50),
                action = color(android.R.color.system_accent1_200),
                actionText = color(android.R.color.system_accent1_900),
                selected = color(android.R.color.system_accent2_700),
                dark = true,
                highContrast = highContrast,
                dynamic = true,
                keyOpacity = 1.0f
            )
        } else {
            KeyboardPalette(
                background = color(android.R.color.system_neutral1_50),
                key = color(android.R.color.system_neutral1_100),
                utility = color(android.R.color.system_neutral2_200),
                ink = color(android.R.color.system_neutral1_900),
                action = color(android.R.color.system_accent1_600),
                actionText = color(android.R.color.system_neutral1_50),
                selected = color(android.R.color.system_accent2_100),
                dark = false,
                highContrast = highContrast,
                dynamic = true,
                keyOpacity = 1.0f
            )
        }
    }
}
