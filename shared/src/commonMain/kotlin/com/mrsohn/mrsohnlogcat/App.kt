package com.mrsohn.mrsohnlogcat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrsohn.mrsohnlogcat.component.AdbSettingsDialog
import com.mrsohn.mrsohnlogcat.component.FormatCheckbox
import com.mrsohn.mrsohnlogcat.component.LabelTitle
import com.mrsohn.mrsohnlogcat.component.LogEntryRow
import com.mrsohn.mrsohnlogcat.data.DeviceInfo
import com.mrsohn.mrsohnlogcat.data.LogEntry
import com.mrsohn.mrsohnlogcat.data.LogLevel
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

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
        Text("Please provide the path to your 'adb' executable")
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
                        error = "Could not find adb at the specified path."
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
    
    // Core data lists for performance
    val allLogs = remember { mutableListOf<LogEntry>() }
    val displayLogs = remember { mutableStateListOf<LogEntry>() }
    
    var isPaused by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(listOf<DeviceInfo>()) }
    var selectedDeviceSerial by remember { mutableStateOf(settings.getString("selectedDevice", "")) }
    
    var filterText by remember { mutableStateOf(settings.getString("filterText", "")) }
    var tagFilter by remember { mutableStateOf(settings.getString("tagFilter", "")) }
    var packageFilter by remember { mutableStateOf(settings.getString("packageFilter", "")) }
    var includeMessageText by remember { mutableStateOf(settings.getString("includeText", "")) }
    var isRegex by remember { mutableStateOf(settings.getBoolean("isRegex", false)) }

    var savedPackages by remember { 
        mutableStateOf(settings.getString("savedPackages", "").split("|").filter { it.isNotEmpty() }.toSet()) 
    }
    var savedTags by remember { 
        mutableStateOf(settings.getString("savedTags", "").split("|").filter { it.isNotEmpty() }.toSet()) 
    }
    var savedFilters by remember { 
        mutableStateOf(settings.getString("savedFilters", "").split("|").filter { it.isNotEmpty() }.toSet()) 
    }
    var savedSearches by remember { 
        mutableStateOf(settings.getString("savedSearches", "").split("|").filter { it.isNotEmpty() }.toSet()) 
    }
    var excludedTags by remember { 
        mutableStateOf(settings.getString("excludedTags", "View|MixpanelAPI|GestureDetector|VRI").split("|").filter { it.isNotEmpty() }.toSet()) 
    }

    var selectedLevels by remember { 
        mutableStateOf(LogLevel.entries.filter { settings.getBoolean("level_${it.name}", it != LogLevel.VERBOSE) }.toSet()) 
    }

    var showTimestamp by remember { mutableStateOf(settings.getBoolean("showTimestamp", true)) }
    var showPid by remember { mutableStateOf(settings.getBoolean("showPid", true)) }
    var showTid by remember { mutableStateOf(settings.getBoolean("showTid", true)) }
    var showLevel by remember { mutableStateOf(settings.getBoolean("showLevel", true)) }
    var showTag by remember { mutableStateOf(settings.getBoolean("showTag", true)) }
    var showPackage by remember { mutableStateOf(settings.getBoolean("showPackage", true)) }
    var fontSize by remember { mutableStateOf(settings.getString("fontSize", "12").toIntOrNull() ?: 12) }

    var targetPids by remember { mutableStateOf(setOf<Int>()) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var currentSearchMatchIndex by remember { mutableStateOf(-1) }

    fun shouldShowEntry(entry: LogEntry): Boolean {
        if (!selectedLevels.contains(entry.level)) return false
        if (excludedTags.any { entry.tag.equals(it, ignoreCase = true) || entry.tag.startsWith(it, ignoreCase = true) }) return false
        val matchesPackage = packageFilter.isEmpty() || (entry.processId != 0 && targetPids.contains(entry.processId)) || (entry.packageName?.startsWith(packageFilter, ignoreCase = true) == true)
        if (!matchesPackage) return false
        val tagParts = tagFilter.split(Regex("\\s+")).filter { it.isNotBlank() }
        val tagExclusions = tagParts.filter { it.startsWith("-tag:") }.map { it.removePrefix("-tag:") }
        val tagInclusions = tagParts.filter { !it.startsWith("-tag:") }
        val tagMatches = (tagExclusions.isEmpty() || !tagExclusions.any { entry.tag.contains(it, ignoreCase = true) }) &&
                         (tagInclusions.isEmpty() || tagInclusions.any { entry.tag.contains(it, ignoreCase = true) || (entry.tag == "SYSTEM" && entry.message.contains(it, ignoreCase = true)) })
        if (!tagMatches) return false
        val includeWords = includeMessageText.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        if (includeWords.isNotEmpty()) {
            val matchesInclude = includeWords.any { word -> entry.message.contains(word, ignoreCase = true) || (showTag && entry.tag.contains(word, ignoreCase = true)) || (showPackage && entry.packageName?.contains(word, ignoreCase = true) == true) }
            if (!matchesInclude) return false
        }
        return true
    }

    LaunchedEffect(filterText, tagFilter, packageFilter, includeMessageText, isRegex, selectedDeviceSerial, excludedTags) {
        settings.setString("filterText", filterText); settings.setString("tagFilter", tagFilter); settings.setString("packageFilter", packageFilter); settings.setString("includeText", includeMessageText); settings.setBoolean("isRegex", isRegex); settings.setString("selectedDevice", selectedDeviceSerial); settings.setString("excludedTags", excludedTags.joinToString("|"))
    }
    LaunchedEffect(savedPackages, savedTags, savedFilters, savedSearches) {
        settings.setString("savedPackages", savedPackages.joinToString("|")); settings.setString("savedTags", savedTags.joinToString("|")); settings.setString("savedFilters", savedFilters.joinToString("|")); settings.setString("savedSearches", savedSearches.joinToString("|"))
    }
    LaunchedEffect(showTimestamp, showPid, showTid, showLevel, showTag, showPackage, fontSize) {
        settings.setBoolean("showTimestamp", showTimestamp); settings.setBoolean("showPid", showPid); settings.setBoolean("showTid", showTid); settings.setBoolean("showLevel", showLevel); settings.setBoolean("showTag", showTag); settings.setBoolean("showPackage", showPackage); settings.setString("fontSize", fontSize.toString())
    }
    LaunchedEffect(selectedLevels) { LogLevel.entries.forEach { settings.setBoolean("level_${it.name}", selectedLevels.contains(it)) } }

    LaunchedEffect(tagFilter, packageFilter, includeMessageText, selectedLevels, excludedTags, targetPids) {
        val snapshot = allLogs.toList()
        withContext(Dispatchers.Default) {
            val filtered = snapshot.filter { shouldShowEntry(it) }
            withContext(Dispatchers.Main) { displayLogs.clear(); displayLogs.addAll(filtered) }
        }
    }

    LaunchedEffect(Unit) { while (true) { devices = repository.getDevices(); if (selectedDeviceSerial.isEmpty() && devices.isNotEmpty()) selectedDeviceSerial = devices.first().serial; delay(3000) } }

    var lastLogTimestamp by remember { mutableStateOf(java.lang.System.currentTimeMillis()) }
    var restartTrigger by remember { mutableStateOf(0) }
    LaunchedEffect(repository, selectedDeviceSerial, restartTrigger) {
        withContext(Dispatchers.Default) {
            allLogs.clear(); withContext(Dispatchers.Main) { displayLogs.clear() }
            var isFirstCollection = true
            while (true) {
                val incomingBuffer = mutableListOf<LogEntry>(); val displayBuffer = mutableListOf<LogEntry>()
                var lastBatchTime = java.lang.System.currentTimeMillis()
                try {
                    repository.getLogs(selectedDeviceSerial.ifEmpty { null }, onlyNew = !isFirstCollection).collect { entry ->
                        lastLogTimestamp = java.lang.System.currentTimeMillis()
                        if (!isPaused) {
                            incomingBuffer.add(entry)
                            if (shouldShowEntry(entry)) displayBuffer.add(entry)
                            val now = java.lang.System.currentTimeMillis()
                            if (now - lastBatchTime > 150 || incomingBuffer.size > 300) {
                                val inBatch = incomingBuffer.toList(); val dispBatch = displayBuffer.toList()
                                incomingBuffer.clear(); displayBuffer.clear()
                                withContext(Dispatchers.Main) {
                                    allLogs.addAll(inBatch); displayLogs.addAll(dispBatch)
                                    if (allLogs.size > 55000) allLogs.subList(0, 5000).clear()
                                    if (displayLogs.size > 30000) displayLogs.subList(0, 2000).clear()
                                }
                                lastBatchTime = now
                            }
                        }
                        isFirstCollection = false
                    }
                } catch (e: Exception) { }
                delay(2000)
            }
        }
    }

    LaunchedEffect(packageFilter, selectedDeviceSerial) {
        if (packageFilter.isNotBlank()) {
            while (true) {
                targetPids = repository.getPidsForPackage(packageFilter, selectedDeviceSerial.ifEmpty { null }).toSet()
                delay(2000.milliseconds)
            }
        } else targetPids = emptySet()
    }

    val searchMatches = remember(displayLogs.size, filterText, isRegex) {
        if (filterText.isEmpty()) return@remember emptyList<Int>()
        displayLogs.indices.filter { idx ->
            val entry = displayLogs[idx]
            if (isRegex) { try { filterText.toRegex(RegexOption.IGNORE_CASE).containsMatchIn(entry.message) } catch (e: Exception) { false } }
            else entry.message.contains(filterText, ignoreCase = true)
        }
    }

    val isAtBottom by remember { derivedStateOf { val layoutInfo = listState.layoutInfo; val visibleItemsInfo = layoutInfo.visibleItemsInfo; if (visibleItemsInfo.isEmpty()) true else { val lastVisibleItem = visibleItemsInfo.last(); lastVisibleItem.index == layoutInfo.totalItemsCount - 1 && lastVisibleItem.offset + lastVisibleItem.size <= layoutInfo.viewportEndOffset } } }

    LaunchedEffect(displayLogs.size, tagFilter, packageFilter, includeMessageText, filterText, selectedLevels) { if (autoScroll && displayLogs.isNotEmpty()) listState.scrollToItem(displayLogs.size - 1) }
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) { snapshotFlow { isAtBottom }.collect { autoScroll = it } } }

    Column(
        modifier = Modifier.fillMaxSize().onPreviewKeyEvent { 
            if (it.type == KeyEventType.KeyDown) {
                if ((it.isCtrlPressed || it.isMetaPressed) && it.key == Key.F) { scope.launch { delay(50); searchFocusRequester.requestFocus() }; return@onPreviewKeyEvent true }
                if ((it.isCtrlPressed || it.isMetaPressed) && it.key == Key.A) {
                    val textToCopy = displayLogs.joinToString("\n") { entry -> buildString { if (showTimestamp) append("${entry.timestamp} "); if (showPid) append(entry.processId.toString().padStart(5)).append(" "); if (showTid) append(entry.threadId.toString().padStart(5)).append(" "); if (showPackage) append((entry.packageName ?: "?").padEnd(25)).append(" "); if (showTag) append(entry.tag.padEnd(35)).append(" "); if (showLevel) append("${entry.level.name.first()} "); append(entry.message) } }
                    clipboardManager.setText(AnnotatedString(textToCopy)); return@onPreviewKeyEvent true
                }
                if (it.key == Key.Escape) { filterText = ""; focusManager.clearFocus(); return@onPreviewKeyEvent true }
                if (it.key == Key.DirectionDown && searchMatches.isNotEmpty()) { currentSearchMatchIndex = (currentSearchMatchIndex + 1) % searchMatches.size; autoScroll = false; scope.launch { listState.animateScrollToItem(searchMatches[currentSearchMatchIndex]) }; return@onPreviewKeyEvent true }
                if (it.key == Key.DirectionUp && searchMatches.isNotEmpty()) { currentSearchMatchIndex = if (currentSearchMatchIndex <= 0) searchMatches.size - 1 else currentSearchMatchIndex - 1; autoScroll = false; scope.launch { listState.animateScrollToItem(searchMatches[currentSearchMatchIndex]) }; return@onPreviewKeyEvent true }
            }
            false
        }
    ) {
        // --- Toolbar ---
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Device ", fontSize = 11.sp)
                var showDeviceMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { showDeviceMenu = true }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        val currentDevice = devices.find { it.serial == selectedDeviceSerial }
                        Text(currentDevice?.let { "${it.model} (${it.serial})" } ?: "No Device", fontSize = 12.sp)
                    }
                    DropdownMenu(expanded = showDeviceMenu, onDismissRequest = { showDeviceMenu = false }) {
                        devices.forEach { device -> DropdownMenuItem(text = { Text("${device.model} (${device.serial})") }, onClick = { selectedDeviceSerial = device.serial; showDeviceMenu = false }) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Theme:", style = MaterialTheme.typography.bodySmall)
                var showThemeMenu by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { showThemeMenu = true }, modifier = Modifier.height(32.dp)) { Text(when(themeMode) { 1 -> "Light"; 2 -> "Dark"; else -> "System" }, fontSize = 12.sp) }
                    DropdownMenu(expanded = showThemeMenu, onDismissRequest = { showThemeMenu = false }) {
                        DropdownMenuItem(text = { Text("System") }, onClick = { onThemeChange(0); showThemeMenu = false })
                        DropdownMenuItem(text = { Text("Light") }, onClick = { onThemeChange(1); showThemeMenu = false })
                        DropdownMenuItem(text = { Text("Dark") }, onClick = { onThemeChange(2); showThemeMenu = false })
                    }
                }
                Text("Font:", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (fontSize > 8) fontSize-- }, modifier = Modifier.size(24.dp)) { Text("-", color = if (isDark) Color.White else Color.Black) }
                    Text("$fontSize", fontSize = 12.sp, color = if (isDark) Color.White else Color.Black)
                    IconButton(onClick = { if (fontSize < 30) fontSize++ }, modifier = Modifier.size(24.dp)) { Text("+", color = if (isDark) Color.White else Color.Black) }
                }
                IconButton(onClick = { showSettingsDialog = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Settings, "Settings", modifier = Modifier.size(20.dp)) }
            }
        }

        // --- Levels & Display ---
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            LabelTitle("Levels")
            LogLevel.entries.forEach { level ->
                FilterChip(selected = selectedLevels.contains(level), onClick = { selectedLevels = if (selectedLevels.contains(level)) selectedLevels - level else selectedLevels + level }, label = { Text(level.name.take(1), fontSize = 10.sp) }, modifier = Modifier.height(24.dp).padding(horizontal = 2.dp))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            LabelTitle("Display")
            FormatCheckbox("Time", showTimestamp, Color(0xFF4FC3F7)) { showTimestamp = it }
            FormatCheckbox("PID", showPid, Color(0xFFFFA726)) { showPid = it }
            FormatCheckbox("TID", showTid, Color(0xFFBA68C8)) { showTid = it }
            FormatCheckbox("Level", showLevel, Color(0xFF66BB6A)) { showLevel = it }
            FormatCheckbox("Package", showPackage, Color(0xFFAED581)) { showPackage = it }
            FormatCheckbox("Tag", showTag, Color(0xFFF06292)) { showTag = it }
        }

        // --- Package ---
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabelTitle("Package")
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                var showDropdown by remember { mutableStateOf(false) }
                BasicTextField(
                    value = packageFilter,
                    onValueChange = { packageFilter = it },
                    modifier = Modifier.fillMaxWidth().height(28.dp).background(if (isDark) Color.DarkGray else Color.LightGray, MaterialTheme.shapes.small).padding(horizontal = 8.dp),
                    textStyle = TextStyle(fontSize = 12.sp, color = if (isDark) Color.White else Color.Black),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(if (isDark) Color.White else Color.Black),
                    singleLine = true,
                    decorationBox = { inner -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { inner() }; if (packageFilter.isNotEmpty() && !savedPackages.contains(packageFilter)) IconButton(onClick = { savedPackages = savedPackages + packageFilter }, Modifier.size(20.dp)) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) }; IconButton(onClick = { showDropdown = true }, Modifier.size(20.dp)) { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp)) } } }
                )
                DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }, modifier = Modifier.width(maxWidth)) {
                    savedPackages.forEach { pkg -> DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically) { Text(pkg, Modifier.weight(1f), fontSize = 12.sp); IconButton(onClick = { savedPackages = savedPackages - pkg }, Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(14.dp)) } } }, onClick = { packageFilter = pkg; showDropdown = false }) }
                }
            }
            Button(onClick = { scope.launch { repository.launchApp(packageFilter, selectedDeviceSerial) } }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp)) }
        }

        // --- Tag & Filter Row ---
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Tag Filter
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelTitle("Tag")
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    var showDropdown by remember { mutableStateOf(false) }
                    BasicTextField(
                        value = tagFilter,
                        onValueChange = { tagFilter = it },
                        modifier = Modifier.fillMaxWidth().height(28.dp).background(if (isDark) Color.DarkGray else Color.LightGray, MaterialTheme.shapes.small).padding(horizontal = 8.dp),
                        textStyle = TextStyle(fontSize = 12.sp, color = if (isDark) Color.White else Color.Black),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(if (isDark) Color.White else Color.Black),
                        singleLine = true,
                        decorationBox = { inner -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { inner() }; if (tagFilter.isNotEmpty() && !savedTags.contains(tagFilter)) IconButton(onClick = { savedTags = savedTags + tagFilter }, Modifier.size(20.dp)) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) }; IconButton(onClick = { showDropdown = true }, Modifier.size(20.dp)) { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp)) } } }
                    )
                    DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }, modifier = Modifier.width(maxWidth)) {
                        savedTags.forEach { t -> DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically) { Text(t, Modifier.weight(1f), fontSize = 12.sp); IconButton(onClick = { savedTags = savedTags - t }, Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(14.dp)) } } }, onClick = { tagFilter = t; showDropdown = false }) }
                    }
                }
            }
            // Filter (Include)
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelTitle("Filter")
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    var showDropdown by remember { mutableStateOf(false) }
                    BasicTextField(
                        value = includeMessageText,
                        onValueChange = { includeMessageText = it },
                        modifier = Modifier.fillMaxWidth().height(28.dp).background(if (isDark) Color.DarkGray else Color.LightGray, MaterialTheme.shapes.small).padding(horizontal = 8.dp),
                        textStyle = TextStyle(fontSize = 12.sp, color = if (isDark) Color.White else Color.Black),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(if (isDark) Color.White else Color.Black),
                        singleLine = true,
                        decorationBox = { inner -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { inner() }; if (includeMessageText.isNotEmpty() && !savedFilters.contains(includeMessageText)) IconButton(onClick = { savedFilters = savedFilters + includeMessageText }, Modifier.size(20.dp)) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) }; IconButton(onClick = { showDropdown = true }, Modifier.size(20.dp)) { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp)) }; if (includeMessageText.isNotEmpty()) IconButton(onClick = { includeMessageText = "" }, Modifier.size(20.dp)) { Icon(Icons.Default.Clear, null, Modifier.size(16.dp)) } } }
                    )
                    DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }, modifier = Modifier.width(maxWidth)) {
                        savedFilters.forEach { f -> DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically) { Text(f, Modifier.weight(1f), fontSize = 12.sp); IconButton(onClick = { savedFilters = savedFilters - f }, Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(14.dp)) } } }, onClick = { includeMessageText = f; showDropdown = false }) }
                    }
                }
            }
            Spacer(Modifier.width(50.dp))
        }

        // --- Search ---
        Row(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                LabelTitle("Search")
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    var showDropdown by remember { mutableStateOf(false) }
                    BasicTextField(value = filterText, onValueChange = { filterText = it }, modifier = Modifier.fillMaxWidth().height(28.dp).background(if (isDark) Color.DarkGray else Color.LightGray, MaterialTheme.shapes.small).padding(horizontal = 8.dp).focusRequester(searchFocusRequester), textStyle = TextStyle(fontSize = 12.sp, color = if (isDark) Color.White else Color.Black), singleLine = true, decorationBox = { inner -> Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { inner() }; if (filterText.isNotEmpty() && !savedSearches.contains(filterText)) IconButton(onClick = { savedSearches = savedSearches + filterText }, Modifier.size(20.dp)) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) }; IconButton(onClick = { showDropdown = true }, Modifier.size(20.dp)) { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp)) }; if (searchMatches.isNotEmpty()) { Text("${currentSearchMatchIndex + 1}/${searchMatches.size}", fontSize = 10.sp, color = if (isDark) Color.White else Color.Black, modifier = Modifier.padding(end = 4.dp)); IconButton(onClick = { currentSearchMatchIndex = if (currentSearchMatchIndex <= 0) searchMatches.size - 1 else currentSearchMatchIndex - 1; autoScroll = false; scope.launch { listState.animateScrollToItem(searchMatches[currentSearchMatchIndex]) } }, Modifier.size(20.dp)) { Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(16.dp)) }; IconButton(onClick = { currentSearchMatchIndex = (currentSearchMatchIndex + 1) % searchMatches.size; autoScroll = false; scope.launch { listState.animateScrollToItem(searchMatches[currentSearchMatchIndex]) } }, Modifier.size(20.dp)) { Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp)) } }; if (filterText.isNotEmpty()) IconButton(onClick = { filterText = "" }, Modifier.size(20.dp)) { Icon(Icons.Default.Clear, null, Modifier.size(16.dp)) } } })
                    DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }, modifier = Modifier.width(maxWidth)) {
                        savedSearches.forEach { s -> DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically) { Text(s, Modifier.weight(1f), fontSize = 12.sp); IconButton(onClick = { savedSearches = savedSearches - s }, Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null, tint = Color.Gray, modifier = Modifier.size(14.dp)) } } }, onClick = { filterText = s; showDropdown = false }) }
                    }
                }
                Checkbox(checked = isRegex, onCheckedChange = { isRegex = it }, Modifier.size(24.dp)); Text("Regex", fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Logs: ${displayLogs.size}/${allLogs.size}", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 8.dp))
                Button(onClick = { allLogs.clear(); displayLogs.clear() }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Clear", fontSize = 12.sp) }
            }
        }

        // --- Logs List ---
        Box(modifier = Modifier.fillMaxSize()) {
            SelectionContainer {
                LazyColumn(
                    state = listState, 
                    modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF0F0F0F) else Color(0xFFFFFFFF)),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp) // Bottom padding for breathing room
                ) {
                    items(displayLogs, key = { it.id }) { entry -> 
                        LogEntryRow(entry, showTimestamp, showPid, showTid, showLevel, showTag, showPackage, fontSize, filterText, false, includeMessageText) 
                    }
                }
            }
            if (!autoScroll && displayLogs.isNotEmpty()) { 
                Button(
                    onClick = { autoScroll = true; scope.launch { listState.scrollToItem(displayLogs.size - 1) } }, 
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).padding(bottom = 8.dp) // Adjust button position
                ) { 
                    Text("↓ Scroll to Bottom", fontSize = 12.sp) 
                } 
            }
        }
    }
    if (showSettingsDialog) AdbSettingsDialog(currentPath = adbPath, excludedTags = excludedTags, onDismiss = { showSettingsDialog = false }, onSave = { newPath, newTags -> onAdbPathChange(newPath); excludedTags = newTags; showSettingsDialog = false })
}
