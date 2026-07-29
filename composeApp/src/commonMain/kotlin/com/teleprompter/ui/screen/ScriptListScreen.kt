package com.teleprompter.ui.screen

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
                    ScriptCard(
                        script = script,
                        onClick = { onEditScript(script) },
                        onPlay = { onPlayScript(script) }
                    )
                }
            }
        }

        LaunchedEffect(Unit) {
            scripts = repository.loadAll()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            scripts = repository.loadAll()
        }
    }
}

@Composable
private fun ScriptCard(
    script: Script,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = script.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${script.wordCount()} words",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            FilledTonalIconButton(onClick = onPlay) {
                Text("▶", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
