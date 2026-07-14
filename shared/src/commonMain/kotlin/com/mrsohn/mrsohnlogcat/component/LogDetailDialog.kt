package com.mrsohn.mrsohnlogcat.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.mrsohn.mrsohnlogcat.data.LogEntry
import com.mrsohn.mrsohnlogcat.data.LogLevel
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun LogDetailDialog(
    entries: List<LogEntry>,
    showTimestamp: Boolean,
    showPid: Boolean,
    showTid: Boolean,
    showLevel: Boolean,
    showTag: Boolean,
    showPackage: Boolean,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    
    // Initial content generation based on entries and visible fields
    val initialAnnotatedText = remember(entries, showTimestamp, showPid, showTid, showLevel, showTag, showPackage) {
        generateLogAnnotatedString(entries, showTimestamp, showPid, showTid, showLevel, showTag, showPackage)
    }

    var textFieldValue by remember { 
        mutableStateOf(TextFieldValue(
            annotatedString = initialAnnotatedText,
            selection = TextRange(initialAnnotatedText.length) // End of text
        ))
    }
    val scrollState = rememberScrollState()

    // Auto-scroll to bottom on start
    LaunchedEffect(Unit) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    val isDark = !MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299 + it.green * 0.587 + it.blue * 0.114) > 0.5
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Log Viewer (Notepad Mode)", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Lines: ${entries.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { clipboardManager.setText(AnnotatedString(textFieldValue.text)) },
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy All", fontSize = 12.sp)
                    }
                    
                    FilledTonalButton(
                        onClick = {
                            val dialog = FileDialog(null as Frame?, "Save Log", FileDialog.SAVE)
                            dialog.file = "logcat_${System.currentTimeMillis()}.txt"
                            dialog.isVisible = true
                            val directory = dialog.directory
                            val fileName = dialog.file
                            if (directory != null && fileName != null) {
                                val file = File(directory, fileName)
                                try {
                                    file.writeText(textFieldValue.text)
                                } catch (e: Exception) {
                                    // Handle error
                                }
                            }
                        },
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save as Text", fontSize = 12.sp)
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFF0),
                    border = AssistChipDefaults.assistChipBorder(true)
                ) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            // Re-apply syntax highlighting if text changed
                            textFieldValue = if (newValue.text != textFieldValue.text) {
                                newValue.copy(annotatedString = reHighlightLogText(newValue.text))
                            } else {
                                newValue
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(scrollState),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = if (isDark) Color.White else Color.Black
                        ),
                        cursorBrush = SolidColor(if (isDark) Color.White else Color.Black)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// Generate the initial rich text from LogEntry objects
private fun generateLogAnnotatedString(
    entries: List<LogEntry>,
    showTimestamp: Boolean,
    showPid: Boolean,
    showTid: Boolean,
    showLevel: Boolean,
    showTag: Boolean,
    showPackage: Boolean
): AnnotatedString = buildAnnotatedString {
    entries.forEachIndexed { index, entry ->
        val levelColor = when (entry.level) {
            LogLevel.VERBOSE -> Color.Gray
            LogLevel.DEBUG -> Color(0xFF64B5F6)
            LogLevel.INFO -> Color(0xFF81C784)
            LogLevel.WARN -> Color(0xFFFFB74D)
            LogLevel.ERROR, LogLevel.FATAL -> Color(0xFFE57373)
        }

        if (showTimestamp) {
            val start = length
            append("${entry.timestamp} ")
            addStyle(SpanStyle(color = Color(0xFF4FC3F7)), start, length)
        }

        if (showPid || showTid) {
            val start = length
            if (showPid) append(entry.processId.toString().padStart(5))
            if (showPid && showTid) append("-")
            if (showTid) append(entry.threadId.toString().padStart(5))
            append(" ")
            addStyle(SpanStyle(color = Color(0xFFFFA726)), start, length)
        }

        if (showPackage) {
            val start = length
            append("${(entry.packageName ?: "?").padEnd(25)} ")
            addStyle(SpanStyle(color = Color(0xFFAED581)), start, length)
        }

        if (showTag) {
            val start = length
            append("${entry.tag.padEnd(35)} ")
            addStyle(SpanStyle(color = Color(0xFFF06292)), start, length)
        }

        if (showLevel) {
            val start = length
            append("${entry.level.name.first()} ")
            addStyle(SpanStyle(color = levelColor, fontWeight = FontWeight.Bold), start, length)
        }

        val msgStart = length
        append(entry.message)
        addStyle(SpanStyle(color = levelColor), msgStart, length)

        if (index < entries.size - 1) append("\n")
    }
}

// Keep colors consistent even when the user edits the text
private fun reHighlightLogText(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.split("\n")
    lines.forEachIndexed { index, line ->
        // Detect level: matches single character [VDIWEF] surrounded by spaces or at line positions
        val levelMatch = Regex("""\s([VDIWEF])\s""").find(line)
        val levelChar = levelMatch?.groupValues?.get(1) ?: "D"
        val levelColor = when (levelChar) {
            "V" -> Color.Gray
            "D" -> Color(0xFF64B5F6)
            "I" -> Color(0xFF81C784)
            "W" -> Color(0xFFFFB74D)
            "E", "F" -> Color(0xFFE57373)
            else -> Color(0xFF64B5F6)
        }

        append(line)
        val lineStart = length - line.length
        addStyle(SpanStyle(color = levelColor), lineStart, length)
        
        // Highlight timestamp pattern
        val tsMatch = Regex("""\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3}""").find(line)
        tsMatch?.let {
            addStyle(SpanStyle(color = Color(0xFF4FC3F7)), lineStart + it.range.first, lineStart + it.range.last + 1)
        }

        if (index < lines.size - 1) append("\n")
    }
}
