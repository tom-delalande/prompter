package co.delalande.prompter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import co.delalande.prompter.billing.BillingManager
import co.delalande.prompter.data.DebugRepository
import co.delalande.prompter.data.PlatformStorage
import co.delalande.prompter.data.ScriptRepository
import co.delalande.prompter.data.SettingsRepository
import co.delalande.prompter.log.Log
import co.delalande.prompter.model.SaveResult
import co.delalande.prompter.model.Script
import co.delalande.prompter.recording.CameraRecorder
import co.delalande.prompter.speech.SpeechRecognizer
import co.delalande.prompter.ui.screen.ScriptEditorScreen
import co.delalande.prompter.ui.screen.ScriptListScreen
import co.delalande.prompter.ui.screen.SettingsScreen
import co.delalande.prompter.ui.screen.TeleprompterScreen
import co.delalande.prompter.ui.theme.TeleprompterTheme
import co.delalande.prompter.viewmodel.TeleprompterViewModel
import kotlinx.coroutines.delay

enum class Screen {
    ScriptList, ScriptEditor, Teleprompter, Settings
}

private const val SAVING_DIALOG_GRACE_MS = 600L
private const val SAVING_DIALOG_MIN_DISPLAY_MS = 1000L
private const val TAG = "App"

@Composable
fun App(
    platformStorage: PlatformStorage,
    isDebug: Boolean,
    speechRecognizerFactory: () -> SpeechRecognizer,
    cameraRecorderFactory: () -> CameraRecorder,
    billingManagerFactory: () -> BillingManager,
    buildInfo: String,
    onSaveToPhotos: (String, (SaveResult) -> Unit) -> Unit,
    onOpenVideo: (String) -> Unit
) {
    val repository = remember { ScriptRepository(platformStorage) }
    val settingsRepository = remember { SettingsRepository(platformStorage) }
    val debugRepository = remember { DebugRepository(platformStorage) }
    val billingManager = remember { billingManagerFactory() }

    var currentScreen by remember { mutableStateOf(Screen.ScriptList) }
    var editingScriptId by remember { mutableStateOf<String?>(null) }
    var teleprompterViewModel by remember { mutableStateOf<TeleprompterViewModel?>(null) }
    var previousScreenBeforeTeleprompter by remember { mutableStateOf(Screen.ScriptList) }
    var saveResult by remember { mutableStateOf<SaveResult?>(null) }
    var isSavingVideo by remember { mutableStateOf(false) }
    var showSavingDialog by remember { mutableStateOf(false) }

    val openTeleprompter: (Script, Int) -> Unit = { script, startWordIndex ->
        teleprompterViewModel = TeleprompterViewModel(
            script = script,
            speechRecognizer = speechRecognizerFactory(),
            cameraRecorder = cameraRecorderFactory(),
            startWordIndex = startWordIndex,
        )
        previousScreenBeforeTeleprompter = currentScreen
        currentScreen = Screen.Teleprompter
    }

    LaunchedEffect(currentScreen) {
        Log.i(TAG) { "Screen: $currentScreen" }
    }

    TeleprompterTheme {
        Box {
            when (currentScreen) {
                Screen.ScriptList -> {
                    ScriptListScreen(
                        repository = repository,
                        billingManager = billingManager,
                        onNewScript = {
                            editingScriptId = null
                            currentScreen = Screen.ScriptEditor
                        },
                        onEditScript = { script ->
                            editingScriptId = script.id
                            currentScreen = Screen.ScriptEditor
                        },
                        onPlayScript = { script -> openTeleprompter(script, 0) },
                        onOpenSettings = { currentScreen = Screen.Settings }
                    )
                }

                Screen.ScriptEditor -> {
                    ScriptEditorScreen(
                        scriptId = editingScriptId,
                        repository = repository,
                        onBack = { currentScreen = Screen.ScriptList },
                        onPlay = { script, startWordIndex -> openTeleprompter(script, startWordIndex) }
                    )
                }

                Screen.Settings -> {
                    SettingsScreen(
                        settingsRepository = settingsRepository,
                        scriptRepository = repository,
                        debugRepository = debugRepository,
                        isDebug = isDebug,
                        buildInfo = buildInfo,
                        onBack = { currentScreen = Screen.ScriptList }
                    )
                }

                Screen.Teleprompter -> {
                    teleprompterViewModel?.let { vm ->
                        val settings = remember(vm) { settingsRepository.load() }
                        TeleprompterScreen(
                            viewModel = vm,
                            debugRepository = debugRepository,
                            defaultCameraMode = settings.defaultCameraMode,
                            textDisplayStyle = settings.textDisplayStyle,
                            onBack = {
                                teleprompterViewModel = null
                                currentScreen = Screen.ScriptList
                            },
                            onSwipeBack = {
                                teleprompterViewModel = null
                                currentScreen = previousScreenBeforeTeleprompter
                            },
                            onSaveToPhotos = { filePath ->
                                isSavingVideo = true
                                onSaveToPhotos(filePath) { result ->
                                    isSavingVideo = false
                                    saveResult = result
                                }
                            },
                            onSaveStateChanged = { saving -> isSavingVideo = saving }
                        )
                    }
                }
            }

            val snackbarHostState = remember { SnackbarHostState() }
            LaunchedEffect(saveResult) {
                val result = saveResult ?: return@LaunchedEffect
                if (result.isSuccess) {
                    val outcome = snackbarHostState.showSnackbar(
                        message = "Saved to Photos",
                        actionLabel = "Open",
                        duration = SnackbarDuration.Short
                    )
                    if (outcome == SnackbarResult.ActionPerformed) {
                        result.uri?.let(onOpenVideo)
                    }
                } else {
                    snackbarHostState.showSnackbar(
                        message = "Failed to save video: ${result.error}"
                    )
                }
                saveResult = null
            }

            LaunchedEffect(isSavingVideo) {
                when {
                    isSavingVideo -> {
                        delay(SAVING_DIALOG_GRACE_MS)
                        if (isSavingVideo) showSavingDialog = true
                    }
                    showSavingDialog -> {
                        delay(SAVING_DIALOG_MIN_DISPLAY_MS)
                        showSavingDialog = false
                    }
                    else -> showSavingDialog = false
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            if (showSavingDialog) {
                SavingVideoDialog()
            }
        }
    }
}

@Composable
private fun SavingVideoDialog() {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text("Saving video…") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Please do not exit the app while your video is being saved.",
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {}
    )
}
