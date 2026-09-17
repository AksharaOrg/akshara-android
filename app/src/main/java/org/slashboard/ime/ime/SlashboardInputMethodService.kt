package org.slashboard.ime.ime

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slashboard.ime.CrashLogger
import org.slashboard.ime.data.Candidate
import org.slashboard.ime.data.ClipboardHistoryStore
import org.slashboard.ime.data.EmojiRepository
import org.slashboard.ime.data.LocalLearningStore
import org.slashboard.ime.data.PredictionRepository
import org.slashboard.ime.data.SnippetManager
import org.slashboard.ime.engine.EnglishPredictionEngine
import org.slashboard.ime.engine.LiveUnitConverter
import org.slashboard.ime.engine.SinglishParagraphConverter
import org.slashboard.ime.engine.SinhalaNumberToWords
import org.slashboard.ime.engine.SlashboardEasterEgg
import org.slashboard.ime.data.SlashboardSyncWorker
import org.slashboard.ime.engine.CompositionSession
import org.slashboard.ime.engine.FmConverter
import org.slashboard.ime.engine.GraphemeDelete
import org.slashboard.ime.engine.InputMode
import org.slashboard.ime.engine.SinhalaEngine
import org.slashboard.ime.engine.SinhalaPillamCorrector
import org.slashboard.ime.settings.KeyboardPreferences
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class SlashboardInputMethodService : InputMethodService(), KeyboardActions {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefs: KeyboardPreferences
    private lateinit var keyboard: KeyboardView
    private lateinit var snippetManager: SnippetManager
    private var learning: LocalLearningStore? = null
    private var prediction: PredictionRepository? = null
    private var englishPrediction: EnglishPredictionEngine? = null
    private var emoji: EmojiRepository? = null
    private var clipboardHistory: ClipboardHistoryStore? = null
    private val composition = CompositionSession()
    private val slsSource = StringBuilder()
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var predictionTask: Future<*>? = null
    private var generation = 0
    private var restricted = false
    private var lastSelectionEnd = -1
    private var previousCommittedWord: String? = null
    private var previousEarlierCommittedWord: String? = null
    private var activeEnglishPrefix: String? = null
    private var recentEmoji = mutableListOf<String>()
    private var editorLayout = EditorLayout.TEXT
    private var voiceInputManager: VoiceInputManager? = null
    private var precedingDirty = true
    private var cachedPreceding = emptyList<String>()
    private var deleteAnchor = -1
    private var deleteLength = 0
    private val undoRedoManager = UndoRedoManager()
    private var activeCorrection: String? = null
    private var activeMathResult: String? = null
    private var activeUnitResult: String? = null
    private var activeUnitQuery: String? = null
    private var activeNumberWords: String? = null
    private var activeNumberDigits: String? = null
    private var activeSnippetPhrase: String? = null
    private var activeSnippetShortcut: String? = null
    private var detectedOtpCode: String? = null
    private var lastSpaceTime = 0L

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureClipboard()
        if (::keyboard.isInitialized && clipboardHistory != null) {
            keyboard.setClipboardItems(clipboardHistory!!.items(), clipboardHistory!!.pinnedItems())
        }
    }

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == KeyboardPreferences.TOP_ROW || key == "theme" || key == "one_handed" || key == "key_spacing" || key == "keyboard_size" || key == "high_contrast" || key == "keyboard_font" || key == "keyboard_font_scale" || key == "mode") {
            if (::keyboard.isInitialized) {
                keyboard.reloadPreferences(prefs)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        CrashLogger.init(this)
        runCatching {
            com.vanniktech.emoji.EmojiManager.install(com.vanniktech.emoji.ios.IosEmojiProvider())
        }

        prefs = KeyboardPreferences(this)
        prefs.store.registerOnSharedPreferenceChangeListener(prefChangeListener)
        snippetManager = SnippetManager(this)
        recentEmoji = prefs.recentEmojis.toMutableList()
        org.slashboard.ime.sound.KeySoundPlayer.getInstance(this)
        
        // Schedule update checks every 30 minutes
        org.slashboard.ime.update.UpdateCheckWorker.schedulePeriodicCheck(this)
        org.slashboard.ime.update.UpdateCheckWorker.checkNow(this)

        // In addition, periodically check every 30 minutes while keyboard service is alive
        serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(30L * 60L * 1000L) // 30 minutes
                try {
                    org.slashboard.ime.update.UpdateCheckWorker.performCheck(applicationContext)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        org.slashboard.ime.translator.TranslatorEngine.init(this)

        voiceInputManager = VoiceInputManager(
            context = this,
            onVoiceResult = { text ->
                currentInputConnection?.commitText(text + " ", 1)
                updateSuggestions()
            },
            onPartialResult = { text ->
                currentInputConnection?.setComposingText(text, 1)
            },
            onError = { error ->
                currentInputConnection?.finishComposingText()
                if (error == android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    val intent = android.content.Intent(this, org.slashboard.ime.settings.PermissionActivity::class.java).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    android.widget.Toast.makeText(this, "Please grant microphone permission", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onReady = {
                Toast.makeText(this, "Listening...", Toast.LENGTH_SHORT).show()
            }
        )

        try {
            val workRequest = PeriodicWorkRequest.Builder(SlashboardSyncWorker::class.java, 1L, TimeUnit.DAYS).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "SlashboardBackgroundSync",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            val initialRequest = OneTimeWorkRequest.Builder(SlashboardSyncWorker::class.java).build()
            WorkManager.getInstance(this).enqueueUniqueWork(
                "SlashboardInitialSync",
                ExistingWorkPolicy.KEEP,
                initialRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        clipboardHistory = ClipboardHistoryStore(this)

        serviceScope.launch(Dispatchers.IO) {
            val localLearning = LocalLearningStore(this@SlashboardInputMethodService)
            val predictionRepo = PredictionRepository(this@SlashboardInputMethodService, localLearning)
            val englishEngine = EnglishPredictionEngine(this@SlashboardInputMethodService, localLearning)
            val emojiRepo = EmojiRepository(this@SlashboardInputMethodService)
            val clipboardStore = ClipboardHistoryStore(this@SlashboardInputMethodService)
            predictionRepo.warmup()
            englishEngine.warmup()
            learning = localLearning
            prediction = predictionRepo
            englishPrediction = englishEngine
            emoji = emojiRepo
            clipboardHistory = clipboardStore
            withContext(Dispatchers.Main) {
                if (::keyboard.isInitialized) {
                    keyboard.updateRepositories(emojiRepo, clipboardStore)
                    keyboard.setClipboardItems(clipboardStore.items(), clipboardStore.pinnedItems())
                    updateSuggestions()
                }
            }
        }
    }

    override fun onCreateInputView(): View {
        keyboard = KeyboardView(this, this, prefs, emoji, clipboardHistory).apply {
            voiceInputManager?.let { setVoiceManager(it) }
        }
        return keyboard
    }

    override fun onConfigureWindow(win: Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly)
        win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        win.decorView.setBackgroundColor(Color.TRANSPARENT)
        win.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        clearLocalCompositionState()
        val isStrictPwd = isStrictPassword(attribute)
        val isSensitive = prefs.securePasswordMode && isPasswordOrSensitive(attribute, true)
        restricted = isRestrictedEditor(attribute) || isStrictPwd || isSensitive
        lastSelectionEnd = attribute?.initialSelEnd ?: -1
        precedingDirty = true
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        clearLocalCompositionState()
        prefs = KeyboardPreferences(this)
        prefs.reload()
        
        if (::keyboard.isInitialized) {
            keyboard.appPackageName = info?.packageName
            keyboard.reloadPreferences(prefs)
        }
        val isStrictPwd = isStrictPassword(info)
        val isSensitive = prefs.securePasswordMode && isPasswordOrSensitive(info, true)
        val isRestricted = isRestrictedEditor(info)
        restricted = isRestricted || isStrictPwd || isSensitive
        editorLayout = editorLayout(info)

        // Layout and editor preparation (preserves user-chosen language across sessions)
        if (prefs.appLayoutMemory && info != null) {
            val pkg = info.packageName?.lowercase().orEmpty()
            if (isTerminalOrDevApp(pkg) && editorLayout == EditorLayout.TEXT) {
                editorLayout = EditorLayout.ASCII
            }
        }

        window?.window?.let { win ->
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            win.decorView.setBackgroundColor(Color.TRANSPARENT)
            win.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
            win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            (keyboard.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
            win.findViewById<View>(android.R.id.inputArea)?.setBackgroundColor(Color.TRANSPARENT)
        }
        keyboard.configure(prefs.mode, offerSystemSwitch(), enterLabel(info), editorLayout)
        val incognito = isStrictPwd || isSensitive
        keyboard.setIncognito(incognito)
        keyboard.learningEnabled = !isRestricted && !incognito && editorLayout == EditorLayout.TEXT
        checkOtp()
        if (prefs.clipboardHistory && !incognito) captureClipboard()
        clipboardHistory?.let { keyboard.setClipboardItems(it.items(), it.pinnedItems()) }
        if (incognito) {
            stopClipboardListener()
        } else {
            listenForClipboard()
        }
        keyboard.setRecentEmoji(recentEmoji)
        updateSuggestions()
    }

    override fun onFinishInput() {
        deleteAnchor = -1
        deleteLength = 0
        clearLocalCompositionState()
        super.onFinishInput()
    }

    override fun onDestroy() {
        runCatching { prefs.store.unregisterOnSharedPreferenceChangeListener(prefChangeListener) }
        voiceInputManager?.destroy()
        stopClipboardListener()
        serviceScope.cancel()
        executor.shutdown()
        super.onDestroy()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopClipboardListener()
        clearLocalCompositionState()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (composition.active) {
            if (candidatesStart < 0 || candidatesEnd < 0 || newSelEnd < candidatesStart || newSelEnd > candidatesEnd) {
                clearLocalCompositionState()
            }
        }
        lastSelectionEnd = newSelEnd
    }

    override fun onCharacter(value: String) {
        runCatching {
            val ic = currentInputConnection
            val selectedText = ic?.getSelectedText(0)?.toString()
            
            // Smart Bracket & Quote Wrapping
            if (!selectedText.isNullOrEmpty()) {
                val pair = when (value) {
                    "(" -> ")"
                    "[" -> "]"
                    "{" -> "}"
                    "<" -> ">"
                    "\"" -> "\""
                    "'" -> "'"
                    else -> null
                }
                if (pair != null) {
                    ic.commitText("$value$selectedText$pair", 1)
                    return
                }
            }
            
            val isPassword = isStrictPassword(currentInputEditorInfo) || (prefs.securePasswordMode && isPasswordOrSensitive(currentInputEditorInfo, true))
            if (isPassword || prefs.useEnglish) {
                commitComposition()
                val fontTransformed = if (prefs.useEnglish && prefs.keyboardFont != "default") {
                    org.slashboard.ime.settings.font.CustomFontManager.transformText(this, prefs.keyboardFont, value)
                } else {
                    value
                }
                if (!isPassword && value == "=") {
                    commitEqualOrCalculated("=")
                } else {
                    currentInputConnection?.commitText(fontTransformed, 1)
                }
                if (value.codePoints().anyMatch { it > 0x1F000 }) {
                    rememberEmoji(value)
                }
            } else if (value.length == 1 && Character.isLetter(value[0]) && value[0] < '\u0080') {
                val rendered = composition.type(value, prefs.mode)
                val corrected = SinhalaPillamCorrector.correctText(rendered)
                currentInputConnection?.setComposingText(corrected, 1)
            } else {
                commitComposition()
                val ic = currentInputConnection
                val preceding = ic?.getTextBeforeCursor(4, 0)?.toString().orEmpty()
                val pillamCorrection = SinhalaPillamCorrector.handleCharacterInput(preceding, value)
                if (pillamCorrection != null && ic != null) {
                    ic.deleteSurroundingText(pillamCorrection.deleteCount, 0)
                    ic.commitText(pillamCorrection.replacement, 1)
                } else if (!isPassword && value == "=") {
                    commitEqualOrCalculated("=")
                } else {
                    ic?.commitText(value, 1)
                }
                if (value.codePoints().anyMatch { it > 0x1F000 }) {
                    rememberEmoji(value)
                }
            }
            precedingDirty = true
            updateSuggestions()
        }
    }

    private fun commitEqualOrCalculated(value: String) {
        val ic = currentInputConnection ?: return
        commitComposition()
        val beforeForMath = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        val mathEval = MathEvaluator.evaluateTrailingExpression(beforeForMath)
        if (mathEval != null) {
            if (beforeForMath.endsWith("=")) {
                ic.commitText(mathEval.formattedResult, 1)
            } else {
                ic.commitText("=${mathEval.formattedResult}", 1)
            }
        } else {
            ic.commitText(value, 1)
        }
    }

    override fun onPasteText(text: String) {
        runCatching {
            val ic = currentInputConnection ?: return
            commitComposition()
            ic.commitText(text, 1)
            precedingDirty = true
            updateSuggestions()
        }
    }

    override fun onBackspace(word: Boolean) {
        runCatching {
            val ic = currentInputConnection
            val selected = ic?.getSelectedText(0)?.toString()
            val isSensitive = isStrictPassword(currentInputEditorInfo) || (prefs.securePasswordMode && isPasswordOrSensitive(currentInputEditorInfo, true))
            if (!selected.isNullOrEmpty()) {
                commitComposition()
                if (!isSensitive) {
                    undoRedoManager.recordDeletedText(selected)
                }
                ic.commitText("", 1)
                precedingDirty = true
                updateSuggestions()
                return
            }
            if (composition.active) {
                val rendered = composition.backspace(prefs.mode)
                if (rendered.isEmpty()) {
                    currentInputConnection?.setComposingText("", 1)
                    currentInputConnection?.finishComposingText()
                } else {
                    currentInputConnection?.setComposingText(rendered, 1)
                }
            } else {
                deleteFromHost(word)
                precedingDirty = true
            }
            updateSuggestions()
        }
    }

    override fun onSpace() {
        runCatching {
            val ic = currentInputConnection
            val now = System.currentTimeMillis()
            
            // Dual-Language Auto-Punctuation (Double space -> Period)
            if (now - lastSpaceTime < 500 && !composition.active) {
                val beforeSpace = ic?.getTextBeforeCursor(2, 0)?.toString()
                if (beforeSpace?.endsWith(" ") == true && !beforeSpace.startsWith(" ") && !beforeSpace.startsWith(".")) {
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                    lastSpaceTime = 0L // reset
                    return@runCatching
                }
            }
            lastSpaceTime = now

            val before = ic?.getTextBeforeCursor(64, 0)?.toString().orEmpty()
            val engCandidate = activeEnglishPrefix ?: if (prefs.useEnglish) Regex("([A-Za-z0-9'’]+)$").find(before)?.value.orEmpty() else null
            if (!engCandidate.isNullOrEmpty()) {
                learnEnglish(engCandidate)
                ic?.commitText(" ", 1)
                activeCorrection = null
                activeEnglishPrefix = null
                precedingDirty = true
                updateSuggestions()
                return@runCatching
            }

            val composed = commitComposition()
            val word = if (!composed.isNullOrBlank()) {
                composed
            } else {
                Regex("([\\p{L}\\p{M}\u200D\u200C]+)$").find(before)?.value
            }
            activeCorrection = null
            
            // Basic Redundancy Checker
            val checkText = ic?.getTextBeforeCursor(20, 0)?.toString()
            if (checkText != null && checkText.endsWith("නැවත නැවතත්")) {
                ic.deleteSurroundingText(11, 0)
                ic.commitText("නැවතත්", 1)
            } else if (checkText != null && checkText.endsWith("නැවත වරක්")) {
                ic.deleteSurroundingText(10, 0)
                ic.commitText("නැවතත්", 1)
            }
            
            ic?.commitText(" ", 1)
            if (!word.isNullOrBlank()) {
                learn(word)
            }
            precedingDirty = true
            updateSuggestions()
        }
    }

    override fun onEnter() {
        runCatching {
            val ic = currentInputConnection
            val before = ic?.getTextBeforeCursor(64, 0)?.toString().orEmpty()
            val engCandidate = activeEnglishPrefix ?: if (prefs.useEnglish) Regex("([A-Za-z0-9'’]+)$").find(before)?.value.orEmpty() else null
            if (!engCandidate.isNullOrEmpty()) {
                learnEnglish(engCandidate)
                activeCorrection = null
                activeEnglishPrefix = null
            } else {
                val composed = commitComposition()
                val word = if (!composed.isNullOrBlank()) {
                    composed
                } else {
                    Regex("([\\p{L}\\p{M}\u200D\u200C]+)$").find(before)?.value
                }
                if (!word.isNullOrBlank()) {
                    learn(word)
                }
            }
            clearLocalCompositionState()
            val info = currentInputEditorInfo
            val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
            val isMultiLine = (info?.inputType ?: 0) and EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE != 0
            val noEnterAction = (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
            if (!isMultiLine && !noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                currentInputConnection?.performEditorAction(action)
                if (action == EditorInfo.IME_ACTION_DONE) {
                    requestHideSelf(0)
                }
            } else {
                val fullBefore = currentInputConnection?.getTextBeforeCursor(500, 0)?.toString().orEmpty()
                val currentLine = fullBefore.substringAfterLast('\n')
                
                val numMatch = Regex("^(\\s*)(\\d+)\\.\\s+").find(currentLine)
                val dashMatch = Regex("^(\\s*)[\\-•]\\s+").find(currentLine)
                
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                
                if (numMatch != null && numMatch.value == currentLine) {
                    // Empty list item: delete it (undo bullet)
                    currentInputConnection?.deleteSurroundingText(currentLine.length + 1, 0) // +1 for the newline just added
                } else if (dashMatch != null && dashMatch.value == currentLine) {
                    currentInputConnection?.deleteSurroundingText(currentLine.length + 1, 0)
                } else if (numMatch != null) {
                    val indent = numMatch.groupValues[1]
                    val nextNum = numMatch.groupValues[2].toIntOrNull()?.plus(1) ?: 2
                    currentInputConnection?.commitText("$indent$nextNum. ", 1)
                } else if (dashMatch != null) {
                    val prefix = dashMatch.value
                    currentInputConnection?.commitText(prefix, 1)
                }
            }
            clearLocalCompositionState()
            updateSuggestions()
        }
    }

    override fun onCandidate(value: String) {
        runCatching {
            feedback()
            if (value.startsWith("= ") || (activeMathResult != null && value == activeMathResult)) {
                val ic = currentInputConnection
                val before = ic?.getTextBeforeCursor(64, 0)?.toString().orEmpty()
                val ans = if (value.startsWith("= ")) value.removePrefix("= ").trim() else value
                if (before.endsWith("=")) {
                    ic?.commitText(ans + " ", 1)
                } else {
                    ic?.commitText("=$ans ", 1)
                }
                activeMathResult = null
                precedingDirty = true
                updateSuggestions()
                return@runCatching
            }

            if (activeUnitResult != null && (value == activeUnitResult || value == "= $activeUnitResult")) {
                val ic = currentInputConnection
                val ans = activeUnitResult!!
                val before = ic?.getTextBeforeCursor(64, 0)?.toString().orEmpty()
                val q = activeUnitQuery
                if (q != null && before.endsWith(q)) {
                    ic?.deleteSurroundingText(q.length, 0)
                }
                ic?.commitText("$ans ", 1)
                activeUnitResult = null
                activeUnitQuery = null
                precedingDirty = true
                updateSuggestions()
                return@runCatching
            }

            if (activeNumberWords != null && value == activeNumberWords) {
                val ic = currentInputConnection
                val digits = activeNumberDigits
                val before = ic?.getTextBeforeCursor(64, 0)?.toString().orEmpty()
                if (digits != null && before.endsWith(digits)) {
                    ic?.deleteSurroundingText(digits.length, 0)
                }
                ic?.commitText("$value ", 1)
                activeNumberWords = null
                activeNumberDigits = null
                precedingDirty = true
                updateSuggestions()
                return@runCatching
            }

            if (activeSnippetPhrase != null && value == activeSnippetPhrase) {
                val ic = currentInputConnection
                val sc = activeSnippetShortcut
                val before = ic?.getTextBeforeCursor(64, 0)?.toString().orEmpty()
                if (sc != null && before.endsWith(sc)) {
                    ic?.deleteSurroundingText(sc.length, 0)
                }
                ic?.commitText("$value ", 1)
                activeSnippetPhrase = null
                activeSnippetShortcut = null
                precedingDirty = true
                updateSuggestions()
                return@runCatching
            }

            if (value == SlashboardEasterEgg.TRUE_NAME_DISPLAY) {
                currentInputConnection?.setComposingText(SlashboardEasterEgg.TRUE_NAME_INSERT, 1)
                currentInputConnection?.finishComposingText()
                composition.clear()
                slsSource.clear()
                updateSuggestions()
                return@runCatching
            }

            val isEngCandidate = prefs.useEnglish || activeEnglishPrefix != null || (value.isNotEmpty() && value.all { (it in 'a'..'z') || (it in 'A'..'Z') || it == '\'' || it == '’' || it == '-' })
            if (isEngCandidate) {
                val ic = currentInputConnection
                if (value.codePoints().anyMatch { it > 0x1F000 }) {
                    ic?.commitText(value + " ", 1)
                    rememberEmoji(value)
                    precedingDirty = true
                    updateSuggestions()
                    return@runCatching
                }
                val before = ic?.getTextBeforeCursor(64, 0)?.toString().orEmpty()
                val prefix = activeEnglishPrefix ?: Regex("([A-Za-z0-9'’]+)$").find(before)?.value.orEmpty()
                if (prefix.isNotEmpty() && before.endsWith(prefix)) {
                    ic?.deleteSurroundingText(prefix.length, 0)
                }
                ic?.commitText(value, 1)
                ic?.commitText(" ", 1)
                learnEnglish(value)
                activeCorrection = null
                activeEnglishPrefix = null
                precedingDirty = true
                updateSuggestions()
                return@runCatching
            }

            if (value.codePoints().anyMatch { it > 0x1F000 }) {
                currentInputConnection?.commitText(value + " ", 1)
                rememberEmoji(value)
                precedingDirty = true
                updateSuggestions()
                return@runCatching
            }

            if (composition.active) {
                currentInputConnection?.setComposingText(value, 1)
                currentInputConnection?.finishComposingText()
                composition.clear()
                slsSource.clear()
            } else {
                val ic = currentInputConnection
                val before = ic?.getTextBeforeCursor(64, 0)?.toString().orEmpty()
                val sinPrefix = Regex("([\\p{L}\\p{M}\u200D\u200C]+)$").find(before)?.value.orEmpty()
                if (sinPrefix.isNotEmpty() && before.endsWith(sinPrefix)) {
                    ic?.deleteSurroundingText(sinPrefix.length, 0)
                }
                ic?.commitText(value, 1)
            }
            learn(value)
            currentInputConnection?.commitText(" ", 1)
            precedingDirty = true
            updateSuggestions()
        }
    }

    override fun onGlobe() {
        runCatching {
            commitComposition()
            
            val ic = currentInputConnection
            if (ic != null) {
                val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
                if (before.isNotEmpty()) {
                    if (before.endsWith("  ")) {
                        ic.deleteSurroundingText(1, 0)
                    } else if (!before.endsWith(" ") && !before.endsWith("\n")) {
                        ic.commitText(" ", 1)
                    }
                }
            }
            
            precedingDirty = true
            updateSuggestions()
        }
    }

    override fun onModeRequested(mode: InputMode) {
        runCatching {
            commitComposition()
            prefs.mode = mode
            if (::keyboard.isInitialized) {
                keyboard.configure(mode, offerSystemSwitch(), enterLabel(currentInputEditorInfo), editorLayout)
            }
        }
    }

    override fun onHide() {
        runCatching {
            commitComposition()
            requestHideSelf(0)
        }
    }

    override fun onToolbarAction(action: String) {
        val ic = currentInputConnection ?: return
        when (action) {
            "whatsapp_quick" -> {
                feedback()
                val text = ic.getTextBeforeCursor(20, 0)?.toString()?.trim() ?: ""
                val number = Regex("\\+?[0-9]{9,15}").find(text)?.value
                if (number != null) {
                    runCatching {
                        val uri = android.net.Uri.parse("https://wa.me/${number.replace("+", "")}")
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        requestHideSelf(0)
                    }.onFailure {
                        Toast.makeText(this, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Please type a phone number first", Toast.LENGTH_SHORT).show()
                }
            }
            "dev_mode" -> {
                feedback()
                val keys = arrayOf("Tab" to KeyEvent.KEYCODE_TAB, "Esc" to KeyEvent.KEYCODE_ESCAPE, "Up" to KeyEvent.KEYCODE_DPAD_UP, "Down" to KeyEvent.KEYCODE_DPAD_DOWN, "Left" to KeyEvent.KEYCODE_DPAD_LEFT, "Right" to KeyEvent.KEYCODE_DPAD_RIGHT)
                val items = keys.map { it.first }.toTypedArray()
                val dialog = android.app.AlertDialog.Builder(this)
                    .setTitle("Developer Keys")
                    .setItems(items) { _, which ->
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keys[which].second))
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keys[which].second))
                    }
                    .create()
                val window = dialog.window
                if (window != null) {
                    val token = (keyboard?.parent as? View)?.windowToken
                    window.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
                    val lp = window.attributes
                    lp.token = token
                    window.attributes = lp
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                }
                dialog.show()
            }
            "format" -> {
                feedback()
                val sel = ic.getSelectedText(0)?.toString()
                if (sel.isNullOrEmpty()) {
                    Toast.makeText(this, "Select text to format", Toast.LENGTH_SHORT).show()
                } else {
                    val formats = arrayOf("Bold (*text*)", "Italic (_text_)", "Strikethrough (~text~)", "Code (`text`)")
                    val dialog = android.app.AlertDialog.Builder(this)
                        .setTitle("Format Text")
                        .setItems(formats) { _, which ->
                            val wrapper = when (which) {
                                0 -> "*"
                                1 -> "_"
                                2 -> "~"
                                3 -> "`"
                                else -> ""
                            }
                            ic.commitText("$wrapper$sel$wrapper", 1)
                        }
                        .create()
                    val window = dialog.window
                    if (window != null) {
                        val token = (keyboard?.parent as? View)?.windowToken
                        window.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
                        val lp = window.attributes
                        lp.token = token
                        window.attributes = lp
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                    }
                    dialog.show()
                }
            }
            "case_convert" -> {
                feedback()
                val sel = ic.getSelectedText(0)?.toString()
                if (sel.isNullOrEmpty()) {
                    Toast.makeText(this, "Select text to convert case", Toast.LENGTH_SHORT).show()
                } else {
                    val converted = when {
                        sel == sel.uppercase() -> sel.lowercase()
                        sel == sel.lowercase() -> sel.split(" ").joinToString(" ") { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() } }
                        else -> sel.uppercase()
                    }
                    ic.commitText(converted, 1)
                }
            }
            "undo" -> {
                feedback()
                undoRedoManager.performUndo(ic) { msg ->
                    runCatching {
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
                precedingDirty = true
                updateSuggestions()
            }
            "redo" -> {
                feedback()
                undoRedoManager.performRedo(ic) { msg ->
                    runCatching {
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
                precedingDirty = true
                updateSuggestions()
            }
            "fm", "font" -> {
                feedback()
                var textToConvert = ""
                if (composition.active && composition.rendered.isNotEmpty()) {
                    textToConvert = composition.rendered
                    commitComposition()
                } else {
                    val sel = ic.getSelectedText(0)?.toString()
                    if (!sel.isNullOrEmpty()) {
                        textToConvert = sel
                    } else {
                        val before = ic.getTextBeforeCursor(200, 0)?.toString().orEmpty()
                        if (before.isNotEmpty()) {
                            val lastWord = before.split(Regex("\\s+")).lastOrNull() ?: before
                            textToConvert = lastWord
                            ic.deleteSurroundingText(lastWord.length, 0)
                        } else {
                            val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
                            val fullText = extracted?.text?.toString().orEmpty()
                            if (fullText.isNotEmpty()) {
                                textToConvert = fullText
                                ic.setSelection(0, fullText.length)
                            }
                        }
                    }
                }
                if (textToConvert.isNotEmpty()) {
                    val converted = FmConverter.convert(textToConvert)
                    ic.commitText(converted, 1)
                    runCatching {
                        Toast.makeText(this, "FM: $converted", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runCatching {
                        Toast.makeText(this, "වචනයක් ටයිප් කර FM අයිකනය ඔබන්න", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            "lang_toggle" -> {
                feedback()
                onGlobe()
            }
            "translate" -> {
                feedback()
                keyboard.openTranslator()
            }
            "templates" -> {
                feedback()
                keyboard.openTemplates()
            }
            "notes" -> {
                feedback()
                keyboard.openNotes()
            }
            "calculator" -> {
                feedback()
                keyboard.openCalculator()
            }
            "font" -> {
                runCatching {
                    android.widget.Toast.makeText(this, "Font styling is not available.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            "otp" -> {
                feedback()
                val otp = detectedOtpCode ?: run {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = cm?.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val text = clip.getItemAt(0).text?.toString().orEmpty()
                        Regex("\\b\\d{4,8}\\b").find(text)?.value
                    } else null
                }
                if (otp != null) {
                    ic.commitText(otp, 1)
                    runCatching { Toast.makeText(this, "OTP Pasted: $otp", Toast.LENGTH_SHORT).show() }
                } else {
                    runCatching { Toast.makeText(this, "No OTP detected in clipboard", Toast.LENGTH_SHORT).show() }
                }
            }
            "singlish_bulk" -> {
                feedback()
                val sel = ic.getSelectedText(0)?.toString()
                if (!sel.isNullOrEmpty()) {
                    val converted = SinglishParagraphConverter.convert(sel)
                    ic.commitText(converted, 1)
                    runCatching { Toast.makeText(this, "Singlish Converted!", Toast.LENGTH_SHORT).show() }
                } else {
                    commitComposition()
                    val before = ic.getTextBeforeCursor(512, 0)?.toString().orEmpty()
                    if (before.isNotEmpty()) {
                        val converted = SinglishParagraphConverter.convert(before)
                        if (converted != before) {
                            ic.deleteSurroundingText(before.length, 0)
                            ic.commitText(converted, 1)
                            runCatching { Toast.makeText(this, "Singlish Converted!", Toast.LENGTH_SHORT).show() }
                        } else {
                            runCatching { Toast.makeText(this, "Select text or type Singlish words first", Toast.LENGTH_SHORT).show() }
                        }
                    } else {
                        runCatching { Toast.makeText(this, "Select Singlish text to convert", Toast.LENGTH_SHORT).show() }
                    }
                }
                precedingDirty = true
                updateSuggestions()
            }
            "one_handed_toggle" -> {
                feedback()
                if (::keyboard.isInitialized) {
                    keyboard.toggleOneHanded()
                }
            }
        }
    }

    override fun onVoiceInputRequested() {
        if (::keyboard.isInitialized) {
            keyboard.openVoiceTyping()
        } else {
            voiceInputManager?.startListening(prefs.useEnglish)
        }
    }

    override fun onMediaSelected(uri: Uri, mimeType: String, description: String) {
        runCatching {
            feedback()
            commitComposition()
            val ic = currentInputConnection
            val editorInfo = currentInputEditorInfo
            val packageName = editorInfo?.packageName

            // Explicitly grant read URI permission to target app (e.g. WhatsApp, Telegram, etc.)
            if (!packageName.isNullOrEmpty()) {
                runCatching {
                    grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            val mimeTypes = arrayOf(mimeType, "image/webp", "image/png", "image/*")
            val contentInfo = InputContentInfoCompat(
                uri,
                ClipDescription(description, mimeTypes),
                null
            )
            var flags = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                flags = flags or InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
            }
            val committed = if (ic != null && editorInfo != null) {
                InputConnectionCompat.commitContent(ic, editorInfo, contentInfo, flags, null)
            } else {
                false
            }
            if (!committed) {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    if (!packageName.isNullOrEmpty()) {
                        setPackage(packageName)
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(sendIntent, "Share Image").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(chooser)
            }
        }
    }

    override fun onCursorDelta(delta: Int) {
        runCatching {
            if (delta == 0) return@runCatching
            commitComposition()
            val ic = currentInputConnection ?: return@runCatching
            val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
            if (extracted != null) {
                val next = (extracted.selectionEnd + delta).coerceIn(0, extracted.text.length)
                ic.setSelection(next, next)
            } else {
                val keyCode = if (delta > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                val steps = kotlin.math.abs(delta)
                repeat(steps) {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                }
            }
        }
    }

    override fun onPressFeedback() {
        if (!prefs.keySounds) return
        org.slashboard.ime.sound.KeySoundPlayer.getInstance(this).playIfEnabled(prefs)
    }

    override fun languageScoreForKey(output: String): Float {
        if (restricted || editorLayout != EditorLayout.TEXT) return 0f
        val string = if (output.length == 1 && Character.isLetter(output[0]) && output[0] < '\u0080') {
            composition.source + output
        } else {
            return 0f
        }
        val next = SinhalaEngine.transliterate(string, prefs.mode)
        return prediction?.prefixEvidence(next) ?: 0f
    }

    override fun onPreviewDelete(clusters: Int) {
        val ic = currentInputConnection ?: return
        commitComposition()
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
        if (deleteAnchor < 0) {
            deleteAnchor = extracted.selectionEnd
        }
        val before = ic.getTextBeforeCursor(256, 0)?.toString() ?: ""
        var consumed = 0
        var text = before
        var remaining = clusters
        while (remaining > 0 && text.isNotEmpty()) {
            val cluster = GraphemeDelete.lastCluster(text)
            if (cluster.isEmpty()) break
            consumed += cluster.length
            text = text.dropLast(cluster.length)
            remaining--
        }
        deleteLength = consumed
        runCatching {
            ic.setSelection((deleteAnchor - consumed).coerceAtLeast(0), deleteAnchor)
        }
    }

    override fun onCommitPreviewDelete() {
        val ic = currentInputConnection
        val isSensitive = isStrictPassword(currentInputEditorInfo) || (prefs.securePasswordMode && isPasswordOrSensitive(currentInputEditorInfo, true))
        if (ic != null && deleteLength > 0) {
            val deleted = ic.getTextBeforeCursor(deleteLength, 0)?.toString().orEmpty()
            if (deleted.isNotEmpty() && !isSensitive) {
                undoRedoManager.recordDeletedText(deleted)
            }
            ic.deleteSurroundingText(deleteLength, 0)
            feedback()
        }
        deleteAnchor = -1
        deleteLength = 0
        precedingDirty = true
        updateSuggestions()
    }

    override fun onCancelPreviewDelete() {
        val ic = currentInputConnection
        if (ic != null && deleteAnchor >= 0) {
            runCatching {
                ic.setSelection(deleteAnchor, deleteAnchor)
            }
        }
        deleteAnchor = -1
        deleteLength = 0
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (prefs.volumeCursor && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            val ic = currentInputConnection
            if (ic != null) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
                } else {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
                }
            }
            return true
        }
        if (event != null && event.isPrintingKey && !event.isCtrlPressed && !event.isAltPressed) {
            onCharacter(event.unicodeChar.toChar().toString())
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            onBackspace()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            onSpace()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            onEnter()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (prefs.volumeCursor && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun commitComposition(): String? {
        if (!composition.active) return null
        val rendered = composition.rendered
        val word = if (rendered.isNotBlank()) rendered else null
        currentInputConnection?.finishComposingText()
        composition.clear()
        slsSource.clear()
        generation++
        activeCorrection = null
        return word
    }

    private fun clearLocalCompositionState() {
        composition.clear()
        slsSource.clear()
        generation++
        predictionTask?.cancel(true)
        activeCorrection = null
        precedingDirty = true
        if (::keyboard.isInitialized) {
            keyboard.setCandidates(emptyList())
        }
    }

    private fun cancelComposition(removeHostText: Boolean) {
        runCatching {
            if (removeHostText && composition.rendered.isNotEmpty()) {
                currentInputConnection?.deleteSurroundingText(composition.rendered.length, 0)
            }
            currentInputConnection?.finishComposingText()
        }
        composition.clear()
        slsSource.clear()
        generation++
        predictionTask?.cancel(true)
        precedingDirty = true
        if (::keyboard.isInitialized) {
            keyboard.setCandidates(emptyList())
        }
    }

    private fun deleteFromHost(word: Boolean) {
        val ic = currentInputConnection ?: return
        runCatching {
            val isSensitive = isStrictPassword(currentInputEditorInfo) || (prefs.securePasswordMode && isPasswordOrSensitive(currentInputEditorInfo, true))
            val selected = ic.getSelectedText(0)?.toString()
            if (!selected.isNullOrEmpty()) {
                if (!isSensitive) {
                    undoRedoManager.recordDeletedText(selected)
                }
                ic.commitText("", 1)
                return@runCatching
            }

            val before = ic.getTextBeforeCursor(if (word) 256 else 32, 0)?.toString() ?: ""
            if (word) {
                val target = GraphemeDelete.lastWordSegment(before)
                if (target.isNotEmpty()) {
                    if (!isSensitive) {
                        undoRedoManager.recordDeletedText(target)
                    }
                    ic.deleteSurroundingText(target.length, 0)
                } else {
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                }
                return@runCatching
            }
            val cluster = GraphemeDelete.lastCluster(before)
            if (cluster.isEmpty()) {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
                return@runCatching
            }
            if (!isSensitive) {
                undoRedoManager.recordDeletedCluster(cluster)
            }
            val reduced = GraphemeDelete.reduceSlashboard(cluster)
            if (reduced == null) {
                ic.deleteSurroundingText(cluster.length, 0)
                return@runCatching
            }
            ic.beginBatchEdit()
            ic.deleteSurroundingText(cluster.length, 0)
            ic.commitText(reduced, 1)
            ic.endBatchEdit()
        }
    }

    private fun updateSuggestions() {
        val isSensitive = isStrictPassword(currentInputEditorInfo) || (prefs.securePasswordMode && isPasswordOrSensitive(currentInputEditorInfo, true))
        val isRestricted = isRestrictedEditor(currentInputEditorInfo)
        if (!::keyboard.isInitialized || isSensitive || isRestricted || !prefs.suggestions) {
            if (::keyboard.isInitialized) {
                keyboard.setCandidates(emptyList())
            }
            return
        }

        val beforeString = runCatching {
            currentInputConnection?.getTextBeforeCursor(128, 0)?.toString()
        }.getOrNull() ?: ""
        
        val afterString = runCatching {
            currentInputConnection?.getTextAfterCursor(128, 0)?.toString()
        }.getOrNull() ?: ""

        if (beforeString.isBlank() && afterString.isBlank()) {
            val isChatApp = currentInputEditorInfo?.packageName?.let { 
                it.contains("whatsapp") || it.contains("messenger") || it.contains("telegram") || it.contains("viber") || it.contains("sms") || it.contains("mms") || it.contains("chat") 
            } == true
            if (isChatApp && prefs.suggestions) {
                if (prefs.useEnglish) {
                    keyboard.setCandidates(listOf("Thanks!", "Okay", "Sounds good", "👍", "❤️"))
                } else {
                    keyboard.setCandidates(listOf("ස්තූතියි!", "එළකිරි", "හරි මචං", "👍", "❤️"))
                }
                return
            }
        }

        val mathEval = if (!composition.active) MathEvaluator.evaluateTrailingExpression(beforeString) else null
        if (mathEval != null) {
            activeMathResult = mathEval.formattedResult
            keyboard.setCandidates(listOf("= ${mathEval.formattedResult}", mathEval.formattedResult))
            return
        } else {
            activeMathResult = null
        }

        val unitEval = if (!composition.active) LiveUnitConverter.findConversion(beforeString) else null
        if (unitEval != null) {
            activeUnitResult = unitEval.result
            activeUnitQuery = unitEval.query
            keyboard.setCandidates(listOf("= ${unitEval.result}", unitEval.result))
            return
        } else {
            activeUnitResult = null
            activeUnitQuery = null
        }

        val lastWordForSnippet = Regex("""([a-zA-Z0-9_\p{L}\p{M}]+)$""").find(beforeString)?.value
        val snippetPhrase = if (!composition.active && !lastWordForSnippet.isNullOrEmpty()) snippetManager.find(lastWordForSnippet) else null
        if (snippetPhrase != null && !lastWordForSnippet.isNullOrEmpty()) {
            val sc = lastWordForSnippet
            activeSnippetShortcut = sc
            activeSnippetPhrase = snippetPhrase
            keyboard.setCandidates(listOf(snippetPhrase, sc), setOf(snippetPhrase))
            return
        } else {
            activeSnippetShortcut = null
            activeSnippetPhrase = null
        }

        // NIC Analyzer
        val trailingNIC = if (!composition.active) Regex("""\b([0-9]{9}[vVxX]|[0-9]{12})\b$""").find(beforeString)?.value else null
        if (trailingNIC != null) {
            val nicInfo = SmartParsers.parseNIC(trailingNIC)
            if (nicInfo != null) {
                keyboard.setCandidates(listOf(nicInfo, trailingNIC))
                return
            }
        }

        // Cheque Amount / Number to Words
        val trailingDigits = if (!composition.active) Regex("""\b(\d{1,12}(?:\.\d{1,2})?)\b$""").find(beforeString)?.value else null
        if (trailingDigits != null) {
            val sinWords = SmartParsers.numberToWordsSinhala(trailingDigits)
            val engWords = SmartParsers.numberToWordsEnglish(trailingDigits)
            
            if (sinWords != null && engWords != null) {
                activeNumberDigits = trailingDigits
                activeNumberWords = sinWords // Keeping previous variable semantic
                keyboard.setCandidates(listOf(sinWords, engWords, trailingDigits))
                return
            }
        } else {
            activeNumberDigits = null
            activeNumberWords = null
        }

        generation++
        val token = generation
        predictionTask?.cancel(true)

        val engMatch = Regex("([A-Za-z0-9'’]+)$").find(beforeString)
        val engPrefix = engMatch?.value.orEmpty()
        val isTypingEnglish = prefs.useEnglish || (!composition.active && engPrefix.isNotEmpty() && engPrefix.any { (it in 'a'..'z') || (it in 'A'..'Z') })

        if (isTypingEnglish) {
            val currentEngPrediction = englishPrediction
            if (currentEngPrediction == null || engPrefix.isBlank()) {
                activeCorrection = null
                activeEnglishPrefix = null
                keyboard.setCandidates(emptyList())
                return
            }

            val textWithoutPrefix = beforeString.dropLast(engPrefix.length)
            val engPreceding = Regex("[A-Za-z0-9'’]+").findAll(textWithoutPrefix)
                .map { it.value }
                .toList()
                .takeLast(2)

            predictionTask = executor.submit {
                try {
                    if (token != generation || Thread.currentThread().isInterrupted) return@submit
                    val candidates = currentEngPrediction.candidates(engPrefix, engPreceding, 3)
                    if (token != generation || Thread.currentThread().isInterrupted) return@submit
                    val corrections = candidates.filter { it.isCorrection }.map { it.text }.toSet()
                    val topCorrection = candidates.firstOrNull { it.isCorrection }?.text
                    val values = candidates.map { it.text }.toMutableList()

                    if (prefs.emojiSuggestions) {
                        val emojiCandidate = emoji?.search(engPrefix)?.firstOrNull()
                        if (emojiCandidate != null && !values.contains(emojiCandidate)) {
                            if (values.size >= 3) {
                                values[2] = emojiCandidate
                            } else {
                                values.add(emojiCandidate)
                            }
                        }
                    }

                    main.post {
                        if (token == generation && ::keyboard.isInitialized) {
                            activeCorrection = topCorrection
                            activeEnglishPrefix = engPrefix
                            keyboard.setCandidates(values.distinct().take(3), corrections)
                        }
                    }
                } catch (t: Throwable) {
                    main.post {
                        if (token == generation && ::keyboard.isInitialized) {
                            activeCorrection = null
                            activeEnglishPrefix = null
                            keyboard.setCandidates(emptyList())
                        }
                    }
                }
            }
            return
        }

        if (!composition.active) {
            precedingDirty = true
        }
        val prefix = if (composition.active && composition.rendered.isNotEmpty()) {
            composition.rendered
        } else {
            Regex("([\\p{L}\\p{M}\u200D\u200C]+)$").find(beforeString)?.value.orEmpty()
        }
        val context = precedingWords()

        val currentPrediction = prediction
        if (currentPrediction == null || prefix.isBlank()) {
            keyboard.setCandidates(emptyList())
            return
        }
        predictionTask = executor.submit {
            try {
                if (token != generation || Thread.currentThread().isInterrupted) return@submit
                val candidates = currentPrediction.candidates(prefix, context, 3)
                if (token != generation || Thread.currentThread().isInterrupted) return@submit
                val corrections = candidates.filter { it.isCorrection }.map { it.text }.toSet()
                val topCorrection = candidates.firstOrNull { it.isCorrection }?.text
                val values = candidates.map { it.text }.toMutableList()
                if (SlashboardEasterEgg.isCompleteTrueName(prefix, composition.source)) {
                    values.add(0, SlashboardEasterEgg.TRUE_NAME_DISPLAY)
                }
                if (prefs.emojiSuggestions) {
                    val emojiCandidate = emoji?.search(prefix)?.firstOrNull()
                    if (emojiCandidate != null && !values.contains(emojiCandidate)) {
                        if (values.size >= 3) {
                            values[2] = emojiCandidate
                        } else {
                            values.add(emojiCandidate)
                        }
                    }
                }
                main.post {
                    if (token == generation && ::keyboard.isInitialized) {
                        activeCorrection = topCorrection
                        keyboard.setCandidates(values.distinct().take(3), corrections)
                    }
                }
            } catch (t: Throwable) {
                main.post {
                    if (token == generation && ::keyboard.isInitialized) {
                        activeCorrection = null
                        keyboard.setCandidates(emptyList())
                    }
                }
            }
        }
    }

    private fun precedingWords(): List<String> {
        if (!precedingDirty && composition.active) {
            return cachedPreceding
        }
        val before = runCatching {
            currentInputConnection?.getTextBeforeCursor(256, 0)?.toString()
        }.getOrNull() ?: ""

        val withoutComposing = if (composition.rendered.isNotEmpty() && before.endsWith(composition.rendered)) {
            before.dropLast(composition.rendered.length)
        } else {
            before
        }

        cachedPreceding = Regex("[\\p{L}\\p{M}]+").findAll(withoutComposing)
            .map { it.value }
            .toList()
            .takeLast(2)
        precedingDirty = false
        return cachedPreceding
    }

    private fun learn(word: String?) {
        val isSensitive = isStrictPassword(currentInputEditorInfo) || (prefs.securePasswordMode && isPasswordOrSensitive(currentInputEditorInfo, true))
        if (word.isNullOrBlank() || isSensitive || !keyboard.learningEnabled) return
        val clean = word.trim()
        val earlier = previousEarlierCommittedWord
        val previous = previousCommittedWord
        previousEarlierCommittedWord = previous
        previousCommittedWord = clean
        val store = learning ?: return
        executor.submit {
            store.record(clean, previous, earlier)
        }
    }

    private fun learnEnglish(word: String?) {
        val isSensitive = isStrictPassword(currentInputEditorInfo) || (prefs.securePasswordMode && isPasswordOrSensitive(currentInputEditorInfo, true))
        if (word.isNullOrBlank() || isSensitive || !keyboard.learningEnabled) return
        val clean = word.trim()
        val earlier = previousEarlierCommittedWord
        val previous = previousCommittedWord
        previousEarlierCommittedWord = previous
        previousCommittedWord = clean
        val store = learning
        val eng = englishPrediction
        executor.submit {
            store?.record(clean, previous, earlier)
            eng?.learn(clean, previous, earlier)
        }
    }

    private fun captureClipboard() {
        runCatching {
            checkOtp()
            val isSensitive = isStrictPassword(currentInputEditorInfo) || (prefs.securePasswordMode && isPasswordOrSensitive(currentInputEditorInfo, true))
            if (!prefs.clipboardHistory || isSensitive) return
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            val clip = manager.primaryClip ?: return
            if (clip.itemCount == 0) return
            val store = clipboardHistory ?: return
            val text = clip.getItemAt(0).coerceToText(this)?.toString()
            if (!text.isNullOrBlank()) {
                store.add(text)
                if (::keyboard.isInitialized) {
                    keyboard.setClipboardItems(store.items(), store.pinnedItems())
                }
            }
        }
    }

    private fun checkOtp() {
        if (!::keyboard.isInitialized) return
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = manager.primaryClip
        var hasOtp = false
        var otpCode: String? = null
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()
            if (text != null) {
                val match = Regex("\\b\\d{4,8}\\b").find(text)
                if (match != null) {
                    hasOtp = true
                    otpCode = match.value
                }
            }
        }
        detectedOtpCode = otpCode
        keyboard.setOtpAvailable(hasOtp, otpCode)
    }

    private fun listenForClipboard() {
        runCatching {
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            manager.removePrimaryClipChangedListener(clipListener)
            val isSensitive = isStrictPassword(currentInputEditorInfo) || (prefs.securePasswordMode && isPasswordOrSensitive(currentInputEditorInfo, true))
            if (prefs.clipboardHistory && !isSensitive) {
                manager.addPrimaryClipChangedListener(clipListener)
            }
        }
    }

    private fun stopClipboardListener() {
        runCatching {
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            manager.removePrimaryClipChangedListener(clipListener)
        }
    }

    private fun rememberEmoji(value: String) {
        recentEmoji.remove(value)
        recentEmoji.add(0, value)
        if (recentEmoji.size > 36) {
            recentEmoji = recentEmoji.take(36).toMutableList()
        }
        prefs.recentEmojis = recentEmoji
        if (::keyboard.isInitialized) {
            keyboard.setRecentEmoji(recentEmoji)
        }
    }

    private fun feedback() {
        if (prefs.haptics && ::keyboard.isInitialized) {
            runCatching {
                val flags = android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                keyboard.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP, flags)
            }
        }
        if (prefs.keySounds) {
            runCatching {
                (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.playSoundEffect(AudioManager.FX_KEY_CLICK, 1.0f)
            }
        }
    }

    private fun offerSystemSwitch(): Boolean {
        return if (Build.VERSION.SDK_INT >= 28) {
            shouldOfferSwitchingToNextInputMethod()
        } else {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val token = window?.window?.attributes?.token
            imm.shouldOfferSwitchingToNextInputMethod(token)
        }
    }

    private fun switchSystemKeyboard() {
        if (Build.VERSION.SDK_INT >= 28) {
            switchToNextInputMethod(false)
        } else {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val token = window?.window?.attributes?.token
            imm.switchToNextInputMethod(token, false)
        }
    }

    companion object {
        fun isBankingOrFinanceApp(pkg: String): Boolean {
            return pkg.contains("bank") || pkg.contains("boc") || pkg.contains("peoplesbank") ||
                   pkg.contains("combank") || pkg.contains("sampath") || pkg.contains("hnb") ||
                   pkg.contains("seylan") || pkg.contains("nsb") || pkg.contains("ndb") ||
                   pkg.contains("wallet") || pkg.contains("pay") || pkg.contains("finance") ||
                   pkg.contains("money") || pkg.contains("koko") || pkg.contains("frimi") ||
                   pkg.contains("vault") || pkg.contains("authenticator") || pkg.contains("keepass") ||
                   pkg.contains("bitwarden") || pkg.contains("1password") || pkg.contains("lastpass") ||
                   pkg.contains("dashlane") || pkg.contains("nordpass")
        }

        fun isStrictPassword(info: EditorInfo?): Boolean {
            if (info == null) return false
            val inputType = info.inputType
            val cls = inputType and EditorInfo.TYPE_MASK_CLASS
            val variation = inputType and EditorInfo.TYPE_MASK_VARIATION

            if (cls == EditorInfo.TYPE_CLASS_TEXT) {
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    variation == 128 || variation == 144 || variation == 224) {
                    return true
                }
            }

            if (cls == EditorInfo.TYPE_CLASS_NUMBER) {
                if (variation == EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD || variation == 16) {
                    return true
                }
            }

            return false
        }

        fun isPasswordOrSensitive(info: EditorInfo?, securePasswordModeEnabled: Boolean = false): Boolean {
            if (info == null) return false
            if (isStrictPassword(info)) return true

            if (!securePasswordModeEnabled) return false

            val pkg = info.packageName?.lowercase().orEmpty()
            if (isBankingOrFinanceApp(pkg)) {
                return true
            }

            return false
        }

        fun isRestrictedEditor(info: EditorInfo?): Boolean {
            if (info == null) return false
            val cls = info.inputType and EditorInfo.TYPE_MASK_CLASS
            return cls == EditorInfo.TYPE_CLASS_NUMBER || cls == EditorInfo.TYPE_CLASS_PHONE || cls == EditorInfo.TYPE_CLASS_DATETIME
        }

        fun enterLabel(info: EditorInfo?): String {
            val isMultiLine = (info?.inputType ?: 0) and EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE != 0
            if (isMultiLine) return "↵"
            return when (info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)) {
                EditorInfo.IME_ACTION_SEARCH -> "⌕"
                else -> "↵"
            }
        }

        fun isTerminalOrDevApp(pkg: String): Boolean {
            return pkg.contains("termux") || pkg.contains("terminal") || pkg.contains("connectbot") ||
                   pkg.contains("juicessh")
        }

        fun isChatApp(pkg: String): Boolean {
            return pkg.contains("whatsapp") || pkg.contains("viber") || pkg.contains("telegram") ||
                   pkg.contains("messenger") || pkg.contains("signal") || pkg.contains("im.vector") ||
                   pkg.contains("discord") || pkg.contains("line") || pkg.contains("wechat")
        }

        fun editorLayout(info: EditorInfo?): EditorLayout {
            if (info == null) return EditorLayout.TEXT
            val cls = info.inputType and EditorInfo.TYPE_MASK_CLASS
            val variation = info.inputType and EditorInfo.TYPE_MASK_VARIATION
            val flags = info.inputType and EditorInfo.TYPE_MASK_FLAGS

            // Show PIN / numeric layout for all numeric fields and numeric PINs
            if (cls == EditorInfo.TYPE_CLASS_NUMBER) {
                val isSigned = (flags and EditorInfo.TYPE_NUMBER_FLAG_SIGNED) != 0
                val isDecimal = (flags and EditorInfo.TYPE_NUMBER_FLAG_DECIMAL) != 0
                return when {
                    isSigned && isDecimal -> EditorLayout.SIGNED_DECIMAL
                    isSigned -> EditorLayout.SIGNED_NUMBER
                    isDecimal -> EditorLayout.DECIMAL
                    else -> EditorLayout.NUMBER
                }
            }

            return when (cls) {
                EditorInfo.TYPE_CLASS_PHONE -> EditorLayout.PHONE
                EditorInfo.TYPE_CLASS_DATETIME -> EditorLayout.DATETIME
                EditorInfo.TYPE_CLASS_TEXT -> {
                    when (variation) {
                        EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                        EditorInfo.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> EditorLayout.EMAIL
                        EditorInfo.TYPE_TEXT_VARIATION_URI -> EditorLayout.URI
                        EditorInfo.TYPE_TEXT_VARIATION_PASSWORD,
                        EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                        EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                        128, 144, 224 -> EditorLayout.ASCII
                        else -> EditorLayout.TEXT
                    }
                }
                else -> EditorLayout.TEXT
            }
        }
    }
}
