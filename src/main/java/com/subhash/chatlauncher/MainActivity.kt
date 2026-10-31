package com.subhash.chatlauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

data class ChatMessage(val fromUser: Boolean, val text: String)

data class AppEntry(val label: String, val packageName: String, val launch: Intent)

class MainActivity : ComponentActivity() {

    private lateinit var router: CommandRouter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("chat_launcher", MODE_PRIVATE)
        router = CommandRouter(this, prefs)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ChatHome(
                        router = router,
                        resolveApps = ::resolveApps,
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

    private fun resolveApps(query: String): List<AppEntry> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val hidden = if (router.focusMode) router.focusHidden else emptySet()
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || pkg in hidden) return@mapNotNull null
                val launch = pm.getLaunchIntentForPackage(pkg) ?: return@mapNotNull null
                AppEntry(ri.loadLabel(pm).toString(), pkg, launch)
            }
            .filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
            .sortedBy {
                val i = it.label.indexOf(query, ignoreCase = true)
                if (i < 0) Int.MAX_VALUE else i
            }
            .take(6)
    }
}

@Composable
fun ChatHome(
    router: CommandRouter,
    resolveApps: (String) -> List<AppEntry>,
    sendToLlm: suspend (List<Pair<String, String>>) -> String?,
    launchApp: (AppEntry) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf(ChatMessage(false, "Hi. Type an app name, 'focus', 'send ... to name: message', or just talk."))) }
    var focusOn by remember { mutableStateOf(router.focusMode) }
    var showSettings by remember { mutableStateOf(false) }
    val suggestions = remember(input) { if (input.isBlank()) emptyList() else resolveApps(input) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Top bar: focus toggle + settings
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = focusOn,
                onClick = {
                    focusOn = !focusOn
                    router.focusMode = focusOn
                    messages = messages + ChatMessage(false, if (focusOn) "Focus mode on." else "Focus mode off.")
                },
                label = { Text(if (focusOn) "Focus on" else "Focus off") },
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
                        Text(msg.text, modifier = Modifier.padding(10.dp))
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
                    // Plain chat -> LLM if configured
                    scope.launch {
                        val reply = sendToLlm(
                            (messages.map { (if (it.fromUser) "user" else "assistant") to it.text })
                        )
                        messages = messages + ChatMessage(false, reply ?: "No API key yet - open Settings to add one, then I can actually chat.")
                    }
                } else {
                    val note = router.execute(result)
                    if (note.isNotEmpty()) messages = messages + ChatMessage(false, note)
                }
            }),
        )
    }

    if (showSettings) {
        var key by remember { mutableStateOf(router.apiKey) }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Settings") },
            text = {
                Column {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text("Anthropic API key") },
                        singleLine = true,
                    )
                    Text(
                        "Stored only on this device. Needed for open-ended chat; app launching and message drafts work without it.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { router.apiKey = key.trim(); showSettings = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Cancel") } },
        )
    }
}
