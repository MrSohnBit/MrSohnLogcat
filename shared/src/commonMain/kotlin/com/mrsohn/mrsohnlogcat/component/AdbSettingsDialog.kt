package com.mrsohn.mrsohnlogcat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AdbSettingsDialog(
    currentPath: String,
    excludedTags: Set<String>,
    onDismiss: () -> Unit,
    onSave: (String, Set<String>) -> Unit
) {
    var path by remember { mutableStateOf(currentPath) }
    var tags by remember { mutableStateOf(excludedTags) }
    var newTag by remember { mutableStateOf("") }

    fun addTag() {
        if (newTag.isNotBlank()) {
            tags = tags + newTag.trim()
            newTag = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ADB Path Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ADB Path Configuration", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = path,
                        onValueChange = { path = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. adb or /path/to/adb") },
                        singleLine = true,
                        label = { Text("ADB Executable Path") }
                    )
                }

                HorizontalDivider()

                // Excluded Tags Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Excluded Tags (Global)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Logs with these tags will be ignored and won't be stored in memory.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BasicTextField(
                            value = newTag,
                            onValueChange = { newTag = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                .padding(horizontal = 8.dp)
                                .onPreviewKeyEvent {
                                    if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) {
                                        addTag()
                                        true
                                    } else false
                                },
                            textStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (newTag.isEmpty()) {
                                        Text("Add Tag to Exclude...", fontSize = 13.sp, color = Color.Gray)
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        IconButton(
                            onClick = { addTag() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        shape = MaterialTheme.shapes.small,
                        tonalElevation = 2.dp,
                        border = AssistChipDefaults.assistChipBorder(true)
                    ) {
                        LazyColumn(
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(tags.toList()) { tag ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(tag, fontSize = 13.sp)
                                    IconButton(
                                        onClick = { tags = tags - tag },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color.Red,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            if (tags.isEmpty()) {
                                item {
                                    Text(
                                        "No tags excluded",
                                        modifier = Modifier.padding(8.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(path, tags) }) {
                Text("Save All Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
