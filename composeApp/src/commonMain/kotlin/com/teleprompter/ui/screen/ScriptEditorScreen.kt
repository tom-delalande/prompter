package com.teleprompter.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.teleprompter.data.ScriptRepository
import com.teleprompter.model.Script
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptEditorScreen(
    scriptId: String?,
    repository: ScriptRepository,
    onBack: () -> Unit,
    onPlay: (Script) -> Unit
) {
    val existing = scriptId?.let { repository.getById(it) }
    val currentId = remember { existing?.id ?: generateId() }

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var content by remember { mutableStateOf(existing?.content ?: "") }

    LaunchedEffect(title, content) {
        if (title.isNotBlank() || content.isNotBlank()) {
            delay(400)
            val script = Script(
                id = currentId,
                title = title.ifBlank { "Untitled" },
                content = content
            )
            repository.save(script)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing != null) "Edit Script" else "New Script") },
                navigationIcon = {
                    TextButton(onClick = {
                        if (title.isNotBlank() || content.isNotBlank()) {
                            repository.save(Script(
                                id = currentId,
                                title = title.ifBlank { "Untitled" },
                                content = content
                            ))
                        }
                        onBack()
                    }) {
                        Text("←")
                    }
                },
                actions = {
                    if (existing != null) {
                        TextButton(onClick = { onPlay(existing) }) {
                            Text("Play")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Script Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Script Content") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 300.dp),
                minLines = 10
            )

            Text(
                text = "${content.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size} words",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

private var idCounter = 0L
internal fun generateId(): String {
    idCounter++
    return "script_${idCounter}_${currentTimeMillis()}"
}

internal expect fun currentTimeMillis(): Long
