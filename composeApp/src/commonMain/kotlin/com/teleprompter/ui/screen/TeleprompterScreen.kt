package com.teleprompter.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.teleprompter.model.CameraMode
import com.teleprompter.recording.CameraRecorder
import com.teleprompter.ui.SystemBackHandler
import com.teleprompter.viewmodel.TeleprompterViewModel

@Composable
fun TeleprompterScreen(
    viewModel: TeleprompterViewModel,
    defaultCameraMode: CameraMode,
    onBack: () -> Unit,
    onSwipeBack: () -> Unit = onBack,
    onSaveToPhotos: (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    var isPipMode by remember { mutableStateOf(defaultCameraMode == CameraMode.PictureInPicture) }

    LaunchedEffect(viewModel.lastRecordedFile) {
        if (viewModel.lastRecordedFile != null) {
            viewModel.isRecording = false
            viewModel.isPlaying = false
            viewModel.lastRecordedFile?.let { onSaveToPhotos(it) }
            viewModel.destroy()
            onBack()
        }
    }

    LaunchedEffect(viewModel.currentWordIndex) {
        if (viewModel.currentWordIndex <= 0) return@LaunchedEffect
        val maxScroll = scrollState.maxValue
        if (maxScroll <= 0) return@LaunchedEffect
        val ratio = viewModel.currentWordIndex.toFloat() / viewModel.script.wordCount().toFloat()
        scrollState.animateScrollTo((maxScroll * ratio).toInt())
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        SystemBackHandler(onBack = onSwipeBack)

        val isLandscape = maxWidth > maxHeight
        val isCameraPip = isPipMode

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (isCameraPip) 2f else 0f)
        ) {
            CameraPreview(
                cameraRecorder = viewModel.cameraRecorder,
                modifier = if (isCameraPip) {
                    val pipSize = if (isLandscape) 180.dp else 140.dp
                    val pipAspect = if (isLandscape) 16f / 9f else 9f / 16f
                    val pipAlign = if (isLandscape) Alignment.TopEnd else Alignment.BottomEnd
                    Modifier
                        .align(pipAlign)
                        .padding(
                            top = if (isLandscape) 64.dp else 0.dp,
                            end = if (isLandscape) 96.dp else 8.dp,
                            bottom = if (isLandscape) 0.dp else 200.dp,
                            start = if (isLandscape) 0.dp else 0.dp
                        )
                        .width(pipSize)
                        .aspectRatio(pipAspect)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isPipMode = false }
                } else {
                    Modifier.fillMaxSize()
                }
            )
        }

        val pipEndPadding = if (isCameraPip) {
            if (isLandscape) 280.dp else 170.dp
        } else 0.dp

        TeleprompterContent(
            viewModel = viewModel,
            scrollState = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (isCameraPip) 0f else 1f),
            pipEndPadding = pipEndPadding
        )

        IconButton(
            onClick = {
                viewModel.destroy()
                onBack()
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .zIndex(4f)
                .statusBarsPadding()
                .padding(start = 8.dp, top = 8.dp)
        ) {
            Text("\u2715", fontSize = 24.sp, color = Color.White)
        }

        if (isLandscape) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .zIndex(3f)
                    .padding(end = 24.dp)
            ) {
                RecordButton(viewModel = viewModel)
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(3f)
                    .padding(bottom = 48.dp)
            ) {
                RecordButton(viewModel = viewModel)
            }
        }

        viewModel.recordingError?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(4f)
                    .padding(bottom = 120.dp, start = 16.dp, end = 16.dp)
            ) {
                Text(error)
            }
        }
    }
}

@Composable
private fun RecordButton(viewModel: TeleprompterViewModel) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color(0xFFFF5252))
            .clickable(enabled = !viewModel.isProcessing) {
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
}

@Composable
private fun TeleprompterContent(
    viewModel: TeleprompterViewModel,
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
    pipEndPadding: Dp = 0.dp,
) {
    Box(modifier = modifier.fillMaxSize()) {
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

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp, top = 48.dp)
        ) {
            val textAreaDp = maxHeight
            val isLandscape = maxWidth > maxHeight
            val words = viewModel.script.words
            val currentIndex = viewModel.currentWordIndex

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        start = if (isLandscape) 56.dp else 28.dp,
                        end = (if (isLandscape) 56.dp else 28.dp) + pipEndPadding
                    )
            ) {
                Spacer(modifier = Modifier.height(textAreaDp / 2))

                val annotatedString = buildAnnotatedString {
                    words.forEachIndexed { index, word ->
                        val isPast = index < currentIndex
                        pushStringAnnotation("word", index.toString())
                        if (isPast) {
                            withStyle(
                                SpanStyle(
                                    color = Color.Gray,
                                    fontSize = 42.sp
                                )
                            ) {
                                append(word)
                            }
                        } else {
                            withStyle(
                                SpanStyle(
                                    color = Color(0xFFCCCCCC),
                                    fontSize = 42.sp
                                )
                            ) {
                                append(word)
                            }
                        }
                        append("   ")
                    }
                }
                ClickableText(
                    text = annotatedString,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { offset ->
                        val annotations = annotatedString.getStringAnnotations("word", offset, offset)
                        if (annotations.isNotEmpty()) {
                            viewModel.seekTo(annotations.first().item.toInt())
                        }
                    },
                    style = androidx.compose.ui.text.TextStyle(lineHeight = 58.sp)
                )

                Spacer(modifier = Modifier.height(textAreaDp / 2))
            }
        }
    }
}

@Composable
expect fun CameraPreview(
    cameraRecorder: CameraRecorder,
    modifier: Modifier,
)
