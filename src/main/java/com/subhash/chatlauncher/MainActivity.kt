package com.subhash.chatlauncher

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.sin
import kotlinx.coroutines.delay
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

/** WCAG relative luminance and contrast ratio, shared with ContrastTest via the palette. */
val sendButtonTextColor = Color(0xFF080502)
val accentBrown = Color(0xFFB08968)

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
    val choiceApps: List<AppEntry> = emptyList(),
    val onChoice: ((AppEntry) -> Unit)? = null,
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
    val displayLabel: String get() = if (isWork) "\uD83D\uDCBC $label" else label

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
    val choiceLabel: String get() = if (isWork) "\uD83D\uDCBC $label (work)" else "$label (personal)"
}

class MainActivity : ComponentActivity() {

    private lateinit var router: CommandRouter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("chat_launcher", MODE_PRIVATE)
        router = CommandRouter(this, prefs)
        setContent {
            var themeMode by remember { mutableStateOf(router.themeMode) }
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            MaterialTheme(colorScheme = if (darkTheme) darkCalmColors else calmColors) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                ChatHome(
                    router = router,
                    themeMode = themeMode,
                    onThemeMode = { themeMode = it; router.themeMode = it },
                    resolveApps = { query -> resolveApps(query) },
                    shareTargets = { text -> shareTargets(text) },
                    allApps = { resolveApps("", limit = Int.MAX_VALUE) },
                    sendToLlm = { history, onDelta ->
                        val key = router.apiKey
                        if (key.isBlank()) {
                            null
                        } else {
                            val provider = LlmClient.providerForKey(key)
                            val gated = LlmClient.gatedModel(provider, router.model, router.freeModelIds.ifEmpty { null })
                            if (gated == null) {
                                "Your saved model '${router.model}' isn't verified as free on OpenRouter - re-save it in Settings > Account or clear it to use the free default."
                            } else {
                                LlmClient.chatStream(key, gated, history, onDelta)
                            }
                        }
                    },
                    launchApp = { app ->
                        try {
                            val handle = app.userHandle
                            if (app.isWork && handle != null) {
                                getSystemService(android.content.pm.LauncherApps::class.java)
                                    .startMainActivity(app.launch.component, handle, null, null)
                            } else {
                                startActivity(app.launch)
                            }
                            true
                        } catch (e: Exception) {
                            false
                        }
                    },
                )
                }
            }
        }
    }

    // Apps that can take a text share draft, filtered by the active focus profile.
    private fun shareTargets(text: String): List<AppEntry> {
        val pm = packageManager
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        val hidden = router.hiddenNow()
        val resolved = pm.queryIntentActivities(send, 0).map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        val chosen = CommandRouter.shareTargetsOf(resolved, packageName, hidden)
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

    // Installed launchable apps are loaded once; focus filtering and search run on the cached list.
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
        val hidden = router.hiddenNow()
        return installedApps()
            .asSequence()
            .filter { it.identityKey !in hidden }
            .filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
            .sortedBy {
                val i = it.label.indexOf(query, ignoreCase = true)
                if (i < 0) Int.MAX_VALUE else i
            }
            .toList()
            .let { CommandRouter.prioritizeWork(it, router.activeProfile.equals("work", ignoreCase = true)) }
            .take(limit)
    }
}

