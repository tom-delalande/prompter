package co.delalande.prompter.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.delalande.prompter.data.ScriptRepository
import co.delalande.prompter.model.Script
import co.delalande.prompter.ui.SystemBackHandler
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptEditorScreen(
    scriptId: String?,
    repository: ScriptRepository,
    onBack: () -> Unit,
    onPlay: (Script) -> Unit
) {
    val existing = scriptId?.let { repository.getById(it) }
    val currentId = remember { existing?.id ?: generateId() }
    val initialText = remember {
        existing?.let { "${it.title}\n${it.content}" } ?: ""
    }

    var text by remember { mutableStateOf(initialText) }

    fun currentScript(): Script {
        val lines = text.split("\n", limit = 2)
        return Script(
            id = currentId,
            title = lines.getOrNull(0)?.ifBlank { "Untitled" } ?: "Untitled",
            content = lines.getOrNull(1) ?: ""
        )
    }

    fun save() {
        if (text.isNotBlank()) repository.save(currentScript())
    }

    LaunchedEffect(text) {
        if (text.isNotBlank()) {
            delay(400)
            repository.save(currentScript())
        }
    }

    val wordCount = remember(text) {
        val t = text.trim()
        if (t.isEmpty()) 0 else t.split("\\s+".toRegex()).size
    }

    val transformation = FirstLineTitleTransformation(
        onSurface = MaterialTheme.colorScheme.onSurface,
        titleSize = 24.sp,
        bodySize = 16.sp,
    )

    SystemBackHandler(onBack = { save(); onBack() })

    Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        TextButton(onClick = { save(); onBack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Back")
                        }
                    },
                    actions = {
                        if (text.isNotBlank()) {
                            TextButton(onClick = { save(); onPlay(currentScript()) }) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Play")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    )
                )
            }
        ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 72.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp)
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = transformation,
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 24.sp,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box {
                                if (text.isEmpty()) {
                                    Column {
                                        Text(
                                            "Title",
                                            style = TextStyle(
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Take a note...",
                                            style = TextStyle(
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                            )
                                        )
                                    }
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$wordCount words",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

private class FirstLineTitleTransformation(
    private val onSurface: Color,
    private val titleSize: TextUnit,
    private val bodySize: TextUnit,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val firstNl = s.indexOf('\n')
        val titleEnd = if (firstNl == -1) s.length else firstNl

        val annotated = buildAnnotatedString {
            if (titleEnd > 0) {
                withStyle(
                    SpanStyle(
                        fontSize = titleSize,
                        fontWeight = FontWeight.SemiBold,
                        color = onSurface,
                    )
                ) {
                    append(s.substring(0, titleEnd))
                }
            }
            if (firstNl != -1) {
                append("\n")
                withStyle(
                    SpanStyle(
                        fontSize = bodySize,
                        color = onSurface,
                    )
                ) {
                    append(s.substring(firstNl + 1))
                }
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}

private var idCounter = 0L
internal fun generateId(): String {
    idCounter++
    return "script_${idCounter}_${currentTimeMillis()}"
}

internal expect fun currentTimeMillis(): Long
