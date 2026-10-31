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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Calm, paper-like palette: warm off-white background, muted teal accent, soft gray text.
private val calmColors = lightColorScheme(
    primary = Color(0xFF3E8E8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCECEA),
    onPrimaryContainer = Color(0xFF1E3A39),
    secondaryContainer = Color(0xFFF0EFEA),
    onSecondaryContainer = Color(0xFF46463F),
    surfaceVariant = Color(0xFFF2F1EC),
    onSurfaceVariant = Color(0xFF51514A),
    background = Color(0xFFFBFAF6),
    onBackground = Color(0xFF2E2E2A),
    surface = Color(0xFFFBFAF6),
    onSurface = Color(0xFF2E2E2A),
    surfaceContainerLow = Color(0xFFF6F5F0),
    surfaceContainer = Color(0xFFF2F1EC),
    surfaceContainerHigh = Color(0xFFEDECE6),
)

/** Faint irregular paper grid behind the chat, matching the calm reference style. */
@Composable
fun PaperGrid(modifier: Modifier = Modifier) {
    val lineColor = Color(0xFF2E2E2A).copy(alpha = 0.05f)
    Canvas(modifier = modifier) {
        val step = 52.dp.toPx()
        var x = step / 2
        while (x < size.width) {
            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = step / 2
        while (y < size.height) {
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
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
    private val appListCache by lazy {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .mapNotNull { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName) return@mapNotNull null
                val launch = pm.getLaunchIntentForPackage(pkg) ?: return@mapNotNull null
                AppEntry(ri.loadLabel(pm).toString(), pkg, launch)
            }
    }

    private fun resolveApps(query: String, limit: Int = 6): List<AppEntry> {
        val hidden = router.hiddenNow()
        return appListCache
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
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var activeProfile by remember { mutableStateOf(router.activeProfile) }
    var showSettings by remember { mutableStateOf(false) }
    var pendingDraft by remember { mutableStateOf<CommandRouter.Result.DraftMessage?>(null) }
    val suggestions = remember(input, activeProfile) { if (input.isBlank()) emptyList() else resolveApps(input) } // resolveApps reads a cached list inside Activity

    // Live clock for the top bar, refreshed every 30s.
    var now by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(30_000)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    PaperGrid(Modifier.fillMaxSize())
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        // Top bar: persona, live clock, new chat
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(30.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Assistant",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(now, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                messages = emptyList()
                input = ""
                pendingDraft = null
                activeProfile = router.activeProfile
            }) { Text("New chat", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        }

        // Focus state + settings
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = activeProfile != null,
                onClick = {
                    if (activeProfile != null) {
                        router.activeProfile = null
                        activeProfile = null
                        messages = messages + ChatMessage(false, "Focus mode off.")
                    } else {
                        val names = router.profileNames()
                        messages = messages + ChatMessage(
                            false,
                            if (names.isEmpty()) "No focus profiles yet - create one in Settings."
                            else "Say 'focus <name>'. Profiles: ${names.joinToString(", ")}"
                        )
                    }
                },
                label = { Text(activeProfile?.let { "Focus: $it" } ?: "Focus off") },
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showSettings = true }) { Text("Settings") }
        }

        // Centered helper when the chat is empty
        if (messages.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Hey,",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Light,
                    )
                    Text(
                        "Type an app name, 'focus', 'send ... to name: message', or just talk.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
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

        // Input: calm rounded composer
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            shape = RoundedCornerShape(26.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
            placeholder = { Text("Talk to your phone", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                val raw = input.trim()
                if (raw.isEmpty()) return@KeyboardActions
                input = ""
                messages = messages + ChatMessage(true, raw)
                val result = router.route(raw, suggestions)
                if (result is CommandRouter.Result.Reply && result.text.isEmpty()) {
                    scope.launch {
                        val reply = sendToLlm(messages.map { (if (it.fromUser) "user" else "assistant") to it.text })
                        messages = messages + ChatMessage(
                            false,
                            reply ?: "No API key yet - open Settings to add one, then I can actually chat.",
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
            }),
        )
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
fun SettingsDialog(router: CommandRouter, allApps: () -> List<AppEntry>, onClose: () -> Unit) {
    var key by remember { mutableStateOf(router.apiKey) }
    var profileName by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var profiles by remember { mutableStateOf(router.profileNames()) }
    val apps = remember { allApps() }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Settings") },
        text = {
            LazyColumn {
                item {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text("Anthropic API key") },
                        singleLine = true,
                    )
                    Text(
                        "Stored only on this device. Needed for open-ended chat; launching and drafts work without it.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    Text("Focus profiles", style = MaterialTheme.typography.titleSmall)
                    profiles.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                router.deleteProfile(p)
                                profiles = router.profileNames()
                            }) { Text("Delete") }
                        }
                    }
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("New profile name") },
                        singleLine = true,
                    )
                    Text("Apps hidden in this profile:", style = MaterialTheme.typography.bodySmall)
                }
                items(apps) { app ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            selected = if (app.packageName in selected) selected - app.packageName
                            else selected + app.packageName
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
        },
        confirmButton = {
            TextButton(onClick = {
                router.apiKey = key.trim()
                if (profileName.isNotBlank()) {
                    router.saveProfile(profileName.trim(), selected)
                }
                onClose()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
