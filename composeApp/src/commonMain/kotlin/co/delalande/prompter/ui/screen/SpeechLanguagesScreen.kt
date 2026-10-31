package co.delalande.prompter.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.delalande.prompter.speech.SpeechLanguageAvailability
import co.delalande.prompter.speech.SpeechLanguageManager
import co.delalande.prompter.speech.SpeechLanguageStatus
import co.delalande.prompter.speech.SpeechModelDownload
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechLanguagesScreen(
    manager: SpeechLanguageManager,
    languages: List<SpeechLanguageAvailability>?,
    onRefreshLanguages: suspend () -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var deleteMessage by remember { mutableStateOf<String?>(null) }
    val downloads = remember { mutableStateMapOf<String, SpeechModelDownload>() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage languages") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search languages") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (!manager.canManageDownloads) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Speech language downloads are managed automatically by this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
            Spacer(Modifier.height(12.dp))

            when {
                languages == null -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                else -> {
                    val visibleLanguages = languages
                        .filter {
                            query.isBlank() ||
                                it.language.displayName.contains(query, ignoreCase = true) ||
                                it.language.localeTag.contains(query, ignoreCase = true)
                        }
                        .sortedWith(
                            compareBy<SpeechLanguageAvailability> {
                                val downloadCompleted =
                                    downloads[it.language.localeTag] is SpeechModelDownload.Completed
                                if (it.status == SpeechLanguageStatus.Installed || downloadCompleted) 0 else 1
                            }.thenBy { it.language.displayName }
                        )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(visibleLanguages, key = { it.language.localeTag }) { item ->
                            SpeechLanguageRow(
                                item = item,
                                download = downloads[item.language.localeTag],
                                canDelete = manager.canDeleteDownloads,
                                onDownload = {
                                    scope.launch {
                                        manager.download(item.language.localeTag).collect { state ->
                                            downloads[item.language.localeTag] = state
                                            if (state is SpeechModelDownload.Completed) onRefreshLanguages()
                                        }
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        val message = manager.delete(item.language.localeTag)
                                        if (message == null) onRefreshLanguages() else deleteMessage = message
                                    }
                                }
                            )
                        }
                        if (manager.canManageDownloads && !manager.canDeleteDownloads) {
                            item(key = "language-deletion-note") {
                                Text(
                                    text = "Downloaded languages are managed by Android. Android does not allow this app to delete them.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                    modifier = Modifier.padding(vertical = 20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    deleteMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { deleteMessage = null },
            title = { Text("Language model storage") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { deleteMessage = null }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun SpeechLanguageRow(
    item: SpeechLanguageAvailability,
    download: SpeechModelDownload?,
    canDelete: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val isInstalled = item.status == SpeechLanguageStatus.Installed ||
        download is SpeechModelDownload.Completed
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isInstalled) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Installed",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(item.language.displayName, style = MaterialTheme.typography.bodyLarge)
                }
            }
            when {
                isInstalled && canDelete ->
                    OutlinedButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Delete")
                    }
                isInstalled -> Unit
                download is SpeechModelDownload.Started || download is SpeechModelDownload.Progress ->
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                download is SpeechModelDownload.Failed ->
                    Button(onClick = onDownload) { Text("Retry") }
                item.status == SpeechLanguageStatus.Downloading ->
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                item.status == SpeechLanguageStatus.Downloadable ->
                    Button(onClick = onDownload) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Download")
                    }
                else -> Text(
                    "Unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        }
        when (download) {
            is SpeechModelDownload.Started -> {
                Spacer(Modifier.height(8.dp))
                DownloadProgressText(0L, download.totalBytes)
            }
            is SpeechModelDownload.Progress -> {
                Spacer(Modifier.height(8.dp))
                DownloadProgressText(download.bytesDownloaded, download.totalBytes)
            }
            is SpeechModelDownload.Failed -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    download.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> Unit
        }
    }
}
