package com.teleprompter.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.teleprompter.billing.BillingManager
import com.teleprompter.billing.TIP_10_PRODUCT_ID
import com.teleprompter.billing.TIP_25_PRODUCT_ID
import com.teleprompter.data.ScriptRepository
import com.teleprompter.model.Script

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptListScreen(
    repository: ScriptRepository,
    billingManager: BillingManager,
    onNewScript: () -> Unit,
    onEditScript: (Script) -> Unit,
    onPlayScript: (Script) -> Unit,
    onOpenSettings: () -> Unit
) {
    var scripts by remember { mutableStateOf(repository.loadAll()) }
    var scriptToDelete by remember { mutableStateOf<Script?>(null) }
    var isPremium by remember { mutableStateOf(billingManager.isPremium()) }
    var showTipDialog by remember { mutableStateOf(false) }
    var isPurchasing by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(billingManager) {
        billingManager.connect { premium -> isPremium = premium }
    }

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
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewScript) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (scripts.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No scripts yet. Tap + to create one.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
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

            if (!isPremium) {
                TipFooter(
                    onClick = {
                        dialogMessage = null
                        showTipDialog = true
                    }
                )
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

    if (showTipDialog) {
        TipDialog(
            isPurchasing = isPurchasing,
            message = dialogMessage,
            onBuy10 = {
                isPurchasing = true
                dialogMessage = null
                billingManager.purchase(TIP_10_PRODUCT_ID) { success ->
                    isPurchasing = false
                    if (success) {
                        isPremium = true
                        showTipDialog = false
                    } else {
                        dialogMessage = "Purchase could not be completed. Please try again."
                    }
                }
            },
            onBuy25 = {
                isPurchasing = true
                dialogMessage = null
                billingManager.purchase(TIP_25_PRODUCT_ID) { success ->
                    isPurchasing = false
                    if (success) {
                        isPremium = true
                        showTipDialog = false
                    } else {
                        dialogMessage = "Purchase could not be completed. Please try again."
                    }
                }
            },
            onRestore = {
                isPurchasing = true
                dialogMessage = null
                billingManager.restorePurchases { success ->
                    isPurchasing = false
                    if (success) {
                        isPremium = true
                        showTipDialog = false
                    } else {
                        dialogMessage = "No previous purchase was found on this device."
                    }
                }
            },
            onCancel = {
                if (!isPurchasing) {
                    showTipDialog = false
                    dialogMessage = null
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

@Composable
private fun TipFooter(onClick: () -> Unit) {
    val text = buildAnnotatedString {
        withStyle(
            SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append("Tip")
        }
        append(" to remove this text & support app development")
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 88.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp)
    )
}

@Composable
private fun TipDialog(
    isPurchasing: Boolean,
    message: String?,
    onBuy10: () -> Unit,
    onBuy25: () -> Unit,
    onRestore: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Card(shape = MaterialTheme.shapes.extraLarge) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Thank you!",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Tipping doesn't unlock any new features, but it helps support app development."
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onBuy10,
                        enabled = !isPurchasing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("\$10")
                    }
                    Button(
                        onClick = onBuy25,
                        enabled = !isPurchasing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("\$25")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRestore,
                    enabled = !isPurchasing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restore Purchase")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onCancel,
                    enabled = !isPurchasing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
                if (message != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
