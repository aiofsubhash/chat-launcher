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
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
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

/** Faint irregular paper grid behind the chat, matching the calm reference style. */
@Composable
fun PaperGrid(modifier: Modifier = Modifier, wobbly: Boolean = false) {
    val lineColor = Color(0xFF6B6B64).copy(alpha = 0.17f)
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

data class ChatMessage(val fromUser: Boolean, val text: String, val isHtml: Boolean = false)

data class AppEntry(val label: String, val packageName: String, val launch: Intent)

class MainActivity : ComponentActivity() {

    private lateinit var router: CommandRouter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("chat_launcher", MODE_PRIVATE)
        router = CommandRouter(this, prefs)
        setContent {
            MaterialTheme(colorScheme = calmColors) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                ChatHome(
                    router = router,
                    resolveApps = { query -> resolveApps(query) },
                    shareTargets = { text -> shareTargets(text) },
                    allApps = { resolveApps("", limit = Int.MAX_VALUE) },
                    sendToLlm = { history ->
                        val key = router.apiKey
                        if (key.isBlank()) null else LlmClient.chat(key, history)
                    },
                    launchApp = { app -> startActivity(app.launch) },
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
        return pm.queryIntentActivities(send, 0).mapNotNull { ri ->
            val pkg = ri.activityInfo.packageName
            if (pkg == packageName || pkg in hidden) return@mapNotNull null
            val launch = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                setClassName(pkg, ri.activityInfo.name)
            }
            AppEntry(ri.loadLabel(pm).toString(), pkg, launch)
        }.sortedBy { it.label }
    }

    // Installed launchable apps are loaded once; focus filtering and search run on the cached list.
    private fun queryInstalledApps(): List<AppEntry> {
        val pm = packageManager
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
            .filter { it.packageName !in hidden }
            .filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
            .sortedBy {
                val i = it.label.indexOf(query, ignoreCase = true)
                if (i < 0) Int.MAX_VALUE else i
            }
            .take(limit)
            .toList()
    }
}

@Composable
fun ChatHome(
    router: CommandRouter,
    resolveApps: (String) -> List<AppEntry>,
    shareTargets: (String) -> List<AppEntry>,
    allApps: () -> List<AppEntry>,
    sendToLlm: suspend (List<Pair<String, String>>) -> String?,
    launchApp: (AppEntry) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val roundedFont = remember { RoundedTypeface.load(context) }
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var activeProfile by remember { mutableStateOf(router.activeProfile) }
    var showSettings by remember { mutableStateOf(false) }
    var dockPackages by remember { mutableStateOf(router.dockApps) }
    var pendingDraft by remember { mutableStateOf<CommandRouter.Result.DraftMessage?>(null) }
    var gridWobbly by remember { mutableStateOf(router.gridWobbly) }
    val suggestions = remember(input, activeProfile) { if (input.isBlank()) emptyList() else resolveApps(input) } // resolveApps reads a cached list inside Activity
    // Home dock: user-configured apps (default Maps, Chrome, Spotify, PhonePe), focus-hidden apps stay hidden.
    // Computed on each recomposition so package changes and dock edits show immediately.
    val allInstalled = allApps()
    val focusHidden = router.hiddenNow()
    val dockApps = dockPackages.mapNotNull { pkg -> allInstalled.firstOrNull { it.packageName == pkg } }
        .filter { it.packageName !in focusHidden }

    // Live clock for the top bar, refreshed every 30s.
    var now by remember { mutableStateOf(SimpleDateFormat("EEE, d MMM - HH:mm", Locale.getDefault()).format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            now = SimpleDateFormat("EEE, d MMM - HH:mm", Locale.getDefault()).format(Date())
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
                    modifier = Modifier.offset(y = 56.dp),
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
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (msg.fromUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        if (msg.isHtml) {
                            HtmlBubble(msg.text)
                        } else {
                            Text(msg.text, modifier = Modifier.padding(10.dp))
                        }
                    }
                }
            }
        }
        }

        // App suggestions while typing
        if (suggestions.isNotEmpty()) {
            LazyRow(modifier = Modifier.padding(vertical = 4.dp)) {
                items(suggestions) { app ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clickable { launchApp(app) },
                    ) {
                        Text(app.label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
            }
        }

        // Input: large inset composer card with inner send pill
        fun send() {
            val raw = input.trim()
            if (raw.isEmpty()) return
            input = ""
            messages = messages + ChatMessage(true, raw)
            val result = router.route(raw, suggestions)
            if (result is CommandRouter.Result.Reply && result.text.isEmpty()) {
                scope.launch {
                    val reply = sendToLlm(messages.map { (if (it.fromUser) "user" else "assistant") to it.text })
                    messages = messages + ChatMessage(
                        false,
                        reply ?: "No API key yet - tap the persona icon to add a key, then I can actually chat.",
                        isHtml = reply?.contains(Regex("<[a-z]+[ >]")) == true,
                    )
                }
            } else if (result is CommandRouter.Result.DraftMessage) {
                pendingDraft = result
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
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontFamily = roundedFont),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    decorationBox = { inner ->
                        Box {
                            if (input.isEmpty()) Text("write what you want to do", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f), fontSize = 15.sp, fontFamily = roundedFont)
                            inner()
                        }
                    },
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFB08968),
                        modifier = Modifier.clickable { send() },
                    ) {
                        Text(
                            "Send",
                            color = Color.White,
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
    pendingDraft?.let { draft ->
        val targets = remember(draft) { shareTargets(draft.text) }
        AlertDialog(
            onDismissRequest = { pendingDraft = null },
            title = { Text("Choose app for draft") },
            text = {
                if (targets.isEmpty()) {
                    Text("No app can take this draft (hidden apps stay hidden in focus mode).")
                } else {
                    LazyColumn {
                        items(targets) { app ->
                            Text(
                                app.label,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        launchApp(app)
                                        messages = messages + ChatMessage(false, "Draft opened in ${app.label} for ${draft.to} - confirm there to send.")
                                        pendingDraft = null
                                    }
                                    .padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pendingDraft = null }) { Text("Cancel") } },
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
            onActiveProfile = { activeProfile = it },
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
fun SettingsDialog(router: CommandRouter, allApps: () -> List<AppEntry>, dockApps: List<String>, onDockApps: (List<String>) -> Unit, gridWobbly: Boolean, onGridWobbly: (Boolean) -> Unit, activeProfile: String?, onActiveProfile: (String?) -> Unit, onClose: () -> Unit) {
    var key by remember { mutableStateOf(router.apiKey) }
    var profileName by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var dockSelected by remember { mutableStateOf(dockApps.toSet()) }
    var profiles by remember { mutableStateOf(router.profileNames()) }
    val apps = remember { allApps() }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Settings") },
        text = {
            SettingsContent(
                key = key, onKey = { key = it },
                profileName = profileName, onProfileName = { profileName = it },
                selected = selected, onSelected = { selected = it },
                dockSelected = dockSelected, onDockSelected = { dockSelected = it },
                profiles = profiles,
                onToggleProfile = { p ->
                    if (activeProfile == p) {
                        router.activeProfile = null
                        onActiveProfile(null)
                    } else {
                        router.activeProfile = p
                        onActiveProfile(p)
                    }
                },
                onDeleteProfile = { p ->
                    router.deleteProfile(p)
                    profiles = router.profileNames()
                    if (activeProfile == p) onActiveProfile(null)
                },
                gridWobbly = gridWobbly, onGridWobbly = onGridWobbly,
                activeProfile = activeProfile,
                apps = apps,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                router.apiKey = key.trim()
                if (profileName.isNotBlank()) {
                    router.saveProfile(profileName.trim(), selected)
                }
                onDockApps(apps.map { it.packageName }.filter { it in dockSelected })
                onClose()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** Settings panel content, extracted so previews and tests can render it outside the animated dialog. */
@Composable
fun SettingsContent(
    key: String, onKey: (String) -> Unit,
    profileName: String, onProfileName: (String) -> Unit,
    selected: Set<String>, onSelected: (Set<String>) -> Unit,
    dockSelected: Set<String>, onDockSelected: (Set<String>) -> Unit,
    profiles: Set<String>,
    onToggleProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    gridWobbly: Boolean, onGridWobbly: (Boolean) -> Unit,
    activeProfile: String?,
    apps: List<AppEntry>,
) {
    LazyColumn {
                item {
                    OutlinedTextField(
                        value = key,
                        onValueChange = onKey,
                        label = { Text("Anthropic API key") },
                        singleLine = true,
                    )
                    Text(
                        "Stored only on this device. Needed for open-ended chat; launching and drafts work without it.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    Text("Grid style", style = MaterialTheme.typography.titleSmall)
                    Row(modifier = Modifier.padding(bottom = 12.dp)) {
                        FilterChip(
                            selected = !gridWobbly,
                            onClick = { onGridWobbly(false) },
                            label = { Text("Straight") },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        FilterChip(
                            selected = gridWobbly,
                            onClick = { onGridWobbly(true) },
                            label = { Text("Wobbly") },
                        )
                    }
                    Text("Home apps", style = MaterialTheme.typography.titleSmall)
                    Text("Up to 4 shown in the center of the home screen.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
                    apps.forEach { app ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                onDockSelected(if (app.packageName in dockSelected) dockSelected - app.packageName
                                else if (dockSelected.size < 4) dockSelected + app.packageName
                                else dockSelected)
                            },
                        ) {
                            Checkbox(
                                checked = app.packageName in dockSelected,
                                onCheckedChange = null,
                            )
                            Text(app.label)
                        }
                    }
                    Text("Focus profiles", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    profiles.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onToggleProfile(p) }) { Text(if (activeProfile == p) "Off" else "Use") }
                            TextButton(onClick = { onDeleteProfile(p) }) { Text("Delete") }
                        }
                    }
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = onProfileName,
                        label = { Text("New profile name") },
                        singleLine = true,
                    )
                    Text("Apps hidden in this profile:", style = MaterialTheme.typography.bodySmall)
                }
                items(apps) { app ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            onSelected(if (app.packageName in selected) selected - app.packageName
                            else selected + app.packageName)
                        },
                    ) {
                        Checkbox(
                            checked = app.packageName in selected,
                            onCheckedChange = null,
                        )
                        Text(app.label)
                    }
                }
            }
}


/** Home dock slot: a small sticky note with the app name, per owner request. */
@Composable
fun StickyNote(app: AppEntry, fontFamily: androidx.compose.ui.text.font.FontFamily?, tilt: Float, onLaunch: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFFF6EFC1),
        shadowElevation = 2.dp,
        modifier = Modifier
            .graphicsLayer { rotationZ = tilt }
            .clickable { onLaunch() },
    ) {
        Text(
            app.label,
            color = Color(0xFF5B5340),
            fontSize = 15.sp,
            fontFamily = fontFamily,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}
