package com.subhash.chatlauncher

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Calm, paper-like palette: warm off-white background, muted teal accent, soft gray text.
val calmColors = lightColorScheme(
    primary = Color(0xFF3E8E8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCECEA),
    onPrimaryContainer = Color(0xFF1E3A39),
    secondaryContainer = Color(0xFFF0EFEA),
    onSecondaryContainer = Color(0xFF46463F),
    surfaceVariant = Color(0xFFF5F5F2),
    onSurfaceVariant = Color(0xFF6B6B64),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF3A3A36),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF3A3A36),
    surfaceContainerLow = Color(0xFFF8F8F5),
    surfaceContainer = Color(0xFFF5F5F2),
    surfaceContainerHigh = Color(0xFFF0F0EC),
)

// Dark variant of the paper palette: warm near-black paper, light ink, same muted accents.
// Contrast targets: body text >= 4.5:1, large/bold accent text >= 3:1 (checked in ContrastTest).
val darkCalmColors = darkColorScheme(
    primary = Color(0xFF8FC5C1),
    onPrimary = Color(0xFF122626),
    primaryContainer = Color(0xFF2E4A48),
    onPrimaryContainer = Color(0xFFD9EFED),
    secondaryContainer = Color(0xFF33312C),
    onSecondaryContainer = Color(0xFFDDDAD2),
    surfaceVariant = Color(0xFF2A2823),
    onSurfaceVariant = Color(0xFFB3AFA6),
    background = Color(0xFF1B1917),
    onBackground = Color(0xFFEAE7E0),
    surface = Color(0xFF242220),
    onSurface = Color(0xFFEAE7E0),
    surfaceContainerLow = Color(0xFF201E1B),
    surfaceContainer = Color(0xFF2A2823),
    surfaceContainerHigh = Color(0xFF33312E),
)

val sendButtonTextColor = Color(0xFF080502)
val accentBrown = Color(0xFFB08968)

/** Accent for actionable TEXT: darker brown on light surfaces, palette brown on dark - both >=4.5:1. */
val accentTextLight = Color(0xFF8A5A38)
val accentTextDark = accentBrown
val accentText: Color
    @androidx.compose.runtime.Composable
    get() = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) accentTextLight else accentTextDark

/** Faint irregular paper grid behind the chat, matching the calm reference style. */
@Composable
fun PaperGrid(modifier: Modifier = Modifier, wobbly: Boolean = false) {
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.17f)
    Canvas(modifier = modifier) {
        val step = 46.dp.toPx()
        val amp = if (wobbly) 2.5.dp.toPx() else 0f
        fun jitter(line: Int, seg: Int): Float {
            var h = line * 374761393 + seg * 668265263
            h = (h xor (h shr 13)) * 1274126177
            h = h xor (h shr 16)
            return (h % 1000) / 1000f * 2f - 1f
        }
        var li = 0
        var x = step / 2
        while (x < size.width) {
            if (wobbly) {
                val path = Path()
                path.moveTo(x, 0f)
                var y = 0f
                var seg = 0
                while (y <= size.height) { path.lineTo(x + jitter(li, seg) * amp, y); y += 20f; seg++ }
                drawPath(path, lineColor, style = Stroke(width = 1f))
            } else {
                drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            }
            x += step; li++
        }
        var y = step / 2
        while (y < size.height) {
            if (wobbly) {
                val path = Path()
                path.moveTo(0f, y)
                var xx = 0f
                var seg = 0
                while (xx <= size.width) { path.lineTo(xx, y + jitter(li, seg) * amp); xx += 20f; seg++ }
                drawPath(path, lineColor, style = Stroke(width = 1f))
            } else {
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            y += step; li++
        }
    }
}

data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val isHtml: Boolean = false,
    /** True when this reply came from the cloud AI (captioned, tagged in history). */
    val fromCloud: Boolean = false,
    val choiceApps: List<AppEntry> = emptyList(),
    val onChoice: ((AppEntry) -> Unit)? = null,
    /** When set, an explicit "Ask AI" action sends ONLY this prompt to the cloud. */
    val askAi: String? = null,
)

data class AppEntry(
    val label: String,
    val packageName: String,
    val launch: Intent,
    val isWork: Boolean = false,
    val userHandle: android.os.UserHandle? = null,
    val userSerial: Long = -1,
) {
    /** Briefcase marker distinguishes duplicate work-profile entries in the text-first UI. */
    val displayLabel: String get() = if (isWork) "💼 $label" else label

    /**
     * Stable identity across personal/work duplicates of the same package: userSerial is the
     * UserManager serial number of the profile (stable across process restarts), distinguishing
     * multiple managed profiles. Multiple launcher activities within one package in one profile
     * share an identity - the first match wins in the dock.
     */
    val identityKey: String get() =
        if (isWork) packageName + "@work:" + (if (userSerial >= 0) userSerial.toString() else "unknown")
        else packageName

    /** Unambiguous label for personal/work duplicate choices: both sides named. */
    val choiceLabel: String get() = if (isWork) "💼 $label (work)" else "$label (personal)"
}

class MainActivity : ComponentActivity() {