@Composable
fun ChatHome(
    router: CommandRouter,
    themeMode: String = "system",
    onThemeMode: (String) -> Unit = {},
    resolveApps: (String) -> List<AppEntry>,
    shareTargets: (String) -> List<AppEntry>,
    allApps: () -> List<AppEntry>,
    sendToLlm: suspend (List<Pair<String, String>>, suspend (String) -> Unit) -> String?,
    launchApp: (AppEntry) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val roundedFont = remember { RoundedTypeface.load(context) }
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var activeProfile by remember { mutableStateOf(router.activeProfile) }
    var showSettings by remember { mutableStateOf(false) }
    var dockPackages by remember { mutableStateOf(router.dockApps) }
    var gridWobbly by remember { mutableStateOf(router.gridWobbly) }
    val suggestions = remember(input, activeProfile) { if (input.isBlank()) emptyList() else resolveApps(input) } // resolveApps reads a cached list inside Activity
    // Home dock: user-configured apps (default Maps, Chrome, Spotify, PhonePe), focus-hidden apps stay hidden.
    // Computed on each recomposition so package changes and dock edits show immediately.
    val allInstalled = allApps()
    val focusHidden = router.hiddenNow()
    val dockApps = dockPackages.mapNotNull { key -> allInstalled.firstOrNull { it.identityKey == key } }
        .filter { it.identityKey !in focusHidden }

    // Live clock for the top bar, refreshed every 30s.
    var now by remember { mutableStateOf(SimpleDateFormat("EEE, d MMM - HH:mm", Locale.getDefault()).format(Date())) }
    var lastActivityAt by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = SimpleDateFormat("EEE, d MMM - HH:mm", Locale.getDefault()).format(Date())
            // 30 minutes idle starts a fresh chat - but never silently discards an unsent
            // draft or an unanswered question from the assistant.
            if (CommandRouter.shouldResetChat(
                    idleMillis = System.currentTimeMillis() - lastActivityAt,
                    hasMessages = messages.isNotEmpty(),
                    hasDraft = input.isNotBlank(),
                    hasPendingQuestion = messages.lastOrNull()?.choiceApps?.isNotEmpty() == true,
                )
            ) {
                messages = emptyList()
            }
            delay(30_000)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    PaperGrid(Modifier.fillMaxSize(), wobbly = gridWobbly)
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        // Top bar: New chat left, date + time center, settings right
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                messages = emptyList()
                input = ""
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

        // Date + time below the top bar, larger per owner request
        Text(
            now,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 26.sp,
            fontFamily = roundedFont,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        )

        // Home dock when the chat is empty: centered, slightly below middle for thumb reach
        if (messages.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    // Scrollable so large font scales never clip note labels.
                    modifier = Modifier.offset(y = 56.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val tilts = listOf(-2.5f, 1.5f, -1.5f, 2.5f)
                    dockApps.forEachIndexed { i, app ->
                        StickyNote(app, roundedFont, tilt = tilts[i % tilts.size], onLaunch = { launchApp(app) })
                    }
                }
            }
        } else {
        // Chat history
        LazyColumn(modifier = Modifier.weight(1f), reverseLayout = true) {
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
                        if (msg.choiceApps.isNotEmpty()) {
                            // Stacked full-width choices: long labels stay readable, no squashed towers.
                            Column(modifier = Modifier.padding(top = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                msg.choiceApps.forEach { app ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            lastActivityAt = System.currentTimeMillis()
                                            (msg.onChoice ?: { a -> launchApp(a) })(app)
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
        fun send() {
            val raw = input.trim()
            if (raw.isEmpty()) return
            lastActivityAt = System.currentTimeMillis()
            input = ""
            messages = messages + ChatMessage(true, raw)
            val result = router.route(raw, suggestions)
            if (result is CommandRouter.Result.Reply && result.text.isEmpty()) {
                scope.launch {
                    // Streamed reply: the assistant message grows in place as chunks arrive.
                    messages = messages + ChatMessage(false, "")
                    var acc = ""
                    val reply = sendToLlm(messages.dropLast(1).map { (if (it.fromUser) "user" else "assistant") to it.text }) { delta ->
                        acc += delta
                        messages = messages.dropLast(1) + ChatMessage(false, acc)
                    }
                    messages = messages.dropLast(1) + ChatMessage(
                        false,
                        reply ?: "No API key yet - add one in Settings > Account, then I can actually chat.",
                        isHtml = reply?.contains(Regex("<[a-z]+[ >]")) == true,
                    )
                }
            } else if (result is CommandRouter.Result.DraftMessage) {
                if (result.pkg != null) {
                    // Explicit channel bypasses the question.
                    messages = messages + ChatMessage(false, router.execute(result))
                } else {
                    val targets = shareTargets(result.text)
                    messages = messages + ChatMessage(
                        false,
                        "Which app should I put that draft in? You'll pick the chat and send it there.",
                        choiceApps = targets,
                        onChoice = { app ->
                            launchApp(app)
                            messages = messages + ChatMessage(false, "Draft opened in ${app.displayLabel} for ${result.to} - pick the chat and tap send there.")
                        },
                    )
                }
            } else if (result is CommandRouter.Result.ChooseApp) {
                messages = messages + ChatMessage(
                    false,
                    "I see more than one '${result.label}' here - which one?",
                    choiceApps = result.apps,
                    onChoice = { app -> messages = messages + ChatMessage(false, router.execute(CommandRouter.Result.Launch(app))) },
                )
            } else {
                if (result is CommandRouter.Result.Reply && result.text.startsWith("Focus profile '")) {
                    activeProfile = router.activeProfile
                }
                if (result is CommandRouter.Result.Reply && result.text == "Focus mode off.") {
                    activeProfile = null
                }
                val note = router.execute(result)
                if (note.isNotEmpty()) messages = messages + ChatMessage(false, note)
            }
        }
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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontFamily = roundedFont),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    decorationBox = { inner ->
                        Box {
                            if (input.isEmpty()) Text("write what you want to do", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f), fontSize = 15.sp, fontFamily = roundedFont)
                            inner()
                        }
                    },
                )
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
                                        .clickable {
                                            if (!launchApp(app)) {
                                                messages = messages + ChatMessage(false, CommandRouter.launchFailureMessage(app))
                                            }
                                        },
                                ) {
                                    Text(app.displayLabel, fontSize = 14.sp, modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight().padding(horizontal = 12.dp))
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
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

    // Focus-aware draft picker: hidden apps never appear, the user picks and confirms in the target app.

    if (showSettings) {
        SettingsDialog(
            router = router,
            allApps = allApps,
            dockApps = dockPackages,
            onDockApps = { dockPackages = it; router.dockApps = it },
            gridWobbly = gridWobbly,
            onGridWobbly = { gridWobbly = it; router.gridWobbly = it },
            activeProfile = activeProfile,
            onActiveProfile = { activeProfile = it },
            themeMode = themeMode,
            onThemeMode = onThemeMode,
            onClose = { showSettings = false },
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
                // Remote images may still load (accepted for v1; revisit if content policy tightens).
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
fun SettingsDialog(router: CommandRouter, allApps: () -> List<AppEntry>, dockApps: List<String>, onDockApps: (List<String>) -> Unit, gridWobbly: Boolean, onGridWobbly: (Boolean) -> Unit, activeProfile: String?, onActiveProfile: (String?) -> Unit, themeMode: String = "system", onThemeMode: (String) -> Unit = {}, onClose: () -> Unit) {
    var key by remember { mutableStateOf(router.apiKey) }
    var model by remember { mutableStateOf(router.model) }
    var profileName by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var dockSelected by remember { mutableStateOf(dockApps.toSet()) }
    // Everything stages locally: Cancel discards all of it, Save applies it all at once.
    var stagedActive by remember { mutableStateOf(activeProfile) }
    var stagedHidden by remember { mutableStateOf(router.profileNames().associateWith { router.profileHidden(it) }) }
    var editingProfile by remember { mutableStateOf<String?>(null) }
    val apps = remember { allApps() }
    val scope = rememberCoroutineScope()
    var modelStatus by remember { mutableStateOf<String?>(null) }

    SettingsPage(
        key = key, onKey = { key = it },
        model = model, onModel = { model = it; modelStatus = null },
        modelStatus = modelStatus,
        profileName = profileName, onProfileName = { profileName = it },
        selected = selected, onSelected = { selected = it },
        dockSelected = dockSelected, onDockSelected = { dockSelected = it },
        profiles = stagedHidden.keys,
        onToggleProfile = { p ->
            stagedActive = if (p == null || stagedActive == p) null else p
        },
        onDeleteProfile = { p ->
            stagedHidden = stagedHidden - p
            if (stagedActive == p) stagedActive = null
            if (editingProfile == p) { editingProfile = null; selected = emptySet() }
        },
        editingProfile = editingProfile,
        onEditProfile = { p ->
            editingProfile = p
            selected = p?.let { stagedHidden[it] } ?: emptySet()
        },
        gridWobbly = gridWobbly, onGridWobbly = onGridWobbly,
        activeProfile = stagedActive,
        themeMode = themeMode, onThemeMode = onThemeMode,
        apps = apps,
        onCancel = onClose,
        onSave = {
            fun applyAll() {
                val finalHidden = stagedHidden.toMutableMap()
                val editing = editingProfile
                if (editing != null) {
                    finalHidden[editing] = selected
                } else if (profileName.isNotBlank()) {
                    finalHidden[profileName.trim()] = selected
                }
                (router.profileNames() - finalHidden.keys).forEach { router.deleteProfile(it) }
                finalHidden.forEach { (n, h) -> router.saveProfile(n, h) }
                val newActive = stagedActive?.takeIf { it in finalHidden.keys }
                router.activeProfile = newActive
                onActiveProfile(newActive)
                onDockApps(apps.map { it.identityKey }.filter { it in dockSelected })
                onClose()
            }
            router.apiKey = key.trim()
            val requestedModel = model.trim()
            if (requestedModel.isNotEmpty() && LlmClient.providerForKey(key.trim()) == LlmClient.Provider.OPENROUTER) {
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
    onCancel: () -> Unit,
    onSave: () -> Unit,
    switcherInitiallyOpen: Boolean = false,
    pickerInitiallyOpen: Boolean = false,
    editorInitiallyOpen: Boolean = false,
    initialSection: String? = null,
) {
    val settingsContext = LocalContext.current
    val settingsFont = remember { RoundedTypeface.load(settingsContext) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Cancel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    fontFamily = settingsFont,
                    modifier = Modifier.clickable { onCancel() }.padding(horizontal = 4.dp, vertical = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFB08968).copy(alpha = 0.8f),
                    modifier = Modifier.clickable { onSave() },
                ) {
                    Text("Save", color = sendButtonTextColor, fontSize = 14.sp, fontFamily = settingsFont, modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp))
                }
            }
            Text("Settings", style = MaterialTheme.typography.headlineSmall, fontFamily = settingsFont, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
            Box(modifier = Modifier.weight(1f, fill = false)) {
                SettingsContent(
                    key = key, onKey = onKey,
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
                    editingProfile = editingProfile, onEditProfile = onEditProfile,
                    switcherInitiallyOpen = switcherInitiallyOpen,
                    pickerInitiallyOpen = pickerInitiallyOpen,
                    editorInitiallyOpen = editorInitiallyOpen,
                    initialSection = initialSection,
                )
            }
        }
    }
}

/** Settings page content: current-profile switcher above navigation into focused subpages. */
@Composable
fun SettingsContent(
    key: String, onKey: (String) -> Unit,
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
    switcherInitiallyOpen: Boolean = false,
    pickerInitiallyOpen: Boolean = false,
    editorInitiallyOpen: Boolean = false,
    initialSection: String? = null,
) {
    val settingsContext = LocalContext.current
    val roundedFont = remember { RoundedTypeface.load(settingsContext) }
    var tab by rememberSaveable { mutableStateOf(initialSection ?: "home") }

    Column(modifier = Modifier.fillMaxWidth()) {
        ProfileSwitcherRow(
            activeProfile = activeProfile,
            profiles = profiles,
            onToggleProfile = onToggleProfile,
            roundedFont = roundedFont,
            initiallyOpen = switcherInitiallyOpen,
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            listOf("home" to "Home", "history" to "History", "account" to "Account").forEach { (id, label) ->
                Text(
                    label,
                    fontFamily = roundedFont,
                    fontSize = 17.sp,
                    fontWeight = if (tab == id) FontWeight.Bold else FontWeight.Normal,
                    color = if (tab == id) Color(0xFFB08968) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { tab = id }.padding(end = 22.dp, top = 6.dp, bottom = 10.dp),
                )
            }
        }
        WobblyDivider()
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            when (tab) {
                "home" -> item {
                    Text(
                        "Grid style",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(modifier = Modifier.padding(top = 6.dp)) {
                        Text(
                            "Straight",
                            fontFamily = roundedFont,
                            fontSize = 16.sp,
                            fontWeight = if (!gridWobbly) FontWeight.Bold else FontWeight.Normal,
                            color = if (!gridWobbly) Color(0xFFB08968) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onGridWobbly(false) }.padding(end = 26.dp, top = 6.dp, bottom = 6.dp),
                        )
                        Text(
                            "Wobbly",
                            fontFamily = roundedFont,
                            fontSize = 16.sp,
                            fontWeight = if (gridWobbly) FontWeight.Bold else FontWeight.Normal,
                            color = if (gridWobbly) accentBrown else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onGridWobbly(true) }.padding(top = 6.dp, bottom = 6.dp),
                        )
                    }
                    Text(
                        "Theme",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    Row(modifier = Modifier.padding(top = 6.dp)) {
                        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (mode, label) ->
                            Text(
                                // Selected state is bold text, never color alone.
                                label,
                                fontFamily = roundedFont,
                                fontSize = 16.sp,
                                fontWeight = if (themeMode == mode) FontWeight.Bold else FontWeight.Normal,
                                color = if (themeMode == mode) accentBrown else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { onThemeMode(mode) }.padding(end = 26.dp, top = 6.dp, bottom = 6.dp),
                            )
                        }
                    }
                    var editingApps by rememberSaveable { mutableStateOf(pickerInitiallyOpen) }
                    Text(
                        "Home apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text(
                            apps.filter { it.identityKey in dockSelected }.joinToString(", ") { it.displayLabel }.ifBlank { "None" },
                            fontFamily = roundedFont,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (editingApps) "Done" else "Change",
                            color = Color(0xFFB08968),
                            fontSize = 15.sp,
                            fontFamily = roundedFont,
                            modifier = Modifier.clickable { editingApps = !editingApps }.padding(horizontal = 4.dp, vertical = 8.dp),
                        )
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
                    Text(
                        "Focus profiles",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 26.dp),
                    )
                    profiles.forEach { p ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) {
                            Text(p, modifier = Modifier.weight(1f), fontFamily = roundedFont, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                            Text(
                                if (activeProfile == p) "Off" else "Use",
                                color = Color(0xFFB08968),
                                fontSize = 15.sp,
                                fontFamily = roundedFont,
                                modifier = Modifier.clickable { confirmDelete = null; onToggleProfile(p) }.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                            Text(
                                "Edit",
                                color = Color(0xFFB08968),
                                fontSize = 15.sp,
                                fontFamily = roundedFont,
                                modifier = Modifier.clickable { confirmDelete = null; onEditProfile(p) }.padding(horizontal = 6.dp, vertical = 8.dp),
                            )
                            Text(
                                if (confirmDelete == p) "Confirm delete" else "Delete",
                                color = if (confirmDelete == p) Color(0xFFB08968) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp,
                                fontFamily = roundedFont,
                                modifier = Modifier.clickable {
                                    if (confirmDelete == p) { confirmDelete = null; onDeleteProfile(p) }
                                    else confirmDelete = p
                                }.padding(horizontal = 6.dp, vertical = 8.dp),
                            )
                        }
                    }
                    var editorOpen by rememberSaveable { mutableStateOf(editorInitiallyOpen) }
                    if (editorOpen || editingProfile != null) {
                        Text(
                            if (editingProfile != null) "Editing \"$editingProfile\"" else "New profile",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 18.dp),
                        )
                        if (editingProfile == null) {
                            OutlinedTextField(
                                value = profileName,
                                onValueChange = onProfileName,
                                label = { Text("New profile name") },
                                singleLine = true,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            )
                        } else {
                            Text(
                                "Cancel edit",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp,
                                fontFamily = roundedFont,
                                modifier = Modifier.clickable { editorOpen = false; onEditProfile(null) }.padding(top = 4.dp, bottom = 6.dp),
                            )
                        }
                        Text(
                            if (editingProfile != null) "Apps hidden in $editingProfile:" else "Apps hidden in the new profile:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        apps.forEach { app ->
                            PaperCheckbox(
                                checked = app.identityKey in selected,
                                onToggle = {
                                    onSelected(if (app.identityKey in selected) selected - app.identityKey
                                    else selected + app.identityKey)
                                },
                                label = app.displayLabel,
                                roundedFont = roundedFont,
                            )
                        }
                        if (editingProfile == null) {
                            Text(
                                "Done",
                                color = Color(0xFFB08968),
                                fontSize = 15.sp,
                                fontFamily = roundedFont,
                                modifier = Modifier.clickable { editorOpen = false }.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                    } else {
                        Text(
                            "+ New profile",
                            color = Color(0xFFB08968),
                            fontSize = 15.sp,
                            fontFamily = roundedFont,
                            modifier = Modifier.clickable { editorOpen = true }.padding(top = 14.dp, bottom = 6.dp),
                        )
                    }
                }
                "history" -> item {
                    Text("History", style = MaterialTheme.typography.titleMedium, fontFamily = roundedFont, modifier = Modifier.padding(bottom = 10.dp))
                    Text(
                        "Not built yet - every chat and ask will live here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                "account" -> item {
                    Text("Account", style = MaterialTheme.typography.titleMedium, fontFamily = roundedFont, modifier = Modifier.padding(bottom = 10.dp))
                    OutlinedTextField(
                        value = key,
                        onValueChange = onKey,
                        label = { Text("API key") },
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Stored only on this device. Needed for open-ended chat; launching and drafts work without it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = onModel,
                        label = { Text("Model (optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
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
                            color = Color(0xFFB08968),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Current-profile switcher above the sections: tap to reveal profiles, the active one marked. */
@Composable
fun ProfileSwitcherRow(
    activeProfile: String?,
    profiles: Set<String>,
    onToggleProfile: (String?) -> Unit,
    roundedFont: androidx.compose.ui.text.font.FontFamily?,
    initiallyOpen: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 12.dp),
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
                )
            }
            Text(
                if (open) "−" else "+",
                fontSize = 18.sp,
                fontFamily = roundedFont,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) {
            Column(modifier = Modifier.padding(start = 2.dp, bottom = 12.dp)) {
                val options = listOf<String?>(null) + profiles.sorted()
                options.forEach { p ->
                    val label = p ?: "Normal"
                    val isActive = p == activeProfile
                    Text(
                        // Selected state is bold text, never a tick or color alone.
                        label,
                        fontFamily = roundedFont,
                        fontSize = 15.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) Color(0xFFB08968) else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onToggleProfile(p) }.padding(vertical = 6.dp, horizontal = 4.dp),
                    )
                }
                Text(
                    "Create and edit profiles in Focus profiles below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        WobblyDivider()
    }
}

/** One collapsible settings section in the calm paper style: rounded-font header, wobbly divider. */
@Composable
fun SettingsSection(
    title: String,
    initiallyExpanded: Boolean,
    expandAll: Boolean,
    roundedFont: androidx.compose.ui.text.font.FontFamily?,
    showDivider: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded || expandAll) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 12.dp),
        ) {
            Text(
                title,
                fontFamily = roundedFont,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (expanded) "\u2212" else "+",
                fontSize = 18.sp,
                fontFamily = roundedFont,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Column(modifier = Modifier.padding(start = 2.dp, bottom = 12.dp)) {
                content()
            }
        }
        if (showDivider) WobblyDivider()
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

/** Hand-drawn checkbox row in the paper style. */
@Composable
fun PaperCheckbox(
    checked: Boolean,
    onToggle: () -> Unit,
    label: String,
    roundedFont: androidx.compose.ui.text.font.FontFamily?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 6.dp),
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

/** Home dock slot: a small sticky note with the app name, per owner request. */
@Composable
fun StickyNote(app: AppEntry, fontFamily: androidx.compose.ui.text.font.FontFamily?, tilt: Float, onLaunch: () -> Unit) {
    Box(
        modifier = Modifier
            .graphicsLayer { rotationZ = tilt }
            .clickable { onLaunch() },
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
