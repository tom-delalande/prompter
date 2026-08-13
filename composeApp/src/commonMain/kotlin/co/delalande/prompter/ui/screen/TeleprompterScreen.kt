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

@Composable
fun TeleprompterScreen(
    viewModel: TeleprompterViewModel,
    defaultCameraMode: CameraMode,
    textDisplayStyle: TextDisplayStyle,
    onBack: () -> Unit,
    onSwipeBack: () -> Unit = onBack,
    onSaveToPhotos: (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    var isPipMode by remember { mutableStateOf(defaultCameraMode == CameraMode.PictureInPicture) }
    val animatedAudioLevel by animateFloatAsState(
        targetValue = viewModel.audioLevel,
        animationSpec = tween(durationMillis = 120),
        label = "audioLevel"
    )

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
            pipEndPadding = pipEndPadding,
            isLandscape = isLandscape,
            textDisplayStyle = textDisplayStyle
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

        if (viewModel.isRecording || viewModel.isPlaying) {
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
                val currentIndex = viewModel.currentWordIndex

                val textColor = parseHexColor(textDisplayStyle.textColorHex)
                val borderColor = parseHexColor(textDisplayStyle.borderColorHex)
                val fontSize = textDisplayStyle.fontSizeSp.sp
                val fontFamily = fontFamilyFor(textDisplayStyle.fontFamily)
                val lineHeight = (textDisplayStyle.fontSizeSp * 1.35f).sp
                val density = LocalDensity.current
                val borderWidthPx = with(density) { textDisplayStyle.borderWidthDp.dp.toPx() }
                val verticalCenterOffset = textAreaDp / 2 - with(density) { lineHeight.toDp() } / 2

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(
                            start = if (isLandscape) 56.dp else 28.dp,
                            end = (if (isLandscape) 56.dp else 28.dp) + pipEndPadding
                        )
                ) {
                    Spacer(modifier = Modifier.height(verticalCenterOffset))

                    fun scriptString(colorFor: (isPast: Boolean) -> Color): AnnotatedString =
                        buildAnnotatedString {
                            words.forEachIndexed { index, word ->
                                pushStringAnnotation("word", index.toString())
                                withStyle(
                                    SpanStyle(
                                        color = colorFor(index < currentIndex),
                                        fontSize = fontSize
                                    )
                                ) {
                                    append(word)
                                }
                                append("   ")
                            }
                        }

                    val fillString = scriptString { isPast ->
                        if (isPast) textColor.copy(alpha = 0.45f) else textColor
                    }
                    val strokeString = scriptString { borderColor }
                    val baseStyle = TextStyle(
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        fontFamily = fontFamily
                    )
                    val onWordClick: (Int) -> Unit = { offset ->
                        val annotations = fillString.getStringAnnotations("word", offset, offset)
                        if (annotations.isNotEmpty()) {
                            viewModel.seekTo(annotations.first().item.toInt())
                        }
                    }

                    Box {
                        if (textDisplayStyle.borderWidthDp > 0f) {
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
                            style = baseStyle
                        )
                    }

                    Spacer(modifier = Modifier.height(verticalCenterOffset))
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
