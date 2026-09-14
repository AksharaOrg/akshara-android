package org.slashboard.ime.settings

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import kotlinx.coroutines.launch
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import org.slashboard.ime.ime.SlashboardInputMethodService
import org.slashboard.ime.settings.theme.ThemeCreatorScreen
import org.slashboard.ime.R
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import org.slashboard.ime.sound.KeySoundPlayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextAlign
import org.slashboard.ime.BuildConfig
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.res.*
import org.slashboard.ime.engine.*
import org.slashboard.ime.settings.theme.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.core.view.WindowCompat
import org.slashboard.ime.data.*
import org.slashboard.ime.ime.*
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import org.slashboard.ime.settings.KeyboardPreferences
import org.slashboard.ime.settings.theme.CustomThemeManager

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            com.vanniktech.emoji.EmojiManager.install(com.vanniktech.emoji.ios.IosEmojiProvider())
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val prefs = KeyboardPreferences(this)
        
        setContent {
            var themeState by remember { mutableStateOf(prefs.theme) }
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFFFFFF),
                    onPrimary = Color(0xFF0044B3),
                    primaryContainer = Color(0xFF00D2FF).copy(alpha = 0.3f),
                    onPrimaryContainer = Color.White,
                    surface = Color.Black.copy(alpha = 0.35f),
                    onSurface = Color.White,
                    surfaceVariant = Color.Black.copy(alpha = 0.45f),
                    onSurfaceVariant = Color.LightGray,
                    background = Color.Transparent,
                    onBackground = Color.White
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0052D4),
                                    Color(0xFF0038A8),
                                    Color(0xFF001F6B),
                                    Color(0xFF000E33)
                                )
                            )
                        )
                ) {
                    SettingsScreen(prefs, onThemeChanged = { themeState = it })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefs: KeyboardPreferences, onThemeChanged: (String) -> Unit = {}) {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    var showThemesPage by remember { mutableStateOf(false) }
    var showThemeCreator by remember { mutableStateOf(false) }
    var themeIdToEdit by remember { mutableStateOf<String?>(null) }
    var showToolbarCustomization by remember { mutableStateOf(false) }
    var showTranslatorScreen by remember { mutableStateOf(false) }
    var showFontStudio by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var restoreSuccess by remember { mutableStateOf(false) }
    val backupManager = remember { org.slashboard.ime.settings.BackupRestoreManager(context) }
    
    val exportLauncher = rememberLauncherForActivityResult(
        object : androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream") {
            override fun createIntent(context: android.content.Context, input: String): Intent {
                val intent = super.createIntent(context, input)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val downloadUri = android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload")
                    intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, downloadUri)
                }
                return intent
            }
        }
    ) { uri ->
        uri?.let {
            scope.launch {
                isBackingUp = true
                val result = backupManager.exportData(it)
                isBackingUp = false
                if (result.isSuccess) {
                    android.widget.Toast.makeText(context, "Backup Saved Successfully!", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    android.widget.Toast.makeText(context, "Backup Failed!", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        object : androidx.activity.result.contract.ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: android.content.Context, input: Array<String>): Intent {
                val intent = super.createIntent(context, input)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val downloadUri = android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload")
                    intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, downloadUri)
                }
                return intent
            }
        }
    ) { uri ->
        uri?.let {
            scope.launch {
                isRestoring = true
                val result = backupManager.importData(it)
                isRestoring = false
                if (result.isSuccess) {
                    restoreSuccess = true
                } else {
                    android.widget.Toast.makeText(context, "Restore Failed! Invalid file.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (restoreSuccess) {
        AlertDialog(
            onDismissRequest = { 
                restoreSuccess = false 
                (context as? android.app.Activity)?.finish()
            },
            title = { Text("Restore Successful", fontWeight = FontWeight.Bold) },
            text = { Text("Your data has been restored successfully. The settings screen will now close to apply changes.") },
            confirmButton = {
                Button(onClick = { 
                    restoreSuccess = false
                    (context as? android.app.Activity)?.finishAffinity()
                    System.exit(0)
                }) {
                    Text("OK")
                }
            }
        )
    }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            refresh++
        }
        prefs.store.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.store.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val imm = remember { context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager }
    val keyboardEnabled = remember(refresh) {
        imm.enabledInputMethodList.any { it.packageName == context.packageName }
    }
    val keyboardSelected = remember(refresh) {
        val selectedStr = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        selectedStr != null && selectedStr.startsWith(context.packageName)
    }

    if (showToolbarCustomization) {
        TopBarCustomizationScreen(
            prefs = prefs,
            onBack = {
                showToolbarCustomization = false
                refresh++
            }
        )
        return
    }

    if (showTranslatorScreen) {
        TranslatorScreen(
            onBack = {
                showTranslatorScreen = false
                refresh++
            }
        )
        return
    }

    if (showThemeCreator) {
        org.slashboard.ime.settings.theme.ThemeCreatorScreen(
            prefs = prefs,
            themeIdToEdit = themeIdToEdit,
            onThemeCreated = {
                onThemeChanged(it)
                refresh++
                showThemeCreator = false
                themeIdToEdit = null
            },
            onBack = {
                showThemeCreator = false
                themeIdToEdit = null
                refresh++
            }
        )
        return
    }

    if (showThemesPage) {
        ThemeLayoutsScreen(
            prefs = prefs,
            externalRefresh = refresh,
            onThemeChanged = {
                onThemeChanged(it)
                refresh++
            },
            onBack = {
                showThemesPage = false
                refresh++
            },
            onCreateTheme = {
                themeIdToEdit = null
                showThemeCreator = true
            },
            onEditTheme = { id ->
                themeIdToEdit = id
                showThemeCreator = true
            },
            onDeleteTheme = { id ->
                val file = java.io.File(org.slashboard.ime.settings.theme.CustomThemeManager.getThemesDir(context), "$id.slashtheme")
                if (file.exists()) file.delete()
                val jsonFile = java.io.File(org.slashboard.ime.settings.theme.CustomThemeManager.getThemesDir(context), "$id.json")
                if (jsonFile.exists()) jsonFile.delete()
                if (prefs.theme == id) {
                    prefs.theme = "dark"
                    onThemeChanged("dark")
                }
                refresh++
            }
        )
        return
    }

    if (showFontStudio) {
        org.slashboard.ime.settings.font.FontStudioScreen(
            prefs = prefs,
            onBack = {
                showFontStudio = false
                refresh++
            }
        )
        return
    }

    var expandedSection by remember { mutableStateOf<String?>("Setup") }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var isCheckingForUpdates by remember { mutableStateOf(false) }
    var manualUpdateInfo by remember { mutableStateOf<org.slashboard.ime.update.UpdateInfo?>(null) }

    // Intercept back button to show exit confirmation when on the root screen
    BackHandler(enabled = !showThemesPage && !showThemeCreator && !showToolbarCustomization && !showTranslatorScreen && !showFontStudio) {
        if (prefs.confirmExit) {
            showExitConfirmDialog = true
        } else {
            (context as? Activity)?.finish()
        }
    }

    // First time launch: Request Notification permissions (Mic is on-demand only!)
    val permsToRequest = remember {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        prefs.firstLaunchPermissionsPrompted = true
    }

    var showFirstTimePermDialog by remember { mutableStateOf(!prefs.firstLaunchPermissionsPrompted && permsToRequest.isNotEmpty()) }

    if (showFirstTimePermDialog) {
        AlertDialog(
            onDismissRequest = {
                showFirstTimePermDialog = false
                prefs.firstLaunchPermissionsPrompted = true
            },
            icon = {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFF00D2FF),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    "අවශ්‍ය අවසර ලබා දෙන්න",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Slashboard යතුරුපුවරුවේ විශේෂාංග උපරිමයෙන් භාවිතා කිරීමට අවසර ලබා දෙන්න:",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.5.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clipboard Alerts සහ යාවත්කාලීන කිරීම් සඳහා Notification Access", fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.85f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFF00D2FF), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clipboard සහ Voice Typing පහසුවෙන් භාවිතා කිරීමට අවසර ලබා දෙන්න.", fontSize = 12.sp, color = Color.LightGray)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFirstTimePermDialog = false
                        prefs.firstLaunchPermissionsPrompted = true
                        permissionLauncher.launch(permsToRequest)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0052D4)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("අවසර ලබා දෙන්න (Allow)", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showFirstTimePermDialog = false
                        prefs.firstLaunchPermissionsPrompted = true
                    }
                ) {
                    Text("පසුව (Later)", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF161B26),
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            icon = {
                Icon(
                    Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = Color(0xFF00D2FF),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "යෙදුමෙන් ඉවත් වන්නද?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            },
            text = {
                Text(
                    "ඔබට Slashboard සැකසුම් යෙදුමෙන් ඉවත් වීමට අවශ්‍යද? (Are you sure you want to exit the app?)",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirmDialog = false
                        (context as? Activity)?.finish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("ඉවත් වන්න (Exit)", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showExitConfirmDialog = false }
                ) {
                    Text("නැත (Cancel)", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E2430),
            shape = RoundedCornerShape(20.dp)
        )
    }

    manualUpdateInfo?.let { info ->
        InAppUpdateDialog(
            updateInfo = info,
            onDismiss = { manualUpdateInfo = null }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Slashboard Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (prefs.confirmExit) {
                                showExitConfirmDialog = true
                            } else {
                                (context as? Activity)?.finish()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Exit App",
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                HeaderAnimation()
            }

            if (keyboardEnabled && keyboardSelected) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF003828).copy(alpha = 0.7f),
                        border = BorderStroke(1.5.dp, Color(0xFF00E676)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676).copy(alpha = 0.2f))
                                    .border(1.5.dp, Color(0xFF00E676), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "යතුරුපුවරුව ක්‍රියාකාරීයි (Active)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00E676)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = Color(0xFF00E676).copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "READY",
                                            color = Color(0xFF00E676),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Slashboard දැනට සක්‍රීය පෙරනිමි යතුරුපුවරුව ලෙස තෝරාගෙන ඇත. පහත කොටුවෙන් හෝ ඕනෑම තැනක ටයිප් කරන්න.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                var testText by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = testText,
                    onValueChange = { testText = it },
                    label = { Text("Test Keyboard Here") },
                    placeholder = { Text("Tap here to type and test Slashboard...") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    trailingIcon = {
                        if (testText.isNotEmpty()) {
                            IconButton(onClick = { testText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear text")
                            }
                        }
                    }
                )
            }

            if (!keyboardEnabled || !keyboardSelected) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (!keyboardEnabled) "Keyboard Not Enabled" else "Keyboard Not Selected",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = if (!keyboardEnabled) 
                                            "Tap below to enable Slashboard in Android Settings."
                                        else 
                                            "Slashboard is enabled! Tap below to make it your current active keyboard.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    if (!keyboardEnabled) {
                                        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                                    } else {
                                        imm.showInputMethodPicker()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (!keyboardEnabled) Icons.Default.Settings else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (!keyboardEnabled) "Enable in Settings" else "Select as Active Keyboard",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            item {
                AccordionSection(
                    title = "Setup", 
                    icon = Icons.Default.Settings, 
                    expanded = expandedSection == "Setup" || (!keyboardEnabled || !keyboardSelected), 
                    onExpandedChange = { expandedSection = if (it) "Setup" else null }
                ) {
                    SettingsActionRow(
                        title = "Step 1: " + stringResource(R.string.enable_keyboard),
                        summary = if (keyboardEnabled) stringResource(R.string.status_enabled) else stringResource(R.string.status_enable_needed),
                        icon = Icons.Default.Keyboard,
                        statusBadge = if (keyboardEnabled) "Enabled" else "Required",
                        isCompleted = keyboardEnabled,
                        onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    SettingsActionRow(
                        title = "Step 2: " + stringResource(R.string.select_keyboard),
                        summary = if (keyboardSelected) stringResource(R.string.status_selected) else stringResource(R.string.status_select_needed),
                        icon = Icons.Default.CheckCircle,
                        statusBadge = if (keyboardSelected) "Active" else if (keyboardEnabled) "Tap to Select" else "Pending Step 1",
                        isCompleted = keyboardSelected,
                        onClick = { imm.showInputMethodPicker() }
                    )
                }
            }

            item {
                AccordionSection("Typing", Icons.Default.Keyboard, expanded = expandedSection == "Typing", onExpandedChange = { expandedSection = if (it) "Typing" else null }) {
                    var showSpacebarDialog by remember { mutableStateOf(false) }
                    SettingsActionRow(
                        title = "Spacebar Name",
                        summary = if (prefs.customSpacebarText.isEmpty()) "Default" else prefs.customSpacebarText,
                        icon = Icons.Default.SpaceBar,
                        onClick = { showSpacebarDialog = true }
                    )
                    
                    if (showSpacebarDialog) {
                        var textValue by remember { mutableStateOf(prefs.customSpacebarText) }
                        AlertDialog(
                            onDismissRequest = { showSpacebarDialog = false },
                            title = { Text("Custom Spacebar Name") },
                            text = {
                                OutlinedTextField(
                                    value = textValue,
                                    onValueChange = { if (it.length <= 14) textValue = it },
                                    label = { Text("Name (max 14)") },
                                    supportingText = { Text("${textValue.length}/14") },
                                    singleLine = true
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = { 
                                    prefs.customSpacebarText = textValue.trim().take(14)
                                    refresh++
                                    showSpacebarDialog = false
                                }) { Text("Save") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showSpacebarDialog = false }) { Text("Cancel") }
                            }
                        )
                    }

                    SettingsChoiceRow(
                        title = stringResource(R.string.input_mode),
                        icon = Icons.Default.Language,
                        entries = stringArrayResource(R.array.input_mode_entries).toList(),
                        values = stringArrayResource(R.array.input_mode_values).toList(),
                        currentValue = prefs.mode.name,
                        onValueChange = { 
                            prefs.mode = runCatching { InputMode.valueOf(it) }.getOrDefault(InputMode.SMART_PHONETIC)
                            refresh++ 
                        }
                    )
                    SettingsToggleRow(
                        title = "Confirm on Exit",
                        summary = "Show confirmation dialog before exiting the app",
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        checked = prefs.confirmExit,
                        onCheckedChange = { prefs.confirmExit = it; refresh++ }
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.suggestions),
                        summary = null,
                        icon = Icons.Default.AutoFixHigh,
                        checked = prefs.suggestions,
                        onCheckedChange = { prefs.suggestions = it; refresh++ }
                    )
                    SettingsChoiceRow(
                        title = stringResource(R.string.top_row),
                        icon = Icons.Default.Numbers,
                        entries = stringArrayResource(R.array.top_row_entries).toList(),
                        values = stringArrayResource(R.array.top_row_values).toList(),
                        currentValue = prefs.topRow,
                        onValueChange = { prefs.topRow = it; refresh++ }
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.app_layout_memory_title),
                        summary = stringResource(R.string.app_layout_memory_summary),
                        icon = Icons.Default.Memory,
                        checked = prefs.appLayoutMemory,
                        onCheckedChange = { prefs.appLayoutMemory = it; refresh++ }
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.smart_modifiers_title),
                        summary = stringResource(R.string.smart_modifiers_summary),
                        icon = Icons.Default.AlternateEmail,
                        checked = prefs.smartKeyModifiers,
                        onCheckedChange = { prefs.smartKeyModifiers = it; refresh++ }
                    )
                }
            }

            item {
                AccordionSection("Emoji & Clipboard", Icons.Default.EmojiEmotions, expanded = expandedSection == "Emoji & Clipboard", onExpandedChange = { expandedSection = if (it) "Emoji & Clipboard" else null }) {
                    SettingsToggleRow(
                        title = stringResource(R.string.emoji_picker),
                        summary = null,
                        icon = Icons.Default.SentimentSatisfied,
                        checked = prefs.emojiPicker,
                        onCheckedChange = { prefs.emojiPicker = it; refresh++ }
                    )
                    SettingsChoiceRow(
                        title = stringResource(R.string.skin_tone),
                        icon = Icons.Default.Face,
                        entries = stringArrayResource(R.array.skin_tone_entries).toList(),
                        values = stringArrayResource(R.array.skin_tone_values).toList(),
                        currentValue = prefs.skinTone,
                        onValueChange = { prefs.skinTone = it; refresh++ }
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.clipboard_history),
                        summary = stringResource(R.string.clipboard_summary),
                        icon = Icons.Default.ContentPaste,
                        checked = prefs.clipboardHistory,
                        onCheckedChange = { prefs.clipboardHistory = it; refresh++ }
                    )
                }
            }

            item {
                AccordionSection("Themes & Appearance", Icons.Default.Palette, expanded = expandedSection == "Appearance" || expandedSection == "Themes & Appearance", onExpandedChange = { expandedSection = if (it) "Themes & Appearance" else null }) {
                    SettingsActionRow(
                        title = "Layouts & Themes",
                        summary = "15+ Themes, gradients, custom wallpapers & styles",
                        icon = Icons.Default.Palette,
                        onClick = { showThemesPage = true }
                    )
                    SettingsActionRow(
                        title = "Font Studio (500+ Fonts)",
                        summary = "30 categories, live preview, copy styled text & import .TTF",
                        icon = Icons.Default.FontDownload,
                        onClick = { showFontStudio = true }
                    )
                    SettingsActionRow(
                        title = "Top Bar Icons",
                        summary = "Customize, reorder, and toggle toolbar shortcuts",
                        icon = Icons.Default.Tune,
                        onClick = { showToolbarCustomization = true }
                    )
                    SettingsChoiceRow(
                        title = stringResource(R.string.key_spacing),
                        icon = Icons.Default.SpaceBar,
                        entries = stringArrayResource(R.array.spacing_entries).toList(),
                        values = stringArrayResource(R.array.spacing_values).toList(),
                        currentValue = prefs.keySpacing,
                        onValueChange = { prefs.keySpacing = it; refresh++ }
                    )
                    SettingsChoiceRow(
                        title = stringResource(R.string.keyboard_size),
                        icon = Icons.Default.Height,
                        entries = stringArrayResource(R.array.keyboard_size_entries).toList(),
                        values = stringArrayResource(R.array.keyboard_size_values).toList(),
                        currentValue = prefs.keyboardSize,
                        onValueChange = { prefs.keyboardSize = it; refresh++ }
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.spatial_decoder),
                        summary = stringResource(R.string.spatial_decoder_summary),
                        icon = Icons.Default.TouchApp,
                        checked = prefs.spatialDecoder,
                        onCheckedChange = { prefs.spatialDecoder = it; refresh++ }
                    )
                    SettingsToggleRow(
                        title = "High Contrast Mode",
                        summary = "Increase contrast on key labels and borders",
                        icon = Icons.Default.Contrast,
                        checked = prefs.highContrast,
                        onCheckedChange = { prefs.highContrast = it; refresh++ }
                    )
                    SettingsToggleRow(
                        title = "Transparent Keyboard Background",
                        summary = "Enable transparent keyboard background instead of solid color",
                        icon = Icons.Default.Opacity,
                        checked = prefs.transparentBackground,
                        onCheckedChange = { prefs.transparentBackground = it; refresh++ }
                    )
                    SettingsSliderRow(
                        title = stringResource(R.string.long_press_delay),
                        summary = stringResource(R.string.long_press_delay_summary),
                        value = prefs.longPressMs.toFloat(),
                        valueRange = 120f..500f,
                        steps = 0,
                        icon = Icons.Default.Timer,
                        valueLabel = "${prefs.longPressMs}ms",
                        onValueChange = { prefs.longPressMs = kotlin.math.round(it / 10f).toLong() * 10L },
                        presets = listOf(
                            "150ms" to 150f,
                            "200ms" to 200f,
                            "280ms" to 280f,
                            "350ms" to 350f,
                            "450ms" to 450f
                        )
                    )
                    SettingsChoiceRow(
                        title = stringResource(R.string.thumb_reach_title),
                        icon = Icons.Default.Gesture,
                        entries = listOf("Standard (Off)", "Right Thumb Dominant", "Left Thumb Dominant", "Adaptive Heatmap"),
                        values = listOf("off", "right_thumb", "left_thumb", "adaptive_heatmap"),
                        currentValue = prefs.thumbReachMode,
                        onValueChange = { prefs.thumbReachMode = it; refresh++ }
                    )
                    SettingsChoiceRow(
                        title = stringResource(R.string.one_handed),
                        icon = Icons.Default.PanTool,
                        entries = stringArrayResource(R.array.one_handed_entries).toList(),
                        values = stringArrayResource(R.array.one_handed_values).toList(),
                        currentValue = prefs.oneHanded,
                        onValueChange = { prefs.oneHanded = it; refresh++ }
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.haptics),
                        summary = null,
                        icon = Icons.Default.Vibration,
                        checked = prefs.haptics,
                        onCheckedChange = { prefs.haptics = it; refresh++ }
                    )
                    SettingsToggleRow(
                        title = "Key Sounds",
                        summary = if (prefs.keySounds) "On (${prefs.soundPack.replaceFirstChar { it.uppercase() }})" else "Off (Sound on keypress)",
                        icon = if (prefs.keySounds) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        checked = prefs.keySounds,
                        onCheckedChange = {
                            prefs.keySounds = it
                            if (it) {
                                KeySoundPlayer.getInstance(context).play(prefs.soundPack)
                            }
                            refresh++
                        }
                    )
                    SoundPackChoiceRow(
                        prefs = prefs,
                        onChanged = { refresh++ }
                    )
                }
            }

            item {
                AccordionSection(
                    title = "Sinhala ⇄ English Translator",
                    icon = Icons.Default.Translate,
                    expanded = expandedSection == "Sinhala ⇄ English Translator" || expandedSection == "Translator",
                    onExpandedChange = { expandedSection = if (it) "Sinhala ⇄ English Translator" else null }
                ) {
                    SettingsActionRow(
                        title = "Open In-App Translator",
                        summary = "Real-time Sinhala ⇄ English translation, voice input, and phrasebook",
                        icon = Icons.Default.Translate,
                        onClick = { showTranslatorScreen = true }
                    )
                }
            }

            item {
                AccordionSection(
                    title = "Top Bar & Toolbar",
                    icon = Icons.Default.Tune,
                    expanded = expandedSection == "Top Bar & Toolbar" || expandedSection == "Toolbar",
                    onExpandedChange = { expandedSection = if (it) "Top Bar & Toolbar" else null }
                ) {
                    SettingsActionRow(
                        title = "Customize Top Bar Icons",
                        summary = "Rearrange, enable or hide quick action icons on the keyboard toolbar",
                        icon = Icons.Default.DashboardCustomize,
                        onClick = { showToolbarCustomization = true }
                    )
                }
            }

            item {
                AccordionSection("Privacy & Reset", Icons.Default.Security, expanded = expandedSection == "Privacy & Reset", onExpandedChange = { expandedSection = if (it) "Privacy & Reset" else null }) {
                    var showDialog by remember { mutableStateOf<String?>(null) }
                    
                    if (showDialog == "clear_learning") {
                        AlertDialog(
                            onDismissRequest = { showDialog = null },
                            title = { Text(stringResource(R.string.clear_learning_title)) },
                            text = { Text(stringResource(R.string.clear_learning_message)) },
                            confirmButton = {
                                TextButton(onClick = { LocalLearningStore(context).clear(); showDialog = null }) { Text(stringResource(R.string.clear)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDialog = null }) { Text(stringResource(android.R.string.cancel)) }
                            }
                        )
                    }
                    if (showDialog == "reset_touch") {
                        AlertDialog(
                            onDismissRequest = { showDialog = null },
                            title = { Text(stringResource(R.string.reset_touch_title)) },
                            text = { Text(stringResource(R.string.reset_touch_message)) },
                            confirmButton = {
                                TextButton(onClick = { 
                                    TouchPersonalizationStore(context).reset()
                                    showDialog = null 
                                }) { Text(stringResource(R.string.clear)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDialog = null }) { Text(stringResource(android.R.string.cancel)) }
                            }
                        )
                    }
                    if (showDialog == "clear_clipboard") {
                        AlertDialog(
                            onDismissRequest = { showDialog = null },
                            title = { Text(stringResource(R.string.clear_clipboard_title)) },
                            text = { Text(stringResource(R.string.clear_clipboard_message)) },
                            confirmButton = {
                                TextButton(onClick = { 
                                    ClipboardHistoryStore(context).clear()
                                    showDialog = null 
                                }) { Text(stringResource(R.string.clear)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDialog = null }) { Text(stringResource(android.R.string.cancel)) }
                            }
                        )
                    }
                    if (showDialog == "reset_all") {
                        AlertDialog(
                            onDismissRequest = { showDialog = null },
                            title = { Text(stringResource(R.string.reset_title)) },
                            text = { Text(stringResource(R.string.reset_message)) },
                            confirmButton = {
                                TextButton(onClick = { 
                                    prefs.reset()
                                    LocalLearningStore(context).clear()
                                    ClipboardHistoryStore(context).clear()
                                    TouchPersonalizationStore(context).reset()
                                    onThemeChanged(prefs.theme)
                                    refresh++
                                    showDialog = null 
                                }) { Text(stringResource(R.string.clear)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDialog = null }) { Text(stringResource(android.R.string.cancel)) }
                            }
                        )
                    }

                    SettingsActionRow(
                        title = stringResource(R.string.clear_learning_title),
                        summary = stringResource(R.string.clear_learning_summary),
                        icon = Icons.Default.Delete,
                        onClick = { showDialog = "clear_learning" }
                    )
                    SettingsActionRow(
                        title = stringResource(R.string.reset_touch_title),
                        summary = stringResource(R.string.reset_touch_summary),
                        icon = Icons.Default.TouchApp,
                        onClick = { showDialog = "reset_touch" }
                    )
                    SettingsActionRow(
                        title = stringResource(R.string.clear_clipboard_title),
                        summary = stringResource(R.string.clear_clipboard_summary),
                        icon = Icons.Default.ClearAll,
                        onClick = { showDialog = "clear_clipboard" }
                    )
                    SettingsActionRow(
                        title = stringResource(R.string.reset_title),
                        summary = stringResource(R.string.reset_summary),
                        icon = Icons.Default.Restore,
                        onClick = { showDialog = "reset_all" }
                    )
                }
            }

            item {
                AccordionSection("Backup & Restore", Icons.Default.Backup, expanded = expandedSection == "Backup", onExpandedChange = { expandedSection = if (it) "Backup" else null }) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Backup or restore all your settings, custom themes, learned words, and clipboard history to a '.slbrd' file.",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Button(
                            onClick = {
                                if (!isBackingUp) {
                                    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                                    exportLauncher.launch("slashboard_backup_$date.slbrd")
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isBackingUp) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export Backup (දත්ත සුරකින්න)", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                if (!isRestoring) {
                                    importLauncher.launch(arrayOf("*/*"))
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isRestoring) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import Backup (දත්ත ලබාගන්න)", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item {
                AccordionSection("Updates", Icons.Default.SystemUpdate, expanded = expandedSection == "Updates", onExpandedChange = { expandedSection = if (it) "Updates" else null }) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "Current Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                if (isCheckingForUpdates) return@Button
                                isCheckingForUpdates = true
                                scope.launch {
                                    val info = org.slashboard.ime.update.UpdateManager(context).checkForUpdates(BuildConfig.VERSION_NAME)
                                    isCheckingForUpdates = false
                                    if (info.hasUpdate) {
                                        manualUpdateInfo = info
                                    } else {
                                        android.widget.Toast.makeText(context, "යෙදුම යාවත්කාලීනයි (Up to date)", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isCheckingForUpdates) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("යාවත්කාලීන පරීක්ෂා කරන්න (Check Updates)", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/dinushlakmal/xxSlashboardxx/releases/latest"))
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("අතින් බාගත කරන්න (Manual Download)", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item {
                AccordionSection("About", Icons.Default.Info, expanded = expandedSection == "About", onExpandedChange = { expandedSection = if (it) "About" else null }) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Made in ❤️ with Sri Lanka",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Developed by Dinush Lakmal\nEmail: dinushlakmal01@gmail.com",
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://dinushlakmal.github.io/xxSlashboardxx/"))
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("නිල වෙබ් අඩවිය (Official Website)")
                        }
                        OutlinedButton(
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.facebook.com/profile.php?id=61593856756750"))
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Facebook, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF1877F2))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("අපගේ Facebook පිටුව (Facebook Page)", color = Color(0xFF1877F2))
                        }
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(0.5f).padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                if (prefs.confirmExit) {
                                    showExitConfirmDialog = true
                                } else {
                                    (context as? Activity)?.finish()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE53935).copy(alpha = 0.85f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "යෙදුමෙන් ඉවත් වන්න (Exit Application)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Slashboard v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
fun InAppUpdateDialog(
    updateInfo: org.slashboard.ime.update.UpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloader = remember { org.slashboard.ime.update.AppUpdateDownloader(context) }

    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        title = { Text(text = "නව යාවත්කාලීන කිරීමක් (Update: ${updateInfo.latestVersion})", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!isDownloading) {
                    Text(text = updateInfo.releaseNotes)
                } else {
                    Text(text = "Downloading update... $progress%")
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                Button(onClick = {
                    if (!downloader.hasInstallPermission()) {
                        downloader.requestInstallPermission()
                        android.widget.Toast.makeText(context, "කරුණාකර අවසර ලබා දී නැවත Update බොත්තම ඔබන්න", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        isDownloading = true
                        scope.launch {
                            downloader.downloadApk(
                                downloadUrl = updateInfo.downloadUrl,
                                onProgress = { progress = it },
                                onComplete = { file ->
                                    isDownloading = false
                                    onDismiss()
                                    downloader.promptInstall(file)
                                },
                                onError = {
                                    isDownloading = false
                                    android.widget.Toast.makeText(context, "බාගත කිරීම අසාර්ථකයි (Download failed)", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }) {
                    Text("යාවත්කාලීන කරන්න (Update)")
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onDismiss) { Text("පසුවට (Later)") }
            }
        }
    )
}

@Composable
fun HeaderAnimation() {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 24.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(120.dp)
                .scale(scale),
            shape = CircleShape,
            shadowElevation = 8.dp,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_slashboard_logo),
                contentDescription = "Slashboard Logo",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(300)
            visible = true
        }
        AnimatedVisibility(visible = visible) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Slashboard",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AccordionSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable { onExpandedChange(!expanded) }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun SettingsChoiceRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    entries: List<String>,
    values: List<String>,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val currentIndex = values.indexOf(currentValue).coerceAtLeast(0)
    val currentLabel = entries.getOrElse(currentIndex) { currentValue }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = title, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    entries.forEachIndexed { index, name ->
                        val itemVal = values.getOrElse(index) { name }
                        val isSelected = (itemVal == currentValue)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onValueChange(itemVal)
                                    showDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onValueChange(itemVal)
                                    showDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SoundPackChoiceRow(
    prefs: KeyboardPreferences,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    val soundPacks = listOf(
        Triple("system", "System Click", "Android Default Haptic & Click Sound"),
        Triple("ios", "iOS Keyboard", "Soft, crisp iPhone-style key click"),
        Triple("mechanical", "Mechanical Switch", "Tactile Cherry MX switch click"),
        Triple("typewriter", "Vintage Typewriter", "Classic typewriter metallic key clack")
    )
    val currentPack = soundPacks.find { it.first == prefs.soundPack } ?: soundPacks[0]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Audiotrack,
            contentDescription = null,
            tint = if (prefs.keySounds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Sound Pack", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${currentPack.second} ${if (!prefs.keySounds) "(Sounds Off)" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (prefs.keySounds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (prefs.keySounds) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.clickable { showDialog = true }
        ) {
            Text(
                text = currentPack.second,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (prefs.keySounds) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Audiotrack, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Sound Pack", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Choose keyboard sound effect. Tap the speaker icon to test:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    soundPacks.forEach { (id, name, desc) ->
                        val isSelected = prefs.soundPack == id
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            border = BorderStroke(
                                1.5.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    prefs.soundPack = id
                                    prefs.keySounds = true
                                    KeySoundPlayer.getInstance(context).play(id)
                                    onChanged()
                                    showDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        prefs.soundPack = id
                                        prefs.keySounds = true
                                        KeySoundPlayer.getInstance(context).play(id)
                                        onChanged()
                                        showDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                    )
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        KeySoundPlayer.getInstance(context).play(id)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VolumeUp,
                                        contentDescription = "Play Preview",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    summary: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var localChecked by remember(checked) { mutableStateOf(checked) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val next = !localChecked
                localChecked = next
                onCheckedChange(next)
            }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (localChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = localChecked,
            onCheckedChange = null
        )
    }
}

@Composable
fun SettingsActionRow(
    title: String,
    summary: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.secondary,
    statusBadge: String? = null,
    isCompleted: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isCompleted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isCompleted) Icons.Default.CheckCircle else icon,
                contentDescription = null,
                tint = if (isCompleted) MaterialTheme.colorScheme.primary else iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (statusBadge != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isCompleted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
            ) {
                Text(
                    text = statusBadge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isCompleted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsSliderRow(
    title: String,
    summary: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    presets: List<Pair<String, Float>> = emptyList()
) {
    var localValue by remember(value) { mutableFloatStateOf(value) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "${localValue.toInt()}ms",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Slider(
            value = localValue,
            onValueChange = { newVal ->
                localValue = newVal
                onValueChange(newVal)
            },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )

        if (presets.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { (label, presetVal) ->
                    val isSelected = kotlin.math.abs(localValue - presetVal) < 15f
                    Surface(
                        onClick = {
                            localValue = presetVal
                            onValueChange(presetVal)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun intColor(c: Int): Color = Color(c.toLong() and 0xFFFFFFFFL)

@Composable
fun InteractiveKeyboardPreview(prefs: KeyboardPreferences, refresh: Int) {
    var testText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val currentTheme = prefs.theme
    val palette = remember(currentTheme, prefs.highContrast, prefs.transparentBackground, refresh) {
        KeyboardPaletteResolver.resolve(context, currentTheme, prefs.highContrast)
    }

    fun intToHex(c: Int): String = String.format("#%06X", 0xFFFFFF and c)

    val hasBgImage = !palette.backgroundImagePath.isNullOrEmpty() && java.io.File(palette.backgroundImagePath).exists()
    val isTransparent = (prefs.transparentBackground || currentTheme.contains("transparent") || palette.background == android.graphics.Color.TRANSPARENT) && !hasBgImage
    val bgHex = remember(palette.background, isTransparent, hasBgImage, palette.dark) {
        if (hasBgImage) {
            if (palette.dark) "#121824" else "#F8FAFC"
        } else if (isTransparent) "transparent" else intToHex(palette.background)
    }
    val effectiveKeyOpacity = remember(palette.keyOpacity, isTransparent, hasBgImage) {
        if (hasBgImage) (if (palette.keyOpacity < 0.85f) 0.90f else palette.keyOpacity)
        else if (isTransparent && palette.keyOpacity >= 1.0f) 0.68f
        else palette.keyOpacity
    }
    val effectiveBorderWidth = remember(palette.borderWidthDp, isTransparent, hasBgImage) {
        if (hasBgImage && palette.borderWidthDp == 0f) 1f
        else if (isTransparent && palette.borderWidthDp == 0f) 1f
        else palette.borderWidthDp
    }
    val keyHex = remember(palette.key) { intToHex(palette.key) }
    val actionHex = remember(palette.action) { intToHex(palette.action) }
    val utilHex = remember(palette.utility) { intToHex(palette.utility) }
    val textHex = remember(palette.dark) { if (palette.dark) "#FFFFFF" else "#0F172A" }
    val inkHex = remember(palette.ink) { intToHex(palette.ink) }
    val spaceHex = remember(palette.spaceKey, palette.key) { intToHex(palette.spaceKey ?: palette.key) }
    val spaceBorderHex = remember(palette.spaceBorder, palette.ink) { intToHex(palette.spaceBorder ?: palette.ink) }
    val gradStartHex = remember(palette.gradientStart, bgHex) { palette.gradientStart?.let { intToHex(it) } ?: bgHex }
    val gradEndHex = remember(palette.gradientEnd, bgHex) { palette.gradientEnd?.let { intToHex(it) } ?: bgHex }
    val borderColorHex = remember(palette.borderColor, inkHex, isTransparent, palette.dark) {
        if (isTransparent && palette.borderColor == null) {
            if (palette.dark) "#55FFFFFF" else "#33000000"
        } else {
            palette.borderColor?.let { intToHex(it) } ?: inkHex
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        "Live Keyboard Preview (සජීවී පෙරදසුන)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.5.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isTransparent) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                ) {
                    Text(
                        text = if (isTransparent) "Glass Mode (පාරදෘශ්‍ය)" else "Real-Time",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isTransparent) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Compact Live Keyboard Preview - instant visual rendering for every style change
            org.slashboard.ime.settings.theme.ThemeLiveKeyboardPreview(
                backgroundHex = bgHex,
                keyHex = keyHex,
                actionHex = actionHex,
                utilityHex = utilHex,
                textHex = textHex,
                inkHex = inkHex,
                spaceKeyHex = spaceHex,
                spaceBorderHex = spaceBorderHex,
                dark = palette.dark,
                blurEffect = palette.blurEffect,
                keyOpacity = effectiveKeyOpacity,
                keyRadiusDp = palette.keyRadiusDp ?: 6f,
                bgImagePath = palette.backgroundImagePath,
                keyStyle = palette.keyStyle,
                enableGradient = palette.gradientStart != null && palette.gradientEnd != null,
                gradientStartHex = gradStartHex,
                gradientEndHex = gradEndHex,
                borderWidthDp = effectiveBorderWidth,
                borderColorHex = borderColorHex,
                onKeyTap = { k ->
                    if (k == "⌫") {
                        if (testText.isNotEmpty()) testText = testText.dropLast(1)
                    } else if (k == "\n") {
                        testText += " "
                    } else {
                        testText += k
                    }
                }
            )

            // Live text strip for testing key taps
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (testText.isEmpty()) "Tap keys above to test live preview..." else testText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (testText.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (testText.isNotEmpty()) {
                        Text(
                            text = "Clear",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { testText = "" }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

data class ThemeEntry(
    val id: String,
    val name: String,
    val category: String, // "dark", "light", "vibrant", "custom"
    val isCustom: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeLayoutsScreen(
    prefs: KeyboardPreferences,
    externalRefresh: Int = 0,
    onThemeChanged: (String) -> Unit = {},
    onBack: () -> Unit,
    onCreateTheme: () -> Unit = {},
    onEditTheme: (String) -> Unit = {},
    onDeleteTheme: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var localRefresh by remember { mutableStateOf(0) }
    val refresh = externalRefresh + localRefresh
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    var themeToDelete by remember { mutableStateOf<String?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showPreview by remember { mutableStateOf(false) }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrEmpty()) {
            snackbarHostState.showSnackbar(msg)
            snackbarMessage = null
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val id = org.slashboard.ime.settings.theme.CustomThemeManager.importTheme(context, uri)
            if (id != null) {
                prefs.theme = id
                onThemeChanged(id)
                localRefresh++
                snackbarMessage = "Theme imported successfully!"
            } else {
                snackbarMessage = "Could not import theme. Invalid file."
            }
        }
    }

    val photoWallpaperLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val newThemeId = org.slashboard.ime.settings.theme.CustomThemeManager.createPhotoWallpaperTheme(context, uri, "Photo Wallpaper")
            if (newThemeId != null) {
                prefs.theme = newThemeId
                onThemeChanged(newThemeId)
                localRefresh++
                snackbarMessage = "Photo wallpaper applied to keyboard! / ඡායාරූපය පසුබිමට යෙදුවා"
            } else {
                snackbarMessage = "Failed to apply photo wallpaper."
            }
        }
    }

    val customThemes = remember(refresh) { 
        org.slashboard.ime.settings.theme.CustomThemeManager.getThemes(context) 
    }

    val preInstalledThemes = remember {
        listOf(
            ThemeEntry("dark", "Slashboard Slate Dark", "dark"),
            ThemeEntry("light", "Slashboard Clean Light", "light"),
            ThemeEntry("system", "Dynamic Material You", "dark"),
            ThemeEntry("amoled_black", "AMOLED Pure Black", "dark"),
            ThemeEntry("pure_dark", "Pure Dark (Clean)", "dark"),
            ThemeEntry("material_light", "Material 3 Light", "light"),
            ThemeEntry("minimal_white", "Minimal White (Clean)", "light"),
            ThemeEntry("nordic_clean", "Nordic Clean", "light"),
            ThemeEntry("pastel_dream", "Pastel Dream", "light"),
            ThemeEntry("transparent_glass", "Transparent Glass Dark", "transparent"),
            ThemeEntry("transparent_glass_light", "Transparent Glass Light", "transparent"),
            ThemeEntry("catppuccin_mocha", "Catppuccin Mocha", "dark"),
            ThemeEntry("dracula", "Dracula Purple", "dark"),
            ThemeEntry("tokyo_night", "Tokyo Night", "dark"),
            ThemeEntry("nord", "Nordic Frost", "dark"),
            ThemeEntry("ocean_wave", "Ocean Wave", "vibrant"),
            ThemeEntry("neon_cyberpunk", "Neon Cyberpunk", "vibrant"),
            ThemeEntry("sunset", "Sunset Flame", "vibrant"),
            ThemeEntry("emerald", "Emerald Forest", "vibrant"),
            ThemeEntry("gold", "Royal Gold", "vibrant"),
            ThemeEntry("ios_style", "iOS Silver", "light"),
            ThemeEntry("rose_gold", "Rose Gold", "light"),
            ThemeEntry("cherry_blossom", "Cherry Blossom", "light"),
            ThemeEntry("lavender", "Lavender Dream", "light"),
            ThemeEntry("mint", "Fresh Mint", "light"),
            ThemeEntry("monokai", "Monokai Pro", "dark"),
            ThemeEntry("solarized_dark", "Solarized Dark", "dark"),
            ThemeEntry("solarized_light", "Solarized Light", "light"),
            ThemeEntry("gruvbox_dark", "Gruvbox Retro", "dark"),
            ThemeEntry("rose_pine", "Rosé Pine", "dark"),
            ThemeEntry("deep_space", "Deep Space", "dark"),
            ThemeEntry("coffee", "Espresso Coffee", "dark"),
            ThemeEntry("crimson", "Crimson Red", "dark"),
            ThemeEntry("royal_purple", "Royal Amethyst", "vibrant"),
            ThemeEntry("sapphire", "Deep Sapphire", "vibrant"),
            ThemeEntry("ruby", "Ruby Wine", "vibrant"),
            ThemeEntry("aquamarine", "Aquamarine", "vibrant"),
            ThemeEntry("obsidian", "Obsidian Black", "dark"),
            ThemeEntry("forest_green", "Forest Green", "dark"),
            ThemeEntry("cyberpunk", "Cyberpunk Yellow", "vibrant")
        )
    }

    val popularIds = remember {
        setOf("dark", "light", "system", "amoled_black", "transparent_glass", "ios_style", "neon_cyberpunk", "sunset", "monokai", "pastel_dream", "nordic_clean", "pure_dark")
    }

    val allThemeEntries = remember(customThemes, preInstalledThemes) {
        val customEntries = customThemes.map { 
            ThemeEntry(
                id = it.id,
                name = it.name,
                category = if (it.dark) "dark" else "light",
                isCustom = true
            )
        }
        customEntries + preInstalledThemes
    }

    val filteredThemes = remember(allThemeEntries, selectedCategoryIndex, searchQuery) {
        allThemeEntries.filter { item ->
            val matchesCategory = when (selectedCategoryIndex) {
                0 -> true // All
                1 -> popularIds.contains(item.id)
                2 -> item.category == "dark"
                3 -> item.category == "light"
                4 -> item.category == "transparent" || item.id.contains("transparent")
                5 -> item.category == "vibrant"
                6 -> item.isCustom
                else -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                item.name.contains(searchQuery, ignoreCase = true) || item.id.contains(searchQuery, ignoreCase = true)
            }
            matchesCategory && matchesSearch
        }
    }

    val activePalette = remember(prefs.theme, prefs.highContrast, refresh) {
        KeyboardPaletteResolver.resolve(context, prefs.theme, prefs.highContrast)
    }
    val activeThemeName = remember(prefs.theme, allThemeEntries) {
        allThemeEntries.find { it.id == prefs.theme }?.name ?: prefs.theme.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
    val hasActiveWallpaper = remember(activePalette.backgroundImagePath) {
        !activePalette.backgroundImagePath.isNullOrEmpty() && java.io.File(activePalette.backgroundImagePath!!).exists()
    }

    BackHandler {
        onBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Themes & Wallpaper", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("තේමා සහ පසුබිම් මෝස්තර", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    IconButton(onClick = { photoWallpaperLauncher.launch("image/*") }) {
                        Icon(Icons.Default.Image, contentDescription = "Add Photo Wallpaper", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onCreateTheme) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Create Custom Theme", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { importLauncher.launch("*/*") }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Import Theme File")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateTheme,
                icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                text = { Text("Create Theme (තේමාවක් හදන්න)", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. Current Active Theme Hero Banner & Quick Actions
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    text = "Current Keyboard Theme",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = activeThemeName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Color swatches preview
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(intColor(activePalette.background))
                                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(intColor(activePalette.key))
                                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(intColor(activePalette.action))
                                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            )
                        }
                    }

                    // Quick Action Pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Photo Wallpaper Action
                        AssistChip(
                            onClick = { photoWallpaperLauncher.launch("image/*") },
                            leadingIcon = {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = {
                                Text(if (hasActiveWallpaper) "Change Photo (ඡායාරූපය)" else "Set Photo Wallpaper (ඡායාරූපයක්)")
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        // Glass Transparency toggle
                        FilterChip(
                            selected = prefs.transparentBackground,
                            onClick = {
                                prefs.transparentBackground = !prefs.transparentBackground
                                localRefresh++
                                snackbarMessage = if (prefs.transparentBackground) "Glass Mode Enabled (වීදුරු මෝස්තරය සක්‍රීයයි)" else "Glass Mode Disabled"
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Opacity, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = {
                                Text(if (prefs.transparentBackground) "Glass: ON" else "Glass Mode (වීදුරු)")
                            }
                        )

                        // High Contrast toggle
                        FilterChip(
                            selected = prefs.highContrast,
                            onClick = {
                                prefs.highContrast = !prefs.highContrast
                                localRefresh++
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Contrast, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = {
                                Text("High Contrast")
                            }
                        )

                        // Toggle Live Typing Preview
                        AssistChip(
                            onClick = { showPreview = !showPreview },
                            leadingIcon = {
                                Icon(
                                    if (showPreview) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(if (showPreview) "Hide Preview" else "Test Typing (පෙරදසුන)")
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (showPreview) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                            )
                        )

                        if (hasActiveWallpaper) {
                            AssistChip(
                                onClick = {
                                    prefs.theme = "dark"
                                    onThemeChanged("dark")
                                    localRefresh++
                                    snackbarMessage = "Photo wallpaper removed / ඡායාරූපය ඉවත් කළා"
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                },
                                label = {
                                    Text("Remove Photo (ඉවත් කරන්න)", color = MaterialTheme.colorScheme.error)
                                }
                            )
                        }
                    }
                }
            }

            // 2. Expandable Live Interactive Preview
            AnimatedVisibility(
                visible = showPreview,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                InteractiveKeyboardPreview(prefs = prefs, refresh = refresh)
            }

            // 3. Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search 40+ themes (තේමා සොයන්න)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            )

            // 4. Category Filter Tabs
            val categories = listOf(
                "All (${allThemeEntries.size})" to Icons.Default.AllInclusive,
                "Popular ⭐" to Icons.Default.Star,
                "Dark 🌙" to Icons.Default.DarkMode,
                "Light ☀️" to Icons.Default.LightMode,
                "Glass 🪟" to Icons.Default.Opacity,
                "Vibrant 🎨" to Icons.Default.FlashOn,
                "Custom (${customThemes.size})" to Icons.Default.Person
            )

            ScrollableTabRow(
                selectedTabIndex = selectedCategoryIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                edgePadding = 14.dp,
                divider = { HorizontalDivider() }
            ) {
                categories.forEachIndexed { index, (title, icon) ->
                    Tab(
                        selected = selectedCategoryIndex == index,
                        onClick = { selectedCategoryIndex = index },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text(title, fontSize = 12.sp, fontWeight = if (selectedCategoryIndex == index) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    )
                }
            }

            if (filteredThemes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Text("No themes found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Try searching for another color or style", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        Button(onClick = onCreateTheme, modifier = Modifier.padding(top = 8.dp)) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text("Create Custom Theme (තේමාවක් හදන්න)")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 10.dp,
                        bottom = 88.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredThemes, key = { it.id }) { themeEntry ->
                        val themeValue = themeEntry.id
                        val palette = remember(themeValue, prefs.highContrast, refresh) {
                            KeyboardPaletteResolver.resolve(context, themeValue, prefs.highContrast)
                        }
                        val isSelected = prefs.theme == themeValue

                        MiniKeyboardPreview(
                            palette = palette,
                            themeName = themeEntry.name,
                            isSelected = isSelected,
                            isCustom = themeEntry.isCustom,
                            onClick = {
                                prefs.theme = themeValue
                                onThemeChanged(themeValue)
                                localRefresh++
                                snackbarMessage = "'${themeEntry.name}' applied! / තේමාව යෙදුවා"
                            },
                            onEdit = if (themeEntry.isCustom) {
                                { onEditTheme(themeValue) }
                            } else null,
                            onShare = {
                                if (themeEntry.isCustom) {
                                    org.slashboard.ime.settings.theme.CustomThemeManager.shareThemeText(context, themeValue)
                                } else {
                                    val newId = org.slashboard.ime.settings.theme.CustomThemeManager.duplicateTheme(context, themeValue)
                                    if (newId != null) {
                                        org.slashboard.ime.settings.theme.CustomThemeManager.shareThemeText(context, newId)
                                    }
                                }
                            },
                            onDuplicate = {
                                val newId = org.slashboard.ime.settings.theme.CustomThemeManager.duplicateTheme(context, themeValue)
                                if (newId != null) {
                                    prefs.theme = newId
                                    onThemeChanged(newId)
                                    localRefresh++
                                    snackbarMessage = "Theme duplicated into Custom Themes!"
                                }
                            },
                            onDelete = if (themeEntry.isCustom) {
                                { themeToDelete = themeValue }
                            } else null
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (themeToDelete != null) {
        val targetId = themeToDelete!!
        AlertDialog(
            onDismissRequest = { themeToDelete = null },
            title = { Text("Delete Custom Theme?") },
            text = { Text("Are you sure you want to delete this custom theme? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTheme(targetId)
                        themeToDelete = null
                        localRefresh++
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete (මකන්න)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { themeToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MiniKeyboardPreview(
    palette: KeyboardPalette,
    themeName: String,
    isSelected: Boolean,
    isCustom: Boolean = false,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetScale = when {
        isPressed -> 1.02f
        isSelected -> 1.045f
        isHovered -> 1.035f
        else -> 1.0f
    }
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tile_scale"
    )

    val targetElevation = when {
        isSelected -> 8.dp
        isHovered -> 6.dp
        else -> 2.dp
    }
    val animatedElevation by animateDpAsState(
        targetValue = targetElevation,
        animationSpec = tween(durationMillis = 200),
        label = "tile_elevation"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = intColor(palette.background)),
        border = if (isSelected) {
            BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        },
        elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation),
        modifier = modifier
            .fillMaxWidth()
            .scale(animatedScale)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
                    .padding(6.dp)
                    .drawBehind {
                        val keyColor = Color(palette.key.toLong() and 0xFFFFFFFFL)
                        val utilColor = Color(palette.utility.toLong() and 0xFFFFFFFFL)
                        val actionColor = Color(palette.action.toLong() and 0xFFFFFFFFL)
                        val actionTextColor = Color(palette.actionText.toLong() and 0xFFFFFFFFL)
                        val inkColor = Color(palette.ink.toLong() and 0xFFFFFFFFL)

                        val spacing = 2.dp.toPx()
                        val radius = CornerRadius(3.dp.toPx())
                        val dotRadius = 1.5.dp.toPx()

                        val contentWidth = size.width
                        if (contentWidth <= 0f || size.height <= 0f) return@drawBehind
                        
                        // Vertical arrangement computation
                        val rowHeight = 13.dp.toPx()
                        val barHeight = 4.dp.toPx()
                        val totalContentHeight = barHeight + (4 * rowHeight)
                        val availableVerticalSpace = size.height - totalContentHeight
                        val rowSpacing = (availableVerticalSpace / 4f).coerceAtLeast(0f)

                        var currentY = 0f

                        // Suggestion bar indicator (3 items: 1f, 1.5f, 1f weight)
                        val barSpacing = 4.dp.toPx()
                        val barPad = 4.dp.toPx()
                        val barAvailableWidth = contentWidth - 2 * barPad - 2 * barSpacing
                        val barUnit = (barAvailableWidth / 3.5f).coerceAtLeast(0f)
                        
                        var barX = barPad
                        drawRoundRect(color = inkColor.copy(alpha = 0.25f), topLeft = Offset(barX, currentY + 0.5.dp.toPx()), size = Size(barUnit, 3.dp.toPx()), cornerRadius = CornerRadius(1.5.dp.toPx()))
                        barX += barUnit + barSpacing
                        drawRoundRect(color = inkColor.copy(alpha = 0.45f), topLeft = Offset(barX, currentY), size = Size(1.5f * barUnit, 4.dp.toPx()), cornerRadius = CornerRadius(2.dp.toPx()))
                        barX += 1.5f * barUnit + barSpacing
                        drawRoundRect(color = inkColor.copy(alpha = 0.25f), topLeft = Offset(barX, currentY + 0.5.dp.toPx()), size = Size(barUnit, 3.dp.toPx()), cornerRadius = CornerRadius(1.5.dp.toPx()))
                        
                        currentY += barHeight + rowSpacing

                        // Row 1: 10 keys
                        val keyWidthR1 = ((contentWidth - 9 * spacing) / 10f).coerceAtLeast(0f)
                        for (i in 0 until 10) {
                            val kX = i * (keyWidthR1 + spacing)
                            drawRoundRect(color = keyColor, topLeft = Offset(kX, currentY), size = Size(keyWidthR1, rowHeight), cornerRadius = radius)
                            drawCircle(color = inkColor.copy(alpha = 0.7f), radius = dotRadius, center = Offset(kX + keyWidthR1 / 2f, currentY + rowHeight / 2f))
                        }

                        currentY += rowHeight + rowSpacing

                        // Row 2: 9 keys
                        val r2Padding = 4.dp.toPx()
                        val r2Width = contentWidth - 2 * r2Padding
                        val keyWidthR2 = ((r2Width - 8 * spacing) / 9f).coerceAtLeast(0f)
                        for (i in 0 until 9) {
                            val kX = r2Padding + i * (keyWidthR2 + spacing)
                            drawRoundRect(color = keyColor, topLeft = Offset(kX, currentY), size = Size(keyWidthR2, rowHeight), cornerRadius = radius)
                            drawCircle(color = inkColor.copy(alpha = 0.7f), radius = dotRadius, center = Offset(kX + keyWidthR2 / 2f, currentY + rowHeight / 2f))
                        }

                        currentY += rowHeight + rowSpacing

                        // Row 3: Shift (1.4f), 7 keys (1f), Backspace (1.4f)
                        val totalWeightR3 = 1.4f + 7f + 1.4f
                        val availableWidthR3 = contentWidth - 8 * spacing
                        val unitWidthR3 = (availableWidthR3 / totalWeightR3).coerceAtLeast(0f)

                        // Shift
                        val shiftWidth = (1.4f * unitWidthR3).coerceAtLeast(0f)
                        drawRoundRect(color = utilColor, topLeft = Offset(0f, currentY), size = Size(shiftWidth, rowHeight), cornerRadius = radius)
                        val shiftCenter = Offset(shiftWidth / 2f, currentY + rowHeight / 2f)
                        drawLine(color = inkColor, start = Offset(shiftCenter.x, shiftCenter.y + 2.dp.toPx()), end = Offset(shiftCenter.x, shiftCenter.y - 3.dp.toPx()), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(color = inkColor, start = Offset(shiftCenter.x - 2.dp.toPx(), shiftCenter.y - 1.dp.toPx()), end = Offset(shiftCenter.x, shiftCenter.y - 3.dp.toPx()), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(color = inkColor, start = Offset(shiftCenter.x + 2.dp.toPx(), shiftCenter.y - 1.dp.toPx()), end = Offset(shiftCenter.x, shiftCenter.y - 3.dp.toPx()), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)

                        var currentX = shiftWidth + spacing
                        for (i in 0 until 7) {
                            drawRoundRect(color = keyColor, topLeft = Offset(currentX, currentY), size = Size(unitWidthR3, rowHeight), cornerRadius = radius)
                            drawCircle(color = inkColor.copy(alpha = 0.7f), radius = dotRadius, center = Offset(currentX + unitWidthR3 / 2f, currentY + rowHeight / 2f))
                            currentX += unitWidthR3 + spacing
                        }

                        // Backspace
                        val backspaceWidth = (1.4f * unitWidthR3).coerceAtLeast(0f)
                        drawRoundRect(color = utilColor, topLeft = Offset(currentX, currentY), size = Size(backspaceWidth, rowHeight), cornerRadius = radius)
                        val bsCenter = Offset(currentX + backspaceWidth / 2f, currentY + rowHeight / 2f)
                        drawLine(color = inkColor, start = Offset(bsCenter.x + 2.dp.toPx(), bsCenter.y), end = Offset(bsCenter.x - 3.dp.toPx(), bsCenter.y), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(color = inkColor, start = Offset(bsCenter.x - 1.dp.toPx(), bsCenter.y - 2.dp.toPx()), end = Offset(bsCenter.x - 3.dp.toPx(), bsCenter.y), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(color = inkColor, start = Offset(bsCenter.x - 1.dp.toPx(), bsCenter.y + 2.dp.toPx()), end = Offset(bsCenter.x - 3.dp.toPx(), bsCenter.y), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)

                        currentY += rowHeight + rowSpacing

                        // Row 4: 123 (1.4f), Space (4.6f), Enter (1.8f)
                        val totalWeightR4 = 1.4f + 4.6f + 1.8f
                        val availableWidthR4 = contentWidth - 2 * spacing
                        val unitWidthR4 = (availableWidthR4 / totalWeightR4).coerceAtLeast(0f)

                        currentX = 0f
                        val symWidth = 1.4f * unitWidthR4
                        drawRoundRect(color = utilColor, topLeft = Offset(currentX, currentY), size = Size(symWidth, rowHeight), cornerRadius = radius)
                        currentX += symWidth + spacing

                        val spaceWidth = 4.6f * unitWidthR4
                        drawRoundRect(color = keyColor, topLeft = Offset(currentX, currentY), size = Size(spaceWidth, rowHeight), cornerRadius = radius)
                        drawRoundRect(color = inkColor.copy(alpha = 0.4f), topLeft = Offset(currentX + spaceWidth / 2f - 11.dp.toPx(), currentY + rowHeight / 2f - 1.dp.toPx()), size = Size(22.dp.toPx(), 2.dp.toPx()), cornerRadius = CornerRadius(1.dp.toPx()))
                        currentX += spaceWidth + spacing

                        val enterWidth = 1.8f * unitWidthR4
                        drawRoundRect(color = actionColor, topLeft = Offset(currentX, currentY), size = Size(enterWidth, rowHeight), cornerRadius = radius)
                        val enterCenter = Offset(currentX + enterWidth / 2f, currentY + rowHeight / 2f)
                        drawLine(color = actionTextColor, start = Offset(enterCenter.x + 2.dp.toPx(), enterCenter.y - 2.dp.toPx()), end = Offset(enterCenter.x + 2.dp.toPx(), enterCenter.y + 1.dp.toPx()), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(color = actionTextColor, start = Offset(enterCenter.x + 2.dp.toPx(), enterCenter.y + 1.dp.toPx()), end = Offset(enterCenter.x - 2.dp.toPx(), enterCenter.y + 1.dp.toPx()), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(color = actionTextColor, start = Offset(enterCenter.x, enterCenter.y - 1.dp.toPx()), end = Offset(enterCenter.x - 2.dp.toPx(), enterCenter.y + 1.dp.toPx()), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    }
            ) {
                // Selection indicator badge
                if (isSelected) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Active",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "ACTIVE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            // Theme Footer (Name & Options Dropdown Menu)
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                modifier = Modifier.fillMaxWidth()
            ) {
                var menuExpanded by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = themeName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val categoryLabel = when {
                            isCustom -> "Custom (මගේ) 👤"
                            !palette.backgroundImagePath.isNullOrEmpty() -> "Wallpaper 🖼️"
                            palette.background == android.graphics.Color.TRANSPARENT -> "Glass 🪟"
                            palette.dark -> "Dark 🌙"
                            else -> "Light ☀️"
                        }
                        Text(
                            text = categoryLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Theme Options",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Apply Theme (යොදන්න)") },
                                onClick = {
                                    menuExpanded = false
                                    onClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            )
                            if (onEdit != null) {
                                DropdownMenuItem(
                                    text = { Text("Edit Theme (වෙනස් කරන්න)") },
                                    onClick = {
                                        menuExpanded = false
                                        onEdit()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                    }
                                )
                            }
                            if (onDuplicate != null) {
                                DropdownMenuItem(
                                    text = { Text("Duplicate & Edit (පිටපත් කර වෙනස් කරන්න)") },
                                    onClick = {
                                        menuExpanded = false
                                        onDuplicate()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                                    }
                                )
                            }
                            if (onShare != null) {
                                DropdownMenuItem(
                                    text = { Text("Share Theme (බෙදාගන්න)") },
                                    onClick = {
                                        menuExpanded = false
                                        onShare()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Share, contentDescription = null)
                                    }
                                )
                            }
                            if (onDelete != null) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete (මකන්න)", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuExpanded = false
                                        onDelete()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

data class ToolbarItemConfig(
    val id: String,
    val titleSinhala: String,
    val titleEnglish: String,
    val description: String,
    val drawableRes: Int,
    val category: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarCustomizationScreen(
    prefs: KeyboardPreferences,
    onBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var refresh by remember { mutableStateOf(0) }
    var rawIcons by remember(refresh) { mutableStateOf(prefs.toolbarIcons) }
    val enabledIconsList = remember(rawIcons) {
        rawIcons.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    val allItems = remember {
        listOf(
            ToolbarItemConfig("lang_toggle", "භාෂා ස්විචය (සිං / EN)", "Language Switcher", "සිංහල හා ඉංග්‍රීසි අතර මාරු වන ලාංඡනය", R.drawable.ic_language, "Language & Input"),
            ToolbarItemConfig("font_studio", "ෆොන්ට් ස්ටූඩියෝ (Font Studio)", "Font Studio", "කීබෝඩ් එක තුලින්ම Font Styles තේරීම", R.drawable.ic_key_font_studio, "Actions Toolbar"),
            ToolbarItemConfig("calculator", "කැල්කියුලේටරය (Calculator)", "Calculator", "යතුරුපුවරුව තුලින්ම ගණනය කිරීම් සිදු කිරීම", R.drawable.ic_key_calc, "Actions Toolbar"),
            ToolbarItemConfig("emoji", "ඉමෝජි කෙටිමග (Emoji)", "Quick Emoji", "ඉහළ තීරුවේ ඉමෝජි පුවරු කෙටිමග පෙන්වීම", R.drawable.ic_key_emoji, "Quick Access"),
            ToolbarItemConfig("voice", "හඬ ආදානය (Voice)", "Voice Input", "හඬින් ටයිප් කිරීමේ කෙටිමග", R.drawable.ic_key_mic, "Quick Access"),
            ToolbarItemConfig("undo", "ආපසු ලබා ගැනීම (Undo)", "Undo", "වැරදීමකින් මැකී ගිය හෝ වෙනස් කළ පාඨ ආපසු ලබාගැනීම", R.drawable.ic_key_undo, "Actions Toolbar"),
            ToolbarItemConfig("redo", "යළි කිරීම (Redo)", "Redo", "ආපසු ලබාගත් වෙනස්කම් යළි ක්‍රියාත්මක කිරීම", R.drawable.ic_key_redo, "Actions Toolbar"),
            ToolbarItemConfig("astrology", "සිංහල ඉලක්කම් හා ලග්න", "Astrology & Numerals", "පැරණි සිංහල ඉලක්කම් (𑇡, 𑇢...) සහ ලග්න/නැකත් සංකේත (♈, ♉...)", R.drawable.ic_key_astrology, "Actions Toolbar"),
            ToolbarItemConfig("translate", "පරිවර්තකය (Translator)", "Translator", "සිංහල හා ඉංග්‍රීසි භාෂා අතර පරිවර්තනය", R.drawable.ic_key_translate, "Actions Toolbar"),
            ToolbarItemConfig("fm", "FM අකුරු පරිවර්තකය", "FM Font Converter", "යුනිකෝඩ් අකුරු FM Derana, FM Abhaya ආදී අකුරු වලට හැරවීම", R.drawable.ic_key_font, "Actions Toolbar"),
            ToolbarItemConfig("otp", "ස්වයංක්‍රීය OTP කේත", "Paste OTP", "SMS වලින් ලැබෙන OTP අංක ඉක්මනින් Paste කිරීම", R.drawable.ic_key_otp, "Actions Toolbar"),
            ToolbarItemConfig("clipboard", "ක්ලිප්බෝඩ් ඉතිහාසය", "Clipboard History", "පිටපත් කළ පාඨ සහ Pinned සටහන් කළමනාකරණය", R.drawable.ic_key_clipboard, "Quick Access"),
            ToolbarItemConfig("settings", "සැකසුම් කෙටිමග", "Settings", "යතුරුපුවරුවේ සිට සැකසුම් වෙත පිවිසීම", R.drawable.ic_key_settings, "Quick Access")
        )
    }

    val activeItems = remember(rawIcons) {
        enabledIconsList.mapNotNull { id -> allItems.find { it.id == id } }
    }

    val availableItems = remember(rawIcons) {
        allItems.filter { it.id !in enabledIconsList }
    }

    BackHandler {
        onBack()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Top Bar Icons", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            prefs.resetToolbarIcons()
                            rawIcons = prefs.toolbarIcons
                            refresh++
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset to Default",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live Preview & Interactive Drag Card
            item {
                ToolbarLivePreviewCard(
                    enabledIcons = enabledIconsList,
                    allItems = allItems,
                    onMoveLeft = { id ->
                        prefs.moveToolbarIcon(id, moveUp = true)
                        rawIcons = prefs.toolbarIcons
                        refresh++
                    },
                    onMoveRight = { id ->
                        prefs.moveToolbarIcon(id, moveUp = false)
                        rawIcons = prefs.toolbarIcons
                        refresh++
                    },
                    onSwap = { indexA, indexB ->
                        prefs.swapToolbarIcons(indexA, indexB)
                        rawIcons = prefs.toolbarIcons
                        refresh++
                    }
                )
            }

            // Quick Presets
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "QUICK PRESETS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray.copy(alpha = 0.8f),
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PresetChip(
                            label = "Default",
                            onClick = {
                                prefs.resetToolbarIcons()
                                rawIcons = prefs.toolbarIcons
                                refresh++
                            }
                        )
                        PresetChip(
                            label = "Left-to-Right",
                            onClick = {
                                prefs.toolbarIcons = "lang_toggle,font_studio,calculator,undo,redo,astrology,fm,translate,emoji,clipboard,settings"
                                rawIcons = prefs.toolbarIcons
                                refresh++
                            }
                        )
                        PresetChip(
                            label = "Right-Handed",
                            onClick = {
                                prefs.toolbarIcons = "settings,clipboard,otp,calculator,fm,translate,astrology,redo,undo,voice,emoji,font_studio,lang_toggle"
                                rawIcons = prefs.toolbarIcons
                                refresh++
                            }
                        )
                        PresetChip(
                            label = "Minimal (4)",
                            onClick = {
                                prefs.toolbarIcons = "lang_toggle,font_studio,calculator,emoji,clipboard,settings"
                                rawIcons = prefs.toolbarIcons
                                refresh++
                            }
                        )
                        PresetChip(
                            label = "All Icons",
                            onClick = {
                                prefs.toolbarIcons = "lang_toggle,font_studio,calculator,emoji,voice,undo,redo,astrology,translate,fm,otp,clipboard,settings"
                                rawIcons = prefs.toolbarIcons
                                refresh++
                            }
                        )
                    }
                }
            }

            // Section 1: Active Icons (Drag & Drop Reorderable)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ක්‍රියාකාරී අයිකන (ACTIVE ON TOP BAR)",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Drag ⠿ handle up/down to reorder, or use ▲ ▼",
                            color = Color.LightGray.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ) {
                        Text(
                            text = "${activeItems.size} in bar",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Active Items with drag and drop
            itemsIndexed(activeItems, key = { _, item -> item.id }) { index, itemConfig ->
                ToolbarActiveItemCard(
                    config = itemConfig,
                    orderIndex = index,
                    totalCount = activeItems.size,
                    onMoveUp = {
                        if (index > 0) {
                            prefs.swapToolbarIcons(index, index - 1)
                            rawIcons = prefs.toolbarIcons
                            refresh++
                        }
                    },
                    onMoveDown = {
                        if (index < activeItems.size - 1) {
                            prefs.swapToolbarIcons(index, index + 1)
                            rawIcons = prefs.toolbarIcons
                            refresh++
                        }
                    },
                    onRemove = {
                        prefs.setToolbarIconEnabled(itemConfig.id, false)
                        rawIcons = prefs.toolbarIcons
                        refresh++
                    },
                    onDragSwap = { deltaSteps ->
                        val targetIndex = (index + deltaSteps).coerceIn(0, activeItems.size - 1)
                        if (targetIndex != index) {
                            val mutableList = enabledIconsList.toMutableList()
                            val item = mutableList.removeAt(index)
                            mutableList.add(targetIndex, item)
                            prefs.reorderToolbarIcons(mutableList)
                            rawIcons = prefs.toolbarIcons
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            refresh++
                        }
                    }
                )
            }

            // Section 2: Available / Inactive Icons (Tap + to Add)
            if (availableItems.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 2.dp)
                    ) {
                        Text(
                            text = "තවත් අයිකන (AVAILABLE ICONS)",
                            color = Color.LightGray.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Tap '+ Add' to include them in the Top Bar",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                itemsIndexed(availableItems, key = { _, item -> item.id }) { _, itemConfig ->
                    ToolbarAvailableItemCard(
                        config = itemConfig,
                        onAdd = {
                            prefs.setToolbarIconEnabled(itemConfig.id, true)
                            rawIcons = prefs.toolbarIcons
                            refresh++
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ToolbarActiveItemCard(
    config: ToolbarItemConfig,
    orderIndex: Int,
    totalCount: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onDragSwap: (Int) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragDeltaY by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current

    val elevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        label = "elevation"
    )
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        label = "scale"
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) Color(0xFF1E2638) else Color.Black.copy(alpha = 0.4f)
        ),
        border = BorderStroke(
            width = if (isDragging) 1.5.dp else 1.dp,
            color = if (isDragging) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.15f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 2f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = if (isDragging) dragDeltaY else 0f
            }
            .shadow(elevation, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Drag Handle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDragging) Color(0xFF00D2FF).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f))
                    .pointerInput(orderIndex, totalCount) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                isDragging = true
                                dragDeltaY = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                isDragging = false
                                dragDeltaY = 0f
                            },
                            onDragCancel = {
                                isDragging = false
                                dragDeltaY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragDeltaY += dragAmount.y
                                val threshold = 140f
                                if (dragDeltaY > threshold) {
                                    onDragSwap(1)
                                    dragDeltaY -= threshold
                                } else if (dragDeltaY < -threshold) {
                                    onDragSwap(-1)
                                    dragDeltaY += threshold
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = if (isDragging) Color(0xFF00D2FF) else Color.LightGray.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Position Number Badge
            Surface(
                shape = CircleShape,
                color = Color(0xFF0052D4).copy(alpha = 0.4f),
                border = BorderStroke(1.dp, Color(0xFF00D2FF).copy(alpha = 0.5f)),
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${orderIndex + 1}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00D2FF)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Icon Graphic
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0052D4).copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(config.drawableRes),
                    contentDescription = null,
                    tint = Color(0xFF00D2FF),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Text Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.titleSinhala,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White
                )
                Text(
                    text = config.titleEnglish,
                    fontSize = 11.sp,
                    color = Color.LightGray.copy(alpha = 0.7f)
                )
            }

            // Up / Down Buttons for 1-tap positioning
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = orderIndex > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move Up",
                        tint = if (orderIndex > 0) Color.White else Color.Gray.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = orderIndex < totalCount - 1,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move Down",
                        tint = if (orderIndex < totalCount - 1) Color.White else Color.Gray.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Remove / Disable button
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove from bar",
                        tint = Color.Red.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ToolbarAvailableItemCard(
    config: ToolbarItemConfig,
    onAdd: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.2f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Inactive Icon Badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(config.drawableRes),
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.titleSinhala,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = Color.LightGray
                )
                Text(
                    text = config.titleEnglish,
                    fontSize = 11.sp,
                    color = Color.DarkGray
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // + Add Pill Button
            FilledTonalButton(
                onClick = onAdd,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF0072FF).copy(alpha = 0.25f),
                    contentColor = Color(0xFF00D2FF)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Add", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun ToolbarLivePreviewCard(
    enabledIcons: List<String>,
    allItems: List<ToolbarItemConfig>,
    onMoveLeft: (String) -> Unit,
    onMoveRight: (String) -> Unit,
    onSwap: (Int, Int) -> Unit
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val currentSelected = selectedId?.takeIf { it in enabledIcons } ?: enabledIcons.firstOrNull()
    val selectedIndex = currentSelected?.let { enabledIcons.indexOf(it) } ?: -1
    val selectedItem = allItems.find { it.id == currentSelected }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Live Top Bar Preview",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Icons distribute evenly across top bar",
                        fontSize = 11.sp,
                        color = Color.LightGray.copy(alpha = 0.8f)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0052D4).copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, Color(0xFF00D2FF).copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "${enabledIcons.size} Active",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF00D2FF),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Simulated Top Bar Suggestion Rail with Interactive Horizontal Drag / Swap & Tap
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141A24))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    enabledIcons.forEachIndexed { index, id ->
                        val itemConfig = allItems.find { it.id == id }
                        val isSelected = id == currentSelected
                        var dragAcc by remember { mutableFloatStateOf(0f) }

                        if (itemConfig != null) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { selectedId = id }
                                    .pointerInput(id, enabledIcons.size) {
                                        detectHorizontalDragGestures(
                                            onDragStart = {
                                                dragAcc = 0f
                                                selectedId = id
                                            },
                                            onDragEnd = { dragAcc = 0f },
                                            onDragCancel = { dragAcc = 0f },
                                            onHorizontalDrag = { _, dragAmount ->
                                                dragAcc += dragAmount
                                                val threshold = 36f
                                                if (dragAcc > threshold && index < enabledIcons.size - 1) {
                                                    onSwap(index, index + 1)
                                                    dragAcc = 0f
                                                } else if (dragAcc < -threshold && index > 0) {
                                                    onSwap(index, index - 1)
                                                    dragAcc = 0f
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = when {
                                        isSelected -> Color(0xFF0052D4).copy(alpha = 0.5f)
                                        else -> Color.White.copy(alpha = 0.12f)
                                    },
                                    border = BorderStroke(
                                        if (isSelected) 1.5.dp else 1.dp,
                                        if (isSelected) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.25f)
                                    ),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        if (id == "lang_toggle") {
                                            Text("සිං", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        } else {
                                            Icon(
                                                painter = painterResource(itemConfig.drawableRes),
                                                contentDescription = null,
                                                tint = if (isSelected) Color(0xFF00D2FF) else Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Shift Controls for Selected Icon
            if (currentSelected != null && selectedItem != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "#${selectedIndex + 1}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF00D2FF),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = selectedItem.titleSinhala,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color.White,
                            maxLines = 1
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { onMoveLeft(currentSelected) },
                            enabled = selectedIndex > 0,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowLeft,
                                contentDescription = "Move Left",
                                tint = if (selectedIndex > 0) Color.White else Color.Gray.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { onMoveRight(currentSelected) },
                            enabled = selectedIndex < enabledIcons.size - 1,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = "Move Right",
                                tint = if (selectedIndex < enabledIcons.size - 1) Color.White else Color.Gray.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PresetChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = Color.White
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var sourceLang by remember { mutableStateOf("si") } // "si" or "en"
    var targetLang by remember { mutableStateOf("en") } // "en" or "si"
    var inputText by remember { mutableStateOf("") }
    var translatedText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("All") }

    val coroutineScope = rememberCoroutineScope()
    var ttsInstance by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        var tts: android.speech.tts.TextToSpeech? = null
        try {
            tts = android.speech.tts.TextToSpeech(context) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    tts?.language = java.util.Locale.US
                }
            }
            ttsInstance = tts
        } catch (e: Exception) {
            // TTS not available
        }
        onDispose {
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (e: Exception) {}
        }
    }

    // Auto-translate with debounce
    LaunchedEffect(inputText, sourceLang, targetLang) {
        val trimmed = inputText.trim()
        if (trimmed.isEmpty()) {
            translatedText = ""
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        delay(300) // Debounce
        val result = org.slashboard.ime.translator.TranslatorEngine.translate(trimmed, sourceLang, targetLang)
        if (result.isSuccess) {
            translatedText = result.getOrNull().orEmpty()
        } else {
            translatedText = "Translation unavailable offline"
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Sinhala ⇄ English Translator",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "සිංහල ⇄ ඉංග්‍රීසි පරිවර්තකය",
                            fontSize = 12.sp,
                            color = Color(0xFF38BDF8)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        containerColor = Color(0xFF0B0F19)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Language Direction Switcher Bar
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Source Lang
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFF0284C7)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (sourceLang == "si") "සිංහල" else "English",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF38BDF8)
                                )
                                Text(
                                    text = if (sourceLang == "si") "Sinhala" else "ඉංග්‍රීසි",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // Swap Button
                        IconButton(
                            onClick = {
                                val temp = sourceLang
                                sourceLang = targetLang
                                targetLang = temp
                                if (translatedText.isNotEmpty() && !translatedText.startsWith("Translation unavailable")) {
                                    val currentRes = translatedText
                                    inputText = currentRes
                                }
                            },
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(44.dp)
                                .background(Color(0xFF334155), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Swap Languages",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Target Lang
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFF10B981)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (targetLang == "si") "සිංහල" else "English",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF34D399)
                                )
                                Text(
                                    text = if (targetLang == "si") "Sinhala" else "ඉංග්‍රීසි",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Input Box Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (sourceLang == "si") "සිංහලෙන් ලියන්න / අලවන්න:" else "Enter English text:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Paste Button
                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = cm.primaryClip
                                        if (clip != null && clip.itemCount > 0) {
                                            val text = clip.getItemAt(0).text?.toString().orEmpty()
                                            if (text.isNotEmpty()) inputText = text
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                if (inputText.isNotEmpty()) {
                                    IconButton(
                                        onClick = { inputText = ""; translatedText = "" },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    text = if (sourceLang == "si") "වචන හෝ වාක්‍ය මෙහි ටයිප් කරන්න (Singlish ද ක්‍රියාත්මකයි)..." else "Type words or sentences to translate...",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 14.sp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            minLines = 3,
                            maxLines = 6,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF475569),
                                cursorColor = Color(0xFF38BDF8)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${inputText.length} characters",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Translation Result Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0F243A)
                    ),
                    border = BorderStroke(1.5.dp, Color(0xFF0284C7))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (targetLang == "si") "සිංහල පරිවර්තනය (Sinhala)" else "English Translation",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                            if (translatedText.isNotEmpty()) {
                                Row {
                                    // Speak Button
                                    if (ttsInstance != null) {
                                        IconButton(
                                            onClick = {
                                                if (targetLang == "en") {
                                                    ttsInstance?.language = java.util.Locale.US
                                                    ttsInstance?.speak(translatedText, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "tts1")
                                                } else {
                                                    android.widget.Toast.makeText(context, translatedText, android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VolumeUp,
                                                contentDescription = "Speak",
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // Share Button
                                    IconButton(
                                        onClick = {
                                            val sendIntent = android.content.Intent().apply {
                                                action = android.content.Intent.ACTION_SEND
                                                putExtra(android.content.Intent.EXTRA_TEXT, translatedText)
                                                type = "text/plain"
                                            }
                                            context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Translation"))
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share",
                                            tint = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Copy Button
                                    IconButton(
                                        onClick = {
                                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            cm.setPrimaryClip(android.content.ClipData.newPlainText("Translation", translatedText))
                                            android.widget.Toast.makeText(context, "පිටපත් කරගන්නා ලදී (Copied to clipboard)", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy",
                                            tint = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        SelectionContainer {
                            Text(
                                text = if (translatedText.isEmpty()) {
                                    if (inputText.isEmpty()) "පරිවර්තනය මෙහි දිස්වේ (Translation will appear here)" else "පරිවර්තනය වෙමින් පවතී..."
                                } else translatedText,
                                fontSize = 16.sp,
                                fontWeight = if (translatedText.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (translatedText.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.4f),
                                lineHeight = 22.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // 4. Quick Phrasebook & Dictionary Section
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "📖 Quick Phrasebook & Vocabulary (නිතර භාවිත වන වාක්‍ය)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    // Categories
                    val categories = listOf("All", "Greetings", "Conversation", "Shopping", "Help", "Travel")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSel = selectedCategory == cat
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSel) Color(0xFF0284C7) else Color(0xFF1E293B),
                                border = BorderStroke(1.dp, if (isSel) Color(0xFF38BDF8) else Color(0xFF334155)),
                                modifier = Modifier.clickable { selectedCategory = cat }
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Color.White else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val phrases = org.slashboard.ime.translator.TranslatorEngine.samplePhrases.filter {
                        selectedCategory == "All" || it.category.equals(selectedCategory, ignoreCase = true)
                    }

                    phrases.forEach { phrase ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    if (sourceLang == "si") {
                                        inputText = phrase.sinhala
                                    } else {
                                        inputText = phrase.english
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = phrase.sinhala,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8)
                                    )
                                    Text(
                                        text = phrase.english,
                                        fontSize = 12.5.sp,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = "Load phrase",
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

