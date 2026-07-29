package com.teleprompter.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teleprompter.recording.CameraRecorder
import com.teleprompter.viewmodel.TeleprompterViewModel

@Composable
fun TeleprompterScreen(
    viewModel: TeleprompterViewModel,
    onBack: () -> Unit,
    onDownload: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    var initialScrollDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (scrollState.maxValue > 0) {
            scrollState.scrollTo((scrollState.maxValue * 0.15f).toInt())
            initialScrollDone = true
        }
    }

    LaunchedEffect(viewModel.scrollOffset) {
        if (!initialScrollDone) return@LaunchedEffect
        val maxScroll = scrollState.maxValue
        if (maxScroll > 0) {
            scrollState.animateScrollTo((maxScroll * viewModel.scrollOffset).toInt())
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(
            cameraRecorder = viewModel.cameraRecorder,
            modifier = Modifier.fillMaxSize()
        )

        // Subtle gradient overlay for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.30f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.40f)
                        )
                    )
                )
        )

        // Teleprompter text
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp, top = 48.dp)
        ) {
            val textAreaDp = maxHeight
            val words = viewModel.script.words
            val currentIndex = viewModel.currentWordIndex

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(textAreaDp / 3))

                Text(
                    text = buildAnnotatedString {
                        words.forEachIndexed { index, word ->
                            val isPast = index < currentIndex
                            val isCurrent = index == currentIndex
                            when {
                                isPast -> withStyle(SpanStyle(
                                    color = Color.Gray,
                                    fontSize = 28.sp
                                )) {
                                    append(word)
                                    append(" ")
                                }
                                isCurrent -> withStyle(SpanStyle(
                                    color = Color.White,
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold
                                )) {
                                    append(word)
                                    append(" ")
                                }
                                else -> withStyle(SpanStyle(
                                    color = Color(0xFFCCCCCC),
                                    fontSize = 28.sp
                                )) {
                                    append(word)
                                    append(" ")
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(textAreaDp / 3))
            }

        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status & word count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        viewModel.isRecording -> "Recording"
                        viewModel.isPlaying -> "Listening"
                        else -> ""
                    },
                    color = if (viewModel.isRecording) Color(0xFFFF5252)
                    else if (viewModel.isPlaying) Color(0xFF4CAF50)
                    else Color.Transparent,
                    fontSize = 14.sp
                )

            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    viewModel.destroy()
                    onBack()
                }) {
                    Text("✕", fontSize = 24.sp, color = Color.White)
                }

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF5252))
                        .clickable {
                            if (!viewModel.isRecording && !viewModel.isPlaying) {
                                viewModel.toggleRecording()
                                viewModel.startPlayback()
                            } else {
                                viewModel.toggleRecording()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (viewModel.isRecording) 28.dp else 32.dp)
                            .clip(if (viewModel.isRecording) RoundedCornerShape(4.dp) else CircleShape)
                            .background(Color.White)
                    )
                }

                if (viewModel.lastRecordedFile != null) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4A90D9))
                            .clickable {
                                viewModel.lastRecordedFile?.let { onDownload(it) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("↓", fontSize = 22.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }
        }

        viewModel.recordingError?.let { error ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp, start = 16.dp, end = 16.dp)
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