    private lateinit var router: CommandRouter
    private lateinit var historyStore: HistoryStore
    private val pairScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pairMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("chat_launcher", MODE_PRIVATE)
        router = CommandRouter(this, prefs)
        historyStore = HistoryStore(prefs)
        // Hidden-list -> allow-list migration, only with a real app list (else deferred).
        val keys = installedApps().map { it.identityKey }.toSet()
        if (keys.isNotEmpty()) router.migrateHiddenProfiles(keys)
        setContent {
            var themeMode by remember { mutableStateOf(router.themeMode) }
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            MaterialTheme(colorScheme = if (darkTheme) darkCalmColors else calmColors) {
                // System bars wear the paper background too; icons contrast with the theme.
                val barBg = MaterialTheme.colorScheme.background
                val barView = LocalView.current
                SideEffect {
                    val w = (barView.context as android.app.Activity).window
                    w.statusBarColor = barBg.toArgb()
                    w.navigationBarColor = barBg.toArgb()
                    val ctl = WindowInsetsControllerCompat(w, barView)
                    ctl.isAppearanceLightStatusBars = !darkTheme
                    ctl.isAppearanceLightNavigationBars = !darkTheme
                }
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                ChatHome(
                    router = router,
                    pairMessage = pairMessage,
                    onPairMessageConsumed = { pairMessage = null },
                    themeMode = themeMode,
                    onThemeMode = { themeMode = it; router.themeMode = it },
                    resolveApps = { query -> resolveApps(query) },
                    shareTargets = { text -> shareTargets(text) },
                    allApps = { resolveApps("", limit = Int.MAX_VALUE) },
                    history = historyStore,
                    aiAvailable = { router.apiKey.isNotBlank() },
                    timerApp = router.timerIntent()?.let { AppEntry("Timer", CommandRouter.TIMER_KEY, it) },
                    sendToLlm = { history, onDelta ->
                        val key = router.apiKey
                        val endpoint = router.chatEndpoint
                        if (key.isBlank()) {
                            null
                        } else if (endpoint.isNotBlank()) {
                            // Connect AI: the server fixes the (free) model; credential is the bearer.
                            LlmClient.chatStream(key, LlmClient.defaultModel(LlmClient.Provider.OPENROUTER), history, endpoint, onDelta)
                        } else {
                            val provider = LlmClient.providerForKey(key)
                            val gated = LlmClient.gatedModel(provider, router.model, router.freeModelIds.ifEmpty { null })
                            if (gated == null) {
                                "Your saved model '${router.model}' isn't verified as free on OpenRouter - re-save it in Settings > Account or clear it to use the free default."
                            } else {
                                LlmClient.chatStream(key, gated, history, "", onDelta)
                            }
                        }
                    },
                    launchApp = { app ->
                        if (app.packageName == CommandRouter.TIMER_KEY) router.launchTimer()
                        else router.launchAppEntry(app)
                    },
                )
                }
            }
        }
        handlePairIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePairIntent(intent)
    }

    override fun onDestroy() {
        pairScope.cancel()
        super.onDestroy()
    }

    /**
     * One-tap Connect AI pairing: chatlauncher://pair?code=<single-use-code>.
     * Only the code is read from the incoming URI; redemption is pinned to the fixed
     * proxy host, and the URI, code, response and credential are never logged. The
     * intent data is cleared so a recreate or relaunch never redeems twice. Pairing
     * sends nothing to the AI - it only stores the per-install credential.
     */
    private fun pairSay(text: String) {
        historyStore.append("assistant", "local", text)
        pairMessage = text
    }

    private fun handlePairIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val data = intent.data ?: return
        if (!data.scheme.equals("chatlauncher", ignoreCase = true) || !data.host.equals("pair", ignoreCase = true)) return
        var code = data.getQueryParameter("code").orEmpty()
        if (code.isBlank()) {
            // Landing page hands off chatlauncher://pair#code=... (fragment keeps it out of logs/history).
            val frag = data.fragment.orEmpty()
            code = if (frag.startsWith("code=")) frag.removePrefix("code=") else frag
        }
        intent.data = null
        if (code.isBlank() || code.length > 512) {
            pairSay("That pairing link didn't look right - ask for a fresh one. Nothing was sent.")
            return
        }
        pairSay("Pairing link received - connecting AI...")
        pairScope.launch {
            when (val result = LlmClient.redeemPairingCode(code)) {
                is LlmClient.RedeemResult.Connected -> {
                    router.apiKey = result.credential
                    router.chatEndpoint = result.endpoint
                    pairSay("This installation is paired - the credential stays on this device. Each Ask AI request will show its own result.")
                }
                LlmClient.RedeemResult.InvalidOrExpired -> pairSay("That pairing link is expired or already used - ask for a fresh one.")
                LlmClient.RedeemResult.RateLimited -> pairSay("Too many pairing attempts - wait a little, then tap the link again.")
                LlmClient.RedeemResult.Unavailable, LlmClient.RedeemResult.NetworkError -> pairSay("Couldn't reach the pairing service - check your connection and tap the link again.")
                LlmClient.RedeemResult.InvalidShape -> pairSay("That pairing link didn't work - ask for a fresh one.")
            }
        }
    }

    // Apps that can take a text share draft, trimmed by the active profile's allow-list.
    private fun shareTargets(text: String): List<AppEntry> {
        val pm = packageManager
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        val allowed = currentAllowed()
        val resolved = pm.queryIntentActivities(send, 0).map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        val chosen = CommandRouter.shareTargetsOf(resolved, packageName, allowed)
        val byPkg = pm.queryIntentActivities(send, 0).associateBy { it.activityInfo.packageName }
        return chosen.mapNotNull { (label, pkg) ->
            val ri = byPkg[pkg] ?: return@mapNotNull null
            val launch = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                setClassName(pkg, ri.activityInfo.name)
            }
            AppEntry(label, pkg, launch)
        }
    }

    private fun workKeys(): List<String> = installedApps().filter { it.isWork }.map { it.identityKey }

    private fun currentAllowed(): Set<String>? = router.allowedNow(workKeys().toSet())

    // Installed launchable apps are loaded once; profile filtering and search run on the cached list.
    // Work-profile apps come from LauncherApps profiles and are marked with isWork; devices
    // without a managed profile just return the personal set.
    private fun queryInstalledApps(): List<AppEntry> {
        val pm = packageManager
        return try {
            val la = getSystemService(android.content.pm.LauncherApps::class.java)
            val me = android.os.Process.myUserHandle()
            val entries = la.profiles.flatMap { handle ->
                la.getActivityList(null, handle).mapNotNull { info ->
                    val pkg = info.applicationInfo.packageName
                    if (pkg == packageName) return@mapNotNull null
                    val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(info.componentName)
                    val serial = getSystemService(android.os.UserManager::class.java)?.getSerialNumberForUser(handle) ?: -1L
                    AppEntry(info.label.toString(), pkg, launch, isWork = handle != me, userHandle = handle, userSerial = if (handle != me) serial else -1L)
                }
            }
            if (entries.isNotEmpty()) entries else queryInstalledAppsPm(pm)
        } catch (e: Exception) {
            queryInstalledAppsPm(pm)
        }
    }

    private fun queryInstalledAppsPm(pm: android.content.pm.PackageManager): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName) return@mapNotNull null
                val launch = pm.getLaunchIntentForPackage(pkg) ?: return@mapNotNull null
                AppEntry(ri.loadLabel(pm).toString(), pkg, launch)
            }
    }

    private var appListCache: List<AppEntry>? = null

    private fun installedApps(): List<AppEntry> {
        // Retry when the cache caught nothing (e.g. packages appeared after first query).
        val cached = appListCache
        if (!cached.isNullOrEmpty()) return cached
        val fresh = queryInstalledApps()
        if (fresh.isNotEmpty()) appListCache = fresh
        return fresh
    }

    private fun resolveApps(query: String, limit: Int = 6): List<AppEntry> {
        val allowed = currentAllowed()
        return installedApps()
            .asSequence()
            .filter { allowed == null || it.identityKey in allowed }
            .filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
            .sortedBy {
                val i = it.label.indexOf(query, ignoreCase = true)
                if (i < 0) Int.MAX_VALUE else i
            }
            .toList()
            .let { CommandRouter.prioritizeWork(it, router.activeProfile == CommandRouter.WORK) }
            .take(limit)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatHome(
    router: CommandRouter,
    pairMessage: String? = null,
    onPairMessageConsumed: () -> Unit = {},
    themeMode: String = "system",
    onThemeMode: (String) -> Unit = {},
    resolveApps: (String) -> List<AppEntry>,
    shareTargets: (String) -> List<AppEntry>,
    allApps: () -> List<AppEntry>,
    sendToLlm: suspend (List<Pair<String, String>>, suspend (String) -> Unit) -> String?,
    launchApp: (AppEntry) -> Boolean,
    history: HistoryStore? = null,
    aiAvailable: () -> Boolean = { false },
    timerApp: AppEntry? = null,
    userContextStore: UserContextStore? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val roundedFont = remember { RoundedTypeface.load(context) }
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var activeProfile by remember { mutableStateOf(router.activeProfile) }
    var showSettings by remember { mutableStateOf(false) }
    var dockPackages by remember { mutableStateOf(router.dockApps) }
    var gridWobbly by remember { mutableStateOf(router.gridWobbly) }
    // Set when the draft's body is being asked for; the next line completes it.
    var pendingDraft by remember { mutableStateOf<Pair<String, String?>?>(null) }
    // Reasoning profile: local-only onboarding context + launch intervention.
    val userContext = userContextStore ?: remember { UserContextStore(context) }
    var showReasoningOnboarding by remember { mutableStateOf(false) }
    var reasoningIntervention by remember { mutableStateOf<AppEntry?>(null) }
    // Apps the user insisted on this session - no repeated interrogation after that.
    var reasoningBypassed by remember { mutableStateOf(setOf<String>()) }
    // Long-pressed suggestion: offers App info / Uninstall (the OS confirms the uninstall).
    var appActionsFor by remember { mutableStateOf<AppEntry?>(null) }
    val suggestions = remember(input, activeProfile) { if (input.isBlank()) emptyList() else resolveApps(input) }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // Dock: no profile = the user's chosen apps; a profile shows exactly its allow-list
    // (Timer first in Focus; work entries first in Work).
    // First composition can race LauncherApps visibility (Robolectric flushes installs
    // async; a real launcher cold start can hit the same window). Retry briefly after
    // composition so the dock always settles (00:17 diagnostic: zero dock notes until the
    // first keystroke forced a re-query). Bounded; a healthy first query exits at once.
    var allInstalled by remember { mutableStateOf(allApps()) }
    LaunchedEffect(Unit) {
        var apps = allInstalled
        var attempts = 0
        while (apps.isEmpty() && attempts < 10) {
            kotlinx.coroutines.delay(100)
            apps = allApps()
            attempts++
        }
        allInstalled = apps
    }
    val workKeys = allInstalled.filter { it.isWork }.map { it.identityKey }.toSet()
    val dockApps: List<AppEntry> = run {
        val profile = activeProfile
        if (profile == null) {
            val keys = router.normalDockKeys(allInstalled.map { it.identityKey }.toSet())
            keys.mapNotNull { key -> if (key == CommandRouter.TIMER_KEY) timerApp else allInstalled.firstOrNull { it.identityKey == key } }
        } else {
            val allowed = router.effectiveAllowed(profile, workKeys)
            val apps = allInstalled.filter { it.identityKey in allowed }
                .sortedWith(compareByDescending<AppEntry> { it.isWork }.thenBy { it.label })
            (if (CommandRouter.TIMER_KEY in allowed && timerApp != null) listOf(timerApp) else emptyList()) + apps
        }
    }

    // Live clock for the top bar, refreshed every 30s.
    var now by remember { mutableStateOf(SimpleDateFormat("EEE, d MMM - HH:mm", Locale.getDefault()).format(Date())) }
    var lastActivityAt by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = SimpleDateFormat("EEE, d MMM - HH:mm", Locale.getDefault()).format(Date())
            // 30 minutes idle starts a fresh chat - but never silently discards an unsent
            // draft, an unanswered question, or a pending Ask AI / draft-text prompt.
            if (CommandRouter.shouldResetChat(
                    idleMillis = System.currentTimeMillis() - lastActivityAt,
                    hasMessages = messages.isNotEmpty(),
                    hasDraft = input.isNotBlank(),
                    hasPendingQuestion = messages.lastOrNull()?.let { it.choiceApps.isNotEmpty() || it.askAi != null } == true,
                )
            ) {
                messages = emptyList()
                pendingDraft = null
            }
            delay(30_000)
        }
    }

    fun addUser(text: String) {
        messages = messages + ChatMessage(true, text)
        history?.append("user", "local", text)
    }

    fun addAssistant(
        text: String,
        fromCloud: Boolean = false,
        choiceApps: List<AppEntry> = emptyList(),
        onChoice: ((AppEntry) -> Unit)? = null,
        askAi: String? = null,
    ) {
        messages = messages + ChatMessage(false, text, fromCloud = fromCloud, choiceApps = choiceApps, onChoice = onChoice, askAi = askAi)
        if (text.isNotBlank()) history?.append("assistant", if (fromCloud) "cloud" else "local", text)
    }

    // Pairing progress/result is a transient status banner, not conversation/history chatter.
    var pairStatus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pairMessage) {
        val m = pairMessage
        if (m != null) {
            pairStatus = m
            onPairMessageConsumed()
        }
    }
    LaunchedEffect(pairStatus) {
        if (pairStatus != null) {
            delay(8_000)
            pairStatus = null
        }
    }

    fun handleDraft(result: CommandRouter.Result.DraftMessage) {
        when {
            result.text == null -> {
                pendingDraft = result.to to result.pkg
                addAssistant("What should I say to ${result.to}?")
            }
            result.pkg != null -> addAssistant(router.execute(result))
            else -> {
                val targets = shareTargets(result.text)
                addAssistant(
                    "Which app should I put that draft in? You'll pick the chat and send it there.",
                    choiceApps = targets,
                    onChoice = { app ->
                        launchApp(app)
                        addAssistant("Draft opened in ${app.displayLabel} for ${result.to} - pick the chat and tap send there.")
                    },
                )
            }
        }
    }

    fun handleResult(result: CommandRouter.Result) {
        when (result) {
            is CommandRouter.Result.Reply -> {
                if (result.text.startsWith("Profile '")) {
                    activeProfile = router.activeProfile
                    // Typed activation gets the same first-time conversation as the settings path.
                    if (activeProfile == CommandRouter.REASONING && userContext.isEmpty()) showReasoningOnboarding = true
                }
                if (result.text == "Focus mode off.") activeProfile = null
                addAssistant(result.text)
            }
            is CommandRouter.Result.DraftMessage -> handleDraft(result)
            is CommandRouter.Result.ChooseApp -> addAssistant(
                "I see more than one '${result.label}' here - which one?",
                choiceApps = result.apps,
                onChoice = { app ->
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    if (activeProfile == CommandRouter.REASONING && app.identityKey !in reasoningBypassed) {
                        reasoningIntervention = app
                    } else {
                        addAssistant(router.execute(CommandRouter.Result.Launch(app)))
                    }
                },
            )
            else -> {
                val note = router.execute(result)
                if (note.isNotEmpty()) addAssistant(note)
            }
        }
    }

    /** Explicit, per-message cloud AI: ONLY this prompt leaves the phone. No history goes with it. */
    fun askAi(prompt: String) {
        lastActivityAt = System.currentTimeMillis()
        if (!aiAvailable()) {
            addAssistant("Ask AI needs a connection first - tap the pairing link you were sent, or see Settings > Account. Nothing was sent.")
            return
        }
        scope.launch {
            messages = messages + ChatMessage(false, "", fromCloud = true)
            var acc = ""
            val reply = sendToLlm(listOf("user" to prompt)) { delta ->
                acc += delta
                messages = messages.dropLast(1) + ChatMessage(false, acc, fromCloud = true)
            }
            val finalText = reply ?: "Couldn't reach the AI - nothing was sent."
            messages = messages.dropLast(1) + ChatMessage(
                false,
                finalText,
                fromCloud = reply != null,
                isHtml = reply?.contains(Regex("<[a-z]+[ >]")) == true,
            )
            history?.append("assistant", if (reply != null) "cloud" else "local", finalText)
        }
    }

    fun send() {
        val raw = input.trim()
        if (raw.isEmpty()) return
        lastActivityAt = System.currentTimeMillis()
        input = ""
        // A pending "what should I say?" question: this line is the draft body.
        val pending = pendingDraft
        if (pending != null) {
            if (raw.equals("cancel", true) || raw.equals("never mind", true)) {
                pendingDraft = null
                addUser(raw)
                addAssistant("Okay, dropped that draft.")
                return
            }
            pendingDraft = null
            addUser(raw)
            handleDraft(CommandRouter.Result.DraftMessage(pending.first, raw, pending.second))
            return
        }
        // Typed override: "just open it" (or close variants) ends the pause immediately,
        // for this app and this session.
        val pause = reasoningIntervention
        if (pause != null && raw.trim().lowercase().replace(Regex("[^a-z ]"), "").let {
                it == "just open it" || it == "open it" || it == "just open" }) {
            addUser(raw)
            reasoningIntervention = null
            reasoningBypassed = reasoningBypassed + pause.identityKey
            keyboardController?.hide()
            focusManager.clearFocus()
            addAssistant(router.execute(CommandRouter.Result.Launch(pause)))
            return
        }
        addUser(raw)
        val result = router.route(raw, suggestions)
        // A dispatched command leaves the app or opens a dialog/chooser: drop the keyboard so
        // the launcher never sits behind a stuck IME (device report: "Opening Instagram" with
        // the keyboard up indefinitely). Drafts still asking for their body keep it.
        if (result is CommandRouter.Result.Launch || result is CommandRouter.Result.UberRide ||
            result is CommandRouter.Result.ChooseApp ||
            (result is CommandRouter.Result.DraftMessage && result.text != null)
        ) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
        // Reasoning also gates typed launches: naming an entertainment app is not yet a
        // reason - the same pause applies as for a dock tap.
        if (result is CommandRouter.Result.Launch &&
            activeProfile == CommandRouter.REASONING &&
            result.app.identityKey !in reasoningBypassed
        ) {
            reasoningIntervention = result.app
            return
        }
        if (result is CommandRouter.Result.Reply && result.text.isEmpty()) {
            // General conversation goes straight to the AI when connected (owner 22:52) - only
            // this one message leaves the phone. Unpaired keeps the explicit per-message chip.
            if (aiAvailable()) {
                askAi(raw)
            } else {
                // Unpaired: explain the connection path - never a silent note or a fake save claim.
                addAssistant("Ask AI needs a connection first - tap the pairing link you were sent, or see Settings > Account. Nothing was sent.")
            }
        } else {
            handleResult(result)
        }
    }

    fun launchReasoningApp(app: AppEntry) {
        if (!launchApp(app)) {
            addAssistant(if (app.packageName == CommandRouter.TIMER_KEY) "No clock app on this phone handles timers." else CommandRouter.launchFailureMessage(app))
        }
    }

    @Composable
    fun DockRow(modifier: Modifier = Modifier) {
        Row(
            // Scrollable so large font scales never clip note labels.
            modifier = modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val tilts = listOf(-2.5f, 1.5f, -1.5f, 2.5f)
            dockApps.forEachIndexed { i, app ->
                StickyNote(app, roundedFont, tilt = tilts[i % tilts.size], onLaunch = {
                    lastActivityAt = System.currentTimeMillis()
                    if (activeProfile == CommandRouter.REASONING && app.identityKey !in reasoningBypassed) {
                        // Reasoning asks why first; "Just open it" always opens immediately.
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        reasoningIntervention = app
                    } else {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        if (!launchApp(app)) {
                            addAssistant(if (app.packageName == CommandRouter.TIMER_KEY) "No clock app on this phone handles timers." else CommandRouter.launchFailureMessage(app))
                        }
                    }
                })
            }
        }
    }

    // Swipe gestures (owner 22:53): left swipe opens the full app grid (Normal mode only);
    // right swipe opens the camera app when the active profile allows it.
    var showAppGrid by remember { mutableStateOf(false) }
    val swipeCameraApp = allInstalled.firstOrNull { it.packageName.contains("camera") }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    PaperGrid(Modifier.fillMaxSize(), wobbly = gridWobbly)
    Box(modifier = Modifier.fillMaxSize().pointerInput(activeProfile, swipeCameraApp) {
        var dragX = 0f
        detectHorizontalDragGestures(
            onDragEnd = {
                when {
                    dragX <= -80 && activeProfile == null -> showAppGrid = true
                    dragX >= 80 -> swipeCameraApp?.let { cam ->
                        // Respect the active profile's allow-list (owner 22:53); no auto photo.
                        val profile = activeProfile
                        if (profile == null || cam.identityKey in router.effectiveAllowed(profile, workKeys)) launchApp(cam)
                    }
                }
                dragX = 0f
            },
            onHorizontalDrag = { _, dragAmount -> dragX += dragAmount },
        )
    })
    // Small available height (IME open, tiny viewport): collapse the owner-mandated 8x
    // date-to-notes gap so the composer and Send stay fully visible (00:08 render showed the
    // Send label clipped at h420dp). Normal heights keep the full 144dp gap (owner 21:54).
    // Inset-aware: an open IME subtracts from the usable height, so the collapse rules
    // respond to the actual inset, not just the screen size (main 01:33 IME gate).
    val homeDensity = androidx.compose.ui.platform.LocalDensity.current
    val imeBottomDp = with(homeDensity) { WindowInsets.ime.getBottom(this).toDp().value }
    val availableHeightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp - imeBottomDp
    val tightHeight = availableHeightDp < 560
    // Extremely tight (small viewport with keyboard open): the date yields so the dock,
    // composer and Send stay reachable.
    val veryTightHeight = availableHeightDp < 400
    // imePadding: respond to the IME window inset (keyboard) so the composer, Send,
    // query chips and dock stay above it. With adjustResize the resized window already
    // reports a zero IME bottom, so this is a no-op on the ordinary path and only pays
    // out where the inset actually arrives (transient resize, non-resizing windows).
    Column(modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
        // Top bar: Fresh chat left, settings right
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                messages = emptyList()
                input = ""
                pendingDraft = null
                activeProfile = router.activeProfile
            }) { Text("Fresh chat", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp, fontFamily = roundedFont) }
            Spacer(Modifier.weight(1f))
            // Tweak icon: two parallel lines with knobs, no background, per owner request
            Box(modifier = Modifier.size(30.dp).clickable { showSettings = true }.semantics { contentDescription = "Settings" }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(20.dp)) {
                    val c = Color(0xFF6B6B64)
                    val stroke = 1.8.dp.toPx()
                    val x1 = size.width * 0.34f
                    val x2 = size.width * 0.66f
                    drawLine(c, Offset(x1, 0f), Offset(x1, size.height), strokeWidth = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(c, Offset(x2, 0f), Offset(x2, size.height), strokeWidth = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawCircle(c, 3.2.dp.toPx(), Offset(x1, size.height * 0.36f))
                    drawCircle(c, 3.2.dp.toPx(), Offset(x2, size.height * 0.64f))
                }
            }
        }

        // Date + time below the top bar, larger per owner request. It yields only when
        // the usable height is extremely tight (small viewport + keyboard inset).
        if (!veryTightHeight) {
        Text(
            now,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 26.sp,
            fontFamily = roundedFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        )
        }

        if (pairStatus != null) {
            Text(
                pairStatus ?: "",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = roundedFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }

        // Notes hold one stable position under the date - visible in every state, no jump
        // when the first message lands. The conversation lives in a bottom-only section
        // that scrolls inside itself; it never takes over the screen.
        // Date-to-notes gap is 8x the original 18dp per owner voice 21:54.
        DockRow(Modifier.fillMaxWidth().padding(top = if (veryTightHeight) 8.dp else if (tightHeight) 16.dp else 144.dp))
        if (messages.isEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp), reverseLayout = true) {
            items(messages.asReversed()) { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start,
                ) {
                    Column {
                        // Assistant replies are plain text on the paper; only the user's own lines get a bubble.
                        if (msg.fromUser) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(msg.text, modifier = Modifier.padding(10.dp))
                            }
                        } else if (msg.isHtml) {
                            HtmlBubble(msg.text)
                        } else {
                            Text(msg.text, fontFamily = roundedFont, modifier = Modifier.padding(vertical = 6.dp))
                        }
                        if (!msg.fromUser && msg.fromCloud) {
                            Text("cloud ai", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontFamily = roundedFont)
                        }
                        if (!msg.fromUser && msg.askAi != null) {
                            Text(
                                "Ask AI",
                                color = accentText,
                                fontSize = 15.sp,
                                fontFamily = roundedFont,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { askAi(msg.askAi) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                        if (msg.choiceApps.isNotEmpty()) {
                            // Stacked full-width choices: long labels stay readable, no squashed towers.
                            Column(modifier = Modifier.padding(top = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                msg.choiceApps.forEach { app ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            lastActivityAt = System.currentTimeMillis()
                                            (msg.onChoice ?: { a -> keyboardController?.hide(); focusManager.clearFocus(); launchApp(a) })(app)
                                        },
                                    ) {
                                        val dup = msg.choiceApps.groupBy { it.label }.any { it.value.size > 1 }
                                        // Same label AND same profile side (e.g. cloned apps): fall back to the package name so the choice stays unambiguous.
                                        val sideClash = msg.choiceApps.count { it.choiceLabel == app.choiceLabel } > 1
                                        val chip = when {
                                            !dup -> app.displayLabel
                                            sideClash -> "${app.choiceLabel} - ${app.packageName}"
                                            else -> app.choiceLabel
                                        }
                                        Text(chip, fontSize = 14.sp, modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight().padding(horizontal = 12.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }

        // Input: large inset composer card with inner send pill
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    // Extremely tight usable height (small viewport + IME inset): compact
                    // composer so the chips row and Send stay visible (main 01:33 IME gate).
                    modifier = Modifier.fillMaxWidth().heightIn(min = if (veryTightHeight) 48.dp else 88.dp),
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontFamily = roundedFont),
                    // Owner 23:10: no blinking caret in Focus mode; Normal mode keeps it.
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(
                        if (activeProfile == CommandRouter.FOCUS) androidx.compose.ui.graphics.Color.Transparent
                        else MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    decorationBox = { inner ->
                        Box {
                            if (input.isEmpty()) Text("write what you want to do", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f), fontSize = 15.sp, fontFamily = roundedFont)
                            inner()
                        }
                    },
                )
                // Query shortcuts (owner 22:53): long typed queries offer one-tap prefill into
                // ChatGPT / Claude / Google through their documented URL contracts. Prefill
                // only - the target app opens with the text filled in; the user sends it there.
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Suggestions sit inline left of Send, not above the composer.
                    if (suggestions.isNotEmpty()) {
                        LazyRow(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            items(suggestions) { app ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .combinedClickable(
                                            onClick = {
                                                keyboardController?.hide()
                                                focusManager.clearFocus()
                                                if (!launchApp(app)) {
                                                    addAssistant(CommandRouter.launchFailureMessage(app))
                                                }
                                            },
                                            // Long-press: App info / Uninstall (user 21:50).
                                            onLongClick = { appActionsFor = app },
                                        ),
                                ) {
                                    Text(app.displayLabel, fontSize = 14.sp, modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight().padding(horizontal = 12.dp))
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    // Owner 21:52: brand-logo query shortcuts for longer queries ("more than
                    // four, five words" -> five-word threshold), plain transparent taps right
                    // next to Send, no bubbles (main 23:49). ChatGPT / Claude currently copy
                    // the query and open a blank chat (fallback pending user consent via main);
                    // Google searches directly.
                    if (input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size >= 5) {
                        queryShortcutTargets.forEach { target ->
                            androidx.compose.material3.Icon(
                                painter = androidx.compose.ui.res.painterResource(target.iconRes),
                                contentDescription = target.label,
                                tint = androidx.compose.ui.graphics.Color.Unspecified,
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        keyboardController?.hide()
                                        if (target.opensBlank) {
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(input.trim()))
                                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(target.urlPrefix)))
                                        } else {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(target.urlPrefix + android.net.Uri.encode(input.trim()))))
                                        }
                                    }
                                    .padding(8.dp),
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFB08968).copy(alpha = 0.8f),
                        modifier = Modifier.clickable { send() },
                    ) {
                        Text(
                            "Send",
                            color = sendButtonTextColor,
                            fontSize = 14.sp,
                            fontFamily = roundedFont,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
    }

    if (showAppGrid) {
        // Normal mode only (gesture is gated); right swipe or a tap closes it.
        AppGridOverlay(
            apps = allInstalled.sortedBy { it.label.lowercase() },
            fontFamily = roundedFont,
            onLaunch = { app -> showAppGrid = false; launchApp(app) },
            onDismiss = { showAppGrid = false },
        )
    }

    if (showSettings) {
        SettingsDialog(
            router = router,
            allApps = allApps,
            dockApps = dockPackages,
            onDockApps = { dockPackages = it; router.dockApps = it },
            gridWobbly = gridWobbly,
            onGridWobbly = { gridWobbly = it; router.gridWobbly = it },
            activeProfile = activeProfile,
            onActiveProfile = { p ->
                activeProfile = p
                // Reasoning wants a short conversation before it turns on (first time only).
                if (p == CommandRouter.REASONING && userContext.isEmpty()) showReasoningOnboarding = true
            },
            themeMode = themeMode,
            onThemeMode = onThemeMode,
            historyEntries = { history?.all() ?: emptyList() },
            contextEntries = userContext.entries(),
            onClose = { showSettings = false },
        )
    }

    appActionsFor?.let { target ->
            Dialog(onDismissRequest = { appActionsFor = null }) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth(0.85f).wrapContentHeight().testTag("app_actions_dialog"),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(target.displayLabel, style = MaterialTheme.typography.titleMedium, fontFamily = roundedFont)
                        Text(
                            "App info",
                            fontFamily = roundedFont, fontSize = 16.sp, color = accentText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    appActionsFor = null
                                    try {
                                        context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + target.packageName)))
                                    } catch (e: Exception) {
                                        addAssistant("Couldn't open app details for ${target.displayLabel}.")
                                    }
                                }
                                .padding(vertical = 12.dp),
                        )
                        Text(
                            "Uninstall",
                            fontFamily = roundedFont, fontSize = 16.sp, color = accentText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    appActionsFor = null
                                    // ACTION_DELETE always routes through the OS confirmation UI.
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:" + target.packageName)))
                                    } catch (e: Exception) {
                                        addAssistant("Couldn't start the uninstall for ${target.displayLabel}.")
                                    }
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
    }

    if (showReasoningOnboarding) {
        ReasoningOnboardingDialog(
            roundedFont = roundedFont,
            wobbly = gridWobbly,
            onDone = { qa ->
                qa.forEach { (q, a) -> userContext.add(q, a) }
                showReasoningOnboarding = false
            },
            onSkip = { showReasoningOnboarding = false },
        )
    }

    reasoningIntervention?.let { app ->
        ReasoningInterventionDialog(
            app = app,
            goal = userContext.goal(),
            roundedFont = roundedFont,
            wobbly = gridWobbly,
            onOpen = {
                reasoningIntervention = null
                reasoningBypassed = reasoningBypassed + app.identityKey
                launchReasoningApp(app)
            },
            onLater = { reasoningIntervention = null },
        )
    }
}

