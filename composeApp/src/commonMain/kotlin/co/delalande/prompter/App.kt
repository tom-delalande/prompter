package co.delalande.prompter

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import co.delalande.prompter.billing.BillingManager
import co.delalande.prompter.data.PlatformStorage
import co.delalande.prompter.data.ScriptRepository
import co.delalande.prompter.data.SettingsRepository
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

enum class Screen {
    ScriptList, ScriptEditor, Teleprompter, Settings
}

@Composable
fun App(
    platformStorage: PlatformStorage,
    speechRecognizerFactory: () -> SpeechRecognizer,
    cameraRecorderFactory: () -> CameraRecorder,
    billingManagerFactory: () -> BillingManager,
    onSaveToPhotos: (String, (SaveResult) -> Unit) -> Unit,
    onOpenVideo: (String) -> Unit
) {
    val repository = remember { ScriptRepository(platformStorage) }
    val settingsRepository = remember { SettingsRepository(platformStorage) }
    val billingManager = remember { billingManagerFactory() }

    var currentScreen by remember { mutableStateOf(Screen.ScriptList) }
    var editingScriptId by remember { mutableStateOf<String?>(null) }
    var teleprompterViewModel by remember { mutableStateOf<TeleprompterViewModel?>(null) }
    var previousScreenBeforeTeleprompter by remember { mutableStateOf(Screen.ScriptList) }
    var saveResult by remember { mutableStateOf<SaveResult?>(null) }

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
                        onPlayScript = { script ->
                            teleprompterViewModel = TeleprompterViewModel(
                                script = script,
                                speechRecognizer = speechRecognizerFactory(),
                                cameraRecorder = cameraRecorderFactory(),
                            )
                            previousScreenBeforeTeleprompter = currentScreen
                            currentScreen = Screen.Teleprompter
                        },
                        onOpenSettings = { currentScreen = Screen.Settings }
                    )
                }

                Screen.ScriptEditor -> {
                    ScriptEditorScreen(
                        scriptId = editingScriptId,
                        repository = repository,
                        onBack = { currentScreen = Screen.ScriptList },
                        onPlay = { script ->
                            teleprompterViewModel = TeleprompterViewModel(
                                script = script,
                                speechRecognizer = speechRecognizerFactory(),
                                cameraRecorder = cameraRecorderFactory(),
                            )
                            previousScreenBeforeTeleprompter = currentScreen
                            currentScreen = Screen.Teleprompter
                        }
                    )
                }

                Screen.Settings -> {
                    SettingsScreen(
                        settingsRepository = settingsRepository,
                        onBack = { currentScreen = Screen.ScriptList }
                    )
                }

                Screen.Teleprompter -> {
                    teleprompterViewModel?.let { vm ->
                        TeleprompterScreen(
                            viewModel = vm,
                            defaultCameraMode = settingsRepository.load().defaultCameraMode,
                            textDisplayStyle = settingsRepository.load().textDisplayStyle,
                            onBack = {
                                teleprompterViewModel = null
                                currentScreen = Screen.ScriptList
                            },
                            onSwipeBack = {
                                teleprompterViewModel = null
                                currentScreen = previousScreenBeforeTeleprompter
                            },
                            onSaveToPhotos = { filePath ->
                                onSaveToPhotos(filePath) { result -> saveResult = result }
                            }
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
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
