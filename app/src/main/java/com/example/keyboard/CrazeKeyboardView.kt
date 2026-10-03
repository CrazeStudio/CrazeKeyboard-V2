package com.example.keyboard

import android.content.Context
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ai.ChatMessage
import com.example.ai.SearchSource
import com.example.ai.SearchResultItem
import com.example.clipboard.ClipboardDao
import com.example.clipboard.ClipboardItemEntity
import com.example.clipboard.ClipboardPanel
import com.example.core.HapticFeedbackHelper
import com.example.core.SoundFeedbackHelper
import com.example.developer.DevKeyType
import com.example.developer.DeveloperToolbar
import com.example.developer.ModifierState
import com.example.emoji.LocalEmojiAiEngine
import com.example.settings.KeyboardPreferences
import com.example.suggestions.SuggestionEngine
import com.example.themes.KeyboardTheme
import com.example.themes.ThemePresets
import kotlinx.coroutines.launch

@Composable
fun CrazeKeyboardView(
    context: Context,
    preferences: KeyboardPreferences,
    clipboardDao: ClipboardDao,
    suggestionEngine: SuggestionEngine,
    currentWordPrefix: String,
    isSentenceStart: Boolean = true,
    isActualSentenceStart: Boolean = true,
    isPreviousUpper: Boolean = false,
    isSelectModeActive: Boolean = false,
    imeAction: Int = EditorInfo.IME_ACTION_NONE,
    inlineSuggestionViews: List<View> = emptyList(),
    listener: KeyboardActionListener,
    modifier: Modifier = Modifier
) {
    val hapticHelper = remember { HapticFeedbackHelper.getInstance(context) }
    val soundHelper = remember { SoundFeedbackHelper.getInstance(context) }
    val currentView = LocalView.current
    val modifierState = remember { ModifierState() }
    val coroutineScope = rememberCoroutineScope()

    var keyboardMode by remember { mutableStateOf(KeyboardMode.ALPHA) }
    var shiftState by remember {
        mutableStateOf(if (preferences.isAutoCapEnabled && isSentenceStart) ShiftState.SHIFTED else ShiftState.OFF)
    }
    var lastShiftTapTime by remember { mutableStateOf(0L) }
    var lastSpaceTapTime by remember { mutableStateOf(0L) }
    var activePanel by remember { mutableStateOf(ActivePanel.KEYBOARD) }
    var mediaInitialTab by remember { mutableStateOf(MediaTab.EMOJI) }
    var sourceLang by remember { mutableStateOf("en") }
    var targetLang by remember { mutableStateOf("es") }
    var swiftAiTab by remember { mutableStateOf(SwiftKeyAiTab.CHATBOT) }

    // AI State
    var aiChatMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var aiIsProcessing by remember { mutableStateOf(false) }
    var aiResultText by remember { mutableStateOf<String?>(null) }
    var snapshotContextText by remember { mutableStateOf("") }
    var aiSearchSource by remember { mutableStateOf(SearchSource.ALL) }
    var aiQuickSearchResults by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }

    // State for Panels
    var isAiPanelOpen by remember { mutableStateOf(false) }
    var isLiveTranslateActive by remember { mutableStateOf(false) }
    var isMediaSearchMode by remember { mutableStateOf(false) }
    
    var localSearchQuery by remember { mutableStateOf("") }

    // Theme resolution (optimized with remember)
    val isSystemDark = isSystemInDarkTheme()
    val currentTheme: KeyboardTheme = remember(
        preferences.themeId, preferences.isCustomThemeEnabled, preferences.customBgColorHex,
        preferences.customKeyColorHex, preferences.customAccentColorHex, preferences.customTextColorHex, isSystemDark
    ) {
        ThemePresets.resolveTheme(
            preferences.themeId, isSystemDark, preferences.customBgColorHex,
            preferences.customKeyColorHex, preferences.customAccentColorHex, preferences.customTextColorHex
        )
    }

    // Suggestions and AI state
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var aiContextualEmojis by remember { mutableStateOf<List<String>>(emptyList()) }
    var autoCorrectNotice by remember { mutableStateOf<Pair<String, String>?>(null) }
    var lastAutoCorrectRecord by remember { mutableStateOf<Triple<String, String, Int>?>(null) }

    LaunchedEffect(currentWordPrefix, preferences.isSuggestionsEnabled) {
        if (preferences.isSuggestionsEnabled && currentWordPrefix.isNotEmpty()) {
            val (newSuggestions, newEmojis) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val sug = suggestionEngine.getSuggestionsSync(currentWordPrefix)
                val emojis = if (preferences.isLocalAiEmojiEnabled) {
                    LocalEmojiAiEngine.predictEmojisForText(currentWordPrefix)
                } else emptyList()
                sug to emojis
            }
            suggestions = newSuggestions
            aiContextualEmojis = newEmojis
        } else {
            suggestions = emptyList()
            aiContextualEmojis = emptyList()
        }
    }

    // Auto-Correct Logic
    LaunchedEffect(currentWordPrefix) {
        if (preferences.isAutoCorrectEnabled && currentWordPrefix.endsWith(" ")) {
            val wordToCorrect = currentWordPrefix.trim()
            val correction = suggestionEngine.findAutoCorrection(wordToCorrect, preferences.autoCorrectLevel)
            if (correction != null && correction.lowercase() != wordToCorrect.lowercase()) {
                listener.onReplaceCurrentWord(correction + " ")
                autoCorrectNotice = wordToCorrect to correction
                lastAutoCorrectRecord = Triple(wordToCorrect, correction, correction.length + 1)
            }
        }
    }

    // Stable feedback trigger
    val triggerTapFeedbackWithRole: (com.example.core.KeyHapticRole) -> Unit = remember(hapticHelper, soundHelper, preferences, currentView) {
        { role ->
            if (preferences.isHapticsEnabled) {
                hapticHelper.performMechanicalKeyPress(
                    role = if (preferences.isKeySpecificHapticsEnabled) role else com.example.core.KeyHapticRole.STANDARD,
                    profileId = preferences.hapticProfile,
                    intensityPercent = preferences.hapticIntensityPercent,
                    isStabilizerSimulation = preferences.isStabilizerSimulationEnabled,
                    isHapticsEnabled = true,
                    view = currentView
                )
            }
            if (preferences.isSoundEnabled) {
                soundHelper.playKeyPress(preferences.soundProfile, preferences.soundVolume, true)
            }
        }
    }
    val triggerTapFeedback = remember(triggerTapFeedbackWithRole) { { triggerTapFeedbackWithRole(com.example.core.KeyHapticRole.STANDARD) } }

    // UI Structure
    Box(modifier = modifier.fillMaxWidth().background(Color.Transparent)) {
        val layoutModifier = when (preferences.layoutMode) {
            com.example.settings.KeyboardLayoutMode.ONE_HANDED_LEFT -> Modifier.fillMaxWidth(0.75f).align(Alignment.BottomStart)
            com.example.settings.KeyboardLayoutMode.ONE_HANDED_RIGHT -> Modifier.fillMaxWidth(0.75f).align(Alignment.BottomEnd)
            com.example.settings.KeyboardLayoutMode.FLOATING -> Modifier.fillMaxWidth(0.8f).align(Alignment.Center).padding(bottom = 40.dp, top = 20.dp).clip(RoundedCornerShape(12.dp))
            else -> Modifier.fillMaxWidth().align(Alignment.BottomCenter)
        }

        if (preferences.layoutMode == com.example.settings.KeyboardLayoutMode.ONE_HANDED_LEFT) {
            Box(modifier = Modifier.fillMaxWidth(0.25f).align(Alignment.BottomEnd).height(200.dp).clickable { preferences.updateLayoutMode(com.example.settings.KeyboardLayoutMode.DEFAULT) }, contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(id = R.drawable.ic_arrow_forward), contentDescription = "Expand", tint = currentTheme.textColor, modifier = Modifier.size(32.dp))
            }
        } else if (preferences.layoutMode == com.example.settings.KeyboardLayoutMode.ONE_HANDED_RIGHT) {
            Box(modifier = Modifier.fillMaxWidth(0.25f).align(Alignment.BottomStart).height(200.dp).clickable { preferences.updateLayoutMode(com.example.settings.KeyboardLayoutMode.DEFAULT) }, contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(id = R.drawable.ic_arrow_back), contentDescription = "Expand", tint = currentTheme.textColor, modifier = Modifier.size(32.dp))
            }
        }

        Column(modifier = layoutModifier.background(currentTheme.backgroundColor).navigationBarsPadding().padding(bottom = 4.dp)) {
    // Toolbar Section with isolated clipboard suggestion logic
    ClipboardToolbarSection(
        activePanel = activePanel,
        isAiPanelOpen = isAiPanelOpen,
        isLiveTranslateActive = isLiveTranslateActive,
        isMediaSearchMode = isMediaSearchMode,
        currentWordPrefix = currentWordPrefix,
        currentTheme = currentTheme,
        suggestions = suggestions,
        aiContextualEmojis = aiContextualEmojis,
        preferences = preferences,
        autoCorrectNotice = autoCorrectNotice,
        inlineSuggestionViews = inlineSuggestionViews,
        isActualSentenceStart = isActualSentenceStart,
        clipboardDao = clipboardDao,
        onUndoAutoCorrect = {
            triggerTapFeedback()
            lastAutoCorrectRecord?.let { (original, _, len) ->
                repeat(len) { listener.onBackspace() }
                listener.onText(KeyboardFontManager.transform(original, preferences.currentFontStyle))
            }
            autoCorrectNotice = null
        },
        onReplaceCurrentWord = { listener.onReplaceCurrentWord(it) },
        onText = { listener.onText(it) },
        onSettingsRequested = { listener.onSettingsRequested() },
        onMediaPanelOpen = { mediaInitialTab = it; activePanel = ActivePanel.MEDIA },
        onClipboardPanelOpen = { activePanel = ActivePanel.CLIPBOARD },
        onToolsMenuOpen = { activePanel = ActivePanel.TOOLS_MENU },
        onToggleDevMode = { preferences.toggleDeveloperMode() },
        onTranslatePanelOpen = { activePanel = ActivePanel.TRANSLATE },
        onFontsPanelOpen = { activePanel = ActivePanel.FONTS },
        onLanguagePickerPanelOpen = { activePanel = ActivePanel.LANGUAGE_PICKER },
        onCursorNavPanelOpen = { activePanel = ActivePanel.CURSOR_NAV },
        onAiChatPanelOpen = { 
            activePanel = ActivePanel.SWIFT_AI
            snapshotContextText = listener.getCurrentContextText()
            localSearchQuery = ""
        },
        onToggleSound = { preferences.setSound(!preferences.isSoundEnabled) },
        onToggleNumberRow = { preferences.setNumberRow(!preferences.isNumberRowEnabled) },
        onVoiceInput = { listener.onVoiceRequested() },
        onCycleTheme = { 
            val themes = listOf("light", "dark", "blue", "green", "pink", "amoled")
            val currentIdx = themes.indexOf(preferences.themeId)
            val nextIdx = (currentIdx + 1) % themes.size
            preferences.setTheme(themes[nextIdx])
        },
        onLiveTranslateToggle = { isLiveTranslateActive = it },
        onMediaSearchClose = { isMediaSearchMode = false },
        onTriggerFeedback = { triggerTapFeedback() },
        onSuggestionLearn = { coroutineScope.launch { suggestionEngine.learnWord(it) } },
        localSearchQuery = localSearchQuery,
        onClearSearchQuery = { localSearchQuery = "" }
    )

            // Developer Toolbar
            AnimatedVisibility(visible = preferences.isDeveloperModeEnabled && activePanel == ActivePanel.KEYBOARD, enter = expandVertically(), exit = shrinkVertically()) {
                DeveloperToolbar(
                    modifierState = modifierState, theme = currentTheme,
                    onDevKeyClick = { devKey ->
                        triggerTapFeedback()
                        if (devKey.type == DevKeyType.CTRL) modifierState.toggleCtrl()
                        else if (devKey.type == DevKeyType.ALT) modifierState.toggleAlt()
                        else { listener.onDevKey(devKey, modifierState); modifierState.resetTransientModifiers() }
                    }
                )
            }

            // AI Panel overlay - shown above the keyboard so the user can type!
            AnimatedVisibility(
                visible = activePanel == ActivePanel.SWIFT_AI,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    SwiftKeyAiPanel(
                        theme = currentTheme,
                        preferences = preferences,
                        contextText = snapshotContextText,
                        currentQuery = localSearchQuery,
                        activeTab = swiftAiTab,
                        chatMessages = aiChatMessages,
                        onTabChange = { swiftAiTab = it },
                        onClearQuery = { 
                            localSearchQuery = ""
                        },
                        onSendChatMessage = { query ->
                            val userMsg = ChatMessage(isUser = true, text = query)
                            aiChatMessages = aiChatMessages + userMsg
                            aiIsProcessing = true
                            localSearchQuery = "" // Clear after sending
                            coroutineScope.launch {
                                val (response, searchResults) = com.example.ai.AiRepository.sendChatWithSearch(
                                    conversation = aiChatMessages,
                                    searchSource = aiSearchSource,
                                    contextText = snapshotContextText
                                )
                                aiChatMessages = aiChatMessages + ChatMessage(isUser = false, text = response, searchResults = searchResults)
                                aiIsProcessing = false
                            }
                        },
                        onClearChat = { aiChatMessages = emptyList() },
                        onSendQuickQuery = { query ->
                            aiIsProcessing = true
                            aiResultText = null
                            localSearchQuery = "" // Clear after sending
                            coroutineScope.launch {
                                val (response, searchResults) = com.example.ai.AiRepository.askWithSearch(
                                    query = query,
                                    searchSource = aiSearchSource,
                                    contextText = snapshotContextText
                                )
                                aiResultText = response
                                aiQuickSearchResults = searchResults
                                aiIsProcessing = false
                            }
                        },
                        onRewriteTone = { tone ->
                            aiIsProcessing = true
                            aiResultText = null
                            coroutineScope.launch {
                                val response = com.example.ai.AiRepository.rewriteTone(
                                    text = snapshotContextText,
                                    tone = tone
                                )
                                aiResultText = response
                                aiIsProcessing = false
                            }
                        },
                        onReplaceText = { listener.onReplaceContextText(it) },
                        onInsertText = { listener.onText(it) },
                        isProcessing = aiIsProcessing,
                        aiResultText = aiResultText,
                        searchSource = aiSearchSource,
                        onSearchSourceChange = { aiSearchSource = it },
                        quickSearchResults = aiQuickSearchResults,
                        onClose = { activePanel = ActivePanel.KEYBOARD }
                    )
                }
            }

            // Main Panel Section
            val panelHeight = if (preferences.isNumberRowEnabled && keyboardMode == KeyboardMode.ALPHA) (preferences.keyHeightDp * 4.9f + 16).dp else (preferences.keyHeightDp * 4f + 16).dp
            Box(modifier = Modifier.fillMaxWidth().height(panelHeight)) {
                AnimatedContent(
                    targetState = activePanel,
                    transitionSpec = { fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(120)) },
                    label = "PanelTransition"
                ) { targetPanel ->
                    when (targetPanel) {
                        ActivePanel.MEDIA -> MediaPanel(
                            theme = currentTheme, preferences = preferences, initialTab = mediaInitialTab,
                            onEmojiSelected = { triggerTapFeedback(); preferences.addRecentEmoji(it); listener.onText(it) },
                            onGifSelected = { triggerTapFeedback(); listener.onMedia(it.url) },
                            onOpenSearch = { isMediaSearchMode = true; activePanel = ActivePanel.KEYBOARD },
                            onBackspace = { triggerTapFeedback(); listener.onBackspace() },
                            onClose = { activePanel = ActivePanel.KEYBOARD }
                        )
                        ActivePanel.CLIPBOARD -> ClipboardPanel(
                            clipboardDao = clipboardDao, theme = currentTheme,
                            onPasteText = { triggerTapFeedback(); listener.onText(it); activePanel = ActivePanel.KEYBOARD },
                            onCloseClipboard = { activePanel = ActivePanel.KEYBOARD }
                        )
                        ActivePanel.TOOLS_MENU -> ToolsMenuPanel(
                            theme = currentTheme, preferences = preferences,
                            onToolSelected = { toolId ->
                                triggerTapFeedback()
                                when (toolId) {
                                    "TRANSLATE" -> activePanel = ActivePanel.TRANSLATE
                                    "FONTS" -> activePanel = ActivePanel.FONTS
                                    "LANGUAGE" -> activePanel = ActivePanel.LANGUAGE_PICKER
                                    "CURSOR_NAV" -> activePanel = ActivePanel.CURSOR_NAV
                                    "AI" -> activePanel = ActivePanel.SWIFT_AI
                                    "CLIPBOARD" -> activePanel = ActivePanel.CLIPBOARD
                                    "GIF" -> { mediaInitialTab = MediaTab.GIF; activePanel = ActivePanel.MEDIA }
                                    "THEME" -> {
                                        val themes = listOf("light", "dark", "blue", "green", "pink", "amoled")
                                        val currentIdx = themes.indexOf(preferences.themeId)
                                        val nextIdx = (currentIdx + 1) % themes.size
                                        preferences.setTheme(themes[nextIdx])
                                    }
                                    "SOUND" -> preferences.setSound(!preferences.isSoundEnabled)
                                    "NUMBER_ROW" -> preferences.setNumberRow(!preferences.isNumberRowEnabled)
                                    "SETTINGS" -> listener.onSettingsRequested()
                                    "VOICE" -> listener.onVoiceRequested()
                                    "DEV_MODE" -> preferences.toggleDeveloperMode()
                                }
                            },
                            onOpenCustomize = { activePanel = ActivePanel.CUSTOMIZE_HOTBAR },
                            onClose = { activePanel = ActivePanel.KEYBOARD }
                        )
                        ActivePanel.TRANSLATE -> TranslatePanel(
                            theme = currentTheme,
                            sourceLanguageCode = sourceLang,
                            targetLanguageCode = targetLang,
                            onSourceLanguageChange = { sourceLang = it },
                            onTargetLanguageChange = { targetLang = it },
                            onInsertTranslatedText = { triggerTapFeedback(); listener.onText(it) },
                            onStartLiveTranslate = { isLiveTranslateActive = true; activePanel = ActivePanel.KEYBOARD },
                            onClose = { activePanel = ActivePanel.KEYBOARD }
                        )
                        ActivePanel.FONTS -> FontsPanel(
                            theme = currentTheme,
                            currentStyleId = preferences.currentFontStyle,
                            onStyleSelected = { preferences.setFontStyle(it) },
                            onClose = { activePanel = ActivePanel.KEYBOARD }
                        )
                        ActivePanel.LANGUAGE_PICKER -> LanguagePickerPanel(
                            theme = currentTheme,
                            currentLanguageCode = preferences.currentLanguage,
                            onLanguageSelected = { preferences.setLanguage(it); activePanel = ActivePanel.KEYBOARD },
                            onClose = { activePanel = ActivePanel.KEYBOARD }
                        )
                        ActivePanel.CURSOR_NAV -> CursorNavPanel(
                            theme = currentTheme,
                            isSelectMode = isSelectModeActive,
                            onAction = { listener.onSpecialKey(it) },
                            onClose = { activePanel = ActivePanel.KEYBOARD }
                        )
                        ActivePanel.CUSTOMIZE_HOTBAR -> CustomizeHotbarPanel(
                            theme = currentTheme,
                            initialHotbarTools = preferences.hotbarTools,
                            onSave = { preferences.updateHotbarTools(it); activePanel = ActivePanel.KEYBOARD },
                            onReset = { preferences.updateHotbarTools(listOf("AI", "TRANSLATE", "GIF", "CLIPBOARD", "CURSOR_NAV", "FONTS", "LANGUAGE")) },
                            onClose = { activePanel = ActivePanel.KEYBOARD }
                        )
                        ActivePanel.AI_CHAT -> AiChatPanel(
                            theme = currentTheme,
                            initialContext = currentWordPrefix,
                            onBack = { activePanel = ActivePanel.KEYBOARD },
                            onInsert = { triggerTapFeedback(); listener.onText(it) }
                        )
                        ActivePanel.SWIFT_AI, ActivePanel.KEYBOARD -> KeyboardGrid(
                            context = context, theme = currentTheme, preferences = preferences,
                            suggestionEngine = suggestionEngine,
                            keyboardMode = keyboardMode, shiftState = shiftState, imeAction = imeAction,
                            onKeyTap = { key ->
                                triggerTapFeedbackWithRole(if (key.type == KeyType.CHARACTER) com.example.core.KeyHapticRole.STANDARD else com.example.core.KeyHapticRole.MODIFIER)
                                when (key.type) {
                                    KeyType.CHARACTER -> {
                                        val text = if (shiftState != ShiftState.OFF) key.primaryText.uppercase() else key.primaryText
                                        val transformed = KeyboardFontManager.transform(text, preferences.currentFontStyle)
                                        if (activePanel == ActivePanel.SWIFT_AI || isMediaSearchMode) {
                                            localSearchQuery += text
                                        } else {
                                            listener.onText(transformed)
                                        }
                                        if (shiftState == ShiftState.SHIFTED) shiftState = ShiftState.OFF
                                    }
                                    KeyType.BACKSPACE -> {
                                        if (activePanel == ActivePanel.SWIFT_AI || isMediaSearchMode) {
                                            if (localSearchQuery.isNotEmpty()) {
                                                localSearchQuery = localSearchQuery.dropLast(1)
                                            }
                                        } else {
                                            listener.onBackspace()
                                        }
                                    }
                                    KeyType.ENTER -> {
                                        if (activePanel == ActivePanel.SWIFT_AI || isMediaSearchMode) {
                                            // Handled by Send buttons
                                        } else {
                                            listener.onEnter()
                                        }
                                    }
                                    KeyType.SHIFT -> {
                                        val now = System.currentTimeMillis()
                                        if (now - lastShiftTapTime < 300) shiftState = ShiftState.CAPS_LOCK
                                        else shiftState = when (shiftState) {
                                            ShiftState.OFF -> ShiftState.SHIFTED
                                            ShiftState.SHIFTED -> ShiftState.OFF
                                            ShiftState.CAPS_LOCK -> ShiftState.OFF
                                        }
                                        lastShiftTapTime = now
                                    }
                                    KeyType.SPACE -> {
                                        val now = System.currentTimeMillis()
                                        if (activePanel == ActivePanel.SWIFT_AI || isMediaSearchMode) {
                                            localSearchQuery += " "
                                        } else {
                                            if (now - lastSpaceTapTime < 300 && preferences.isDoubleSpacePeriodEnabled) {
                                                listener.onBackspace(); listener.onText(". ")
                                            } else {
                                                listener.onText(" ")
                                            }
                                        }
                                        lastSpaceTapTime = now
                                    }
                                    KeyType.MODE_CHANGE -> keyboardMode = if (keyboardMode == KeyboardMode.ALPHA) KeyboardMode.NUMBERS else KeyboardMode.ALPHA
                                    KeyType.EMOJI -> { mediaInitialTab = MediaTab.EMOJI; activePanel = ActivePanel.MEDIA }
                                    else -> {}
                                }
                            },
                            onKeyLongPress = { key ->
                                hapticHelper.performLongPressHaptic(preferences.isHapticsEnabled, currentView)
                                val insert = key.longPressOptions.firstOrNull() ?: key.alternateText
                                if (insert.isNotEmpty()) {
                                    if (activePanel == ActivePanel.SWIFT_AI || isMediaSearchMode) {
                                        localSearchQuery += insert
                                    } else {
                                        listener.onText(KeyboardFontManager.transform(insert, preferences.currentFontStyle))
                                    }
                                }
                            },
                            onKeyInsertText = { 
                                if (activePanel == ActivePanel.SWIFT_AI || isMediaSearchMode) {
                                    localSearchQuery += it
                                } else {
                                    listener.onText(KeyboardFontManager.transform(it, preferences.currentFontStyle))
                                }
                            },
                            onMoveCursor = { listener.onMoveCursor(it) },
                            onSpecialKey = { listener.onSpecialKey(it) },
                            onDeleteWord = { 
                                if (activePanel == ActivePanel.SWIFT_AI || isMediaSearchMode) {
                                    localSearchQuery = localSearchQuery.trimEnd().dropLastWhile { !it.isWhitespace() }.trimEnd()
                                } else {
                                    listener.onDeleteWord()
                                }
                            },
                            onBackspace = { 
                                if (activePanel == ActivePanel.SWIFT_AI || isMediaSearchMode) {
                                    if (localSearchQuery.isNotEmpty()) {
                                        localSearchQuery = localSearchQuery.dropLast(1)
                                    }
                                } else {
                                    listener.onBackspace()
                                }
                            },
                            onGlideWord = { word ->
                                triggerTapFeedback()
                                val wordToInsert = if (shiftState != ShiftState.OFF) word.uppercase() else if (isSentenceStart) word.replaceFirstChar { it.uppercase() } else word
                                if (activePanel == ActivePanel.SWIFT_AI || isMediaSearchMode) {
                                    localSearchQuery += (if (localSearchQuery.isNotEmpty()) " " else "") + wordToInsert
                                } else {
                                    listener.onText(KeyboardFontManager.transform(wordToInsert, preferences.currentFontStyle) + " ")
                                }
                                if (shiftState == ShiftState.SHIFTED) shiftState = ShiftState.OFF
                                coroutineScope.launch { suggestionEngine.learnWord(word) }
                            }
                        )
                        else -> Box(Modifier.fillMaxSize()) // Fallback for other panels
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipboardToolbarSection(
    activePanel: ActivePanel,
    isAiPanelOpen: Boolean,
    isLiveTranslateActive: Boolean,
    isMediaSearchMode: Boolean,
    currentWordPrefix: String,
    currentTheme: KeyboardTheme,
    suggestions: List<String>,
    aiContextualEmojis: List<String>,
    preferences: KeyboardPreferences,
    autoCorrectNotice: Pair<String, String>?,
    inlineSuggestionViews: List<android.view.View>,
    isActualSentenceStart: Boolean,
    clipboardDao: ClipboardDao,
    onUndoAutoCorrect: () -> Unit,
    onReplaceCurrentWord: (String) -> Unit,
    onText: (String) -> Unit,
    onSettingsRequested: () -> Unit,
    onMediaPanelOpen: (MediaTab) -> Unit,
    onClipboardPanelOpen: () -> Unit,
    onToolsMenuOpen: () -> Unit,
    onToggleDevMode: () -> Unit,
    onTranslatePanelOpen: () -> Unit,
    onFontsPanelOpen: () -> Unit,
    onLanguagePickerPanelOpen: () -> Unit,
    onCursorNavPanelOpen: () -> Unit,
    onAiChatPanelOpen: () -> Unit,
    onToggleSound: () -> Unit,
    onToggleNumberRow: () -> Unit,
    onVoiceInput: () -> Unit,
    onCycleTheme: () -> Unit,
    onLiveTranslateToggle: (Boolean) -> Unit,
    onMediaSearchClose: () -> Unit,
    onTriggerFeedback: () -> Unit,
    onSuggestionLearn: (String) -> Unit,
    localSearchQuery: String,
    onClearSearchQuery: () -> Unit
) {
    // Collect clipboard state locally to this component to avoid recomposing the whole keyboard
    val latestClipItem by clipboardDao.getLatestItem().collectAsStateWithLifecycle(initialValue = null)
    var consumedClipId by remember { mutableStateOf(0L) }
    
    val clipboardSuggestion = remember(latestClipItem, consumedClipId) {
        val clip = latestClipItem
        if (clip != null && clip.id != consumedClipId && clip.text.isNotBlank() && 
            (System.currentTimeMillis() - clip.timestamp < 300_000L)) {
            clip.text
        } else {
            null
        }
    }

    if (isAiPanelOpen) {
        // AI Panel logic if needed
    } else if (isLiveTranslateActive) {
        TranslateToolbar(
            theme = currentTheme, sourceLanguageCode = "en", targetLanguageCode = "es",
            sourceText = currentWordPrefix, translatedText = "",
            onSwapLanguages = {},
            onCommitTranslated = { onTriggerFeedback(); onReplaceCurrentWord("$it ") },
            onOpenTranslatePanel = onTranslatePanelOpen,
            onClose = { onLiveTranslateToggle(false) }
        )
    } else if (isMediaSearchMode) {
        MediaSearchToolbar(
            theme = currentTheme,
            query = localSearchQuery,
            tab = MediaTab.EMOJI,
            onClose = onMediaSearchClose,
            onEmojiSelected = { 
                onTriggerFeedback()
                preferences.addRecentEmoji(it)
                onText(it)
                onMediaSearchClose()
                onClearSearchQuery()
            }
        )
    } else {
        KeyboardToolbar(
            theme = currentTheme, suggestions = suggestions,
            aiContextualEmojis = aiContextualEmojis,
            hotbarToolIds = preferences.hotbarTools,
            isDevModeActive = preferences.isDeveloperModeEnabled,
            autoCorrectNotice = autoCorrectNotice,
            clipboardSuggestion = clipboardSuggestion,
            inlineViews = inlineSuggestionViews,
            isSentenceStart = isActualSentenceStart,
            onUndoAutoCorrect = onUndoAutoCorrect,
            onPasteClipboardSuggestion = { 
                onTriggerFeedback()
                latestClipItem?.let { consumedClipId = it.id }
                onText(it)
            },
            onSuggestionClick = { 
                onTriggerFeedback()
                onReplaceCurrentWord("${KeyboardFontManager.transform(it, preferences.currentFontStyle)} ")
                onSuggestionLearn(it)
            },
            onEmojiClick = { 
                onTriggerFeedback()
                preferences.addRecentEmoji(it)
                onText(it)
            },
            onAction = { action ->
                onTriggerFeedback()
                when (action) {
                    ToolbarAction.OPEN_SETTINGS -> onSettingsRequested()
                    ToolbarAction.OPEN_EMOJI -> onMediaPanelOpen(MediaTab.EMOJI)
                    ToolbarAction.OPEN_CLIPBOARD -> onClipboardPanelOpen()
                    ToolbarAction.OPEN_TOOLS_MENU -> onToolsMenuOpen()
                    ToolbarAction.TOGGLE_DEV_MODE -> onToggleDevMode()
                    ToolbarAction.OPEN_TRANSLATE -> onTranslatePanelOpen()
                    ToolbarAction.OPEN_FONTS -> onFontsPanelOpen()
                    ToolbarAction.OPEN_LANGUAGE -> onLanguagePickerPanelOpen()
                    ToolbarAction.OPEN_CURSOR_NAV -> onCursorNavPanelOpen()
                    ToolbarAction.OPEN_AI -> onAiChatPanelOpen()
                    ToolbarAction.OPEN_GIF -> onMediaPanelOpen(MediaTab.GIF)
                    ToolbarAction.TOGGLE_SOUND -> onToggleSound()
                    ToolbarAction.TOGGLE_NUMBER_ROW -> onToggleNumberRow()
                    ToolbarAction.VOICE_INPUT -> onVoiceInput()
                    ToolbarAction.CYCLE_THEME -> onCycleTheme()
                    else -> {}
                }
            }
        )
    }
}