/** Renders assistant HTML with JavaScript disabled and navigation blocked - display only. */
@Composable
fun HtmlBubble(html: String) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                // Untrusted model output: no script, no file/content access, navigation blocked.
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true // display only: no remote images/tracking
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean = true
                }
                loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
    )
}

@Composable
fun SettingsDialog(
    router: CommandRouter,
    allApps: () -> List<AppEntry>,
    dockApps: List<String>,
    onDockApps: (List<String>) -> Unit,
    gridWobbly: Boolean,
    onGridWobbly: (Boolean) -> Unit,
    activeProfile: String?,
    onActiveProfile: (String?) -> Unit,
    themeMode: String = "system",
    onThemeMode: (String) -> Unit = {},
    historyEntries: () -> List<HistoryEntry> = { emptyList() },
    contextEntries: List<UserContextStore.Entry> = emptyList(),
    onClose: () -> Unit,
) {
    var key by remember { mutableStateOf(router.apiKey) }
    // Prefilled with the fixed public proxy URL; a credential only arrives via pairing (no key entry).
    var endpoint by remember { mutableStateOf(router.chatEndpoint.ifBlank { LlmClient.DEFAULT_CHAT_ENDPOINT }) }
    var model by remember { mutableStateOf(router.model) }
    var profileName by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var dockSelected by remember { mutableStateOf(dockApps.toSet()) }
    // Everything stages locally: Cancel discards all of it, Save applies it all at once.
    var stagedActive by remember { mutableStateOf(activeProfile) }
    var stagedAllowed by remember { mutableStateOf(router.profileNames().associateWith { router.profileAllowed(it) }) }
    var editingProfile by remember { mutableStateOf<String?>(null) }
    val apps = remember { allApps() }
    val scope = rememberCoroutineScope()
    var modelStatus by remember { mutableStateOf<String?>(null) }

    SettingsPage(
        key = key, onKey = { key = it },
        endpoint = endpoint, onEndpoint = { endpoint = it },
        model = model, onModel = { model = it; modelStatus = null },
        modelStatus = modelStatus,
        profileName = profileName, onProfileName = { profileName = it },
        selected = selected, onSelected = { selected = it },
        dockSelected = dockSelected, onDockSelected = { dockSelected = it },
        profiles = stagedAllowed.keys,
        onToggleProfile = { p ->
            stagedActive = if (p == null || stagedActive == p) null else p
        },
        onDeleteProfile = { p ->
            if (!router.isBuiltin(p)) {
                stagedAllowed = stagedAllowed - p
                if (stagedActive == p) stagedActive = null
                if (editingProfile == p) { editingProfile = null; selected = emptySet() }
            }
        },
        editingProfile = editingProfile,
        onEditProfile = { p ->
            editingProfile = p
            selected = p?.let { stagedAllowed[it] } ?: emptySet()
        },
        gridWobbly = gridWobbly, onGridWobbly = onGridWobbly,
        activeProfile = stagedActive,
        themeMode = themeMode, onThemeMode = onThemeMode,
        apps = apps,
        historyEntries = historyEntries(),
        contextEntries = contextEntries,
        onCancel = onClose,
        onSave = {
            fun applyAll() {
                val finalAllowed = stagedAllowed.toMutableMap()
                val editing = editingProfile
                if (editing != null) {
                    finalAllowed[editing] = selected
                } else if (profileName.isNotBlank()) {
                    finalAllowed[profileName.trim()] = selected
                }
                (router.profileNames() - finalAllowed.keys).forEach { router.deleteProfile(it) }
                finalAllowed.forEach { (n, a) -> router.saveProfile(n, a) }
                val newActive = stagedActive?.takeIf { it in finalAllowed.keys }
                router.activeProfile = newActive
                onActiveProfile(newActive)
                onDockApps(apps.map { it.identityKey }.filter { it in dockSelected })
                onClose()
            }
            router.apiKey = key.trim()
            router.chatEndpoint = endpoint.trim()
            val requestedModel = model.trim()
            // An endpoint (Connect AI) lets the server fix the model: no local catalog check.
            if (endpoint.isBlank() && requestedModel.isNotEmpty() && LlmClient.providerForKey(key.trim()) == LlmClient.Provider.OPENROUTER) {
                // Free-only scope: check the id against OpenRouter's live catalog; offline only :free ids pass.
                scope.launch {
                    val catalogIds = try { LlmClient.freeOpenRouterModels(LlmClient.fetchOpenRouterCatalog()) } catch (e: Exception) { null }
                    if (catalogIds != null) router.freeModelIds = catalogIds
                    if (catalogIds != null && LlmClient.isAllowedModel(LlmClient.Provider.OPENROUTER, requestedModel, catalogIds)) {
                        router.model = requestedModel
                        applyAll()
                    } else {
                        modelStatus = if (catalogIds != null) {
                            "That model isn't listed as free on OpenRouter - pick a free one or leave it blank."
                        } else {
                            "Couldn't verify pricing right now - nothing was saved. Try again when you're online."
                        }
                    }
                }
            } else {
                router.model = requestedModel
                applyAll()
            }
        },
    )
}

