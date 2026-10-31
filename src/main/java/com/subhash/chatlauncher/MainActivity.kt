package com.subhash.chatlauncher

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

data class ChatMessage(val fromUser: Boolean, val text: String, val isHtml: Boolean = false)

data class AppEntry(val label: String, val packageName: String, val launch: Intent)

class MainActivity : ComponentActivity() {

    private lateinit var router: CommandRouter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("chat_launcher", MODE_PRIVATE)
        router = CommandRouter(this, prefs)
        setContent {
            MaterialTheme {
                ChatHome(
                    router = router,
                    resolveApps = { query -> resolveApps(query) },
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
    allApps: () -> List<AppEntry>,
    sendToLlm: suspend (List<Pair<String, String>>) -> String?,
    launchApp: (AppEntry) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var messages by remember {
        mutableStateOf(listOf(ChatMessage(false, "Hi. Type an app name, 'focus', 'send ... to name: message', or just talk.")))
    }
    var activeProfile by remember { mutableStateOf(router.activeProfile) }
    var showSettings by remember { mutableStateOf(false) }
    val suggestions = remember(input, activeProfile) { if (input.isBlank()) emptyList() else resolveApps(input) } // resolveApps reads a cached list inside Activity

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Top bar: focus state + settings
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

        // Input
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Talk to your phone") },
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
                settings.javaScriptEnabled = false
                webViewClient = object : WebViewClient() {
                    @Deprecated("deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true
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
