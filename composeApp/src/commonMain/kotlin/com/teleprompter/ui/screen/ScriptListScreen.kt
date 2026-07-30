package com.teleprompter.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.teleprompter.data.ScriptRepository
import com.teleprompter.model.Script

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptListScreen(
    repository: ScriptRepository,
    onNewScript: () -> Unit,
    onEditScript: (Script) -> Unit,
    onPlayScript: (Script) -> Unit
) {
    var scripts by remember { mutableStateOf(repository.loadAll()) }
    var scriptToDelete by remember { mutableStateOf<Script?>(null) }

    LaunchedEffect(Unit) {
        scripts = repository.loadAll()
        if (scripts.isEmpty()) {
            repository.save(
                Script(
                    id = "default",
                    title = "Sample Script",
                    content = "Welcome to Teleprompter!\n\nThis is your first script. You can edit it by tapping on the card, or create a new script by tapping the plus button.\n\nWhen you're ready to record, tap the play button to enter the teleprompter screen. Press the red record button to start recording your video with scrolling text.\n\nGood luck with your presentation!"
                )
            )
            scripts = repository.loadAll()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Scripts") },
                actions = {
                    IconButton(onClick = onNewScript) {
                        Text("+", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            )
        }
    ) { padding ->
        if (scripts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No scripts yet. Tap + to create one.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(scripts, key = { it.id }) { script ->
                    val dismissState = rememberSwipeToDismissBoxState()

                    if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                        LaunchedEffect(dismissState.currentValue) {
                            scriptToDelete = script
                            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                        }
                    }

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = true,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        MaterialTheme.colorScheme.errorContainer,
                                        shape = MaterialTheme.shapes.medium
                                    )
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Text(
                                    "Delete",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    ) {
                        ScriptCard(
                            script = script,
                            onClick = { onEditScript(script) }
                        )
                    }
                }
            }
        }
    }

    scriptToDelete?.let { script ->
        AlertDialog(
            onDismissRequest = { scriptToDelete = null },
            title = { Text("Delete '${script.title}'?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    repository.delete(script.id)
                    scripts = repository.loadAll()
                    scriptToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { scriptToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ScriptCard(
    script: Script,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Text(
                text = script.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            val preview = script.content
                .replace("\n", " ")
                .trim()
                .take(120)
                .let { if (it.length >= 120) "$it..." else it }
            if (preview.isNotBlank()) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = "${script.wordCount()} words",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}
