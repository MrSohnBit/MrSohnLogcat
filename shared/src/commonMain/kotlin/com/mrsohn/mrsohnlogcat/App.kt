package com.mrsohn.mrsohnlogcat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
@Preview
fun App() {
    val settings = remember { getSettingsRepository() }
    var adbPath by remember { mutableStateOf(settings.getString("adbPath", "adb")) }
    var isAdbValid by remember { mutableStateOf<Boolean?>(null) }
    
    // Theme state: 0 = System, 1 = Light, 2 = Dark
    var themeMode by remember { mutableStateOf(settings.getString("themeMode", "0").toIntOrNull() ?: 0) }
    
    val isDark = when (themeMode) {
        1 -> false
        2 -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    val repositoryForCheck = remember(adbPath) { getLogRepository(adbPath) }
    LaunchedEffect(adbPath) {
        isAdbValid = repositoryForCheck.checkAdb(adbPath)
    }

    MaterialTheme(
        colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (isAdbValid == false) {
                AdbSetupScreen(
                    initialPath = adbPath,
                    onPathSelected = { newPath ->
                        adbPath = newPath
                        settings.setString("adbPath", newPath)
                    }
                )
            } else if (isAdbValid == true) {
                LogcatScreen(
                    adbPath = adbPath,
                    themeMode = themeMode,
                    onThemeChange = { newMode ->
                        themeMode = newMode
                        settings.setString("themeMode", newMode.toString())
                    },
                    onAdbPathChange = { newPath ->
                        adbPath = newPath
                        settings.setString("adbPath", newPath)
                    }
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun AdbSetupScreen(initialPath: String, onPathSelected: (String) -> Unit) {
    var path by remember { mutableStateOf(initialPath) }
    var error by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val repository = remember { getLogRepository(path) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ADB Configuration", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text("Please provide the path to your 'adb' executable (e.g., C:\\Android\\Sdk\\platform-tools\\adb.exe)")
        Spacer(Modifier.height(24.dp))
        
        TextField(
            value = path,
            onValueChange = { path = it },
            label = { Text("ADB Path") },
            modifier = Modifier.fillMaxWidth(),
            isError = error != null
        )
        
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        
        Spacer(Modifier.height(24.dp))
        
        Button(
            onClick = {
                testing = true
                scope.launch {
                    val isValid = repository.checkAdb(path)
                    testing = false
                    if (isValid) {
                        onPathSelected(path)
                    } else {
                        error = "Could not find adb at the specified path. Please check and try again."
                    }
                }
            },
            enabled = !testing
        ) {
            if (testing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Text("Test and Save")
            }
        }
    }
}

@Composable
fun LogcatScreen(
    adbPath: String,
    themeMode: Int,
    onThemeChange: (Int) -> Unit,
    onAdbPathChange: (String) -> Unit
) {
    val isDark = when (themeMode) {
        1 -> false
        2 -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val repository = remember(adbPath) { getLogRepository(adbPath) }
    val settings = remember { getSettingsRepository() }
    
    val logs = remember { mutableStateListOf<LogEntry>() }
    var isPaused by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(listOf<DeviceInfo>()) }
    var selectedDeviceSerial by remember { mutableStateOf(settings.getString("selectedDevice", "")) }
    
    var filterText by remember { mutableStateOf(settings.getString("filterText", "")) }
    var tagFilter by remember { mutableStateOf(settings.getString("tagFilter", "")) }
    var packageFilter by remember { mutableStateOf(settings.getString("packageFilter", "")) }
    var excludeMessageText by remember { mutableStateOf(settings.getString("excludeText", "")) }
    var excludeTagText by remember { mutableStateOf(settings.getString("excludeTagText", "")) }
    var isRegex by remember { mutableStateOf(settings.getBoolean("isRegex", false)) }
    
    // Level Filters
    var selectedLevels by remember { 
        mutableStateOf(
            LogLevel.entries.filter { 
                settings.getBoolean("level_${it.name}", it != LogLevel.VERBOSE) 
            }.toSet()
        ) 
    }

    // Format settings
    var showTimestamp by remember { mutableStateOf(settings.getBoolean("showTimestamp", true)) }
    var showPid by remember { mutableStateOf(settings.getBoolean("showPid", true)) }
    var showTid by remember { mutableStateOf(settings.getBoolean("showTid", true)) }
    var showLevel by remember { mutableStateOf(settings.getBoolean("showLevel", true)) }
    var showTag by remember { mutableStateOf(settings.getBoolean("showTag", true)) }
    var showPackage by remember { mutableStateOf(settings.getBoolean("showPackage", true)) }

    var fontSize by remember { mutableStateOf(settings.getString("fontSize", "12").toIntOrNull() ?: 12) }

    var targetPids by remember { mutableStateOf(setOf<Int>()) }
    var logUpdateTick by remember { mutableStateOf(0L) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var currentSearchMatchIndex by remember { mutableStateOf(-1) }

    // Persist settings
    LaunchedEffect(filterText) { settings.setString("filterText", filterText) }
    LaunchedEffect(tagFilter) { settings.setString("tagFilter", tagFilter) }
    LaunchedEffect(packageFilter) { settings.setString("packageFilter", packageFilter) }
    LaunchedEffect(excludeMessageText) { settings.setString("excludeText", excludeMessageText) }
    LaunchedEffect(excludeTagText) { settings.setString("excludeTagText", excludeTagText) }
    LaunchedEffect(isRegex) { settings.setBoolean("isRegex", isRegex) }
    LaunchedEffect(selectedDeviceSerial) { settings.setString("selectedDevice", selectedDeviceSerial) }
    
    LaunchedEffect(showTimestamp) { settings.setBoolean("showTimestamp", showTimestamp) }
    LaunchedEffect(showPid) { settings.setBoolean("showPid", showPid) }
    LaunchedEffect(showTid) { settings.setBoolean("showTid", showTid) }
    LaunchedEffect(showLevel) { settings.setBoolean("showLevel", showLevel) }
    LaunchedEffect(showTag) { settings.setBoolean("showTag", showTag) }
    LaunchedEffect(showPackage) { settings.setBoolean("showPackage", showPackage) }
    LaunchedEffect(fontSize) { settings.setString("fontSize", fontSize.toString()) }
    
    LaunchedEffect(selectedLevels) {
        LogLevel.entries.forEach { 
            settings.setBoolean("level_${it.name}", selectedLevels.contains(it))
        }
    }

    // Fetch devices
    LaunchedEffect(Unit) {
        while (true) {
            devices = repository.getDevices()
            if (selectedDeviceSerial.isEmpty() && devices.isNotEmpty()) {
                selectedDeviceSerial = devices.first().serial
            }
            kotlinx.coroutines.delay(3000)
        }
    }

    val filteredLogs = remember(logUpdateTick, tagFilter, targetPids, packageFilter, excludeMessageText, excludeTagText, selectedLevels) {
        val excludeMessageList = excludeMessageText.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        val excludeTagList = excludeTagText.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        
        logs.filter { entry ->
            if (!selectedLevels.contains(entry.level)) return@filter false

            // Exclude logic
            if (excludeMessageList.any { entry.message.contains(it, ignoreCase = true) }) return@filter false
            if (excludeTagList.any { entry.tag.contains(it, ignoreCase = true) }) return@filter false

            // Package filtering: supports exact match or prefix match (e.g., "com.mrsohn")
            val matchesPackage = packageFilter.isEmpty() || 
                                 targetPids.contains(entry.processId) ||
                                 (entry.packageName?.startsWith(packageFilter, ignoreCase = true) == true)

            if (!matchesPackage) return@filter false

            val matchesTag = tagFilter.isEmpty() || entry.tag.contains(tagFilter, ignoreCase = true)
            
            matchesTag
        }
    }

    val searchMatches = remember(filteredLogs, filterText, isRegex) {
        if (filterText.isEmpty()) return@remember emptyList<Int>()
        
        filteredLogs.indices.filter { index ->
            val entry = filteredLogs[index]
            if (isRegex) {
                try {
                    filterText.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(entry.message)
                } catch (e: Exception) {
                    false
                }
            } else {
                entry.message.contains(filterText, ignoreCase = true)
            }
        }
    }

    // Reset search index when matches change
    LaunchedEffect(searchMatches) {
        if (searchMatches.isNotEmpty()) {
            if (currentSearchMatchIndex == -1 || currentSearchMatchIndex >= searchMatches.size) {
                currentSearchMatchIndex = 0
            }
        } else {
            currentSearchMatchIndex = -1
        }
    }

    LaunchedEffect(packageFilter, selectedDeviceSerial) {
        if (packageFilter.isNotBlank()) {
            while (true) {
                val pids = repository.getPidsForPackage(packageFilter, selectedDeviceSerial.ifEmpty { null })
                targetPids = pids.toSet()
                kotlinx.coroutines.delay(2000)
            }
        } else {
            targetPids = emptySet()
        }
    }

    // Optimized log collection with buffering
    LaunchedEffect(repository, selectedDeviceSerial) {
        logs.clear()
        val buffer = mutableListOf<LogEntry>()
        var lastUpdateTime = 0L
        
        repository.getLogs(selectedDeviceSerial.ifEmpty { null }).collect { entry ->
            if (!isPaused) {
                // Early filtering by package name and PIDs to save memory
                val matchesPackage = packageFilter.isEmpty() || 
                                     targetPids.contains(entry.processId) ||
                                     (entry.packageName?.startsWith(packageFilter, ignoreCase = true) == true)
                
                if (matchesPackage) {
                    buffer.add(entry)
                    val currentTime = java.lang.System.currentTimeMillis()
                    
                    // Update UI every 100ms or if buffer gets large
                    if (currentTime - lastUpdateTime > 100 || buffer.size > 100) {
                        logs.addAll(buffer)
                        buffer.clear()
                        lastUpdateTime = currentTime
                        
                        // Keep memory in check (10,000 entries)
                        if (logs.size > 10000) {
                            repeat(logs.size - 10000) { logs.removeAt(0) }
                        }
                        logUpdateTick++
                    }
                }
            }
        }
    }

    // Detect if we are at the bottom to enable/disable auto-scroll
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (visibleItemsInfo.isEmpty()) {
                true
            } else {
                val lastVisibleItem = visibleItemsInfo.last()
                // Strict detection: must be the very last item
                val isLastItem = lastVisibleItem.index == layoutInfo.totalItemsCount - 1
                // And its bottom must be at or above the viewport bottom (fully visible at the end)
                val isBottomReached = lastVisibleItem.offset + lastVisibleItem.size <= layoutInfo.viewportEndOffset
                isLastItem && isBottomReached
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { 
                if (it.type == KeyEventType.KeyDown) {
                    if (it.isCtrlPressed && it.key == Key.F) {
                        searchFocusRequester.requestFocus()
                        return@onPreviewKeyEvent true
                    }
                    if (it.key == Key.Escape) {
                        filterText = ""
                        focusManager.clearFocus()
                        return@onPreviewKeyEvent true
                    }
                    if (it.key == Key.DirectionDown && searchMatches.isNotEmpty()) {
                        currentSearchMatchIndex = (currentSearchMatchIndex + 1) % searchMatches.size
                        autoScroll = false
                        scope.launch {
                            listState.animateScrollToItem(searchMatches[currentSearchMatchIndex])
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (it.key == Key.DirectionUp && searchMatches.isNotEmpty()) {
                        currentSearchMatchIndex = if (currentSearchMatchIndex <= 0) searchMatches.size - 1 else currentSearchMatchIndex - 1
                        autoScroll = false
                        scope.launch {
                            listState.animateScrollToItem(searchMatches[currentSearchMatchIndex])
                        }
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Device Selector
            var showDeviceMenu by remember { mutableStateOf(false) }
            val currentDevice = devices.find { it.serial == selectedDeviceSerial }
            Box {
                OutlinedButton(onClick = { showDeviceMenu = true }) {
                    Text(currentDevice?.let { "${it.model} (${it.serial})" } ?: "No Device")
                }
                DropdownMenu(expanded = showDeviceMenu, onDismissRequest = { showDeviceMenu = false }) {
                    devices.forEach { device ->
                        DropdownMenuItem(
                            text = { Text("${device.model} (${device.serial})") },
                            onClick = {
                                selectedDeviceSerial = device.serial
                                showDeviceMenu = false
                            }
                        )
                    }
                }
            }

            TextField(
                value = filterText,
                onValueChange = { filterText = it },
                label = { Text("Search (Messages)") },
                modifier = Modifier.weight(1f).focusRequester(searchFocusRequester),
                singleLine = true,
                trailingIcon = {
                    if (searchMatches.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${currentSearchMatchIndex + 1}/${searchMatches.size}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            IconButton(onClick = {
                                currentSearchMatchIndex = if (currentSearchMatchIndex <= 0) searchMatches.size - 1 else currentSearchMatchIndex - 1
                                autoScroll = false
                                scope.launch { listState.animateScrollToItem(searchMatches[currentSearchMatchIndex]) }
                            }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Prev")
                            }
                            IconButton(onClick = {
                                currentSearchMatchIndex = (currentSearchMatchIndex + 1) % searchMatches.size
                                autoScroll = false
                                scope.launch { listState.animateScrollToItem(searchMatches[currentSearchMatchIndex]) }
                            }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
                            }
                        }
                    }
                }
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isRegex, onCheckedChange = { isRegex = it })
                Text("Use Regex", style = MaterialTheme.typography.bodySmall)
            }

            TextField(
                value = tagFilter,
                onValueChange = { tagFilter = it },
                label = { Text("Filter Tag") },
                modifier = Modifier.width(180.dp),
                singleLine = true
            )

            TextField(
                value = packageFilter,
                onValueChange = { packageFilter = it },
                label = { Text("Package Name") },
                modifier = Modifier.width(240.dp),
                singleLine = true
            )

            Button(onClick = { logs.clear() }) {
                Text("Clear Logs")
            }

            Button(
                onClick = { isPaused = !isPaused },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isPaused) "Resume" else "Pause")
            }

            IconButton(onClick = { showSettingsDialog = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }

            Text(
                text = "${filteredLogs.size}/${logs.size}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // Exclude and Format Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = excludeMessageText,
                onValueChange = { excludeMessageText = it },
                label = { Text("Exclude Messages (e.g. word1; word2)") },
                modifier = Modifier.weight(0.6f),
                singleLine = true
            )

            TextField(
                value = excludeTagText,
                onValueChange = { excludeTagText = it },
                label = { Text("Exclude Tags (e.g. Tag1; Tag2)") },
                modifier = Modifier.weight(0.4f),
                singleLine = true
            )
        }

        // Level and Format Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Log Levels
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Levels:", style = MaterialTheme.typography.bodySmall)
                LogLevel.entries.forEach { level ->
                    FilterChip(
                        selected = selectedLevels.contains(level),
                        onClick = {
                            selectedLevels = if (selectedLevels.contains(level)) {
                                selectedLevels - level
                            } else {
                                selectedLevels + level
                            }
                        },
                        label = { Text(level.name.take(1), fontSize = 10.sp) },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            // Show Options
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Theme Switcher
                Text("Theme:", style = MaterialTheme.typography.bodySmall)
                var showThemeMenu by remember { mutableStateOf(false) }
                Box {
                    TextButton(
                        onClick = { showThemeMenu = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            when(themeMode) {
                                1 -> "Light"
                                2 -> "Dark"
                                else -> "System"
                            },
                            fontSize = 12.sp
                        )
                    }
                    DropdownMenu(expanded = showThemeMenu, onDismissRequest = { showThemeMenu = false }) {
                        DropdownMenuItem(text = { Text("System") }, onClick = { onThemeChange(0); showThemeMenu = false })
                        DropdownMenuItem(text = { Text("Light") }, onClick = { onThemeChange(1); showThemeMenu = false })
                        DropdownMenuItem(text = { Text("Dark") }, onClick = { onThemeChange(2); showThemeMenu = false })
                    }
                }

                Spacer(Modifier.width(8.dp))
                Text("Font:", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (fontSize > 8) fontSize-- }, modifier = Modifier.size(24.dp)) {
                        Text("-", color = Color.White)
                    }
                    Text("$fontSize", fontSize = 12.sp, color = Color.White)
                    IconButton(onClick = { if (fontSize < 30) fontSize++ }, modifier = Modifier.size(24.dp)) {
                        Text("+", color = Color.White)
                    }
                }
                
                Spacer(Modifier.width(8.dp))
                Text("Display:", style = MaterialTheme.typography.bodySmall)
                FormatCheckbox("Time", showTimestamp) { showTimestamp = it }
                FormatCheckbox("PID", showPid) { showPid = it }
                FormatCheckbox("TID", showTid) { showTid = it }
                FormatCheckbox("Level", showLevel) { showLevel = it }
                FormatCheckbox("Tag", showTag) { showTag = it }
                FormatCheckbox("Package", showPackage) { showPackage = it }
            }
        }

        // When user scrolls, update the auto-scroll state
        LaunchedEffect(listState.isScrollInProgress) {
            if (listState.isScrollInProgress) {
                // Monitor isAtBottom WHILE scrolling to catch wheel events immediately
                snapshotFlow { isAtBottom }.collect { bottom ->
                    autoScroll = bottom
                }
            }
        }
        
        LaunchedEffect(logUpdateTick) {
            if (autoScroll && filteredLogs.isNotEmpty()) {
                listState.scrollToItem(filteredLogs.size - 1)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF0F0F0F) else Color(0xFFFFFFFF)).padding(top = 8.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { entry ->
                        val index = filteredLogs.indexOf(entry)
                        val isCurrentMatch = currentSearchMatchIndex != -1 && searchMatches.getOrNull(currentSearchMatchIndex) == index
                        
                        LogEntryRow(
                            entry,
                            showTimestamp,
                            showPid,
                            showTid,
                            showLevel,
                            showTag,
                            showPackage,
                            fontSize,
                            filterText,
                            isCurrentMatch
                        )
                    }
                }
            }

            // Scroll to Bottom Button
            if (!autoScroll && filteredLogs.isNotEmpty()) {
                val scope = rememberCoroutineScope()
                Button(
                    onClick = {
                        autoScroll = true
                        scope.launch {
                            listState.scrollToItem(filteredLogs.size - 1)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("↓ Scroll to Bottom", fontSize = 12.sp)
                }
            }
        }
    }

    if (showSettingsDialog) {
        AdbSettingsDialog(
            currentPath = adbPath,
            onDismiss = { showSettingsDialog = false },
            onSave = { newPath ->
                onAdbPathChange(newPath)
                showSettingsDialog = false
            }
        )
    }
}

@Composable
fun AdbSettingsDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var path by remember { mutableStateOf(currentPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ADB Path Configuration", style = MaterialTheme.typography.titleMedium)
                Text("Enter the full path to the 'adb' executable:", style = MaterialTheme.typography.bodySmall)
                TextField(
                    value = path,
                    onValueChange = { path = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. C:\\Android\\Sdk\\platform-tools\\adb.exe") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(path) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun FormatCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(24.dp)
        )
        Text(label, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
    }
}

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
    isFocusedMatch: Boolean = false
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
        val header = buildString {
            if (showTimestamp) append("${entry.timestamp} ")
            if (showPid || showTid) {
                if (showPid) append(entry.processId.toString().padStart(5))
                if (showPid && showTid) append("-")
                if (showTid) append(entry.threadId.toString().padStart(5))
                append(" ")
            }
            if (showPackage) {
                val pkg = (entry.packageName ?: "?").padEnd(25)
                append("$pkg ")
            }
            if (showTag) {
                val tag = entry.tag.padEnd(35)
                append("$tag ")
            }
            if (showLevel) append("${entry.level.name.first()}  ")
        }
        
        append(header)
        addStyle(SpanStyle(color = color.copy(alpha = 0.8f)), 0, length)

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
                val highlightColor = if (isFocusedMatch) Color(0xFFFFA726) else Color(0xFFFFEB3B).copy(alpha = 0.5f)
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
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = annotatedText,
            fontSize = contentFontSize,
            fontFamily = FontFamily.Monospace,
            lineHeight = contentFontSize // 줄 간격 최소화 (1.0배)
        )
    }
}
