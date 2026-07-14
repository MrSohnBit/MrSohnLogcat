package com.mrsohn.mrsohnlogcat.component

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.text.SimpleDateFormat

@Composable
fun LogHistoryDialog(
    onDismiss: () -> Unit,
    onFileSelected: (String) -> Unit
) {
    val logDir = File(System.getProperty("user.home"), "Documents/Logcat")
    var files by remember { mutableStateOf(getLogFiles(logDir)) }
    var fileToRename by remember { mutableStateOf<File?>(null) }
    var newFileName by remember { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss") }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.7f),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("Saved Log History")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Path: ${logDir.absolutePath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (files.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No saved logs found.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(files) { file ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(file) {
                                        detectTapGestures(
                                            onDoubleTap = { 
                                                try {
                                                    onFileSelected(file.readText())
                                                    // Removed onDismiss() to keep history open
                                                } catch (e: Exception) { }
                                            }
                                        )
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = "${file.length() / 1024} KB | ${dateFormat.format(file.lastModified())}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                    
                                    Row {
                                        IconButton(onClick = { 
                                            fileToRename = file
                                            newFileName = file.nameWithoutExtension
                                        }) {
                                            Icon(Icons.Default.Edit, "Rename", modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = { 
                                            file.delete()
                                            files = getLogFiles(logDir)
                                        }) {
                                            Icon(Icons.Default.Delete, "Delete", tint = Color.Red, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )

    // Rename Dialog
    fileToRename?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToRename = null },
            title = { Text("Rename File") },
            text = {
                TextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("New Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val newFile = File(file.parentFile, "$newFileName.txt")
                    if (file.renameTo(newFile)) {
                        files = getLogFiles(logDir)
                    }
                    fileToRename = null
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToRename = null }) { Text("Cancel") }
            }
        )
    }
}

private fun getLogFiles(dir: File): List<File> {
    if (!dir.exists()) return emptyList()
    return dir.listFiles { f -> f.isFile && f.extension == "txt" }?.sortedByDescending { it.lastModified() } ?: emptyList()
}
