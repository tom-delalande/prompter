package com.teleprompter

import androidx.compose.runtime.*
import com.teleprompter.audio.AudioRecorder
import com.teleprompter.audio.AudioVideoMuxer
import com.teleprompter.audio.createAudioVideoMuxer
import com.teleprompter.data.PlatformStorage
import com.teleprompter.data.ScriptRepository
import com.teleprompter.model.Script
import com.teleprompter.recording.CameraRecorder
import com.teleprompter.speech.SpeechRecognizer
import com.teleprompter.ui.screen.ScriptEditorScreen
import com.teleprompter.ui.screen.ScriptListScreen
import com.teleprompter.ui.screen.TeleprompterScreen
import com.teleprompter.ui.theme.TeleprompterTheme
import com.teleprompter.viewmodel.TeleprompterViewModel

enum class Screen {
    ScriptList, ScriptEditor, Teleprompter
}

@Composable
fun App(
    platformStorage: PlatformStorage,
    speechRecognizerFactory: () -> SpeechRecognizer,
    cameraRecorderFactory: () -> CameraRecorder,
    audioRecorderFactory: () -> AudioRecorder,
    onDownloadFile: (String) -> Unit
) {
    val repository = remember { ScriptRepository(platformStorage) }
    val muxer = remember { createAudioVideoMuxer() }

    var currentScreen by remember { mutableStateOf(Screen.ScriptList) }
    var editingScriptId by remember { mutableStateOf<String?>(null) }
    var teleprompterViewModel by remember { mutableStateOf<TeleprompterViewModel?>(null) }

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
                            audioRecorder = audioRecorderFactory(),
                            audioVideoMuxer = muxer
                        )
                        currentScreen = Screen.Teleprompter
                    }
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
                            audioRecorder = audioRecorderFactory(),
                            audioVideoMuxer = muxer
                        )
                        currentScreen = Screen.Teleprompter
                    }
                )
            }

            Screen.Teleprompter -> {
                teleprompterViewModel?.let { vm ->
                    TeleprompterScreen(
                        viewModel = vm,
                        onBack = {
                            teleprompterViewModel = null
                            currentScreen = Screen.ScriptList
                        },
                        onDownload = { filePath ->
                            onDownloadFile(filePath)
                        }
                    )
                }
            }
        }
    }
}
