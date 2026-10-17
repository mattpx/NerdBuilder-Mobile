package com.nerdbuilder.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nerdbuilder.mobile.model.ChatMessage
import com.nerdbuilder.mobile.model.RuntimeEvent
import com.nerdbuilder.mobile.model.ToolRequest

@Composable
fun AgentScreen(
    messages: List<ChatMessage>,
    streaming: String,
    pendingApproval: ToolRequest?,
    onSend: (String) -> Unit,
    onApprove: (ToolRequest, Boolean) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { m ->
                Card { Text("${m.role}: ${m.text}", Modifier.padding(8.dp)) }
            }
            if (streaming.isNotBlank()) item { Card { Text(streaming, Modifier.padding(8.dp)) } }
        }
        pendingApproval?.let { req ->
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Text("Allow ${req.toolName}?")
                    Text(req.detail, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onApprove(req, true) }) { Text("Allow") }
                        OutlinedButton(onClick = { onApprove(req, false) }) { Text("Deny") }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Ask agent...") })
            Button(onClick = { if (input.isNotBlank()) { onSend(input); input = "" } }) { Text("Send") }
        }
    }
}

@Composable
fun TerminalScreen(log: String) {
    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
        item { Text(log, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun SettingsScreen(
    providers: List<String>,
    onSaveKey: (String, String) -> Unit,
) {
    var selected by remember { mutableStateOf(providers.firstOrNull().orEmpty()) }
    var key by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Providers", style = MaterialTheme.typography.headlineSmall)
        providers.forEach { p ->
            Row {
                RadioButton(selected == p, { selected = p })
                Text(p, Modifier.padding(top = 12.dp))
            }
        }
        OutlinedTextField(key, { key = it }, label = { Text("API key") })
        Button(onClick = { onSaveKey(selected, key); key = "" }) { Text("Save") }
    }
}
