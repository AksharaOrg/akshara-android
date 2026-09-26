package org.akshara.ime.ime

import android.content.ClipboardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.LocaleList
import android.os.Looper
import android.os.SystemClock
import android.net.Uri
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.inline.InlinePresentationSpec
import android.util.Size
import androidx.core.view.WindowCompat
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import org.akshara.ime.data.*
import org.akshara.ime.engine.*
import org.akshara.ime.settings.KeyboardPreferences
import java.util.concurrent.Executors
import java.util.concurrent.Future

class AksharaInputMethodService : InputMethodService(), KeyboardActions {
    private lateinit var prefs: KeyboardPreferences
    private lateinit var keyboard: KeyboardView
    private lateinit var learning: LocalLearningStore
    private lateinit var prediction: PredictionRepository
    private lateinit var autocorrection: SinhalaAutocorrection
    private lateinit var englishPrediction: EnglishPredictionRepository
    private lateinit var emoji: EmojiRepository
    private lateinit var clipboardHistory: ClipboardHistoryStore
    private lateinit var recentEmojiStore: RecentEmojiStore
    private lateinit var clipboardImageCache: ClipboardImageCache
    private val composition = CompositionSession()
    private val preview = UnmarkedPreview()
    private val slsSource = StringBuilder()
    private val executor = Executors.newSingleThreadExecutor()
    private val clipboardExecutor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var predictionTask: Future<*>? = null
    private var suggestionRunnable: Runnable? = null
    private var generation = 0
    private var restricted = false
    private var secureEditor = true
    private var clipboardEligible = false
    private var latestClipboard: ClipData? = null
    private data class StagedImage(val source: Uri, val content: Uri, val mimeType: String)
    private var stagedImage: StagedImage? = null
    private var stagingSource: Uri? = null
    private var clipboardGeneration = 0
    private var inputViewActive = false
    private var previousCommittedWord: String? = null
    private var recentEmoji = mutableListOf<String>()
    private var editorLayout = EditorLayout.TEXT
    private var spaceIntroAllowed = true
    private var latinWordActive = false
    private var persistentEnglish = false
    private var lastSpaceAt = 0L
    private var pendingAutocorrection: PendingAutocorrection? = null
    private var rejectedCorrection: String? = null
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        main.post { applyPreferenceChange(key) }
    }

    private var precedingDirty = true
    private var cachedPreceding = emptyList<String>()
    private var deleteAnchor = -1
    private var deleteLength = 0

    override fun onCreate() {
        super.onCreate(); prefs = KeyboardPreferences(this); learning = LocalLearningStore(this)
        prediction = PredictionRepository(this, learning); autocorrection = SinhalaAutocorrection(this); englishPrediction = EnglishPredictionRepository(this, learning); emoji = EmojiRepository(this); clipboardHistory = ClipboardHistoryStore(this); recentEmojiStore = RecentEmojiStore(this); clipboardImageCache = ClipboardImageCache(this)
        recentEmoji = recentEmojiStore.items().toMutableList()
        prefs.register(preferenceListener)
        executor.submit { prediction.warmup(); autocorrection.warmup(); englishPrediction.warmup() }
    }
    override fun onCreateInputView(): View {
        window?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        keyboard = KeyboardView(this, this, prefs)
        applySystemBarAppearance()
        return keyboard
    }
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting); cancelComposition(false)
        pendingAutocorrection = null
        rejectedCorrection = null
        previousCommittedWord = null
        clearClipboardPreview()
        restricted = attribute?.let(::isRestrictedEditor) ?: true
        secureEditor = attribute?.let(::isSecureEditor) ?: true
        clipboardEligible = attribute?.let(::isClipboardEditor) ?: false
        precedingDirty = true
        if (::keyboard.isInitialized) keyboard.setInlineAutofillSuggestions(emptyList())
    }
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        inputViewActive = true
        prefs = KeyboardPreferences(this); restricted = info?.let(::isRestrictedEditor) ?: true
        secureEditor = info?.let(::isSecureEditor) ?: true
        clipboardEligible = info?.let(::isClipboardEditor) ?: false
        editorLayout = editorLayout(info)
        val intro = spaceIntroAllowed && restarting != true
        spaceIntroAllowed = false
        latinWordActive = false
        persistentEnglish = prefs.persistentEnglish
        keyboard.configure(prefs.mode, offerSystemSwitch(), enterLabel(info), editorLayout, intro, persistentEnglish)
        keyboard.learningEnabled = !restricted && editorLayout == EditorLayout.TEXT
        refreshClipboard()
        keyboard.setClipboardItems(clipboardHistory.items(), clipboardHistory.pinnedItems())
        listenForClipboard()
        recentEmoji = recentEmojiStore.items().toMutableList()
        keyboard.setRecentEmoji(recentEmoji)
        applySystemBarAppearance()
        updateSuggestions()
    }
    override fun onFinishInput() { deleteAnchor = -1; deleteLength = 0; cancelComposition(false); super.onFinishInput() }
    override fun onDestroy() {
        clearClipboardPreview()
        stopClipboardListener()
        prefs.unregister(preferenceListener)
        predictionTask?.cancel(true)
        suggestionRunnable?.let(main::removeCallbacks)
        executor.shutdownNow()
        clipboardExecutor.shutdownNow()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
    override fun onFinishInputView(finishingInput: Boolean) {
        inputViewActive = false
        clearClipboardPreview()
        stopClipboardListener()
        cancelComposition(false)
        super.onFinishInputView(finishingInput)
    }

    override fun onWindowHidden() {
        spaceIntroAllowed = true
        super.onWindowHidden()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (composition.active && currentInputConnection?.let { !preview.matches(it) } == true) {
            cancelComposition(false)
            previousCommittedWord = null
        }
        if (oldSelStart != newSelStart || oldSelEnd != newSelEnd) {
            precedingDirty = true
        }
        updateEnglishCapitalization()
    }

    override fun onCharacter(value: String) {
        if (latestClipboard != null) clearClipboardPreview()
        rejectedCorrection = null
        validatePreview()
        if (persistentEnglish) {
            commitComposition()
            insertCommitted(value)
            updateSuggestions()
            return
        }
        if (latinWordActive) {
            insertCommitted(value)
            updateSuggestions()
            return
        }
        if (!supportsSinhala(editorLayout)) {
            commitComposition(); insertCommitted(value)
        } else if (prefs.mode == InputMode.WIJESEKARA && (value == "\u200D" || value.codePoints().anyMatch { it in 0x0D80..0xE0FF })) {
            slsSource.append(value)
            val rendered = SinhalaEngine.normalizeSls(slsSource.toString())
            composition.replace(rendered); writePreview(rendered, true)
        } else if (prefs.mode != InputMode.WIJESEKARA && value.length == 1 && value[0].isLetter() && value[0].code < 128) {
            val rendered = composition.type(value, prefs.mode)
            writePreview(rendered, true)
        } else {
            commitComposition(); insertCommitted(value)
        }
        updateSuggestions()
    }

    override fun onBackspace(word: Boolean) {
        validatePreview()
        if (!word && undoAutocorrection()) { updateSuggestions(); return }
        if (composition.active) {
            if (prefs.mode == InputMode.WIJESEKARA) {
                if (slsSource.isNotEmpty()) {
                    val next = GraphemeDelete.peelLastScalar(slsSource.toString())
                    slsSource.setLength(0)
                    slsSource.append(next)
                } else {
                    slsSource.append(GraphemeDelete.reduceAkshara(composition.rendered).orEmpty())
                }
                val rendered = SinhalaEngine.normalizeSls(slsSource.toString())
                composition.replace(rendered)
                if (rendered.isEmpty()) {
                    writePreview("", true)
                    currentInputConnection?.finishComposingText()
                    slsSource.clear()
                } else {
                    writePreview(rendered, true)
                }
            } else {
                val rendered = composition.backspace(prefs.mode)
                if (rendered.isEmpty()) {
                    writePreview("", true)
                    currentInputConnection?.finishComposingText()
                } else writePreview(rendered, true)
            }
        } else {
            deleteFromHost(word)
            precedingDirty = true
        }
        updateSuggestions()
    }
    override fun onSpace() {
        val englishWord = if (persistentEnglish) currentLatinPrefix() else null
        val word = commitComposition()
        if (latinWordActive) endLatinWord()
        val ic = currentInputConnection
        val corrected = applyAutocorrection(englishWord ?: word, " ")
        if (corrected) {
            lastSpaceAt = SystemClock.elapsedRealtime()
        } else if (ic != null && tryDoubleSpace(ic)) {
            lastSpaceAt = 0L
        } else {
            ic?.commitText(" ", 1)
            lastSpaceAt = SystemClock.elapsedRealtime()
        }
        if (!corrected) learn(englishWord ?: word)
        precedingDirty = true
        updateSuggestions()
    }

    override fun onSpaceSwipe(up: Boolean) {
        if (persistentEnglish) return
        if (!prefs.englishForOneWord || prefs.mode != InputMode.SMART_PHONETIC || editorLayout != EditorLayout.TEXT) return
        if (latinWordActive) {
            endLatinWord()
            return
        }
        if (!up) return
        commitComposition()
        latinWordActive = true
        keyboard.setEnglishOneWord(true)
        updateSuggestions()
    }
    override fun onSpaceLongPress() = switchKeyboardLanguage()

    override fun onLanguageSwitch() = switchKeyboardLanguage()

    private fun switchKeyboardLanguage() {
        if (!supportsSinhala(editorLayout)) return
        commitComposition()
        latinWordActive = false
        persistentEnglish = !persistentEnglish
        prefs.persistentEnglish = persistentEnglish
        keyboard.setPersistentEnglish(persistentEnglish)
        updateSuggestions()
    }
    override fun onEnter() {
        val englishWord = if (persistentEnglish) currentLatinPrefix() else null
        val word = commitComposition(); if (latinWordActive) endLatinWord()
        val info = currentInputEditorInfo
        val action = enterAction(info)
        val customAction = info?.actionLabel != null && info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0
        val hasAction = customAction || action !in setOf(EditorInfo.IME_ACTION_NONE, EditorInfo.IME_ACTION_UNSPECIFIED)
        val corrected = applyAutocorrection(englishWord ?: word, if (hasAction) "" else "\n")
        if (!corrected) learn(englishWord ?: word)
        if (customAction) currentInputConnection?.performEditorAction(info!!.actionId)
        else if (hasAction) {
            currentInputConnection?.performEditorAction(action)
            if (action == EditorInfo.IME_ACTION_DONE) requestHideSelf(0)
        }
        else if (!corrected) currentInputConnection?.commitText("\n", 1)
        cancelComposition(false); updateSuggestions()
    }
    override fun onCandidate(value: String) {
        validatePreview()
        feedback()
        if (value == AksharaEasterEgg.TRUE_NAME_DISPLAY) {
            writePreview(AksharaEasterEgg.TRUE_NAME_INSERT, true); preview.clear()
            composition.clear(); slsSource.clear(); updateSuggestions(); return
        }
        if (persistentEnglish) {
            val editedWord = currentWordAtCursor()
            val editing = isEditingExistingWord(editedWord)
            val typed = if (editing) editedWord.text else currentLatinPrefix()
            currentInputConnection?.beginBatchEdit()
            try {
                if (typed.isNotEmpty()) {
                    currentInputConnection?.deleteSurroundingText(
                        if (editing) editedWord.prefix.length else typed.length,
                        if (editing) editedWord.suffix.length else 0
                    )
                }
                currentInputConnection?.commitText("$value ", 1)
            } finally { currentInputConnection?.endBatchEdit() }
            learn(value)
            precedingDirty = true
            updateSuggestions()
            return
        }
        val editing = currentWordAtCursor().takeIf(::isEditingExistingWord)
        if (editing != null) {
            replaceEditedWord(value, editing)
        } else {
            writePreview(value, true); preview.clear()
            composition.clear(); slsSource.clear(); currentInputConnection?.commitText(" ", 1)
        }
        learn(value)
        precedingDirty = true
        updateSuggestions()
    }
    override fun onEmojiPicked(value: String) {
        commitComposition()
        insertCommitted(value)
        rememberEmoji(value)
        updateSuggestions()
    }
    override fun onPasteText(value: String) {
        commitComposition()
        pendingAutocorrection = null
        lastSpaceAt = 0L
        currentInputConnection?.commitText(value, 1)
        precedingDirty = true
        updateSuggestions()
    }
    override fun onGlobe() { commitComposition(); if (latinWordActive) endLatinWord(); switchSystemKeyboard() }
    override fun onModeRequested(mode: InputMode) {
        commitComposition(); if (latinWordActive) endLatinWord(); prefs.mode = mode
        keyboard.configure(mode, offerSystemSwitch(), enterLabel(currentInputEditorInfo), editorLayout, english = persistentEnglish)
    }
    override fun onHide() { commitComposition(); requestHideSelf(0) }
    override fun onCursorDelta(delta: Int) {
        if (delta == 0) return; commitComposition(); val ic = currentInputConnection ?: return
        val extracted = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0) ?: return
        val next = (extracted.selectionEnd + delta).coerceIn(0, extracted.text.length); ic.setSelection(next, next)
    }
    override fun onSettings() {
        startActivity(Intent(this, org.akshara.ime.settings.SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    override fun onClipboardOpen() {
        refreshClipboard()
        if (::keyboard.isInitialized) keyboard.setClipboardItems(clipboardHistory.items(), clipboardHistory.pinnedItems())
    }

    override fun onClipboardPreviewPaste() {
        val clip = latestClipboard ?: return
        if (!prefs.clipboardPreview || !clipboardEligible || secureEditor || clip.itemCount == 0) return
        val item = clip.getItemAt(0)
        val supportedImageMime = item.uri?.let { compatibleImageMime(clip.description) }
        val image = stagedImage?.takeIf { it.source == item.uri }
        // Do not disturb the current word if the copied image is still being prepared.
        if (supportedImageMime != null && image == null) return
        // Rich-content commits must not race an active composing region.
        commitComposition()
        val committed = image?.let { staged ->
            supportedImageMime?.let {
                val description = ClipDescription(clip.description.label, arrayOf(staged.mimeType))
                val content = InputContentInfoCompat(staged.content, description, null)
                runCatching {
                    InputConnectionCompat.commitContent(
                        currentInputConnection,
                        currentInputEditorInfo,
                        content,
                        InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                        null
                    )
                }.getOrDefault(false)
            }
        } == true
        if (!committed) {
            val text = clipboardText(clip) ?: return
            onPasteText(text)
        }
        latestClipboard = null
        stagedImage = null
        stagingSource = null
        clipboardGeneration++
        if (::keyboard.isInitialized) keyboard.setClipboardPreview(null)
        updateSuggestions()
    }

    /** Avoid Android's landscape extract UI, which can make the IME appear detached. */
    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || restricted || !prefs.inlineAutofill) return null
        val height = (KeyboardGeometry.railHeightPx(false, resources.displayMetrics.density)).toInt()
        val spec = InlinePresentationSpec.Builder(
            Size((64 * resources.displayMetrics.density).toInt(), height),
            Size(resources.displayMetrics.widthPixels, height)
        ).setStyle(uiExtras).build()
        return InlineSuggestionsRequest.Builder(listOf(spec))
            .setMaxSuggestionCount(3)
            .setSupportedLocales(LocaleList(java.util.Locale("si", "LK"), java.util.Locale.ENGLISH))
            .build()
    }

    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !::keyboard.isInitialized || restricted || !prefs.inlineAutofill) return false
        keyboard.setInlineAutofillSuggestions(response.inlineSuggestions)
        return true
    }

    override fun onPressFeedback() {
        if (prefs.keySounds) (getSystemService(AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEY_CLICK, .35f)
    }

    override fun languageScoreForKey(output: String): Float {
        if (restricted || editorLayout != EditorLayout.TEXT || latinWordActive || persistentEnglish) return 0f
        val next = if (prefs.mode != InputMode.WIJESEKARA && output.length == 1 && output[0].isLetter() && output[0].code < 128) {
            SinhalaEngine.transliterate(composition.source + output, prefs.mode)
        } else if (prefs.mode == InputMode.WIJESEKARA) {
            SinhalaEngine.normalizeSls(slsSource.toString() + output)
        } else return 0f
        return prediction.prefixEvidence(next)
    }

    override fun onPreviewDelete(clusters: Int) {
        val ic = currentInputConnection ?: return
        commitComposition()
        val extracted = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0) ?: return
        if (deleteAnchor < 0) deleteAnchor = extracted.selectionEnd
        val before = ic.getTextBeforeCursor(256, 0)?.toString().orEmpty()
        var remaining = clusters
        var consumed = 0
        var text = before
        while (remaining > 0 && text.isNotEmpty()) {
            val cluster = GraphemeDelete.lastCluster(text)
            if (cluster.isEmpty()) break
            consumed += cluster.length
            text = text.dropLast(cluster.length)
            remaining--
        }
        deleteLength = consumed
        runCatching { ic.setSelection((deleteAnchor - consumed).coerceAtLeast(0), deleteAnchor) }
    }

    override fun onCommitPreviewDelete() {
        val ic = currentInputConnection
        if (ic != null && deleteLength > 0) ic.commitText("", 1)
        deleteAnchor = -1
        deleteLength = 0
        precedingDirty = true
        updateSuggestions()
    }

    override fun onCancelPreviewDelete() {
        val ic = currentInputConnection
        if (ic != null && deleteAnchor >= 0) runCatching { ic.setSelection(deleteAnchor, deleteAnchor) }
        deleteAnchor = -1
        deleteLength = 0
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && event.isPrintingKey && !event.isCtrlPressed && !event.isAltPressed) {
            onCharacter(event.unicodeChar.toChar().toString()); return true
        }
        if (keyCode == KeyEvent.KEYCODE_DEL) { onBackspace(); return true }
        if (keyCode == KeyEvent.KEYCODE_SPACE) { onSpace(); return true }
        if (keyCode == KeyEvent.KEYCODE_ENTER) { onEnter(); return true }
        return super.onKeyDown(keyCode, event)
    }

    private fun insertCommitted(value: String) {
        pendingAutocorrection = null
        val ic = currentInputConnection ?: return
        val quoted = if (!persistentEnglish && prefs.smartQuotes && editorLayout == EditorLayout.TEXT && value in setOf("'", "\"")) {
            val previous = ic.getTextBeforeCursor(1, 0)?.toString()?.lastOrNull()
            SmartPunctuationSpacing.smartQuote(value, previous)
        } else value
        if (!persistentEnglish && editorLayout == EditorLayout.TEXT && prefs.smartPunctuation && quoted.isNotEmpty() && quoted != " " && !quoted.startsWith("\n")) {
            val before = ic.getTextBeforeCursor(8, 0)?.toString().orEmpty()
            val change = SmartPunctuationSpacing.adjustment(quoted, before, punctuationField())
            if (change.deletePrecedingCount > 0) ic.deleteSurroundingText(change.deletePrecedingCount, 0)
            ic.commitText(change.text, 1)
        } else {
            ic.commitText(quoted, 1)
        }
        lastSpaceAt = 0L
    }

    private fun tryDoubleSpace(ic: InputConnection): Boolean {
        val before = ic.getTextBeforeCursor(8, 0)?.toString().orEmpty()
        val elapsed = SystemClock.elapsedRealtime() - lastSpaceAt
        if (!SmartPunctuationSpacing.DoubleSpace.shouldReplace(prefs.doubleSpacePeriod && editorLayout == EditorLayout.TEXT, elapsed, before)) {
            return false
        }
        ic.deleteSurroundingText(1, 0)
        ic.commitText(". ", 1)
        return true
    }

    private fun punctuationField(): SmartPunctuationSpacing.FieldKind =
        if (editorLayout == EditorLayout.TEXT) SmartPunctuationSpacing.FieldKind.STANDARD
        else SmartPunctuationSpacing.FieldKind.SUPPRESSES_SENTENCE_SPACING

    private fun endLatinWord() {
        if (!latinWordActive) return
        latinWordActive = false
        if (::keyboard.isInitialized) keyboard.setEnglishOneWord(false)
    }

    private fun validatePreview() {
        if (composition.active && currentInputConnection?.let { !preview.matches(it) } == true) cancelComposition(false)
    }

    private fun writePreview(value: String, alreadyValidated: Boolean = false) {
        currentInputConnection?.let { if (!preview.replace(it, value, alreadyValidated)) cancelComposition(false) }
    }

    private fun commitComposition(): String? {
        validatePreview()
        if (!composition.active) return null
        val word = composition.rendered.takeIf { it.isNotBlank() }
        currentInputConnection?.finishComposingText(); preview.clear(); composition.clear(); slsSource.clear(); generation++
        return word
    }
    private data class PendingAutocorrection(val original: String, val replacement: String, val suffix: String)

    private fun applyAutocorrection(word: String?, suffix: String): Boolean {
        if (!(if (persistentEnglish) prefs.englishAutocorrect else prefs.autocorrect) || restricted || editorLayout != EditorLayout.TEXT || word.isNullOrBlank()) return false
        if (word == rejectedCorrection) return false
        val editor = currentInputEditorInfo
        if (editor != null && editor.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return false
        if (!currentInputConnection?.getSelectedText(0).isNullOrEmpty()) return false
        if (currentInputConnection?.getTextAfterCursor(1, 0)?.firstOrNull()?.let(::isWordCharacter) == true) return false
        val replacement = (if (persistentEnglish) englishPrediction.correction(word) else autocorrection.correction(word))
            ?: return false
        val ic = currentInputConnection ?: return false
        ic.beginBatchEdit()
        try {
            ic.deleteSurroundingText(word.length, 0)
            ic.commitText(replacement + suffix, 1)
        } finally { ic.endBatchEdit() }
        pendingAutocorrection = PendingAutocorrection(word, replacement, suffix)
        learn(replacement)
        precedingDirty = true
        return true
    }

    private fun undoAutocorrection(): Boolean {
        val pending = pendingAutocorrection ?: return false
        val ic = currentInputConnection ?: return false
        val expected = pending.replacement + pending.suffix
        if (ic.getTextBeforeCursor(expected.length + 1, 0)?.toString()?.endsWith(expected) != true) {
            pendingAutocorrection = null
            return false
        }
        ic.beginBatchEdit()
        try {
            ic.deleteSurroundingText(expected.length, 0)
            ic.commitText(pending.original, 1)
        } finally { ic.endBatchEdit() }
        pendingAutocorrection = null
        rejectedCorrection = pending.original
        precedingDirty = true
        return true
    }
    private fun cancelComposition(removeHostText: Boolean) {
        if (removeHostText && composition.rendered.isNotEmpty()) currentInputConnection?.deleteSurroundingText(composition.rendered.length, 0)
        currentInputConnection?.finishComposingText(); preview.clear(); composition.clear(); slsSource.clear(); generation++; predictionTask?.cancel(true)
        suggestionRunnable?.let(main::removeCallbacks)
        precedingDirty = true
        if (::keyboard.isInitialized) keyboard.setCandidates(emptyList())
    }
    private fun deleteFromHost(word: Boolean) {
        val ic = currentInputConnection ?: return
        if (!ic.getSelectedText(0).isNullOrEmpty()) { ic.commitText("", 1); return }
        val before = ic.getTextBeforeCursor(if (word) 256 else 32, 0)?.toString().orEmpty()
        if (word) {
            val target = GraphemeDelete.lastWordSegment(before)
            if (target.isNotEmpty()) ic.deleteSurroundingText(target.length, 0)
            else ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            return
        }
        val cluster = GraphemeDelete.lastCluster(before)
        if (cluster.isEmpty()) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            return
        }
        val reduced = GraphemeDelete.reduceAkshara(cluster)
        if (reduced == null) {
            ic.deleteSurroundingText(cluster.length, 0)
            return
        }
        ic.beginBatchEdit()
        ic.deleteSurroundingText(cluster.length, 0)
        ic.commitText(reduced, 1)
        ic.endBatchEdit()
    }
    private fun updateSuggestions() {
        generation++
        predictionTask?.cancel(true)
        suggestionRunnable?.let(main::removeCallbacks)
        updateEnglishCapitalization()
        if (!::keyboard.isInitialized || restricted || !prefs.suggestions || latinWordActive || editorLayout != EditorLayout.TEXT) { if (::keyboard.isInitialized) keyboard.setCandidates(emptyList()); return }
        suggestionRunnable?.let(main::removeCallbacks)
        val pending = Runnable {
            suggestionRunnable = null
            computeSuggestions()
        }
        suggestionRunnable = pending
        main.postDelayed(pending, SUGGESTION_DEBOUNCE_MS)
    }

    private fun updateEnglishCapitalization() {
        if (!::keyboard.isInitialized) return
        val info = currentInputEditorInfo
        val enabled = prefs.autoCapitalization && persistentEnglish && editorLayout == EditorLayout.TEXT && !secureEditor
        val requested = info?.inputType?.and(InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) ?: 0
        val mode = if (requested != 0) requested else InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        val connection = currentInputConnection
        val editorCaps = connection?.getCursorCapsMode(mode) ?: 0
        // Some editors return zero even at the start of an empty field. Derive the same
        // capitalization mode from nearby text when the editor does not provide one.
        val nearby = if (enabled && editorCaps == 0) connection?.getTextBeforeCursor(128, 0) else null
        val localCaps = nearby != null && when {
            mode and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0 -> true
            nearby.isEmpty() -> true
            mode and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0 -> nearby.last().isWhitespace()
            else -> nearby.last() == '\n' || Regex("""[.!?]["')\]]?\s+$""").containsMatchIn(nearby)
        }
        val caps = enabled && (editorCaps != 0 || localCaps)
        keyboard.setAutoCapitalization(caps)
    }

    private fun computeSuggestions() {
        if (!::keyboard.isInitialized || restricted || !prefs.suggestions || latinWordActive || editorLayout != EditorLayout.TEXT) return
        if (!composition.active) precedingDirty = true
        val word = currentWordAtCursor()
        // A correction in the middle of a word must be scored as that complete word.
        val editing = isEditingExistingWord(word)
        val prefix = if (editing) word.text else composition.rendered
        val source = composition.source; val context = precedingWords(if (editing) word.prefix.length else 0)
        val englishActive = persistentEnglish
        val latinPrefix = if (englishActive) {
            if (editing) word.text else currentLatinPrefix()
        } else ""
        val token = ++generation
        predictionTask?.cancel(true)
        predictionTask = executor.submit {
            try {
                val values = if (englishActive) englishPrediction.candidates(latinPrefix, context, 3).toMutableList()
                else prediction.candidates(prefix, context, 3).map { it.text }.toMutableList()
                if (AksharaEasterEgg.isCompleteTrueName(prefix, source)) values.add(0, AksharaEasterEgg.TRUE_NAME_DISPLAY)
                val emojiQuery = if (englishActive) latinPrefix else prefix
                val emojiHits = if (prefs.emojiSuggestions && emojiQuery.isNotBlank()) {
                    emoji.search(emojiQuery, 2, scanNames = false)
                } else emptyList()
                main.post { if (token == generation) keyboard.setCandidates(values.distinct().take(3), emojiHits) }
            } catch (_: Throwable) {
                main.post { if (token == generation) keyboard.setCandidates(emptyList()) }
            }
        }
    }
    private data class CursorWord(val prefix: String, val suffix: String) {
        val text get() = prefix + suffix
    }

    private fun currentWordAtCursor(): CursorWord {
        val ic = currentInputConnection ?: return CursorWord(composition.rendered, "")
        val before = ic.getTextBeforeCursor(256, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(256, 0)?.toString().orEmpty()
        return CursorWord(before.takeLastWhile(::isWordCharacter), after.takeWhile(::isWordCharacter))
    }

    private fun currentLatinPrefix(): String = currentInputConnection?.getTextBeforeCursor(128, 0)?.toString()
        ?.takeLastWhile { it.isLetter() || it == '\'' || it == '’' }.orEmpty()

    private fun isEditingExistingWord(word: CursorWord): Boolean {
        if (word.suffix.isNotEmpty()) return true
        // With no active composition, text before the cursor belongs to the host document.
        // This covers deleting the final character of an existing word, where there is no suffix.
        return if (!composition.active) word.prefix.isNotEmpty()
        else word.prefix.length > composition.rendered.length
    }

    private fun isWordCharacter(char: Char): Boolean {
        val type = Character.getType(char)
        return char.isLetter() || (persistentEnglish && (char == '\'' || char == '’')) || type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt()
    }

    private fun replaceEditedWord(value: String, word: CursorWord) {
        val ic = currentInputConnection ?: return
        val selection = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)?.selectionEnd ?: return
        ic.beginBatchEdit()
        try {
            ic.finishComposingText()
            ic.setSelection((selection - word.prefix.length).coerceAtLeast(0), selection + word.suffix.length)
            ic.commitText(value, 1)
        } finally {
            ic.endBatchEdit()
        }
        preview.clear(); composition.clear(); slsSource.clear()
    }

    private fun precedingWords(currentPrefixLength: Int = 0): List<String> {
        if (!precedingDirty && composition.active) return cachedPreceding
        val before = currentInputConnection?.getTextBeforeCursor(256, 0)?.toString().orEmpty()
        val withoutComposing = if (currentPrefixLength > 0 && before.length >= currentPrefixLength) before.dropLast(currentPrefixLength)
        else if (composition.rendered.isNotEmpty() && before.endsWith(composition.rendered)) before.dropLast(composition.rendered.length) else before
        cachedPreceding = Regex("[\\p{L}\\p{M}]+").findAll(withoutComposing).map { it.value }.toList().takeLast(2)
        precedingDirty = false
        return cachedPreceding
    }
    private fun learn(word: String?) {
        if (word.isNullOrBlank() || restricted) return
        val previous = previousCommittedWord
        previousCommittedWord = word
        executor.submit { learning.record(word, previous) }
    }
    private fun captureClipboardHistory(clip: ClipData) {
        if (!prefs.clipboardHistory || !clipboardEligible || secureEditor || clip.itemCount == 0) return
        if (currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) return
        clipboardText(clip)?.let(clipboardHistory::add)
    }

    private fun refreshClipboard(fresh: Boolean = false) {
        if ((!prefs.clipboardHistory && !prefs.clipboardPreview) || !clipboardEligible || secureEditor) {
            latestClipboard = null
            stagedImage = null
            stagingSource = null
            clipboardGeneration++
            if (::keyboard.isInitialized) keyboard.setClipboardPreview(null)
            return
        }
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = runCatching { manager.primaryClip }.getOrNull()
        if (clip == null || clip.itemCount == 0 || clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) {
            latestClipboard = null
            stagedImage = null
            stagingSource = null
            clipboardGeneration++
            if (::keyboard.isInitialized) keyboard.setClipboardPreview(null)
            return
        }
        captureClipboardHistory(clip)
        val next = clip.takeIf { prefs.clipboardPreview && (fresh || isRecentClipboard(it.description)) }
        if (fresh || next?.getItemAt(0)?.uri != latestClipboard?.getItemAt(0)?.uri) {
            stagedImage = null
            stagingSource = null
            clipboardGeneration++
        }
        latestClipboard = next
        updateClipboardPreview()
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        refreshClipboard(fresh = true)
        if (::keyboard.isInitialized) keyboard.setClipboardItems(clipboardHistory.items(), clipboardHistory.pinnedItems())
    }

    private fun listenForClipboard() {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.removePrimaryClipChangedListener(clipListener)
        if (inputViewActive && (prefs.clipboardHistory || prefs.clipboardPreview) && clipboardEligible && !secureEditor) {
            manager.addPrimaryClipChangedListener(clipListener)
        }
    }

    private fun stopClipboardListener() {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(clipListener)
    }

    private fun clearClipboardPreview() {
        latestClipboard = null
        stagedImage = null
        stagingSource = null
        clipboardGeneration++
        if (::keyboard.isInitialized) keyboard.setClipboardPreview(null)
    }

    private fun clipboardText(clip: ClipData): String? {
        if (clip.itemCount == 0) return null
        val item = clip.getItemAt(0)
        item.text?.toString()?.let { return it.takeIf(String::isNotBlank) }
        item.htmlText?.let { html ->
            return android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY)
                .toString().takeIf(String::isNotBlank)
        }
        if ((0 until clip.description.mimeTypeCount).any { clip.description.getMimeType(it).startsWith("text/") }) {
            return item.coerceToText(this)?.toString()?.takeIf(String::isNotBlank)
        }
        return null
    }

    private fun compatibleImageMime(description: ClipDescription): String? {
        val accepted = EditorInfoCompat.getContentMimeTypes(currentInputEditorInfo ?: return null)
        if (accepted.isEmpty()) return null
        return (0 until description.mimeTypeCount)
            .map(description::getMimeType)
            .firstOrNull { offered ->
                offered.startsWith("image/") && accepted.any { wanted -> ClipDescription.compareMimeTypes(offered, wanted) }
            }
    }

    private fun isRecentClipboard(description: ClipDescription): Boolean {
        val timestamp = description.timestamp
        return timestamp <= 0L || System.currentTimeMillis() - timestamp <= CLIPBOARD_PREVIEW_MAX_AGE_MS
    }

    private fun updateClipboardPreview() {
        if (!::keyboard.isInitialized) return
        val clip = latestClipboard
        if (clip == null || clip.itemCount == 0 || !prefs.clipboardPreview || !clipboardEligible || secureEditor) {
            keyboard.setClipboardPreview(null)
            return
        }
        val image = clip.getItemAt(0).uri != null && compatibleImageMime(clip.description) != null
        val text = clipboardText(clip)
        when {
            image -> prepareClipboardImage(clip)
            text != null -> {
                val previewText = text.replace(Regex("\\s+"), " ").trim().take(64)
                keyboard.setClipboardPreview(previewText)
            }
            else -> keyboard.setClipboardPreview(null)
        }
    }

    private fun prepareClipboardImage(clip: ClipData) {
        val source = clip.getItemAt(0).uri ?: return
        val mime = compatibleImageMime(clip.description) ?: return
        if (stagedImage?.source == source) {
            keyboard.setClipboardPreview("Image", true)
            return
        }
        if (stagingSource == source) {
            keyboard.setClipboardPreview("Preparing image…", true)
            return
        }
        stagingSource = source
        val token = ++clipboardGeneration
        keyboard.setClipboardPreview("Preparing image…", true)
        clipboardExecutor.submit {
            val staged = clipboardImageCache.stage(source, mime)
            main.post {
                if (token != clipboardGeneration || latestClipboard?.getItemAt(0)?.uri != source) return@post
                stagingSource = null
                stagedImage = staged?.let { StagedImage(source, it, mime) }
                if (stagedImage != null) keyboard.setClipboardPreview("Image", true)
                else keyboard.setClipboardPreview(null)
            }
        }
    }

    private fun applyPreferenceChange(key: String?) {
        prefs = KeyboardPreferences(this)
        if (!::keyboard.isInitialized) return
        if (key == "persistent_english" && persistentEnglish == prefs.persistentEnglish) return
        commitComposition()
        val recreate = key == null || key == KeyboardPreferences.THEME || key == "high_contrast"
        if (recreate) {
            keyboard = KeyboardView(this, this, prefs)
            setInputView(keyboard)
        }
        persistentEnglish = prefs.persistentEnglish
        keyboard.configure(
            prefs.mode,
            offerSystemSwitch(),
            enterLabel(currentInputEditorInfo),
            editorLayout,
            english = persistentEnglish
        )
        keyboard.learningEnabled = !restricted && editorLayout == EditorLayout.TEXT
        keyboard.setRecentEmoji(recentEmojiStore.items())
        keyboard.setClipboardItems(clipboardHistory.items(), clipboardHistory.pinnedItems())
        if (!prefs.inlineAutofill) keyboard.setInlineAutofillSuggestions(emptyList())
        if (inputViewActive) refreshClipboard() else clearClipboardPreview()
        listenForClipboard()
        applySystemBarAppearance()
        updateSuggestions()
    }

    private fun rememberEmoji(value: String) {
        recentEmoji = recentEmojiStore.add(value).toMutableList()
        if (::keyboard.isInitialized) keyboard.setRecentEmoji(recentEmoji)
    }
    private fun feedback() {
        if (prefs.haptics && ::keyboard.isInitialized) {
            val type = if (Build.VERSION.SDK_INT >= 27) android.view.HapticFeedbackConstants.KEYBOARD_PRESS else android.view.HapticFeedbackConstants.KEYBOARD_TAP
            keyboard.performHapticFeedback(type)
        }
        if (prefs.keySounds) (getSystemService(AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEY_CLICK, .35f)
    }
    private fun offerSystemSwitch(): Boolean = if (Build.VERSION.SDK_INT >= 28) shouldOfferSwitchingToNextInputMethod()
        else (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).shouldOfferSwitchingToNextInputMethod(window.window?.attributes?.token)
    private fun switchSystemKeyboard() {
        if (Build.VERSION.SDK_INT >= 28) switchToNextInputMethod(false)
        else (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).switchToNextInputMethod(window.window?.attributes?.token, false)
    }

    private fun applySystemBarAppearance() {
        val win = window?.window ?: return
        WindowCompat.setDecorFitsSystemWindows(win, false)
        win.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        val darkKeyboard = ::keyboard.isInitialized && keyboard.isDarkTheme()
        WindowCompat.getInsetsController(win, win.decorView).isAppearanceLightNavigationBars = !darkKeyboard
        if (Build.VERSION.SDK_INT >= 29) {
            win.isNavigationBarContrastEnforced = false
            win.decorView.isForceDarkAllowed = false
        }
        win.navigationBarColor = if (::keyboard.isInitialized) keyboard.keyboardBackground() else android.graphics.Color.TRANSPARENT
    }

    companion object {
        private const val SUGGESTION_DEBOUNCE_MS = 24L
        private const val CLIPBOARD_PREVIEW_MAX_AGE_MS = 5 * 60 * 1000L
        fun enterAction(info: EditorInfo?): Int {
            if (info == null) return EditorInfo.IME_ACTION_NONE
            if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EditorInfo.IME_ACTION_NONE
            return info.imeOptions and EditorInfo.IME_MASK_ACTION
        }
        fun isSecureEditor(info: EditorInfo): Boolean {
            val cls = info.inputType and InputType.TYPE_MASK_CLASS
            val variation = info.inputType and InputType.TYPE_MASK_VARIATION
            return (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) || cls == InputType.TYPE_CLASS_TEXT && variation in setOf(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            )
        }
        fun isClipboardEditor(info: EditorInfo): Boolean =
            info.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT && !isSecureEditor(info)

        fun supportsSinhala(layout: EditorLayout): Boolean =
            layout == EditorLayout.TEXT || layout == EditorLayout.URI
        fun isRestrictedEditor(info: EditorInfo): Boolean {
            val cls = info.inputType and InputType.TYPE_MASK_CLASS
            val variation = info.inputType and InputType.TYPE_MASK_VARIATION
            if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME) return true
            if (cls == InputType.TYPE_CLASS_TEXT && variation in setOf(
                    InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI, InputType.TYPE_TEXT_VARIATION_FILTER
                )) return true
            return info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0 ||
                info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0 ||
                info.imeOptions and EditorInfo.IME_MASK_ACTION == EditorInfo.IME_ACTION_SEARCH
        }
        fun enterLabel(info: EditorInfo?): String = if (info?.actionLabel != null && info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0) info.actionLabel.toString() else when (enterAction(info)) {
            EditorInfo.IME_ACTION_GO -> "Go"; EditorInfo.IME_ACTION_SEARCH -> "⌕"; EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"; EditorInfo.IME_ACTION_PREVIOUS -> "Previous"; EditorInfo.IME_ACTION_DONE -> "Done"; else -> "↵"
        }

        fun editorLayout(info: EditorInfo?): EditorLayout {
            if (info == null) return EditorLayout.TEXT
            val cls = info.inputType and InputType.TYPE_MASK_CLASS
            val variation = info.inputType and InputType.TYPE_MASK_VARIATION
            return when (cls) {
                InputType.TYPE_CLASS_NUMBER -> {
                    val decimal = info.inputType and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0
                    val signed = info.inputType and InputType.TYPE_NUMBER_FLAG_SIGNED != 0
                    when { decimal && signed -> EditorLayout.SIGNED_DECIMAL; decimal -> EditorLayout.DECIMAL; signed -> EditorLayout.SIGNED_NUMBER; else -> EditorLayout.NUMBER }
                }
                InputType.TYPE_CLASS_PHONE -> EditorLayout.PHONE
                InputType.TYPE_CLASS_DATETIME -> EditorLayout.DATETIME
                InputType.TYPE_CLASS_TEXT -> when (variation) {
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> EditorLayout.EMAIL
                    InputType.TYPE_TEXT_VARIATION_URI -> EditorLayout.URI
                    InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> EditorLayout.ASCII
                    else -> EditorLayout.TEXT
                }
                else -> EditorLayout.TEXT
            }
        }
    }
}
