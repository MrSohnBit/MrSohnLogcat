package com.mrsohn.mrsohnlogcat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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
    var includeMessageText by remember { mutableStateOf(settings.getString("includeText", "")) }
    var isRegex by remember { mutableStateOf(settings.getBoolean("isRegex", false)) }

    var savedPackages by remember { 
        mutableStateOf(
            settings.getString("savedPackages", "").split("|").filter { it.isNotEmpty() }.toSet()
        ) 
    }
    var savedTags by remember { 
        mutableStateOf(
            settings.getString("savedTags", "").split("|").filter { it.isNotEmpty() }.toSet()
        ) 
    }
    var savedFilters by remember { 
        mutableStateOf(
            settings.getString("savedFilters", "").split("|").filter { it.isNotEmpty() }.toSet()
        ) 
    }
    var savedSearches by remember { 
        mutableStateOf(
            settings.getString("savedSearches", "").split("|").filter { it.isNotEmpty() }.toSet()
        ) 
    }

    var isAppInstalledOnDevice by remember { mutableStateOf(false) }
    
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
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var currentSearchMatchIndex by remember { mutableStateOf(-1) }

    // Persist settings
    LaunchedEffect(filterText) { settings.setString("filterText", filterText) }
    LaunchedEffect(tagFilter) { settings.setString("tagFilter", tagFilter) }
    LaunchedEffect(packageFilter) { settings.setString("packageFilter", packageFilter) }
    LaunchedEffect(includeMessageText) { settings.setString("includeText", includeMessageText) }
    LaunchedEffect(isRegex) { settings.setBoolean("isRegex", isRegex) }
    LaunchedEffect(savedPackages) { settings.setString("savedPackages", savedPackages.joinToString("|")) }
    LaunchedEffect(savedTags) { settings.setString("savedTags", savedTags.joinToString("|")) }
    LaunchedEffect(savedFilters) { settings.setString("savedFilters", savedFilters.joinToString("|")) }
    LaunchedEffect(savedSearches) { settings.setString("savedSearches", savedSearches.joinToString("|")) }
    LaunchedEffect(selectedDeviceSerial) { settings.setString("selectedDevice", selectedDeviceSerial) }
    
    LaunchedEffect(showTimestamp) { settings.setBoolean("showTimestamp", showTimestamp) }
    LaunchedEffect(showPid) { settings.setBoolean("showPid", showPid) }
    LaunchedEffect(showTid) { settings.setBoolean("showTid", showTid) }
    LaunchedEffect(showLevel) { settings.setBoolean("showLevel", showLevel) }
    LaunchedEffect(showTag) { settings.setBoolean("showTag", showTag) }
    LaunchedEffect(showPackage) { settings.setBoolean("showPackage", showPackage) }
    LaunchedEffect(fontSize) { settings.setString("fontSize", fontSize.toString()) }


    val currentDevice = devices.find { it.serial == selectedDeviceSerial }

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
            kotlinx.coroutines.delay(3000.milliseconds)
        }
    }

    val filteredLogs = remember(logUpdateTick, tagFilter, targetPids, packageFilter, includeMessageText, selectedLevels, 
                                showTimestamp, showPid, showTid, showLevel, showTag, showPackage) {
        val includeWords = includeMessageText.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        
        logs.filter { entry ->
            if (!selectedLevels.contains(entry.level)) return@filter false

            // Package filtering: supports exact match or prefix match (e.g., "com.mrsohn")
            val matchesPackage = packageFilter.isEmpty() || 
                                 targetPids.contains(entry.processId) ||
                                 (entry.packageName?.startsWith(packageFilter, ignoreCase = true) == true)

            if (!matchesPackage) return@filter false

            val matchesTag = tagFilter.isEmpty() || entry.tag.contains(tagFilter, ignoreCase = true)
            if (!matchesTag) return@filter false

            // Include logic: If words are provided, constructing a string from ONLY visible fields
            if (includeWords.isNotEmpty()) {
                val visibleContent = buildString {
//                    if (showTimestamp) append(entry.timestamp).append(" ")
//                    if (showPid) append(entry.processId).append(" ")
//                    if (showTid) append(entry.threadId).append(" ")
                    if (showPackage) append(entry.packageName ?: "").append(" ")
                    if (showTag) append(entry.tag).append(" ")
//                    if (showLevel) append(entry.level.name).append(" ")
                    append(entry.message)
                }
                if (includeWords.none { visibleContent.contains(it, ignoreCase = true) }) return@filter false
            }
            
            true
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

    LaunchedEffect(packageFilter, selectedDeviceSerial, currentDevice) {
        if (packageFilter.isNotBlank() && selectedDeviceSerial.isNotEmpty()) {
            isAppInstalledOnDevice = repository.isAppInstalled(packageFilter, selectedDeviceSerial)
        } else {
            isAppInstalledOnDevice = false
        }

        if (packageFilter.isNotBlank()) {
            while (true) {
                val pids = repository.getPidsForPackage(packageFilter, selectedDeviceSerial.ifEmpty { null })
                targetPids = pids.toSet()
                kotlinx.coroutines.delay(2000.milliseconds)
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
                    if ((it.isCtrlPressed || it.isMetaPressed) && it.key == Key.F) {
                        scope.launch {
                            kotlinx.coroutines.delay(50)
                            searchFocusRequester.requestFocus()
                        }
                        return@onPreviewKeyEvent true
                    }
                    if ((it.isCtrlPressed || it.isMetaPressed) && it.key == Key.A) {
                        val textToCopy = filteredLogs.joinToString("\n") { entry ->
                            buildString {
                                if (showTimestamp) append("${entry.timestamp} ")
                                if (showPid) append(entry.processId.toString().padStart(5)).append(" ")
                                if (showTid) append(entry.threadId.toString().padStart(5)).append(" ")
                                if (showPackage) append((entry.packageName ?: "?").padEnd(25)).append(" ")
                                if (showTag) append(entry.tag.padEnd(35)).append(" ")
                                if (showLevel) append("${entry.level.name.first()} ")
                                append(entry.message)
                            }
                        }
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(textToCopy))
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
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Device ", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                // Device Selector
                var showDeviceMenu by remember { mutableStateOf(false) }

                Box {
                    OutlinedButton(
                        onClick = { showDeviceMenu = true },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(currentDevice?.let { "${it.model} (${it.serial})" } ?: "No Device", fontSize = 12.sp)
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


                IconButton(onClick = { showSettingsDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp))
                }
            }

        }


        // Level and Format Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Log Levels
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LabelTitle("Levels")
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

        }



        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
//            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LabelTitle("Display")
            Spacer(modifier = Modifier.width(4.dp))
            FormatCheckbox("Time", showTimestamp, Color(0xFF4FC3F7)) { showTimestamp = it }
            FormatCheckbox("PID", showPid, Color(0xFFFFA726)) { showPid = it }
            FormatCheckbox("TID", showTid, Color(0xFFBA68C8)) { showTid = it }
            FormatCheckbox("Level", showLevel, Color(0xFF66BB6A)) { showLevel = it }
            FormatCheckbox("Package", showPackage, Color(0xFFAED581)) { showPackage = it }
            FormatCheckbox("Tag", showTag, Color(0xFFF06292)) { showTag = it }
        }


        // Package and Tag Filters
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Package Filter
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelTitle("Package")
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    var showDropdown by remember { mutableStateOf(false) }
                    BasicTextField(
                        value = packageFilter,
                        onValueChange = { packageFilter = it },
                        modifier = Modifier.fillMaxWidth().height(28.dp).background(if (isDark) Color.DarkGray else Color.LightGray, MaterialTheme.shapes.small).padding(horizontal = 8.dp)
                            .onPreviewKeyEvent {
                                if (it.type == KeyEventType.KeyDown && it.key == Key.Enter && packageFilter.isNotBlank()) {
                                    if (!savedPackages.contains(packageFilter)) {
                                        savedPackages = savedPackages + packageFilter
                                    }
                                    return@onPreviewKeyEvent true
                                }
                                false
                            },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = if (isDark) Color.White else Color.Black),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(if (isDark) Color.White else Color.Black),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    innerTextField()
                                }

                                if (packageFilter.isNotEmpty() && !savedPackages.contains(packageFilter)) {
                                    IconButton(onClick = { savedPackages = savedPackages + packageFilter }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.Add, contentDescription = "Save Package", modifier = Modifier.size(16.dp))
                                    }
                                }

                                IconButton(onClick = { showDropdown = true }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Saved Packages", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = showDropdown, 
                        onDismissRequest = { showDropdown = false },
                        modifier = Modifier.width(maxWidth)
                    ) {
                        if (savedPackages.isEmpty()) {
                            DropdownMenuItem(text = { Text("No saved packages", fontSize = 12.sp) }, onClick = { })
                        }
                        savedPackages.forEach { pkg ->
                            DropdownMenuItem(
                                text = { 
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(pkg, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { savedPackages = savedPackages - pkg }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp), tint = Color.Red)
                                        }
                                    }
                                },
                                onClick = {
                                    packageFilter = pkg
                                    showDropdown = false
                                }
                            )
                        }
                    }
                }


                Button(
                    onClick = {
                        scope.launch {
                            repository.launchApp(packageFilter, selectedDeviceSerial)
                        }
                    },
                    enabled = isAppInstalledOnDevice && currentDevice != null,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = if (isDark) Color(0xFF2D2D2D) else Color(0xFFE0E0E0),
                        disabledContentColor = Color.Gray
                    )
                ) {
                    Icon(
                        Icons.Default.PlayArrow, 
                        contentDescription = "Launch App", 
                        modifier = Modifier.size(16.dp), 
                        tint = if (isAppInstalledOnDevice) Color(0xFF66BB6A) else Color.Gray.copy(alpha = 0.5f)
                    )
                }
            }

            // Tag Filter
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),) {
                Text("Tag", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    var showDropdown by remember { mutableStateOf(false) }
                    BasicTextField(
                        value = tagFilter,
                        onValueChange = { tagFilter = it },
                        modifier = Modifier.fillMaxWidth().height(28.dp).background(if (isDark) Color.DarkGray else Color.LightGray, MaterialTheme.shapes.small).padding(horizontal = 8.dp)
                            .onPreviewKeyEvent {
                                if (it.type == KeyEventType.KeyDown && it.key == Key.Enter && tagFilter.isNotBlank()) {
                                    if (!savedTags.contains(tagFilter)) {
                                        savedTags = savedTags + tagFilter
                                    }
                                    return@onPreviewKeyEvent true
                                }
                                false
                            },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = if (isDark) Color.White else Color.Black),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(if (isDark) Color.White else Color.Black),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    innerTextField()
                                }

                                if (tagFilter.isNotEmpty() && !savedTags.contains(tagFilter)) {
                                    IconButton(onClick = { savedTags = savedTags + tagFilter }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.Add, contentDescription = "Save Tag", modifier = Modifier.size(16.dp))
                                    }
                                }
                                
                                IconButton(onClick = { showDropdown = true }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Saved Tags", modifier = Modifier.size(16.dp))
                                }

                            }
                        }
                    )
                    DropdownMenu(
                        expanded = showDropdown, 
                        onDismissRequest = { showDropdown = false },
                        modifier = Modifier.width(maxWidth)
                    ) {
                        if (savedTags.isEmpty()) {
                            DropdownMenuItem(text = { Text("No saved tags", fontSize = 12.sp) }, onClick = { })
                        }
                        savedTags.forEach { tag ->
                            DropdownMenuItem(
                                text = { 
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(tag, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { savedTags = savedTags - tag }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp), tint = Color.Red)
                                        }
                                    }
                                },
                                onClick = {
                                    tagFilter = tag
                                    showDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        }
        // Include and Format Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LabelTitle("Filter")
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                var showDropdown by remember { mutableStateOf(false) }
                BasicTextField(
                    value = includeMessageText,
                    onValueChange = { includeMessageText = it },
                    modifier = Modifier.fillMaxWidth().height(28.dp).background(if (isDark) Color.DarkGray else Color.LightGray, MaterialTheme.shapes.small).padding(horizontal = 8.dp)
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.Enter && includeMessageText.isNotBlank()) {
                                if (!savedFilters.contains(includeMessageText)) {
                                    savedFilters = savedFilters + includeMessageText
                                }
                                return@onPreviewKeyEvent true
                            }
                            false
                        },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = if (isDark) Color.White else Color.Black),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(if (isDark) Color.White else Color.Black),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                innerTextField()
                            }
                            
                            if (includeMessageText.isNotEmpty() && !savedFilters.contains(includeMessageText)) {
                                IconButton(onClick = { savedFilters = savedFilters + includeMessageText }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Add, contentDescription = "Save Filter", modifier = Modifier.size(16.dp))
                                }
                            }

                            IconButton(onClick = { showDropdown = true }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Saved Filters", modifier = Modifier.size(16.dp))
                            }

                            if (includeMessageText.isNotEmpty()) {
                                IconButton(onClick = { includeMessageText = "" }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                )
                DropdownMenu(
                    expanded = showDropdown, 
                    onDismissRequest = { showDropdown = false },
                    modifier = Modifier.width(maxWidth)
                ) {
                    if (savedFilters.isEmpty()) {
                        DropdownMenuItem(text = { Text("No saved filters", fontSize = 12.sp) }, onClick = { })
                    }
                    savedFilters.forEach { filter ->
                        DropdownMenuItem(
                            text = { 
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(filter, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { savedFilters = savedFilters - filter }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp), tint = Color.Red)
                                    }
                                }
                            },
                            onClick = {
                                includeMessageText = filter
                                showDropdown = false
                            }
                        )
                    }
                }
            }
        }

        // Action Buttons Row (Search, Clear, Pause, Settings, Info)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                LabelTitle("Search")
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    var showDropdown by remember { mutableStateOf(false) }
                    BasicTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        modifier = Modifier.fillMaxWidth().height(28.dp).background(if (isDark) Color.DarkGray else Color.LightGray, MaterialTheme.shapes.small).padding(horizontal = 8.dp).focusRequester(searchFocusRequester)
                            .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) {
                                if (filterText.isNotBlank() && !savedSearches.contains(filterText)) {
                                    savedSearches = savedSearches + filterText
                                }
                                if (searchMatches.isNotEmpty()) {
                                    currentSearchMatchIndex = (currentSearchMatchIndex + 1) % searchMatches.size
                                    autoScroll = false
                                    scope.launch {
                                        listState.animateScrollToItem(searchMatches[currentSearchMatchIndex])
                                    }
                                }
                                return@onPreviewKeyEvent true
                            }
                            false
                        },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, color = if (isDark) Color.White else Color.Black),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(if (isDark) Color.White else Color.Black),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    innerTextField()
                                }
                                
                                if (filterText.isNotEmpty() && !savedSearches.contains(filterText)) {
                                    IconButton(onClick = { savedSearches = savedSearches + filterText }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.Add, contentDescription = "Save Search", modifier = Modifier.size(16.dp))
                                    }
                                }

                                IconButton(onClick = { showDropdown = true }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Saved Searches", modifier = Modifier.size(16.dp))
                                }

                                if (searchMatches.isNotEmpty()) {
                                    Text(
                                        text = "${currentSearchMatchIndex + 1}/${searchMatches.size}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                    IconButton(onClick = {
                                        currentSearchMatchIndex = if (currentSearchMatchIndex <= 0) searchMatches.size - 1 else currentSearchMatchIndex - 1
                                        autoScroll = false
                                        scope.launch { listState.animateScrollToItem(searchMatches[currentSearchMatchIndex]) }
                                    }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Prev", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = {
                                        currentSearchMatchIndex = (currentSearchMatchIndex + 1) % searchMatches.size
                                        autoScroll = false
                                        scope.launch { listState.animateScrollToItem(searchMatches[currentSearchMatchIndex]) }
                                    }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next", modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (filterText.isNotEmpty()) {
                                    IconButton(onClick = { 
                                        filterText = "" 
                                        logs.clear()
                                        logUpdateTick++
                                    }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = showDropdown, 
                        onDismissRequest = { showDropdown = false },
                        modifier = Modifier.width(maxWidth)
                    ) {
                        if (savedSearches.isEmpty()) {
                            DropdownMenuItem(text = { Text("No saved searches", fontSize = 12.sp) }, onClick = { })
                        }
                        savedSearches.forEach { search ->
                            DropdownMenuItem(
                                text = { 
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(search, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { savedSearches = savedSearches - search }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp), tint = Color.Gray)
                                        }
                                    }
                                },
                                onClick = {
                                    filterText = search
                                    showDropdown = false
                                }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isRegex, onCheckedChange = { isRegex = it }, modifier = Modifier.size(24.dp))
                    Text("Regex", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.width(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Logs: ${filteredLogs.size}/${logs.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(end = 12.dp)
                )

//                Button(
//                    onClick = { isPaused = !isPaused },
//                    modifier = Modifier.height(32.dp),
//                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
//                    colors = ButtonDefaults.buttonColors(
//                        containerColor = if (isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
//                    )
//                ) {
//                    Text(if (isPaused) "Resume" else "Pause", fontSize = 12.sp)
//                }
                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = {
                        val textToCopy = filteredLogs.joinToString("\n") { entry ->
                            buildString {
                                if (showTimestamp) append("${entry.timestamp} ")
                                if (showPid) append(entry.processId.toString().padStart(5)).append(" ")
                                if (showTid) append(entry.threadId.toString().padStart(5)).append(" ")
                                if (showPackage) append((entry.packageName ?: "?").padEnd(25)).append(" ")
                                if (showTag) append(entry.tag.padEnd(35)).append(" ")
                                if (showLevel) append("${entry.level.name.first()} ")
                                append(entry.message)
                            }
                        }
                        clipboardManager.setText(AnnotatedString(textToCopy))
                    },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text("Copy", fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        logs.clear()
                        logUpdateTick++
                    },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text("Clear", fontSize = 12.sp)
                }

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
                            isCurrentMatch,
                            includeMessageText
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
fun FormatCheckbox(label: String, checked: Boolean, color: Color, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(24.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = color,
                uncheckedColor = color.copy(alpha = 0.5f)
            )
        )
        Text(
            label, 
            style = MaterialTheme.typography.bodySmall, 
            fontSize = 10.sp,
            color = if (checked) color else Color.Gray
        )
    }
}

@Composable
fun LabelTitle(title: String) {
    Text(title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.widthIn(min = 46.dp))
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
            addStyle(SpanStyle(color = color, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), start, length)
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

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = annotatedText,
            modifier = Modifier.fillMaxWidth(),
            fontSize = contentFontSize,
            fontFamily = FontFamily.Monospace,
            lineHeight = contentFontSize // 줄 간격 최소화 (1.0배)
        )
    }
}
