package com.teleprompter.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teleprompter.recording.CameraRecorder
import com.teleprompter.viewmodel.TeleprompterViewModel

@Composable
fun TeleprompterScreen(
    viewModel: TeleprompterViewModel,
    onBack: () -> Unit,
    onShare: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(viewModel.scrollOffset) {
        val maxScroll = scrollState.maxValue
        if (maxScroll > 0) {
            scrollState.animateScrollTo((maxScroll * viewModel.scrollOffset).toInt())
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Camera preview area (top 35%)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.35f)
                    .background(Color.DarkGray)
            ) {
                CameraPreview(
                    cameraRecorder = viewModel.cameraRecorder,
                    modifier = Modifier.fillMaxSize()
                )

                IconButton(
                    onClick = { viewModel.switchCamera() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Text("🔄", fontSize = 20.sp)
                }
            }

            // Teleprompter text area (middle)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF1A1A1A))
            ) {
                val words = viewModel.script.words
                val currentIndex = viewModel.currentWordIndex

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(60.dp))

                    words.forEachIndexed { index, word ->
                        val isPast = index < currentIndex
                        val isCurrent = index == currentIndex
                        Text(
                            text = word + " ",
                            color = when {
                                isPast && viewModel.isMatching -> Color(0xFF4CAF50)
                                isPast && !viewModel.isMatching -> Color(0xFFFF5252)
                                isCurrent -> Color.White
                                else -> Color.Gray
                            },
                            fontSize = if (isCurrent) 28.sp else 24.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(120.dp))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.CenterStart)
                        .background(Color.White.copy(alpha = 0.3f))
                        .padding(horizontal = 24.dp)
                )
            }

            // Controls area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF121212)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = when {
                            viewModel.isRecording -> "Recording..."
                            viewModel.isPlaying -> "Listening..."
                            else -> "Ready"
                        },
                        color = when {
                            viewModel.isRecording -> Color(0xFFFF5252)
                            viewModel.isPlaying -> Color(0xFF4CAF50)
                            else -> Color.Gray
                        },
                        fontSize = 14.sp
                    )

                    Text(
                        text = "${viewModel.currentWordIndex} / ${viewModel.script.wordCount()} words",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(onClick = {
                            viewModel.destroy()
                            onBack()
                        }) {
                            Text("✕", fontSize = 18.sp)
                        }

                        Button(
                            onClick = {
                                if (!viewModel.isRecording && !viewModel.isPlaying) {
                                    viewModel.startPlayback()
                                    viewModel.toggleRecording()
                                } else {
                                    viewModel.toggleRecording()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewModel.isRecording)
                                    Color(0xFFFF5252)
                                else
                                    Color(0xFF4CAF50)
                            ),
                            modifier = Modifier.size(72.dp),
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Text(
                                text = if (viewModel.isRecording) "■" else "▶",
                                fontSize = 28.sp
                            )
                        }

                        if (viewModel.lastRecordedFile != null) {
                            FilledTonalButton(onClick = {
                                viewModel.lastRecordedFile?.let { onShare(it) }
                            }) {
                                Text("Share", fontSize = 14.sp)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                    }
                }
            }
        }

        viewModel.recordingError?.let { error ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            ) {
                Text(error)
            }
        }
    }
}

@Composable
expect fun CameraPreview(
    cameraRecorder: CameraRecorder,
    modifier: Modifier
)
