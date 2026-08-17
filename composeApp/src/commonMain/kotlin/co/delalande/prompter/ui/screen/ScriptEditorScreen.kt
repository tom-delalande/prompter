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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.delalande.prompter.data.ScriptRepository
import co.delalande.prompter.model.Script
import co.delalande.prompter.ui.SystemBackHandler
import co.delalande.prompter.ui.formatLastUpdated
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptEditorScreen(
    scriptId: String?,
    repository: ScriptRepository,
    onBack: () -> Unit,
    onPlay: (Script, Int) -> Unit
) {
    val existing = scriptId?.let { repository.getById(it) }
    val currentId = remember { existing?.id ?: generateId() }
    val initialTitle = remember { existing?.title ?: "" }
    val initialContent = remember { existing?.content ?: "" }

    var title by remember { mutableStateOf(initialTitle) }
    var contentValue by remember { mutableStateOf(TextFieldValue(initialContent)) }
    var lastUpdatedAt by remember { mutableStateOf(existing?.updatedAt ?: 0L) }

    val content = contentValue.text

    fun currentScript(): Script = Script(
        id = currentId,
        title = title.ifBlank { "Untitled" },
        content = content
    )

    fun hasText() = title.isNotBlank() || content.isNotBlank()

    fun cursorOffsetToWordIndex(text: String, offset: Int): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        var wordCount = 0
        var inWord = false
        for (i in 0 until offset.coerceAtMost(text.length)) {
            if (text[i].isWhitespace()) {
                inWord = false
            } else if (!inWord) {
                inWord = true
                wordCount++
            }
        }
        return wordCount
    }

    fun wordIndexAtCursor(): Int = cursorOffsetToWordIndex(content, contentValue.selection.start)

    fun save() {
        if (hasText() && repository.save(currentScript())) {
            lastUpdatedAt = currentTimeMillis()
        }
    }

    LaunchedEffect(title, content) {
        if (hasText()) {
            delay(400)
            save()
        }
    }

    val wordCount = remember(content) {
        val t = content.trim()
        if (t.isEmpty()) 0 else t.split("\\s+".toRegex()).size
    }

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
                    if (hasText()) {
                        val cursorIndex = wordIndexAtCursor()
                        TextButton(onClick = { save(); onPlay(currentScript(), 0) }) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play")
                        }
                        if (cursorIndex > 0) {
                            TextButton(onClick = { save(); onPlay(currentScript(), cursorIndex) }) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Play from here")
                            }
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
                    Column {
                        HintedTextField(
                            value = title,
                            onValueChange = { title = it },
                            hint = "Title",
                            textStyle = TextStyle(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        HintedTextField(
                            value = contentValue,
                            onValueChange = { contentValue = it },
                            hint = "Take a note...",
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 24.sp,
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
                    val updatedText = lastUpdatedAt
                        .takeIf { it > 0 }
                        ?.let { "Updated ${formatLastUpdated(it)}" }
                    if (updatedText != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = updatedText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HintedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    textStyle: TextStyle,
    singleLine: Boolean = false,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        modifier = modifier,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        hint,
                        style = textStyle.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun HintedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    hint: String,
    textStyle: TextStyle,
    singleLine: Boolean = false,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        modifier = modifier,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box {
                if (value.text.isEmpty()) {
                    Text(
                        hint,
                        style = textStyle.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                        )
                    )
                }
                innerTextField()
            }
        }
    )
}

private var idCounter = 0L
internal fun generateId(): String {
    idCounter++
    return "script_${idCounter}_${currentTimeMillis()}"
}

internal expect fun currentTimeMillis(): Long
