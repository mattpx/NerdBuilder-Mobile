package com.nerdproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.nerdproject.data.ApiKeyVault
import com.nerdproject.model.*
import com.nerdproject.runtime.OpenClaudeBridge
import com.nerdproject.runtime.RuntimeInstaller
import com.nerdproject.ui.AgentScreen
import com.nerdproject.ui.SettingsScreen
import com.nerdproject.ui.TerminalScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vault = ApiKeyVault(this)
        val installer = RuntimeInstaller(this)
        val bridge = OpenClaudeBridge(this) { profile -> vault.get(profile.kind.name) }
        setContent {
            MaterialTheme {
                var tab by remember { mutableStateOf(0) }
                var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
                var streaming by remember { mutableStateOf("") }
                var approval by remember { mutableStateOf<ToolRequest?>(null) }
                var termLog by remember { mutableStateOf("") }
                val scope = rememberCoroutineScope()
                var provider by remember { mutableStateOf(ProviderProfile(kind = ProviderKind.ANTHROPIC)) }

                LaunchedEffect(Unit) {
                    bridge.events.collect { e ->
                        when (e) {
                            is RuntimeEvent.TextDelta -> streaming += e.text
                            is RuntimeEvent.SessionCompleted -> {
                                messages = messages + ChatMessage("agent", streaming.ifBlank { e.result })
                                streaming = ""
                            }
                            is RuntimeEvent.SessionFailed -> {
                                messages = messages + ChatMessage("error", e.message)
                                streaming = ""
                            }
                            is RuntimeEvent.ToolCall -> approval = e.request
                            is RuntimeEvent.ToolApproved, is RuntimeEvent.ToolRejected -> approval = null
                            is RuntimeEvent.FilesChanged -> termLog += "\nChanged: ${e.changes.map { it.path }}"
                            else -> {}
                        }
                    }
                }

                Scaffold(bottomBar = {
                    NavigationBar {
                        listOf("Agent", "Terminal", "Settings").forEachIndexed { i, t ->
                            NavigationBarItem(tab == i, { tab = i }, label = { Text(t) }, icon = {})
                        }
                    }
                }) { pad ->
                    Surface(Modifier.padding(pad)) {
                        when (tab) {
                            0 -> AgentScreen(messages, streaming, approval,
                                onSend = { prompt ->
                                    messages = messages + ChatMessage("user", prompt)
                                    streaming = ""
                                    scope.launch {
                                        bridge.startSession("default", "default", ProjectKind.GENERIC, prompt, messages, provider)
                                    }
                                },
                                onApprove = { req, ok -> scope.launch { bridge.respondToApproval(req, ok) } })
                            1 -> TerminalScreen(termLog)
                            else -> SettingsScreen(ProviderKind.entries.map { it.name }) { id, key ->
                                vault.put(id, key)
                            }
                        }
                    }
                }
            }
        }
    }
}
