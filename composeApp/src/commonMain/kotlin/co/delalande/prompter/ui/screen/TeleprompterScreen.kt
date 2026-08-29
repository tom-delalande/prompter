package co.delalande.prompter.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MicExternalOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import co.delalande.prompter.data.DebugRepository
import co.delalande.prompter.model.CameraMode
import co.delalande.prompter.model.TextDisplayStyle
import co.delalande.prompter.recording.CameraRecorder
import co.delalande.prompter.ui.SystemBackHandler
import co.delalande.prompter.ui.fontFamilyFor
import co.delalande.prompter.ui.parseHexColor
import co.delalande.prompter.viewmodel.TeleprompterViewModel
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

private val CURRENT_WORD_BACKGROUND = Color(0xFF1565C0)

private fun formatRecordingTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    fun pad(value: Long): String = if (value < 10) "0$value" else value.toString()
    return if (hours > 0) "$hours:${pad(minutes)}:${pad(seconds)}" else "${pad(minutes)}:${pad(seconds)}"
}

@Composable
fun TeleprompterScreen(
    viewModel: TeleprompterViewModel,
    debugRepository: DebugRepository,
    defaultCameraMode: CameraMode,
    textDisplayStyle: TextDisplayStyle,
    onBack: () -> Unit,
    onSwipeBack: () -> Unit = onBack,
    onSaveToPhotos: (String) -> Unit,
    onSaveStateChanged: (Boolean) -> Unit = {},
) {
    var isPipMode by remember { mutableStateOf(defaultCameraMode == CameraMode.PictureInPicture) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var recordingElapsedMillis by remember { mutableStateOf(0L) }
    val animatedAudioLevel by animateFloatAsState(
        targetValue = viewModel.audioLevel,
        animationSpec = tween(durationMillis = 120),
        label = "audioLevel"
    )

    LaunchedEffect(viewModel.isRecording) {
        if (!viewModel.isRecording) return@LaunchedEffect
        val startTime = TimeSource.Monotonic.markNow()
        recordingElapsedMillis = 0L
        while (true) {
            delay(100)
            recordingElapsedMillis = startTime.elapsedNow().inWholeMilliseconds
        }
    }

    LaunchedEffect(viewModel.lastRecordedFile) {
        if (viewModel.lastRecordedFile != null) {
            viewModel.lastRecordedFile?.let { onSaveToPhotos(it) }
            viewModel.resetAfterSave()
        }
    }

    LaunchedEffect(viewModel.isProcessing, viewModel.lastRecordedFile) {
        if (viewModel.isProcessing) {
            onSaveStateChanged(true)
        } else if (viewModel.lastRecordedFile == null) {
            onSaveStateChanged(false)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startMonitoring()
        repeat(10) {
            delay(400)
            viewModel.startMonitoring()
        }
    }

    val autoAdvance = remember { debugRepository.load().autoAdvanceWords }
    LaunchedEffect(autoAdvance) {
        if (!autoAdvance) return@LaunchedEffect
        while (viewModel.currentWordIndex < viewModel.script.wordCount()) {
            delay(150)
            viewModel.seekTo(viewModel.currentWordIndex + 1)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        SystemBackHandler(
            onBack = {
                if (viewModel.isProcessing) return@SystemBackHandler
                if (viewModel.isRecording) {
                    showExitConfirmDialog = true
                } else {
                    viewModel.destroy()
                    onBack()
                }
            }
        )

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
                    val pipSize = if (isLandscape) 120.dp * (16f / 9f) else 140.dp
                    val pipAspect = if (isLandscape) 16f / 9f else 9f / 16f
                    val pipAlign = if (isLandscape) Alignment.TopEnd else Alignment.BottomEnd
                    Modifier
                        .align(pipAlign)
                        .padding(
                            top = if (isLandscape) 8.dp else 0.dp,
                            end = 16.dp,
                            bottom = if (isLandscape) 0.dp else 12.dp,
                            start = 0.dp
                        )
                        .then(if (isLandscape) Modifier.statusBarsPadding() else Modifier)
                        .width(pipSize)
                        .aspectRatio(pipAspect)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isPipMode = false }
                } else {
                    Modifier.fillMaxSize()
                }
            )
        }

        TeleprompterContent(
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxSize()
                .clickable { if (!isPipMode) isPipMode = true }
                .zIndex(if (isCameraPip) 0f else 1f),
            isLandscape = isLandscape,
            isVerticalPip = isCameraPip && !isLandscape,
            textDisplayStyle = textDisplayStyle,
            onTapEmptyWordArea = { if (!isPipMode) isPipMode = true }
        )

        IconButton(
            onClick = {
                if (viewModel.isProcessing) return@IconButton
                if (viewModel.isRecording) {
                    showExitConfirmDialog = true
                } else {
                    viewModel.destroy()
                    onBack()
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .zIndex(4f)
                .statusBarsPadding()
                .padding(start = 8.dp, top = 8.dp)
        ) {
            Text("\u2715", fontSize = 24.sp, color = Color.White)
        }

        if (autoAdvance) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(4f)
                    .padding(bottom = 128.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF8B7500).copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "DEBUG AUTO-SCROLL",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (viewModel.isRecording) {
            Row(
                modifier = Modifier
                    .align(if (isLandscape) Alignment.BottomCenter else Alignment.TopCenter)
                    .zIndex(4f)
                    .then(if (isLandscape) Modifier.navigationBarsPadding().padding(bottom = 10.dp) else Modifier.statusBarsPadding().padding(top = 10.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF5252))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatRecordingTime(recordingElapsedMillis),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Column(
            modifier = Modifier
                .align(if (isLandscape) Alignment.BottomEnd else Alignment.TopEnd)
                .zIndex(4f)
                .then(
                    if (isLandscape) Modifier.navigationBarsPadding().padding(bottom = 24.dp, end = 14.dp)
                    else Modifier.statusBarsPadding().padding(top = 10.dp, end = 14.dp)
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (viewModel.isExternalMic) {
                Icon(
                    imageVector = Icons.Filled.MicExternalOn,
                    contentDescription = "External microphone in use",
                    tint = Color(0xFF81C784),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.height(6.dp))
            }
            AudioLevelBar(level = animatedAudioLevel)
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

        if (showExitConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showExitConfirmDialog = false },
                title = { Text("Stop recording?") },
                text = { Text("Your current recording will be stopped and you'll leave the teleprompter screen.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExitConfirmDialog = false
                            viewModel.destroy()
                            onBack()
                        }
                    ) {
                        Text("Stop & Exit")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
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
    modifier: Modifier = Modifier,
    isLandscape: Boolean,
    isVerticalPip: Boolean = false,
    textDisplayStyle: TextDisplayStyle,
    onTapEmptyWordArea: () -> Unit = {},
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomNavInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
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
                .padding(
                    bottom = when {
                        isVerticalPip -> 280.dp
                        isLandscape -> bottomNavInset + 16.dp
                        else -> 150.dp
                    },
                    top = if (isLandscape) 44.dp else statusBarTop + 60.dp
                )
        ) {
            val textAreaDp = maxHeight
            val words = viewModel.script.words
            val lineBreakBefore = viewModel.script.lineBreakBefore
            val currentIndex = viewModel.currentWordIndex
            val wordCount = words.size

            val textColor = parseHexColor(textDisplayStyle.textColorHex)
            val borderColor = parseHexColor(textDisplayStyle.borderColorHex)
            val fontSize = textDisplayStyle.fontSizeSp.sp
            val fontFamily = fontFamilyFor(textDisplayStyle.fontFamily)
            val lineHeight = (textDisplayStyle.fontSizeSp * 1.35f).sp
            val density = LocalDensity.current
            val borderWidthPx = with(density) { textDisplayStyle.borderWidthDp.dp.toPx() }

            val baseStyle = TextStyle(
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontFamily = fontFamily
            )

            val horizontalPadding = if (isLandscape) 56.dp else 28.dp
            val endExtraPadding = if (isLandscape) 140.dp else 0.dp
            val availableWidthPx = with(density) {
                (maxWidth - horizontalPadding * 2 - endExtraPadding).toPx()
            }
            val fontSizePx = with(density) { fontSize.toPx() }
            val charWidthFactor = when (textDisplayStyle.fontFamily) {
                "Monospace" -> 0.60f
                "Serif" -> 0.46f
                "Cursive" -> 0.50f
                else -> 0.43f
            }
            val charsPerLine = (availableWidthPx / (fontSizePx * charWidthFactor)).toInt().coerceIn(10, 160)

            val chunkStarts = remember(words, charsPerLine) {
                val starts = mutableListOf(0)
                var charBudget = 0
                for (i in words.indices) {
                    val wordLen = words[i].length
                    val spaceLen = if (i > 0 && !lineBreakBefore[i]) 3 else 0
                    if (i > 0 && charBudget + spaceLen + wordLen > charsPerLine) {
                        val lineFullness = charBudget.toFloat() / charsPerLine
                        if (lineFullness >= 0.45f) {
                            starts.add(i)
                            charBudget = wordLen
                        } else {
                            charBudget += spaceLen + wordLen
                        }
                    } else {
                        charBudget += spaceLen + wordLen
                    }
                }
                starts
            }
            val chunkCount = chunkStarts.size
            val listState = rememberLazyListState()

            LaunchedEffect(currentIndex, wordCount) {
                if (wordCount == 0 || chunkStarts.isEmpty()) return@LaunchedEffect
                val safeIndex = currentIndex.coerceIn(0, wordCount - 1)
                val chunkIndex = chunkStarts.indexOfLast { it <= safeIndex }
                val target = (chunkIndex - 2).coerceAtLeast(0)
                if (target != listState.firstVisibleItemIndex) {
                    listState.animateScrollToItem(target)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = horizontalPadding,
                        end = horizontalPadding + endExtraPadding
                    )
            ) {
                items(chunkCount, key = { it }) { chunkIndex ->
                    val start = chunkStarts[chunkIndex]
                    val end = if (chunkIndex + 1 < chunkStarts.size) chunkStarts[chunkIndex + 1] else wordCount

                    val annotatedString = buildAnnotatedString {
                        for (i in start until end) {
                            if (i > start) {
                                if (lineBreakBefore[i]) append("\n") else append("   ")
                            }
                            pushStringAnnotation("word", i.toString())
                            withStyle(
                                SpanStyle(
                                    color = if (i < currentIndex) textColor.copy(alpha = 0.45f) else textColor,
                                    background = if (i == currentIndex) CURRENT_WORD_BACKGROUND.copy(alpha = 0.6f) else Color.Transparent,
                                    fontSize = fontSize
                                )
                            ) {
                                append(words[i])
                            }
                            pop()
                        }
                    }

                    val strokeString = if (textDisplayStyle.borderWidthDp > 0f) {
                        buildAnnotatedString {
                            for (i in start until end) {
                                if (i > start) {
                                    if (lineBreakBefore[i]) append("\n") else append("   ")
                                }
                                withStyle(SpanStyle(color = borderColor, fontSize = fontSize)) {
                                    append(words[i])
                                }
                            }
                        }
                    } else null

                    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                    Box {
                        if (strokeString != null) {
                            Text(
                                text = strokeString,
                                modifier = Modifier.fillMaxWidth(),
                                style = baseStyle.copy(
                                    drawStyle = Stroke(
                                        width = borderWidthPx,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            )
                        }
                        Text(
                            text = annotatedString,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        val layout = textLayoutResult ?: return@detectTapGestures
                                        val charOffset = layout.getOffsetForPosition(offset)
                                        val end = (charOffset + 1).coerceAtMost(annotatedString.length)
                                        val annotations = annotatedString.getStringAnnotations("word", charOffset, end)
                                        if (annotations.isNotEmpty()) {
                                            viewModel.seekTo(annotations.first().item.toInt())
                                        } else {
                                            onTapEmptyWordArea()
                                        }
                                    }
                                },
                            onTextLayout = { textLayoutResult = it },
                            style = baseStyle
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioLevelBar(level: Float) {
    val barWidth = 5.dp
    val barHeight = 44.dp
    Box(
        modifier = Modifier
            .width(barWidth + 10.dp)
            .height(barHeight + 10.dp)
            .padding(5.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .width(barWidth)
                .height(barHeight)
                .clip(RoundedCornerShape(2.5.dp))
                .background(Color.White.copy(alpha = 0.15f))
        )
        Box(
            modifier = Modifier
                .width(barWidth)
                .fillMaxHeight(level.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(2.5.dp))
                .background(Color(0xFF66BB6A))
        )
    }
}

@Composable
expect fun CameraPreview(
    cameraRecorder: CameraRecorder,
    modifier: Modifier,
)