/** Full-page settings surface: paper background, Cancel/Save top bar, profile switcher above sections. */
@Composable
fun SettingsPage(
    key: String, onKey: (String) -> Unit,
    endpoint: String = "", onEndpoint: (String) -> Unit = {},
    model: String = "", onModel: (String) -> Unit = {},
    modelStatus: String? = null,
    profileName: String, onProfileName: (String) -> Unit,
    selected: Set<String>, onSelected: (Set<String>) -> Unit,
    dockSelected: Set<String>, onDockSelected: (Set<String>) -> Unit,
    profiles: Set<String>,
    onToggleProfile: (String?) -> Unit,
    onDeleteProfile: (String) -> Unit,
    editingProfile: String?,
    onEditProfile: (String?) -> Unit,
    gridWobbly: Boolean, onGridWobbly: (Boolean) -> Unit,
    activeProfile: String?,
    themeMode: String = "system", onThemeMode: (String) -> Unit = {},
    apps: List<AppEntry>,
    historyEntries: List<HistoryEntry> = emptyList(),
    contextEntries: List<UserContextStore.Entry> = emptyList(),
    onCancel: () -> Unit,
    onSave: () -> Unit,
    switcherInitiallyOpen: Boolean = false,
    pickerInitiallyOpen: Boolean = false,
    editorInitiallyOpen: Boolean = false,
    advancedInitiallyOpen: Boolean = false,
    initialSection: String? = null,
) {
    val settingsContext = LocalContext.current
    val settingsFont = remember { RoundedTypeface.load(settingsContext) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Cancel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    fontFamily = settingsFont,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onCancel() }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFB08968).copy(alpha = 0.8f),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onSave() },
                ) {
                    Text("Save", color = sendButtonTextColor, fontSize = 14.sp, fontFamily = settingsFont, modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp))
                }
            }
            Text("Settings", style = MaterialTheme.typography.headlineSmall, fontFamily = settingsFont, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            Box(modifier = Modifier.weight(1f, fill = false)) {
                SettingsContent(
                    key = key, onKey = onKey,
                    endpoint = endpoint, onEndpoint = onEndpoint,
                    model = model, onModel = onModel,
                    modelStatus = modelStatus,
                    profileName = profileName, onProfileName = onProfileName,
                    selected = selected, onSelected = onSelected,
                    dockSelected = dockSelected, onDockSelected = onDockSelected,
                    profiles = profiles,
                    onToggleProfile = onToggleProfile, onDeleteProfile = onDeleteProfile,
                    gridWobbly = gridWobbly, onGridWobbly = onGridWobbly,
                    activeProfile = activeProfile,
                    themeMode = themeMode, onThemeMode = onThemeMode,
                    apps = apps,
                    historyEntries = historyEntries,
                    contextEntries = contextEntries,
                    editingProfile = editingProfile, onEditProfile = onEditProfile,
                    switcherInitiallyOpen = switcherInitiallyOpen,
                    pickerInitiallyOpen = pickerInitiallyOpen,
                    editorInitiallyOpen = editorInitiallyOpen,
                    advancedInitiallyOpen = advancedInitiallyOpen,
                    initialSection = initialSection,
                )
            }
        }
    }
}

