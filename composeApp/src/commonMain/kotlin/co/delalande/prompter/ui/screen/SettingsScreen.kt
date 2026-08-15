package co.delalande.prompter.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.delalande.prompter.data.DebugRepository
import co.delalande.prompter.data.ScriptRepository
import co.delalande.prompter.data.SettingsRepository
import co.delalande.prompter.log.Log
import co.delalande.prompter.model.CameraMode
import co.delalande.prompter.model.Script
import co.delalande.prompter.ui.FontFamilyOptions
import co.delalande.prompter.ui.TextDisplaySample
import co.delalande.prompter.ui.parseHexColor
import kotlin.math.roundToInt

private val ColorSwatches = listOf(
    "#FFFFFF", "#CCCCCC", "#9E9E9E", "#000000",
    "#FF5252", "#FF9800", "#FFEB3B", "#4CAF50",
    "#2196F3", "#9C27B0", "#E91E63", "#00BCD4"
)

private const val DEBUG_SCRIPT_ID = "debug-large"
private const val TAG = "Settings"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    scriptRepository: ScriptRepository,
    debugRepository: DebugRepository,
    isDebug: Boolean,
    buildInfo: String,
    onBack: () -> Unit
) {
    val initial = remember { settingsRepository.load() }
    var settings by remember { mutableStateOf(initial) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = {
                        settingsRepository.save(settings)
                        onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Default Camera Mode",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            CameraMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = settings.defaultCameraMode == mode,
                        onClick = { settings = settings.copy(defaultCameraMode = mode) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (mode) {
                            CameraMode.PictureInPicture -> "Picture-in-Picture"
                            CameraMode.FullScreen -> "Full Screen"
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "When starting a new recording, the camera preview will default to this mode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Text Display",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF121212)),
                contentAlignment = Alignment.Center
            ) {
                TextDisplaySample(
                    style = settings.textDisplayStyle,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Font",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            FontDropdown(
                current = settings.textDisplayStyle.fontFamily,
                onChange = { font ->
                    settings = settings.copy(
                        textDisplayStyle = settings.textDisplayStyle.copy(fontFamily = font)
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Size: ${settings.textDisplayStyle.fontSizeSp.roundToInt()}sp",
                style = MaterialTheme.typography.titleSmall
            )
            Slider(
                value = settings.textDisplayStyle.fontSizeSp,
                onValueChange = { size ->
                    settings = settings.copy(
                        textDisplayStyle = settings.textDisplayStyle.copy(fontSizeSp = size)
                    )
                },
                valueRange = 20f..80f
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Text Colour",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            ColorSwatchRow(
                selected = settings.textDisplayStyle.textColorHex,
                onSelect = { color ->
                    settings = settings.copy(
                        textDisplayStyle = settings.textDisplayStyle.copy(textColorHex = color)
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Border Width: ${settings.textDisplayStyle.borderWidthDp.roundToInt()}dp",
                style = MaterialTheme.typography.titleSmall
            )
            Slider(
                value = settings.textDisplayStyle.borderWidthDp,
                onValueChange = { width ->
                    settings = settings.copy(
                        textDisplayStyle = settings.textDisplayStyle.copy(borderWidthDp = width)
                    )
                },
                valueRange = 0f..10f
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Border Colour",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            ColorSwatchRow(
                selected = settings.textDisplayStyle.borderColorHex,
                onSelect = { color ->
                    settings = settings.copy(
                        textDisplayStyle = settings.textDisplayStyle.copy(borderColorHex = color)
                    )
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = buildInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            if (isDebug) {
                Spacer(modifier = Modifier.height(32.dp))
                DebugPanel(
                    debugRepository = debugRepository,
                    scriptRepository = scriptRepository
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FontDropdown(
    current: String,
    onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(fontFamilyLabel(current))
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            FontFamilyOptions.forEach { name ->
                DropdownMenuItem(
                    text = { Text(fontFamilyLabel(name)) },
                    onClick = {
                        onChange(name)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun fontFamilyLabel(name: String): String =
    if (name == "Default") "System Default" else name

@Composable
private fun ColorSwatchRow(
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ColorSwatches.forEach { hex ->
            val isSelected = hex.equals(selected, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(hex))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
                    .clickable { onSelect(hex) }
            )
        }
    }
}

@Composable
private fun DebugPanel(
    debugRepository: DebugRepository,
    scriptRepository: ScriptRepository,
) {
    var settings by remember { mutableStateOf(debugRepository.load()) }
    var message by remember { mutableStateOf<String?>(null) }

    Column {
        Text(
            text = "Debug",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        DebugToggleRow(
            title = "Verbose logging",
            subtitle = "Emit debug/info/verbose levels to logcat",
            checked = settings.verboseLogging,
            onCheckedChange = { checked ->
                settings = settings.copy(verboseLogging = checked)
                debugRepository.save(settings)
            }
        )
        DebugToggleRow(
            title = "Auto-advance words",
            subtitle = "Simulate speech: auto-scrolls the script in the teleprompter",
            checked = settings.autoAdvanceWords,
            onCheckedChange = { checked ->
                settings = settings.copy(autoAdvanceWords = checked)
                debugRepository.save(settings)
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                val script = Script(
                    id = DEBUG_SCRIPT_ID,
                    title = "Debug Large Script",
                    content = debugLargeScriptContent()
                )
                scriptRepository.save(script)
                Log.i(TAG) { "Injected debug script (${script.wordCount()} words)" }
                message = "Injected '${script.title}' (${script.wordCount()} words)."
            }
        ) {
            Text("Inject 1000+ word test script")
        }
        TextButton(
            onClick = {
                scriptRepository.loadAll()
                    .filter { it.id == DEBUG_SCRIPT_ID }
                    .forEach { scriptRepository.delete(it.id) }
                Log.i(TAG) { "Removed debug scripts" }
                message = "Removed debug scripts."
            }
        ) {
            Text("Remove debug scripts")
        }
        if (message != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun DebugToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private fun debugLargeScriptContent(): String {
    val paragraph = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."
    return buildString {
        repeat(30) {
            append(paragraph)
            append("\n\n")
        }
    }
}
