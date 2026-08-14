package co.delalande.prompter.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import co.delalande.prompter.model.CameraMode
import co.delalande.prompter.model.TextDisplayStyle
import co.delalande.prompter.recording.CameraRecorder
import co.delalande.prompter.ui.SystemBackHandler
import co.delalande.prompter.ui.fontFamilyFor
import co.delalande.prompter.ui.parseHexColor
import co.delalande.prompter.viewmodel.TeleprompterViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlin.math.abs
import kotlin.time.TimeSource

private const val WINDOW_COVER_BEFORE = 60
private const val WINDOW_INITIAL = 300
private const val VIEWPORT_MARGIN_MULT = 1.5f

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
    defaultCameraMode: CameraMode,
    textDisplayStyle: TextDisplayStyle,
    onBack: () -> Unit,
    onSwipeBack: () -> Unit = onBack,
    onSaveToPhotos: (String) -> Unit,
    onSaveStateChanged: (Boolean) -> Unit = {},
) {
    val scrollState = rememberScrollState()
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
            viewModel.isRecording = false
            viewModel.isPlaying = false
            viewModel.lastRecordedFile?.let { onSaveToPhotos(it) }
            viewModel.destroy()
            onBack()
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
                            top = if (isLandscape) 16.dp else 0.dp,
                            end = if (isLandscape) 16.dp else 8.dp,
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
            if (isLandscape) 100.dp else 170.dp
        } else 0.dp

        TeleprompterContent(
            viewModel = viewModel,
            scrollState = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .clickable { if (!isPipMode) isPipMode = true }
                .zIndex(if (isCameraPip) 0f else 1f),
            pipEndPadding = pipEndPadding,
            isLandscape = isLandscape,
            textDisplayStyle = textDisplayStyle
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

        if (viewModel.isRecording) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(4f)
                    .statusBarsPadding()
                    .padding(top = 10.dp)
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
                .align(Alignment.TopEnd)
                .zIndex(4f)
                .statusBarsPadding()
                .padding(top = 10.dp, end = 14.dp),
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
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
    pipEndPadding: Dp = 0.dp,
    isLandscape: Boolean,
    textDisplayStyle: TextDisplayStyle,
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
                .padding(
                    bottom = if (isLandscape) 0.dp else 120.dp,
                    top = if (isLandscape) 0.dp else 48.dp
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
                val verticalCenterOffset = textAreaDp / 2 - with(density) { lineHeight.toDp() } / 2

                val lineHeightPx = with(density) { lineHeight.toPx() }
                val wordY = remember(words, lineHeightPx) {
                    FloatArray(wordCount) { i -> (lineHeightPx / 8f) * i }
                }
                var currentWordCenterY by remember { mutableStateOf(-1f) }
                val centerSpacerPx = with(density) { verticalCenterOffset.toPx() }
                val vpPx = with(density) { textAreaDp.toPx() }

                var windowStart by remember { mutableStateOf(0) }
                var windowEnd by remember(wordCount) { mutableStateOf(minOf(WINDOW_INITIAL, wordCount)) }
                var offsetBefore by remember { mutableStateOf(0f) }
                var windowHeightPx by remember { mutableStateOf(0f) }
                var estimatedDocHeightPx by remember { mutableStateOf(0f) }
                var autoScrollInProgress by remember { mutableStateOf(false) }
                var userScrolling by remember { mutableStateOf(false) }
                var wordsPerLine by remember { mutableStateOf(1f) }

                val windowSize = windowEnd - windowStart

                LaunchedEffect(currentIndex, wordCount) {
                    if (wordCount == 0) return@LaunchedEffect
                    if (currentIndex < windowStart || currentIndex >= windowEnd) {
                        currentWordCenterY = -1f
                        val newStart = (currentIndex - WINDOW_COVER_BEFORE).coerceIn(0, wordCount - 1)
                        offsetBefore = wordY[newStart]
                        windowStart = newStart
                        windowEnd = maxOf(windowEnd, minOf(newStart + WINDOW_INITIAL, wordCount))
                        windowEnd = maxOf(windowEnd, currentIndex + 1)
                    }
                }

                LaunchedEffect(currentIndex, currentWordCenterY, scrollState.maxValue) {
                    if (currentIndex >= wordCount) return@LaunchedEffect
                    if (currentWordCenterY < 0f) return@LaunchedEffect
                    if (userScrolling) return@LaunchedEffect
                    val target = (centerSpacerPx + currentWordCenterY - vpPx / 2f).toInt()
                    if (abs(target - scrollState.value) <= lineHeightPx * 0.8f) return@LaunchedEffect
                    autoScrollInProgress = true
                    try {
                        scrollState.animateScrollTo(target.coerceAtLeast(0))
                    } finally {
                        autoScrollInProgress = false
                    }
                }

                LaunchedEffect(scrollState.maxValue, vpPx) {
                    if (wordCount == 0) return@LaunchedEffect
                    snapshotFlow { scrollState.value to scrollState.isScrollInProgress }
                        .collect { (scroll, inProgress) ->
                            userScrolling = inProgress && !autoScrollInProgress
                            val viewportTopY = scroll - centerSpacerPx
                            val viewportBottomY = scroll + vpPx - centerSpacerPx
                            val windowBottom = offsetBefore + windowHeightPx
                            val marginPx = vpPx * VIEWPORT_MARGIN_MULT
                            val avgWordHeight = lineHeightPx / maxOf(wordsPerLine, 1f)
                            if (windowStart > 0 && viewportTopY - offsetBefore < marginPx) {
                                val deficit = marginPx - (viewportTopY - offsetBefore)
                                val wordsToAdd = ((deficit + marginPx) / avgWordHeight).toInt() + 1
                                val newStart = (windowStart - wordsToAdd).coerceAtLeast(0)
                                if (newStart != windowStart) {
                                    offsetBefore = wordY[newStart]
                                    windowStart = newStart
                                }
                            }
                            if (windowEnd < wordCount && windowBottom - viewportBottomY < marginPx) {
                                val deficit = marginPx - (windowBottom - viewportBottomY)
                                val wordsToAdd = ((deficit + marginPx) / avgWordHeight).toInt() + 1
                                windowEnd = (windowEnd + wordsToAdd).coerceAtMost(wordCount)
                            }
                        }
                }

                val offsets = IntArray(windowSize)
                val fillString = buildAnnotatedString {
                    for (i in windowStart until windowEnd) {
                        if (i > windowStart) {
                            if (lineBreakBefore[i]) {
                                append("\n")
                            } else {
                                append("   ")
                            }
                        }
                        offsets[i - windowStart] = length
                        pushStringAnnotation("word", i.toString())
                        withStyle(
                            SpanStyle(
                                color = if (i < currentIndex) textColor.copy(alpha = 0.45f) else textColor,
                                background = if (i == currentIndex) Color(0xFF1565C0).copy(alpha = 0.6f) else Color.Transparent,
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
                        for (i in windowStart until windowEnd) {
                            if (i > windowStart) {
                                if (lineBreakBefore[i]) {
                                    append("\n")
                                } else {
                                    append("   ")
                                }
                            }
                            withStyle(SpanStyle(color = borderColor, fontSize = fontSize)) {
                                append(words[i])
                            }
                        }
                    }
                } else null
                val baseStyle = TextStyle(
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    fontFamily = fontFamily
                )
                val onWordClick: (Int) -> Unit = { offset ->
                    val end = (offset + 1).coerceAtMost(fillString.length)
                    val annotations = fillString.getStringAnnotations("word", offset, end)
                    if (annotations.isNotEmpty()) {
                        viewModel.seekTo(annotations.first().item.toInt())
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(
                            start = if (isLandscape) 56.dp else 28.dp,
                            end = (if (isLandscape) 56.dp else 28.dp) + pipEndPadding
                        )
                ) {
                    ChunkedSpacer(heightPx = centerSpacerPx + offsetBefore)

                    Box {
                        if (strokeString != null) {
                            ClickableText(
                                text = strokeString,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onWordClick,
                                style = baseStyle.copy(
                                    drawStyle = Stroke(
                                        width = borderWidthPx,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            )
                        }
                        ClickableText(
                            text = fillString,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onWordClick,
                            style = baseStyle,
                            onTextLayout = { result ->
                                windowHeightPx = result.size.height.toFloat()
                                for (k in 0 until windowSize) {
                                    val box = result.getBoundingBox(offsets[k])
                                    wordY[windowStart + k] = offsetBefore + box.top
                                }
                                if (windowSize > 0) {
                                    val newWpl = windowSize.toFloat() / result.lineCount.coerceAtLeast(1)
                                    if (newWpl != wordsPerLine) wordsPerLine = newWpl
                                    var prev = offsetBefore
                                    for (i in windowStart - 1 downTo 0) {
                                        prev -= if (lineBreakBefore[i + 1]) lineHeightPx else lineHeightPx / newWpl
                                        wordY[i] = prev
                                    }
                                    prev = wordY[windowEnd - 1]
                                    for (i in windowEnd until wordCount) {
                                        prev += if (lineBreakBefore[i]) lineHeightPx else lineHeightPx / newWpl
                                        wordY[i] = prev
                                    }
                                    estimatedDocHeightPx = wordY[wordCount - 1] + lineHeightPx
                                    val rel = (currentIndex - windowStart).coerceIn(0, windowSize - 1)
                                    val cb = result.getBoundingBox(offsets[rel])
                                    currentWordCenterY = offsetBefore + cb.top + cb.height / 2f
                                }
                            }
                        )
                    }

                    ChunkedSpacer(
                        heightPx = (estimatedDocHeightPx - (offsetBefore + windowHeightPx))
                            .coerceAtLeast(centerSpacerPx)
                    )
                }
        }
    }
}

@Composable
private fun ChunkedSpacer(heightPx: Float) {
    if (heightPx <= 0f) return
    val density = LocalDensity.current
    val chunkPx = 30000f
    val fullChunks = (heightPx / chunkPx).toInt()
    repeat(fullChunks) {
        Spacer(modifier = Modifier.height(with(density) { chunkPx.toDp() }))
    }
    val remainder = heightPx - fullChunks * chunkPx
    if (remainder > 0f) {
        Spacer(modifier = Modifier.height(with(density) { remainder.toDp() }))
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