/** A tappable settings option with even padding inside the ripple - no lopsided press. */
@Composable
fun OptionText(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    font: androidx.compose.ui.text.font.FontFamily?,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp = 10.dp,
) {
    Text(
        label,
        fontFamily = font,
        fontSize = fontSize,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) accentText else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        textAlign = textAlign,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = horizontalPadding, vertical = 10.dp),
    )
}

/** Small section label in the settings hierarchy. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Settings page content: current-profile switcher above navigation into focused subpages. */
@Composable
fun SettingsContent(
    key: String, onKey: (String) -> Unit,
    endpoint: String = "", onEndpoint: (String) -> Unit = {},
    model: String = "", onModel: (String) -> Unit = {},
    modelStatus: String? = null,
    profileName: String, onProfileName: (String) -> Unit,
    selected: Set<String>, onSelected: (Set<String>) -> Unit,
    dockSelected: Set<String>, onDockSelected: (Set<String>) -> Unit,
    profiles: Set<String>,
    onToggleProfile: (String?) -> Unit,
    onDeleteProfile: (String) -> Unit,
    editingProfile: String?,
    onEditProfile: (String?) -> Unit,
    gridWobbly: Boolean, onGridWobbly: (Boolean) -> Unit,
    activeProfile: String?,
    themeMode: String = "system", onThemeMode: (String) -> Unit = {},
    apps: List<AppEntry>,
    historyEntries: List<HistoryEntry> = emptyList(),
    contextEntries: List<UserContextStore.Entry> = emptyList(),
    switcherInitiallyOpen: Boolean = false,
    pickerInitiallyOpen: Boolean = false,
    editorInitiallyOpen: Boolean = false,
    advancedInitiallyOpen: Boolean = false,
    initialSection: String? = null,
) {
    val settingsContext = LocalContext.current
    val roundedFont = remember { RoundedTypeface.load(settingsContext) }
    var tab by rememberSaveable { mutableStateOf(initialSection ?: "home") }
    val builtins = CommandRouter.BUILTIN_PROFILES.toList()
    val orderedProfiles = builtins.filter { it in profiles } + (profiles - builtins.toSet()).sorted()

    Column(modifier = Modifier.fillMaxWidth()) {
        ProfileSwitcherRow(
            activeProfile = activeProfile,
            profiles = orderedProfiles,
            onToggleProfile = onToggleProfile,
            roundedFont = roundedFont,
            initiallyOpen = switcherInitiallyOpen,
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            // Equal-weight, single-line tabs: "Account" must never wrap or clip, even at 320dp.
            listOf("home" to "Home", "history" to "History", "context" to "Context", "account" to "Account").forEachIndexed { i, (id, label) ->
                if (i > 0) Spacer(Modifier.width(6.dp))
                OptionText(label, selected = tab == id, onClick = { tab = id }, font = roundedFont, fontSize = 14.sp,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center, horizontalPadding = 4.dp)
            }
        }
        WobblyDivider()
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            when (tab) {
                "home" -> item {
                    SectionLabel("Grid style")
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        OptionText("Straight", selected = !gridWobbly, onClick = { onGridWobbly(false) }, font = roundedFont)
                        Spacer(Modifier.width(4.dp))
                        OptionText("Wobbly", selected = gridWobbly, onClick = { onGridWobbly(true) }, font = roundedFont)
                    }
                    SectionLabel("Theme", modifier = Modifier.padding(top = 28.dp))
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEachIndexed { i, (mode, label) ->
                            if (i > 0) Spacer(Modifier.width(4.dp))
                            OptionText(label, selected = themeMode == mode, onClick = { onThemeMode(mode) }, font = roundedFont)
                        }
                    }
                    var editingApps by rememberSaveable { mutableStateOf(pickerInitiallyOpen) }
                    SectionLabel("Home apps", modifier = Modifier.padding(top = 28.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(
                            apps.filter { it.identityKey in dockSelected }.joinToString(", ") { it.displayLabel }.ifBlank { "None" },
                            fontFamily = roundedFont,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        OptionText(if (editingApps) "Done" else "Change", selected = false, onClick = { editingApps = !editingApps }, font = roundedFont, fontSize = 15.sp)
                    }
                    if (editingApps) {
                        Text(
                            "Up to 4 shown in the center of the home screen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        apps.forEach { app ->
                            PaperCheckbox(
                                checked = app.identityKey in dockSelected,
                                onToggle = {
                                    onDockSelected(if (app.identityKey in dockSelected) dockSelected - app.identityKey
                                    else if (dockSelected.size < 4) dockSelected + app.identityKey
                                    else dockSelected)
                                },
                                label = app.label,
                                roundedFont = roundedFont,
                            )
                        }
                    }
                    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }
                    SectionLabel("Profiles", modifier = Modifier.padding(top = 30.dp))
                    Text(
                        "While a profile is on, only the apps added to it can be opened.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    orderedProfiles.forEach { p ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 2.dp),
                        ) {
                            Text(p, modifier = Modifier.weight(1f), fontFamily = roundedFont, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                            OptionText(
                                if (activeProfile == p) "Off" else "Use",
                                selected = false,
                                onClick = { confirmDelete = null; onToggleProfile(p) },
                                font = roundedFont, fontSize = 15.sp,
                            )
                            OptionText(
                                "Edit",
                                selected = false,
                                onClick = { confirmDelete = null; onEditProfile(p) },
                                font = roundedFont, fontSize = 15.sp,
                            )
                            if (p !in builtins) {
                                Text(
                                    if (confirmDelete == p) "Confirm delete" else "Delete",
                                    color = if (confirmDelete == p) accentText else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 15.sp,
                                    fontFamily = roundedFont,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (confirmDelete == p) { confirmDelete = null; onDeleteProfile(p) }
                                            else confirmDelete = p
                                        }
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                    var editorOpen by rememberSaveable { mutableStateOf(editorInitiallyOpen) }
                    if (editorOpen || editingProfile != null) {
                        Text(
                            if (editingProfile != null) "Editing \"$editingProfile\"" else "New profile",
                            fontFamily = roundedFont,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 22.dp),
                        )
                        if (editingProfile == null) {
                            OutlinedTextField(
                                value = profileName,
                                onValueChange = onProfileName,
                                label = { Text("New profile name") },
                                singleLine = true,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                        } else {
                            Text(
                                "Cancel edit",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp,
                                fontFamily = roundedFont,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { editorOpen = false; onEditProfile(null) }.padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                        if (editingProfile == CommandRouter.WORK) {
                            Text(
                                "Apps from your managed work profile are always available in Work. Add personal apps below if you want them too.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        } else if (editingProfile == CommandRouter.REASONING) {
                            Text(
                                "Add the entertainment apps you want a pause before - YouTube, Instagram, Reddit, TikTok, whatever they are for you. Opening one asks why first; \"Just open it\" always works.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        } else if (editingProfile == CommandRouter.FOCUS) {
                            Text(
                                "Timer (your clock app) is always available in Focus. Add more apps below only if you want them.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        Text(
                            if (editingProfile != null) "Apps \"$editingProfile\" can open:" else "Apps the new profile can open:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                        val editorApps = if (editingProfile == CommandRouter.WORK) apps.filter { !it.isWork } else apps
                        editorApps.forEach { app ->
                            PaperCheckbox(
                                checked = app.identityKey in selected,
                                onToggle = {
                                    onSelected(if (app.identityKey in selected) selected - app.identityKey
                                    else selected + app.identityKey)
                                },
                                label = app.displayLabel,
                                roundedFont = roundedFont,
                                modifier = Modifier.testTag("profile_app_" + app.identityKey),
                            )
                        }
                        if (editingProfile == null) {
                            OptionText("Done", selected = false, onClick = { editorOpen = false }, font = roundedFont, fontSize = 15.sp)
                        }
                    } else {
                        OptionText("+ New profile", selected = false, onClick = { editorOpen = true }, font = roundedFont, fontSize = 15.sp)
                    }
                }
                "history" -> item {
                    Text("History", style = MaterialTheme.typography.titleMedium, fontFamily = roundedFont, modifier = Modifier.padding(bottom = 10.dp))
                    if (historyEntries.isEmpty()) {
                        Text(
                            "Nothing yet - your chats, drafts and asks land here, on this phone only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        historyEntries.take(50).forEach { e ->
                            Column(modifier = Modifier.padding(vertical = 7.dp)) {
                                Text(
                                    "${SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(e.ts))} · ${if (e.role == "user") "You" else "Instinct"}${if (e.source == "cloud") " · cloud ai" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    e.text,
                                    fontFamily = roundedFont,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
                "context" -> item {
                    Text("User context", style = MaterialTheme.typography.titleMedium, fontFamily = roundedFont, modifier = Modifier.padding(bottom = 10.dp))
                    Text(
                        "What the Reasoning profile learned from your onboarding chat. Read-only for now, stored only on this phone, never sent anywhere.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (contextEntries.isEmpty()) {
                        Text(
                            "Nothing yet - turn on the Reasoning profile and it will ask a few short questions first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    } else {
                        contextEntries.forEach { e ->
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    e.question,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    e.answer,
                                    fontFamily = roundedFont,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
                "account" -> item {
                    Text("Connect AI", style = MaterialTheme.typography.titleMedium, fontFamily = roundedFont, modifier = Modifier.padding(bottom = 10.dp))
                    if (key.isNotBlank()) {
                        Text("This installation is paired.", fontFamily = roundedFont, fontSize = 15.sp, color = accentText)
                    } else {
                        Text("AI isn't connected yet.", fontFamily = roundedFont, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            "Tap the pairing link you were sent and this connects itself - no API key to enter, nothing to paste.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Text(
                        "Open-ended questions can go to a cloud AI - only when you tap \"Ask AI\" on a specific message, and only that message's text is sent. Apps, drafts, notes, history and profiles never leave this phone. What the cloud route retains depends on the server's current approved provider, so avoid sending personal or confidential info.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    var advancedOpen by rememberSaveable { mutableStateOf(advancedInitiallyOpen) }
                    Text(
                        if (advancedOpen) "Hide advanced" else "Advanced: manual key or custom provider",
                        fontFamily = roundedFont,
                        fontSize = 14.sp,
                        color = accentText,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { advancedOpen = !advancedOpen }
                            .padding(vertical = 8.dp)
                            .testTag("account_advanced_toggle"),
                    )
                    if (advancedOpen) {
                        OutlinedTextField(
                            value = key,
                            onValueChange = onKey,
                            label = { Text("API key or pairing code") },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                        Text(
                            "Stored only on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        OutlinedTextField(
                            value = endpoint,
                            onValueChange = onEndpoint,
                            label = { Text("Chat endpoint (optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                        Text(
                            "Full URL of a chat endpoint (for example a Cloudflare Worker). With one set, the server chooses the model and your pairing code goes as the credential.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        if (endpoint.isBlank()) {
                            OutlinedTextField(
                                value = model,
                                onValueChange = onModel,
                                label = { Text("Model (optional)") },
                                singleLine = true,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            )
                            Text(
                                "Anthropic key: Claude by default. OpenRouter key (sk-or-...): free Nemotron by default - paste another model id to change it. Only models verified free on OpenRouter are accepted.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            if (modelStatus != null) {
                                Text(
                                    modelStatus,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accentText,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Current-profile switcher: a calm container with a dropdown arrow, tap to reveal profiles. */
@Composable
fun ProfileSwitcherRow(
    activeProfile: String?,
    profiles: List<String>,
    onToggleProfile: (String?) -> Unit,
    roundedFont: androidx.compose.ui.text.font.FontFamily?,
    initiallyOpen: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { open = !open }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Current profile",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        activeProfile ?: "Normal",
                        fontFamily = roundedFont,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Icon(
                    if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (open) "Hide profiles" else "Show profiles",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (open) {
            Column(modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 12.dp)) {
                val options = listOf<String?>(null) + profiles
                options.forEach { p ->
                    val label = p ?: "Normal"
                    val isActive = p == activeProfile
                    Text(
                        // Selected state is bold text, never a tick or color alone.
                        label,
                        fontFamily = roundedFont,
                        fontSize = 15.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) accentText else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleProfile(p) }
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                    )
                }
                Text(
                    "Create and edit profiles below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 12.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        WobblyDivider()
    }
}

/** Hand-drawn wavy separator matching the home grid. */
@Composable
fun WobblyDivider() {
    val color = Color(0xFF6B6B64).copy(alpha = 0.17f)
    Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
        val path = Path()
        val step = 14.dp.toPx()
        var x = 0f
        var i = 0
        path.moveTo(0f, size.height / 2)
        while (x < size.width) {
            x += step
            val y = size.height / 2 + (if (i % 2 == 0) -1.5f else 1.5f) * density
            path.lineTo(x, y)
            i++
        }
        drawPath(path, color, style = Stroke(width = 1.dp.toPx()))
    }
}

/** Hand-drawn checkbox row in the paper style: full-width padded tap target. */
@Composable
fun PaperCheckbox(
    checked: Boolean,
    onToggle: () -> Unit,
    label: String,
    roundedFont: androidx.compose.ui.text.font.FontFamily?,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            drawRoundRect(
                color = Color(0xFF6B6B64),
                style = Stroke(width = 1.5.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
            if (checked) {
                val p = Path().apply {
                    moveTo(size.width * 0.22f, size.height * 0.52f)
                    lineTo(size.width * 0.42f, size.height * 0.72f)
                    lineTo(size.width * 0.78f, size.height * 0.28f)
                }
                drawPath(p, Color(0xFFB08968), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, fontFamily = roundedFont, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

/**
 * Reasoning onboarding: a grid-background popup dialog (not a bottom sheet, not full
 * screen) with a short local conversation before the profile turns on. Answers stay on
 * this phone in the User context tab; nothing here ever goes to the cloud.
 */
@Composable
fun ReasoningOnboardingDialog(
    roundedFont: androidx.compose.ui.text.font.FontFamily?,
    wobbly: Boolean,
    onDone: (List<Pair<String, String>>) -> Unit,
    onSkip: () -> Unit,
) {
    // decorFitsSystemWindows=false: with the default, the TextField's IME/inset listeners
    // and the dialog window's resizing never settle (composition never idles).
    Dialog(onDismissRequest = onSkip, properties = DialogProperties(decorFitsSystemWindows = false, usePlatformDefaultWidth = false)) {
        ReasoningOnboardingContent(roundedFont = roundedFont, wobbly = wobbly, onDone = onDone, onSkip = onSkip)
    }
}

@Composable
fun ReasoningOnboardingContent(
    roundedFont: androidx.compose.ui.text.font.FontFamily?,
    wobbly: Boolean,
    onDone: (List<Pair<String, String>>) -> Unit,
    onSkip: () -> Unit,
) {
    var step by remember { mutableStateOf(0) }
    var answer by remember { mutableStateOf("") }
    val qa = remember { mutableStateListOf<Pair<String, String>>() }
    val questions = UserContextStore.QUESTIONS
    Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.background,
            // Visible edge: the card must read as a card over the home backdrop in both themes.
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                // The 70-80% popup the user asked for. Bounded, and safe now that the
                // TextField loop is fixed via dialog window properties.
                .heightIn(
                    min = (LocalConfiguration.current.screenHeightDp * 0.7f).dp,
                    max = (LocalConfiguration.current.screenHeightDp * 0.78f).dp,
                )
                .testTag("reasoning_onboarding_card"),
        ) {
            Box {
                PaperGrid(Modifier.matchParentSize(), wobbly = wobbly)
                Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                    Text("Before Reasoning turns on", style = MaterialTheme.typography.titleMedium, fontFamily = roundedFont)
                    Text(
                        "Let's talk first - a few short questions. Answers stay on this phone (Settings > Context).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 14.dp)) {
                        qa.forEach { (q, a) ->
                            Text(q, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(a, fontFamily = roundedFont, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                        }
                        val q = questions[step]
                        Text(q, fontFamily = roundedFont, fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground)
                        OutlinedTextField(
                            value = answer,
                            onValueChange = { answer = it },
                            label = { Text("Your answer") },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Skip for now",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp,
                            fontFamily = roundedFont,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onSkip() }.padding(horizontal = 10.dp, vertical = 10.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (step < questions.size - 1) "Next" else "Done",
                            color = accentText,
                            fontSize = 17.sp,
                            fontFamily = roundedFont,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (answer.isNotBlank()) qa.add(questions[step] to answer.trim())
                                    if (step < questions.size - 1) { step++; answer = "" } else onDone(qa.toList())
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
}
/**
 * Reasoning intervention: one short, personal pause before an entertainment app opens.
 * Playful, not a gatekeeper - "Just open it" opens immediately, every time. No repeated
 * interrogation, nothing leaves the phone.
 */
@Composable
fun ReasoningInterventionDialog(
    app: AppEntry,
    goal: String?,
    roundedFont: androidx.compose.ui.text.font.FontFamily?,
    wobbly: Boolean,
    onOpen: () -> Unit,
    onLater: () -> Unit,
) {
    Dialog(onDismissRequest = onLater) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.background,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(0.92f).wrapContentHeight().testTag("reasoning_intervention_card"),
        ) {
            Box {
                PaperGrid(Modifier.matchParentSize(), wobbly = wobbly)
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Before ${app.displayLabel}…", style = MaterialTheme.typography.titleMedium, fontFamily = roundedFont)
                    Text(
                        if (goal != null) "You said you want more time for $goal. Quick check: what's this for, and how long? You can do better."
                        else "Quick check: what's this for, and how long? You can do better.",
                        fontFamily = roundedFont,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    ) {
                        Text(
                            "Maybe later",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp,
                            fontFamily = roundedFont,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onLater() }.padding(horizontal = 10.dp, vertical = 10.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Just open it",
                            color = accentText,
                            fontSize = 17.sp,
                            fontFamily = roundedFont,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpen() }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("reasoning_open_anyway"),
                        )
                    }
                }
            }
        }
    }
}



/** Full app grid overlay (swipe left, Normal mode). */
data class QueryShortcutTarget(val label: String, val urlPrefix: String, val iconRes: Int, val opensBlank: Boolean = false)

val queryShortcutTargets = listOf(
    // chatgpt.com/?q= AUTO-SENDS for logged-out web sessions (observed live 23:48) - rejected
    // by main 23:49. claude.ai/new?q= draft semantics are documented only for the desktop
    // claude:// scheme, not verified for Android/web (main 23:52). Until a safe draft-only
    // contract verifies on the user's device, both AI chips use the consent-pending fallback:
    // copy the query to the clipboard and open a blank new chat; the user pastes and submits.
    // Google runs the search directly - the owner explicitly asked for that (21:52).
    QueryShortcutTarget("ChatGPT", "https://chatgpt.com/", R.drawable.ic_brand_chatgpt, opensBlank = true),
    QueryShortcutTarget("Claude", "https://claude.ai/new", R.drawable.ic_brand_claude, opensBlank = true),
    QueryShortcutTarget("Google", "https://www.google.com/search?q=", R.drawable.ic_brand_google),
)

@Composable
fun AppGridOverlay(apps: List<AppEntry>, fontFamily: androidx.compose.ui.text.font.FontFamily?, onLaunch: (AppEntry) -> Unit, onDismiss: () -> Unit) {
    // Opaque background: the translucent overlay ghosted the home screen beneath it (main 23:28).
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) {
        var dragX = 0f
        detectHorizontalDragGestures(
            onDragEnd = { if (dragX >= 80) onDismiss(); dragX = 0f },
            onHorizontalDrag = { _, dragAmount -> dragX += dragAmount },
        )
    }) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            Text("All apps", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp, fontFamily = fontFamily, modifier = Modifier.padding(bottom = 12.dp))
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                // Adaptive columns keep cells wide enough for full labels on small viewports.
                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 96.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(apps.size) { i ->
                    val app = apps[i]
                    AppGridCell(app, fontFamily, onLaunch = { onLaunch(app) })
                }
            }
        }
    }
}

/** Grid cell: full readable label (wraps to two lines, smaller type), sticky-note styling. */
@Composable
fun AppGridCell(app: AppEntry, fontFamily: androidx.compose.ui.text.font.FontFamily?, onLaunch: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "gridCellPress")
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (pressed) 0.82f else 1f
            }
            .clip(RoundedCornerShape(6.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onLaunch() },
    ) {
        Surface(shape = RoundedCornerShape(6.dp), shadowElevation = 3.dp) {
            Box(
                modifier = Modifier.background(
                    Brush.verticalGradient(listOf(Color(0xFFFCF4BC), Color(0xFFF4E89A)))
                ).fillMaxWidth(),
            ) {
                Text(
                    app.displayLabel,
                    color = Color(0xFF5B5340),
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontFamily = fontFamily,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Home dock slot: a small sticky note with the app name, per owner request. Press shrinks
 *  and dims it slightly, so a tap reads as a tap without breaking the calm style. */
@Composable
fun StickyNote(app: AppEntry, fontFamily: androidx.compose.ui.text.font.FontFamily?, tilt: Float, onLaunch: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "notePress")
    Box(
        modifier = Modifier
            .graphicsLayer {
                rotationZ = tilt
                scaleX = scale
                scaleY = scale
                alpha = if (pressed) 0.82f else 1f
            }
            .clip(RoundedCornerShape(6.dp))
            .clickable(interactionSource = interactionSource, indication = null) { onLaunch() },
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            shadowElevation = 3.dp,
            modifier = Modifier.padding(top = 7.dp),
        ) {
            Box(
                modifier = Modifier.background(
                    Brush.verticalGradient(listOf(Color(0xFFFCF4BC), Color(0xFFF4E89A)))
                ),
            ) {
                Text(
                    app.displayLabel,
                    color = Color(0xFF5B5340),
                    fontSize = 15.sp,
                    fontFamily = fontFamily,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
                Canvas(modifier = Modifier.size(18.dp).align(Alignment.BottomEnd)) {
                    val fold = Path().apply {
                        moveTo(0f, size.height)
                        lineTo(size.width, 0f)
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(fold, Color(0xFFE3D488))
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(width = 44.dp, height = 14.dp)
                .graphicsLayer { rotationZ = -tilt / 2f }
                .background(Color.White.copy(alpha = 0.45f), RoundedCornerShape(3.dp)),
        )
    }
}
