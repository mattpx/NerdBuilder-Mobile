package com.nerdbuilder.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ProjectInfo(val id: String, val name: String, val updated: String)

@Composable
fun ProjectListScreen(
    projects: List<ProjectInfo>,
    onOpen: (String) -> Unit,
    onNew: (String) -> Unit,
    onQuick: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("NerdBuilder Mobile", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.weight(1f), placeholder = { Text("New project name") })
            Button(onClick = { if (name.isNotBlank()) { onNew(name); name = "" } }) { Text("New") }
        }
        OutlinedButton(onClick = onQuick, Modifier.fillMaxWidth()) { Text("Quick Project") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(projects) { p ->
                Card(onClick = { onOpen(p.id) }, Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(p.updated, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun WebPreviewScreen(url: String, onNavigate: (String) -> Unit) {
    var current by remember(url) { mutableStateOf(url) }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(current, { current = it }, Modifier.weight(1f), placeholder = { Text("http://127.0.0.1:3000") })
            Button(onClick = { onNavigate(current) }) { Text("Go") }
        }
        Text("WebView goes here: $current", style = MaterialTheme.typography.bodySmall)
        Text("Wire Android WebView with console telemetry in Android Studio.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ChangesScreen(
    changes: List<com.nerdbuilder.mobile.model.ChangeItem>,
    onUndo: (String) -> Unit,
    onKeep: (String) -> Unit,
    onUndoAll: () -> Unit,
    onKeepAll: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onUndoAll) { Text("Undo all") }
            OutlinedButton(onClick = onKeepAll) { Text("Keep all") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(changes) { c ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        Text(c.path, style = MaterialTheme.typography.titleSmall)
                        Text("+${c.additions} -${c.deletions}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onUndo(c.path) }) { Text("Undo") }
                            Button(onClick = { onKeep(c.path) }) { Text("Keep") }
                        }
                    }
                }
            }
        }
    }
}
