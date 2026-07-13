package com.mrsohn.mrsohnlogcat.component

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.mrsohn.mrsohnlogcat.data.LogEntry
import com.mrsohn.mrsohnlogcat.data.LogLevel

@Composable
fun LogEntryRow(
    entry: LogEntry,
    showTimestamp: Boolean,
    showPid: Boolean,
    showTid: Boolean,
    showLevel: Boolean,
    showTag: Boolean,
    showPackage: Boolean,
    fontSize: Int,
    searchQuery: String = "",
    isFocusedMatch: Boolean = false,
    includeQuery: String = ""
) {
    val isDark = !MaterialTheme.colorScheme.surface.let {
        (it.red * 0.299 + it.green * 0.587 + it.blue * 0.114) > 0.5
    }

    val color = when (entry.level) {
        LogLevel.VERBOSE -> Color.Gray
        LogLevel.DEBUG -> if (isDark) Color(0xFF64B5F6) else Color(0xFF1976D2)
        LogLevel.INFO -> if (isDark) Color(0xFF81C784) else Color(0xFF388E3C)
        LogLevel.WARN -> if (isDark) Color(0xFFFFB74D) else Color(0xFFF57C00)
        LogLevel.ERROR -> if (isDark) Color(0xFFE57373) else Color(0xFFD32F2F)
        LogLevel.FATAL -> if (isDark) Color(0xFFD32F2F) else Color(0xFFB71C1C)
    }

    val contentFontSize = fontSize.sp

    val annotatedText = buildAnnotatedString {
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
            val pkg = (entry.packageName ?: "?").padEnd(25)
            append("$pkg ")
            addStyle(SpanStyle(color = Color(0xFFAED581)), start, length)
        }

        if (showTag) {
            val start = length
            val tag = entry.tag.padEnd(35)
            append("$tag ")
            addStyle(SpanStyle(color = Color(0xFFF06292)), start, length)
        }

        if (showLevel) {
            val start = length
            append("${entry.level.name.first()}  ")
            addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), start, length)
        }

        val messageStart = length
        append(entry.message)
        addStyle(SpanStyle(color = color), messageStart, length)

        // URL highlighting
        val urlRegex = """(https?://[^\s]+)""".toRegex()
        val urlMatches = urlRegex.findAll(entry.message)
        for (match in urlMatches) {
            val start = messageStart + match.range.first
            val end = messageStart + match.range.last + 1
            addStyle(
                SpanStyle(
                    color = Color(0xFF4FC3F7), // Bright Sky Blue
                    textDecoration = TextDecoration.Underline
                ),
                start,
                end
            )
        }

        // Search Query Highlighting
        if (searchQuery.isNotEmpty()) {
            var startIndex = entry.message.indexOf(searchQuery, ignoreCase = true)
            while (startIndex >= 0) {
                val start = messageStart + startIndex
                val end = start + searchQuery.length
                val highlightColor =
                    if (isFocusedMatch) Color(0xFFFFA726) else Color(0xFFFFEB3B).copy(alpha = 0.5f)
                addStyle(
                    SpanStyle(
                        background = highlightColor,
                        color = if (isDark) Color.Black else Color.Unspecified
                    ),
                    start,
                    end
                )
                startIndex = entry.message.indexOf(searchQuery, startIndex + 1, ignoreCase = true)
            }
        }

        // Include Keywords Highlighting
        if (includeQuery.isNotEmpty()) {
            val words = includeQuery.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            words.forEach { word ->
                var startIndex = entry.message.indexOf(word, ignoreCase = true)
                while (startIndex >= 0) {
                    val start = messageStart + startIndex
                    val end = start + word.length
                    addStyle(
                        SpanStyle(
                            background = Color(0xff63cdc3).copy(alpha = 0.8f), // Teal-ish Green for Include
                            color = if (isDark) Color.Black else Color.Unspecified
                        ),
                        start,
                        end
                    )
                    startIndex = entry.message.indexOf(word, startIndex + 1, ignoreCase = true)
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = annotatedText,
            modifier = Modifier.fillMaxWidth(),
            fontSize = contentFontSize,
            fontFamily = FontFamily.Monospace,
            lineHeight = contentFontSize // 줄 간격 최소화 (1.0배)
        )
    }
}