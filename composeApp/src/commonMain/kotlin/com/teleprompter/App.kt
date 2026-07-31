package com.teleprompter

import androidx.compose.runtime.*
import com.teleprompter.data.PlatformStorage
import com.teleprompter.data.ScriptRepository
import com.teleprompter.data.SettingsRepository
import com.teleprompter.model.Script
import com.teleprompter.recording.CameraRecorder
import com.teleprompter.speech.SpeechRecognizer
import com.teleprompter.ui.screen.ScriptEditorScreen
import com.teleprompter.ui.screen.ScriptListScreen
import com.teleprompter.ui.screen.SettingsScreen
import com.teleprompter.ui.screen.TeleprompterScreen
import com.teleprompter.ui.theme.TeleprompterTheme
import com.teleprompter.viewmodel.TeleprompterViewModel

enum class Screen {
    ScriptList, ScriptEditor, Teleprompter, Settings
}

@Composable
fun App(
    platformStorage: PlatformStorage,
    speechRecognizerFactory: () -> SpeechRecognizer,
    cameraRecorderFactory: () -> CameraRecorder,
    onSaveToPhotos: (String) -> Unit
) {
    val repository = remember { ScriptRepository(platformStorage) }
    val settingsRepository = remember { SettingsRepository(platformStorage) }

    var currentScreen by remember { mutableStateOf(Screen.ScriptList) }
    var editingScriptId by remember { mutableStateOf<String?>(null) }
    var teleprompterViewModel by remember { mutableStateOf<TeleprompterViewModel?>(null) }
    var previousScreenBeforeTeleprompter by remember { mutableStateOf(Screen.ScriptList) }

    TeleprompterTheme {
        when (currentScreen) {
            Screen.ScriptList -> {
                ScriptListScreen(
                    repository = repository,
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
                        onBack = {
                            teleprompterViewModel = null
                            currentScreen = Screen.ScriptList
                        },
                        onSwipeBack = {
                            teleprompterViewModel = null
                            currentScreen = previousScreenBeforeTeleprompter
                        },
                        onSaveToPhotos = { filePath -> onSaveToPhotos(filePath) }
                    )
                }
            }
        }
    }
}
