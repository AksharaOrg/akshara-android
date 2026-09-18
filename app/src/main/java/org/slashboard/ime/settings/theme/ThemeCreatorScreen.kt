package org.slashboard.ime.settings.theme

import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.slashboard.ime.settings.KeyboardPreferences
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeCreatorScreen(
    prefs: KeyboardPreferences,
    themeIdToEdit: String?,
    onThemeCreated: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("My Custom Theme") }
    var backgroundHex by remember { mutableStateOf("#0F172A") }
    var keyHex by remember { mutableStateOf("#1E293B") }
    var actionHex by remember { mutableStateOf("#0284C7") }
    var utilityHex by remember { mutableStateOf("#1E293B") }
    var textHex by remember { mutableStateOf("#FFFFFF") }
    var inkHex by remember { mutableStateOf("#38BDF8") }
    var spaceKeyHex by remember { mutableStateOf("#1E293B") }
    var spaceBorderHex by remember { mutableStateOf("#38BDF8") }

    var dark by remember { mutableStateOf(true) }
    var blurEffect by remember { mutableStateOf(false) }
    var keyOpacity by remember { mutableStateOf(1.0f) }
    var keyRadiusDp by remember { mutableStateOf(12f) }
    var bgImagePath by remember { mutableStateOf<String?>(null) }

    var keyStyle by remember { mutableStateOf("rounded") }
    var animationType by remember { mutableStateOf("scale") }
    var enableGradient by remember { mutableStateOf(false) }
    var gradientStartHex by remember { mutableStateOf("#0F172A") }
    var gradientEndHex by remember { mutableStateOf("#1E1B4B") }
    var borderWidthDp by remember { mutableStateOf(0f) }
    var borderColorHex by remember { mutableStateOf("#38BDF8") }
    var glowColorHex by remember { mutableStateOf<String?>("#00F0FF") }
    var activeColorTargetIndex by remember { mutableIntStateOf(0) }

    val colorTargets = remember(
        keyHex, backgroundHex, actionHex, utilityHex,
        textHex, inkHex, spaceKeyHex, spaceBorderHex
    ) {
        listOf(
            ColorTargetItem("key", "Key Caps", "යතුරු", Icons.Default.Keyboard, keyHex, { keyHex = it }),
            ColorTargetItem("bg", "Background", "පසුබිම", Icons.Default.Wallpaper, backgroundHex, { backgroundHex = it }),
            ColorTargetItem("action", "Action Key", "Enter යතුර", Icons.Default.KeyboardReturn, actionHex, { actionHex = it }),
            ColorTargetItem("util", "Utility Keys", "Shift/Del/123", Icons.Default.SpaceBar, utilityHex, { utilityHex = it }),
            ColorTargetItem("text", "Key Text", "අකුරු", Icons.Default.FormatColorText, textHex, { textHex = it }),
            ColorTargetItem("ink", "Accent / Ink", "සලකුණු", Icons.Default.AutoAwesome, inkHex, { inkHex = it }),
            ColorTargetItem("space", "Spacebar", "හිස්තැන", Icons.Default.SpaceBar, spaceKeyHex, { spaceKeyHex = it }),
            ColorTargetItem("border", "Key Border", "මායිම", Icons.Default.CropSquare, spaceBorderHex, { spaceBorderHex = it })
        )
    }

    var selectedTab by remember { mutableStateOf(0) } // 0: Colors, 1: Styles & Shapes, 2: Background, 3: Presets

    // Load initial theme data if editing or cloning
    LaunchedEffect(themeIdToEdit) {
        if (themeIdToEdit != null) {
            if (themeIdToEdit.startsWith("custom_")) {
                val data = CustomThemeManager.getTheme(context, themeIdToEdit)
                if (data != null) {
                    name = data.name
                    backgroundHex = data.background
                    keyHex = data.key
                    actionHex = data.action
                    utilityHex = data.utility
                    textHex = data.actionText
                    inkHex = data.ink
                    spaceKeyHex = data.spaceKey ?: data.key
                    spaceBorderHex = data.spaceBorder ?: data.ink
                    dark = data.dark
                    blurEffect = data.blurEffect
                    keyOpacity = data.keyOpacity
                    keyRadiusDp = data.keyRadiusDp ?: 12f
                    bgImagePath = data.backgroundImagePath
                    keyStyle = data.keyStyle ?: "rounded"
                    animationType = data.animationType ?: "scale"
                    if (data.gradientStart != null && data.gradientEnd != null) {
                        enableGradient = true
                        gradientStartHex = data.gradientStart
                        gradientEndHex = data.gradientEnd
                    }
                    borderWidthDp = data.borderWidthDp ?: 0f
                    borderColorHex = data.borderColor ?: data.ink
                    glowColorHex = data.glowColor
                }
            } else {
                val palette = org.slashboard.ime.ime.KeyboardPaletteResolver.resolve(context, themeIdToEdit, prefs.highContrast)
                name = themeIdToEdit.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } + " (Custom)"
                val hex = { c: Int -> String.format("#%06X", (0xFFFFFF and c)) }
                backgroundHex = hex(palette.background)
                keyHex = hex(palette.key)
                actionHex = hex(palette.action)
                utilityHex = hex(palette.utility)
                textHex = hex(palette.actionText)
                inkHex = hex(palette.ink)
                spaceKeyHex = palette.spaceKey?.let { hex(it) } ?: hex(palette.key)
                spaceBorderHex = palette.spaceBorder?.let { hex(it) } ?: hex(palette.ink)
                dark = palette.dark
                blurEffect = palette.blurEffect
                keyOpacity = palette.keyOpacity
                keyRadiusDp = palette.keyRadiusDp ?: 12f
                bgImagePath = palette.backgroundImagePath
                keyStyle = palette.keyStyle
                animationType = palette.animationType
                borderWidthDp = palette.borderWidthDp
                borderColorHex = palette.borderColor?.let { hex(it) } ?: hex(palette.ink)
                glowColorHex = palette.glowColor?.let { hex(it) }
            }
        }
    }

    val imageLauncher = rememberLauncherForActivityResult(
        com.canhub.cropper.CropImageContract()
    ) { result ->
        if (result.isSuccessful) {
            val uri = result.uriContent
            if (uri != null) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val imagesDir = File(context.filesDir, "themes/images").apply { mkdirs() }
                    val destFile = File(imagesDir, "bg_${System.currentTimeMillis()}.jpg")
                    val outputStream = FileOutputStream(destFile)
                    inputStream?.copyTo(outputStream)
                    inputStream?.close()
                    outputStream.close()
                    bgImagePath = destFile.absolutePath
                    if (backgroundHex.equals("transparent", ignoreCase = true)) {
                        backgroundHex = if (dark) "#0F172A" else "#F8FAFC"
                    }
                    if (keyOpacity < 0.85f) {
                        keyOpacity = 0.90f
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    var showDiscardDialog by remember { mutableStateOf(false) }

    val performSave: () -> Unit = {
        val id = if (themeIdToEdit != null && themeIdToEdit.startsWith("custom_")) {
            themeIdToEdit
        } else {
            "custom_" + System.currentTimeMillis()
        }
        val finalName = if (name.trim().isEmpty()) "My Custom Theme" else name.trim()
        val customTheme = CustomThemeData(
            id = id,
            name = finalName,
            background = backgroundHex,
            key = keyHex,
            utility = utilityHex,
            ink = inkHex,
            action = actionHex,
            actionText = textHex,
            selected = utilityHex,
            dark = dark,
            highContrast = false,
            keyRadiusDp = keyRadiusDp,
            blurEffect = blurEffect,
            keyOpacity = keyOpacity,
            backgroundImagePath = bgImagePath,
            spaceKey = spaceKeyHex,
            spaceBorder = spaceBorderHex,
            keyStyle = keyStyle,
            animationType = animationType,
            gradientStart = if (enableGradient) gradientStartHex else null,
            gradientEnd = if (enableGradient) gradientEndHex else null,
            borderWidthDp = borderWidthDp,
            borderColor = if (borderWidthDp > 0f) borderColorHex else null,
            shadowElevationDp = 0f,
            glowColor = glowColorHex
        )
        val success = CustomThemeManager.saveTheme(context, customTheme)
        if (success) {
            prefs.theme = id
            android.widget.Toast.makeText(
                context,
                "Theme \"$finalName\" saved & applied! (තීමය සුරැකිණි)",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            onThemeCreated(id)
        } else {
            android.widget.Toast.makeText(
                context,
                "Error saving theme. Please try again.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    BackHandler {
        showDiscardDialog = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (themeIdToEdit != null) "Edit Theme" else "Theme Creator",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(onClick = { showDiscardDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { performSave() },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp).padding(end = 4.dp))
                        Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    IconButton(onClick = {
                        // Quick Reset
                        backgroundHex = "#0F172A"
                        keyHex = "#1E293B"
                        actionHex = "#0284C7"
                        utilityHex = "#1E293B"
                        textHex = "#FFFFFF"
                        inkHex = "#38BDF8"
                        spaceKeyHex = "#1E293B"
                        spaceBorderHex = "#38BDF8"
                        keyStyle = "rounded"
                        animationType = "scale"
                        enableGradient = false
                        borderWidthDp = 0f
                        keyOpacity = 1f
                        keyRadiusDp = 12f
                        blurEffect = false
                        bgImagePath = null
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Defaults")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { performSave() },
                icon = { Icon(Icons.Default.Save, contentDescription = null) },
                text = { Text("Save & Apply (සුරකින්න)", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // 1. PINNED / COMPACT LIVE KEYBOARD PREVIEW AT TOP (Sleek & Space-Efficient)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Default.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Live Preview (සජීවී පෙරදසුන)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            name.ifEmpty { "Theme" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            fontSize = 10.sp
                        )
                    }

                    // Live Interactive Mini Keyboard Preview
                    ThemeLiveKeyboardPreview(
                        backgroundHex = backgroundHex,
                        keyHex = keyHex,
                        actionHex = actionHex,
                        utilityHex = utilityHex,
                        textHex = textHex,
                        inkHex = inkHex,
                        spaceKeyHex = spaceKeyHex,
                        spaceBorderHex = spaceBorderHex,
                        dark = dark,
                        blurEffect = blurEffect,
                        keyOpacity = keyOpacity,
                        keyRadiusDp = keyRadiusDp,
                        bgImagePath = bgImagePath,
                        keyStyle = keyStyle,
                        enableGradient = enableGradient,
                        gradientStartHex = gradientStartHex,
                        gradientEndHex = gradientEndHex,
                        borderWidthDp = borderWidthDp,
                        borderColorHex = borderColorHex
                    )
                }
            }

            // 2. CATEGORY TABS
            androidx.compose.material3.ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = { HorizontalDivider() },
                edgePadding = 8.dp
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.ColorLens, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Colors (වර්ණ)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Widgets, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Styles (හැඩ)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Background", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Stock Images", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Presets", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
            }

            // 3. SCROLLABLE CUSTOMIZATION OPTIONS
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Theme Name (තීම් නම)") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                when (selectedTab) {
                    0 -> {
                        // TAB 0: LIVE CIRCULAR RGB COLOR STUDIO
                        val activeTarget = colorTargets[activeColorTargetIndex.coerceIn(0, colorTargets.size - 1)]

                        Text(
                            "Color Target (වෙනස් කරන කොටස)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Select element below & drag inside circle or use RGB mode for instant live preview:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 1. Horizontal Scrollable Element Selector Chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(colorTargets.size) { idx ->
                                val item = colorTargets[idx]
                                val isSelected = activeColorTargetIndex == idx
                                val swatchColor = parseHexColor(item.hex, Color.Gray)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { activeColorTargetIndex = idx },
                                    label = {
                                        Text(
                                            item.label,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(swatchColor)
                                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }

                        // 2. Live Interactive Circular & RGB Color Studio
                        LiveCircularColorStudio(
                            targetLabel = activeTarget.label,
                            targetSinhalaLabel = activeTarget.sinhalaLabel,
                            currentHex = activeTarget.hex,
                            onHexChange = activeTarget.onColorChange
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Text(
                            "All Colors List (සියලු වර්ණ ලැයිස්තුව)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        // 3. All 8 ColorInputRow cards linked to activeTarget
                        colorTargets.forEachIndexed { index, item ->
                            ColorInputRow(
                                label = "${item.label} (${item.sinhalaLabel})",
                                hex = item.hex,
                                isSelected = activeColorTargetIndex == index,
                                onSelect = { activeColorTargetIndex = index },
                                onHexChange = item.onColorChange
                            )
                        }
                    }

                    1 -> {
                        // TAB 1: STYLES & SHAPES
                        Text(
                            "Key Cap Shapes & Effects",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Key Styles
                        val keyStyles = listOf(
                            "rounded" to "Rounded (වටකුරු)",
                            "sharp" to "Sharp Square",
                            "circle" to "Circle (රවුම්)",
                            "pill" to "Pill Stadium",
                            "minimal" to "Minimal Flat",
                            "material" to "Material 3",
                            "ios" to "iOS Bevel",
                            "neumorphic" to "Neumorphic"
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(keyStyles) { (styleKey, styleTitle) ->
                                FilterChip(
                                    selected = (keyStyle == styleKey),
                                    onClick = { keyStyle = styleKey },
                                    label = { Text(styleTitle) },
                                    leadingIcon = if (keyStyle == styleKey) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }

                        // Animation Type
                        Text(
                            "Keypress Animation Effect",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "scale" to "Scale / Shrink",
                                "ripple" to "Material Ripple",
                                "glow" to "Border Glow Pulse"
                            ).forEach { (animKey, animTitle) ->
                                FilterChip(
                                    selected = (animationType == animKey),
                                    onClick = { animationType = animKey },
                                    label = { Text(animTitle) },
                                    leadingIcon = if (animationType == animKey) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // Corner Radius
                        Text("Key Corner Radius: ${keyRadiusDp.toInt()}dp", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Slider(
                            value = keyRadiusDp,
                            onValueChange = { keyRadiusDp = it },
                            valueRange = 0f..24f
                        )

                        // Opacity
                        Text("Key Background Opacity: ${(keyOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Slider(
                            value = keyOpacity,
                            onValueChange = { keyOpacity = it },
                            valueRange = 0.1f..1f
                        )

                        // Border Width & Color
                        Text("Key Border Width: ${String.format("%.1f", borderWidthDp)}dp", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Slider(
                            value = borderWidthDp,
                            onValueChange = { borderWidthDp = it },
                            valueRange = 0f..4f
                        )
                        if (borderWidthDp > 0f) {
                            ColorInputRow("Key Border Color", borderColorHex) { borderColorHex = it }
                        }

                        if (animationType == "glow") {
                            ColorInputRow("Glow Color", glowColorHex ?: "#00F0FF") { glowColorHex = it }
                        }
                    }

                    2 -> {
                        // TAB 2: BACKGROUND & EFFECTS
                        Text(
                            "Background & Visual Effects",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Gradient Toggle
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Background Gradient", fontWeight = FontWeight.SemiBold)
                                        Text("Smooth color transition on keyboard canvas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = enableGradient, onCheckedChange = { enableGradient = it })
                                }
                                if (enableGradient) {
                                    ColorInputRow("Gradient Start Color", gradientStartHex) { gradientStartHex = it }
                                    ColorInputRow("Gradient End Color", gradientEndHex) { gradientEndHex = it }
                                }
                            }
                        }

                        // Dark theme base
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Dark Base Mode", fontWeight = FontWeight.SemiBold)
                                    Text("White suggestion text and dark status bars", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = dark, onCheckedChange = { dark = it })
                            }
                        }

                        // Glass Blur Effect
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Glass Blur Effect", fontWeight = FontWeight.SemiBold)
                                    Text("Frosted glass aesthetic behind keyboard keys", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = blurEffect, onCheckedChange = { blurEffect = it })
                            }
                        }

                        // Background Image Picker
                        OutlinedButton(
                            onClick = {
                                imageLauncher.launch(
                                    com.canhub.cropper.CropImageContractOptions(
                                        uri = null,
                                        cropImageOptions = com.canhub.cropper.CropImageOptions().apply {
                                            imageSourceIncludeGallery = true
                                            imageSourceIncludeCamera = false
                                        }
                                    )
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(if (bgImagePath == null) "Pick Background Image from Gallery" else "Change Background Image")
                        }

                        if (bgImagePath != null) {
                            TextButton(
                                onClick = { bgImagePath = null },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                                Text("Remove Background Image")
                            }
                        }
                    }

                    3 -> {
                        // TAB 3: STOCK IMAGES
                        Text(
                            "High-Quality Stock Backgrounds",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Tap an image to download and apply it as the keyboard background.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val context = LocalContext.current
                        val coroutineScope = rememberCoroutineScope()
                        val stockImages = remember { (0..19).map { "stock_$it.jpg" } }
                        
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
                        ) {
                            items(stockImages) { assetName ->
                                Card(
                                    modifier = Modifier
                                        .aspectRatio(0.7f)
                                        .clickable {
                                            coroutineScope.launch {
                                                try {
                                                    val inputStream = context.assets.open("stock_images/$assetName")
                                                    val destDir = java.io.File(context.filesDir, "theme_bg").apply { mkdirs() }
                                                    val destFile = java.io.File(destDir, "bg_${System.currentTimeMillis()}.jpg")
                                                    val outputStream = java.io.FileOutputStream(destFile)
                                                    inputStream.copyTo(outputStream)
                                                    inputStream.close()
                                                    outputStream.close()
                                                    bgImagePath = destFile.absolutePath
                                                    android.widget.Toast.makeText(context, "Applied stock background", android.widget.Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    android.widget.Toast.makeText(context, "Error applying background", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                    // We load the asset using Coil or Bitmap
                                    androidx.compose.foundation.Image(
                                        painter = coil.compose.rememberAsyncImagePainter(
                                            model = coil.request.ImageRequest.Builder(context)
                                                .data("file:///android_asset/stock_images/$assetName")
                                                .crossfade(true)
                                                .build()
                                        ),
                                        contentDescription = "Stock background",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }

                    4 -> {
                        // TAB 4: STARTER PRESETS
                        Text(
                            "One-Tap Starter Theme Palettes",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Tap any preset to load its colors & styling instantly into the editor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val starterPresets = listOf(
                            ThemeStarterPreset("Neon Cyberpunk", "#0D0221", "#0F084B", "#FF003C", "#261447", "#00F0FF", "#00F0FF", true, "rounded", 14f, true, "#0D0221", "#261447", 1.5f, "#00F0FF"),
                            ThemeStarterPreset("Catppuccin Mocha", "#1E1E2E", "#313244", "#CBA6F7", "#181825", "#CDD6F4", "#CBA6F7", true, "rounded", 12f, false),
                            ThemeStarterPreset("Ocean Wave", "#0A192F", "#172A45", "#00B4D8", "#0F1E36", "#FFFFFF", "#64FFDA", true, "rounded", 12f, true, "#0A192F", "#172A45"),
                            ThemeStarterPreset("Dracula Purple", "#282A36", "#44475A", "#BD93F9", "#343746", "#F8F8F2", "#50FA7B", true, "rounded", 10f, false),
                            ThemeStarterPreset("Sunset Flame", "#2D0A0A", "#4C1D1D", "#EA580C", "#3B1414", "#FFFFFF", "#FDE047", true, "rounded", 12f, true, "#2D0A0A", "#7C2D12"),
                            ThemeStarterPreset("Emerald Forest", "#022C22", "#064E3B", "#10B981", "#022018", "#FFFFFF", "#50C878", true, "rounded", 12f, false),
                            ThemeStarterPreset("Material Clean Light", "#F3F4F6", "#FFFFFF", "#3B82F6", "#E5E7EB", "#1F2937", "#2563EB", false, "material", 12f, false),
                            ThemeStarterPreset("iOS Soft Silver", "#D1D5DB", "#FFFFFF", "#007AFF", "#ADB5BD", "#000000", "#007AFF", false, "ios", 8f, false),
                            ThemeStarterPreset("AMOLED Pure Black", "#000000", "#121212", "#00E676", "#1C1C1C", "#FFFFFF", "#00E676", true, "rounded", 12f, false),
                            ThemeStarterPreset("Rose Gold Luxury", "#FFF1F2", "#FFE4E6", "#E11D48", "#FFE4E6", "#881337", "#E11D48", false, "pill", 16f, false)
                        )

                        starterPresets.forEach { preset ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        name = preset.name
                                        backgroundHex = preset.bg
                                        keyHex = preset.key
                                        actionHex = preset.action
                                        utilityHex = preset.util
                                        textHex = preset.text
                                        inkHex = preset.ink
                                        spaceKeyHex = preset.key
                                        spaceBorderHex = preset.ink
                                        dark = preset.dark
                                        keyStyle = preset.keyStyle
                                        keyRadiusDp = preset.radius
                                        enableGradient = preset.gradient
                                        if (preset.gradient) {
                                            gradientStartHex = preset.gradStart ?: preset.bg
                                            gradientEndHex = preset.gradEnd ?: preset.key
                                        }
                                        borderWidthDp = preset.borderWidth
                                        if (preset.borderColor != null) {
                                            borderColorHex = preset.borderColor
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(preset.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf(preset.bg, preset.key, preset.action, preset.ink).forEach { h ->
                                                val c = parseHexColor(h, Color.Gray)
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(c)
                                                        .border(0.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                                )
                                            }
                                        }
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            name = preset.name
                                            backgroundHex = preset.bg
                                            keyHex = preset.key
                                            actionHex = preset.action
                                            utilityHex = preset.util
                                            textHex = preset.text
                                            inkHex = preset.ink
                                            spaceKeyHex = preset.key
                                            spaceBorderHex = preset.ink
                                            dark = preset.dark
                                            keyStyle = preset.keyStyle
                                            keyRadiusDp = preset.radius
                                            enableGradient = preset.gradient
                                            if (preset.gradient) {
                                                gradientStartHex = preset.gradStart ?: preset.bg
                                                gradientEndHex = preset.gradEnd ?: preset.key
                                            }
                                            borderWidthDp = preset.borderWidth
                                            if (preset.borderColor != null) {
                                                borderColorHex = preset.borderColor
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Apply", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // SAVE & APPLY BUTTON
                Button(
                    onClick = { performSave() },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Save & Apply Theme (සුරකින්න)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Save Custom Theme?") },
            text = { Text("Do you want to save your customized theme changes before leaving? (තීම් වෙනස්කම් සුරැකීමට අවශ්‍යද?)") },
            confirmButton = {
                Button(onClick = {
                    showDiscardDialog = false
                    performSave()
                }) {
                    Text("Save & Apply")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        showDiscardDialog = false
                        onBack()
                    }) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showDiscardDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

private data class ThemeStarterPreset(
    val name: String,
    val bg: String,
    val key: String,
    val action: String,
    val util: String,
    val text: String,
    val ink: String,
    val dark: Boolean,
    val keyStyle: String,
    val radius: Float,
    val gradient: Boolean = false,
    val gradStart: String? = null,
    val gradEnd: String? = null,
    val borderWidth: Float = 0f,
    val borderColor: String? = null
)

private fun parseHexColor(hex: String, defaultColor: Color): Color {
    return try {
        val clean = if (hex.startsWith("#")) hex else "#$hex"
        Color(android.graphics.Color.parseColor(clean))
    } catch (e: Exception) {
        defaultColor
    }
}

/**
 * Compact Live Keyboard Preview with accurate styles and live responsive reactivity.
 */
@Composable
fun ThemeLiveKeyboardPreview(
    backgroundHex: String,
    keyHex: String,
    actionHex: String,
    utilityHex: String,
    textHex: String,
    inkHex: String,
    spaceKeyHex: String,
    spaceBorderHex: String,
    dark: Boolean,
    blurEffect: Boolean,
    keyOpacity: Float,
    keyRadiusDp: Float,
    bgImagePath: String?,
    keyStyle: String = "rounded",
    enableGradient: Boolean = false,
    gradientStartHex: String = "#0F172A",
    gradientEndHex: String = "#1E1B4B",
    borderWidthDp: Float = 0f,
    borderColorHex: String = "#38BDF8",
    onKeyTap: ((String) -> Unit)? = null
) {
    val hasBgImage = remember(bgImagePath) { !bgImagePath.isNullOrEmpty() && java.io.File(bgImagePath).exists() }
    val bgColor = remember(backgroundHex) { parseHexColor(backgroundHex, Color(0xFF0F172A)) }
    val isTransparentBg = (backgroundHex.equals("transparent", ignoreCase = true) || bgColor == Color.Transparent) && !hasBgImage
    val effectiveKeyOpacity = remember(keyOpacity, isTransparentBg, hasBgImage) {
        if (hasBgImage) (if (keyOpacity < 0.85f) 0.90f else keyOpacity)
        else if (isTransparentBg && keyOpacity >= 1.0f) 0.68f
        else keyOpacity
    }
    val effectiveBorderWidth = remember(borderWidthDp, isTransparentBg, hasBgImage) {
        if (hasBgImage && borderWidthDp == 0f) 1f
        else if (isTransparentBg && borderWidthDp == 0f) 1f
        else borderWidthDp
    }
    val keyColor = remember(keyHex, effectiveKeyOpacity) { parseHexColor(keyHex, Color(0xFF1E293B)).copy(alpha = effectiveKeyOpacity) }
    val actionColor = remember(actionHex) { parseHexColor(actionHex, Color(0xFF0284C7)) }
    val utilColor = remember(utilityHex, effectiveKeyOpacity) { parseHexColor(utilityHex, Color(0xFF1E293B)).copy(alpha = effectiveKeyOpacity) }
    val textColor = remember(textHex) { parseHexColor(textHex, Color.White) }
    val inkColor = remember(inkHex) { parseHexColor(inkHex, Color(0xFF38BDF8)) }
    val spaceBarColor = remember(spaceKeyHex, effectiveKeyOpacity) { parseHexColor(spaceKeyHex, Color(0xFF1E293B)).copy(alpha = effectiveKeyOpacity) }
    val spaceBorderColor = remember(spaceBorderHex) { parseHexColor(spaceBorderHex, Color(0xFF38BDF8)) }
    val keyBorderColor = remember(borderColorHex, isTransparentBg, dark) {
        if (isTransparentBg && (borderColorHex.isBlank() || borderColorHex == "#38BDF8")) {
            if (dark) Color(0x55FFFFFF) else Color(0x33000000)
        } else {
            parseHexColor(borderColorHex, Color(0xFF38BDF8))
        }
    }

    val gradStartColor = remember(gradientStartHex) { parseHexColor(gradientStartHex, Color(0xFF0F172A)) }
    val gradEndColor = remember(gradientEndHex) { parseHexColor(gradientEndHex, Color(0xFF1E1B4B)) }

    val context = LocalContext.current
    val spaceLabel = remember {
        val prefText = org.slashboard.ime.settings.KeyboardPreferences(context).customSpacebarText.trim().take(14)
        if (prefText.isNotEmpty()) prefText else "Slashboard"
    }

    val bgBitmap = remember(bgImagePath) {
        if (!bgImagePath.isNullOrEmpty()) {
            runCatching {
                android.graphics.BitmapFactory.decodeFile(bgImagePath)?.asImageBitmap()
            }.getOrNull()
        } else null
    }

    val computedShape = remember(keyStyle, keyRadiusDp) {
        when (keyStyle) {
            "circle" -> CircleShape
            "pill" -> CircleShape
            "sharp" -> RoundedCornerShape(2.dp)
            "minimal" -> RoundedCornerShape(4.dp)
            "ios" -> RoundedCornerShape(6.dp)
            else -> RoundedCornerShape(keyRadiusDp.dp)
        }
    }

    val cardModifier = if (enableGradient) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(gradStartColor, gradEndColor)))
    } else if (isTransparentBg) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (dark) Color(0x33334155) else Color(0x22CBD5E1),
                        if (dark) Color(0x1A1E293B) else Color(0x11E2E8F0)
                    )
                )
            )
    } else {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (enableGradient || isTransparentBg) Color.Transparent else bgColor),
        border = BorderStroke(1.dp, if (isTransparentBg) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isTransparentBg) 0.dp else 2.dp),
        modifier = cardModifier
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (bgBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bgBitmap,
                    contentDescription = "Background",
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
                if (blurEffect) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.5.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Toolbar / Suggestion Rail
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(15.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(utilColor.copy(alpha = (keyOpacity * 0.7f).coerceIn(0.15f, 0.9f)))
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("EN", color = inkColor, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        Text("😀", fontSize = 8.5.sp)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("මම", color = textColor, fontSize = 8.5.sp, fontWeight = FontWeight.Medium)
                        Text("ගෙදර", color = inkColor, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        Text("යනවා", color = textColor, fontSize = 8.5.sp, fontWeight = FontWeight.Medium)
                    }
                    Text("⚙", color = textColor.copy(alpha = 0.7f), fontSize = 8.5.sp)
                }

                // Row 1: Q W E R T Y U I O P
                val r1Keys = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (k in r1Keys) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(19.5.dp)
                                .clip(computedShape)
                                .background(keyColor)
                                .then(
                                    if (borderWidthDp > 0f) Modifier.border(borderWidthDp.dp, keyBorderColor, computedShape)
                                    else Modifier
                                )
                                .then(
                                    if (onKeyTap != null) Modifier.clickable { onKeyTap(k) }
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(k, color = textColor, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Row 2: A S D F G H J K L
                val r2Keys = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (k in r2Keys) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(19.5.dp)
                                .clip(computedShape)
                                .background(keyColor)
                                .then(
                                    if (borderWidthDp > 0f) Modifier.border(borderWidthDp.dp, keyBorderColor, computedShape)
                                    else Modifier
                                )
                                .then(
                                    if (onKeyTap != null) Modifier.clickable { onKeyTap(k) }
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(k, color = textColor, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Row 3: Shift, Z X C V B N M, Delete
                val r3Keys = listOf("Z", "X", "C", "V", "B", "N", "M")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(19.5.dp)
                            .clip(computedShape)
                            .background(utilColor)
                            .then(
                                if (borderWidthDp > 0f) Modifier.border(borderWidthDp.dp, keyBorderColor, computedShape)
                                else Modifier
                            )
                            .then(
                                if (onKeyTap != null) Modifier.clickable { onKeyTap("⇧") }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⇧", color = textColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    for (k in r3Keys) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(19.5.dp)
                                .clip(computedShape)
                                .background(keyColor)
                                .then(
                                    if (borderWidthDp > 0f) Modifier.border(borderWidthDp.dp, keyBorderColor, computedShape)
                                    else Modifier
                                )
                                .then(
                                    if (onKeyTap != null) Modifier.clickable { onKeyTap(k) }
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(k, color = textColor, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(19.5.dp)
                            .clip(computedShape)
                            .background(utilColor)
                            .then(
                                if (borderWidthDp > 0f) Modifier.border(borderWidthDp.dp, keyBorderColor, computedShape)
                                else Modifier
                            )
                            .then(
                                if (onKeyTap != null) Modifier.clickable { onKeyTap("⌫") }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⌫", color = textColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Row 4: ?123, Globe, Spacebar, ., Enter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .height(18.5.dp)
                            .clip(computedShape)
                            .background(utilColor)
                            .border(1.dp, spaceBorderColor.copy(alpha = 0.5f), computedShape)
                            .then(
                                if (onKeyTap != null) Modifier.clickable { onKeyTap("?123") }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("?123", color = textColor, fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier
                            .weight(0.85f)
                            .height(18.5.dp)
                            .clip(computedShape)
                            .background(utilColor)
                            .border(1.dp, spaceBorderColor.copy(alpha = 0.4f), computedShape)
                            .then(
                                if (onKeyTap != null) Modifier.clickable { onKeyTap("🌐") }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🌐", fontSize = 8.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(4.2f)
                            .height(18.5.dp)
                            .padding(horizontal = 2.dp)
                            .clip(computedShape)
                            .background(spaceBarColor)
                            .border(1.2.dp, spaceBorderColor, computedShape)
                            .then(
                                if (onKeyTap != null) Modifier.clickable { onKeyTap(" ") }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(spaceLabel, color = textColor.copy(alpha = 0.75f), fontSize = 7.5.sp, maxLines = 1)
                    }
                    Box(
                        modifier = Modifier
                            .weight(0.85f)
                            .height(18.5.dp)
                            .clip(computedShape)
                            .background(keyColor)
                            .border(1.dp, spaceBorderColor.copy(alpha = 0.4f), computedShape)
                            .then(
                                if (onKeyTap != null) Modifier.clickable { onKeyTap(".") }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(".", color = textColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .height(18.5.dp)
                            .clip(computedShape)
                            .background(actionColor)
                            .border(1.2.dp, spaceBorderColor, computedShape)
                            .then(
                                if (onKeyTap != null) Modifier.clickable { onKeyTap("\n") }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⏎", color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ColorInputRow(
    label: String,
    hex: String,
    isSelected: Boolean = false,
    onSelect: (() -> Unit)? = null,
    onHexChange: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val color = remember(hex) { parseHexColor(hex, Color.Transparent) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                 else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (onSelect != null) onSelect()
                else showDialog = true
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Color Circle Swatch
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), CircleShape)
                    .shadow(2.dp, CircleShape)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = hex.uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { showDialog = true },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.ColorLens, contentDescription = "Pick Color Dialog", modifier = Modifier.size(20.dp))
            }
        }
    }

    if (showDialog) {
        ColorPickerDialog(
            initialHex = hex,
            title = label,
            onDismiss = { showDialog = false },
            onColorSelected = { selectedHex ->
                onHexChange(selectedHex)
                showDialog = false
            }
        )
    }
}

data class ColorTargetItem(
    val id: String,
    val label: String,
    val sinhalaLabel: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val hex: String,
    val onColorChange: (String) -> Unit
)

@Composable
fun LiveCircularColorStudio(
    targetLabel: String,
    targetSinhalaLabel: String,
    currentHex: String,
    onHexChange: (String) -> Unit
) {
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var value by remember { mutableFloatStateOf(1f) }
    var redInt by remember { mutableIntStateOf(0) }
    var greenInt by remember { mutableIntStateOf(0) }
    var blueInt by remember { mutableIntStateOf(0) }
    var hexText by remember { mutableStateOf(currentHex.uppercase()) }
    var selectedStudioTab by remember { mutableIntStateOf(0) } // 0: Circle Wheel, 1: RGB Mod, 2: Presets

    // Re-sync when currentHex changes externally or target changes
    LaunchedEffect(currentHex) {
        try {
            val cleanHex = if (currentHex.startsWith("#")) currentHex else "#$currentHex"
            val colorInt = AndroidColor.parseColor(cleanHex)
            val r = AndroidColor.red(colorInt)
            val g = AndroidColor.green(colorInt)
            val b = AndroidColor.blue(colorInt)
            if (r != redInt || g != greenInt || b != blueInt) {
                redInt = r
                greenInt = g
                blueInt = b
                val hsv = FloatArray(3)
                AndroidColor.colorToHSV(colorInt, hsv)
                hue = hsv[0]
                saturation = hsv[1]
                value = hsv[2]
                hexText = String.format("#%02X%02X%02X", redInt, greenInt, blueInt)
            }
        } catch (e: Exception) {
            // Keep existing values
        }
    }

    fun applyHSV(h: Float, s: Float, v: Float) {
        hue = h.coerceIn(0f, 360f)
        saturation = s.coerceIn(0f, 1f)
        value = v.coerceIn(0f, 1f)

        val colorInt = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
        redInt = AndroidColor.red(colorInt)
        greenInt = AndroidColor.green(colorInt)
        blueInt = AndroidColor.blue(colorInt)
        val formatted = String.format("#%02X%02X%02X", redInt, greenInt, blueInt)
        hexText = formatted
        onHexChange(formatted)
    }

    fun applyRGB(r: Int, g: Int, b: Int) {
        redInt = r.coerceIn(0, 255)
        greenInt = g.coerceIn(0, 255)
        blueInt = b.coerceIn(0, 255)

        val colorInt = AndroidColor.rgb(redInt, greenInt, blueInt)
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(colorInt, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        val formatted = String.format("#%02X%02X%02X", redInt, greenInt, blueInt)
        hexText = formatted
        onHexChange(formatted)
    }

    fun applyHex(hex: String) {
        hexText = hex.uppercase()
        try {
            val cleanHex = if (hex.startsWith("#")) hex else "#$hex"
            if (cleanHex.length == 7 || cleanHex.length == 9) {
                val colorInt = AndroidColor.parseColor(cleanHex)
                redInt = AndroidColor.red(colorInt)
                greenInt = AndroidColor.green(colorInt)
                blueInt = AndroidColor.blue(colorInt)

                val hsv = FloatArray(3)
                AndroidColor.colorToHSV(colorInt, hsv)
                hue = hsv[0]
                saturation = hsv[1]
                value = hsv[2]
                onHexChange(cleanHex.take(7))
            }
        } catch (e: Exception) {
            // typing
        }
    }

    val currentColor = remember(redInt, greenInt, blueInt) {
        Color(redInt, greenInt, blueInt)
    }
    val clipboardManager = LocalClipboardManager.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Target Header Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Editing: $targetLabel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$targetSinhalaLabel • සජීවීව වෙනස් වේ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Swatch Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .shadow(3.dp, CircleShape)
                    )
                    Text(
                        text = hexText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }

            // Mode Selector Tabs (Circle Wheel / RGB Mod / Presets)
            TabRow(
                selectedTabIndex = selectedStudioTab,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                divider = {}
            ) {
                Tab(
                    selected = selectedStudioTab == 0,
                    onClick = { selectedStudioTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.ColorLens, contentDescription = null, modifier = Modifier.size(15.dp))
                            Text("Circle Wheel", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
                Tab(
                    selected = selectedStudioTab == 1,
                    onClick = { selectedStudioTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                            Text("RGB Mod", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
                Tab(
                    selected = selectedStudioTab == 2,
                    onClick = { selectedStudioTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(15.dp))
                            Text("Presets", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
            }

            when (selectedStudioTab) {
                0 -> {
                    // MODE 0: CIRCULAR COLOR WHEEL (Instant Real-time Touch)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularColorWheel(
                            hue = hue,
                            saturation = saturation,
                            onHueSaturationChange = { newHue, newSat ->
                                applyHSV(newHue, newSat, value)
                            },
                            modifier = Modifier.size(185.dp)
                        )
                    }

                    // Brightness Slider
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Brightness / ආලෝකය",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "${(value * 100).roundToInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = value,
                            onValueChange = { newVal ->
                                applyHSV(hue, saturation, newVal)
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                1 -> {
                    // MODE 1: RGB SLIDERS (Instant Real-time Drag)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RgbSliderRow(
                            label = "Red (රතු R)",
                            value = redInt,
                            activeColor = Color(0xFFEF4444),
                            onValueChange = { applyRGB(it, greenInt, blueInt) }
                        )
                        RgbSliderRow(
                            label = "Green (කොළ G)",
                            value = greenInt,
                            activeColor = Color(0xFF22C55E),
                            onValueChange = { applyRGB(redInt, it, blueInt) }
                        )
                        RgbSliderRow(
                            label = "Blue (නිල් B)",
                            value = blueInt,
                            activeColor = Color(0xFF3B82F6),
                            onValueChange = { applyRGB(redInt, greenInt, it) }
                        )
                    }
                }
                2 -> {
                    // MODE 2: PRESET SWATCHES
                    val studioPresetColors = remember {
                        listOf(
                            "#0F172A", "#1E293B", "#334155", "#000000", "#181825", "#24283B",
                            "#FFFFFF", "#F8FAFC", "#F1F5F9", "#E2E8F0", "#CBD5E1", "#94A3B8",
                            "#38BDF8", "#0284C7", "#0072FF", "#2563EB", "#6366F1", "#00F0FF",
                            "#10B981", "#059669", "#15803D", "#22C55E", "#4ADE80", "#64FFDA",
                            "#F59E0B", "#D97706", "#F97316", "#EA580C", "#EF4444", "#DC2626",
                            "#EC4899", "#DB2777", "#F43F5E", "#8B5CF6", "#7C3AED", "#A855F7"
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 34.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(vertical = 4.dp)
                    ) {
                        items(studioPresetColors) { hex ->
                            val isSelected = hexText.equals(hex, ignoreCase = true)
                            val swatchColor = try { Color(AndroidColor.parseColor(hex)) } catch (e: Exception) { Color.Gray }
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        applyHex(hex)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = if (value < 0.5f || (redInt + greenInt + blueInt) < 300) Color.White else Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Hex Input Bar with Copy Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { applyHex(it) },
                    label = { Text("Hex Code (#)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(hexText))
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", fontSize = 12.sp)
                }
            }
        }
    }
}
