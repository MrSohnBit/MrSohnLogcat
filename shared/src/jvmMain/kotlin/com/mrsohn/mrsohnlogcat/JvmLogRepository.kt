package com.mrsohn.mrsohnlogcat

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import java.io.BufferedReader
import java.io.InputStreamReader

class JvmLogRepository(private val adbPath: String) : LogRepository {
    private var process: Process? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pidToPackage = mutableMapOf<Int, String>()
    private var lastId = 0L

    private val actualAdb = if (adbPath.isBlank()) "adb" else adbPath

    init {
        scope.launch {
            while (isActive) {
                updatePidMap()
                delay(5000)
            }
        }
    }

    private suspend fun updatePidMap() = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(actualAdb, "shell", "ps", "-A", "-o", "PID,NAME").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLine() // skip header
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val parts = line!!.trim().split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val pid = parts[0].toIntOrNull()
                    if (pid != null) {
                        pidToPackage[pid] = parts[1]
                    }
                }
            }
        } catch (e: Exception) {
        }
    }

    override fun getLogs(deviceSerial: String?, packageName: String?): Flow<LogEntry> = callbackFlow {
        val commands = mutableListOf(actualAdb)
        if (deviceSerial != null) {
            commands.addAll(listOf("-s", deviceSerial))
        }
        commands.add("logcat")
        commands.addAll(listOf("-v", "threadtime"))

        val processBuilder = ProcessBuilder(commands)
        val proc = try {
            processBuilder.start()
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }
        process = proc

        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        
        val job = scope.launch {
            try {
                while (isActive) {
                    val line = reader.readLine() ?: break
                    val logEntry = parseLogLine(line)
                    if (logEntry != null) {
                        send(logEntry) // Use send instead of trySend to ensure no logs are dropped
                    }
                }
            } catch (e: Exception) {
                close(e)
            }
        }

        awaitClose {
            job.cancel()
            proc.destroy()
        }
    }

    override fun stopLogs() {
        process?.destroy()
    }

    override suspend fun getPidsForPackage(packageName: String, deviceSerial: String?): List<Int> = withContext(Dispatchers.IO) {
        if (packageName.isBlank()) return@withContext emptyList()
        
        // Filter the cached PID map for packages that START WITH the given string
        pidToPackage.filter { (pid, name) ->
            name.startsWith(packageName, ignoreCase = true)
        }.keys.toList()
    }

    override suspend fun checkAdb(adbPath: String): Boolean = withContext(Dispatchers.IO) {
        val pathToCheck = if (adbPath.isBlank()) "adb" else adbPath
        try {
            val process = ProcessBuilder(pathToCheck, "version").start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getDevices(): List<DeviceInfo> = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(actualAdb, "devices").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val devices = mutableListOf<String>()
            reader.readLine()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.isNotBlank()) {
                    val parts = line!!.split(Regex("\\s+"))
                    if (parts.size >= 2 && parts[1] == "device") {
                        devices.add(parts[0])
                    }
                }
            }
            
            devices.map { serial ->
                val model = getDeviceModel(serial)
                DeviceInfo(serial, model)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun isAppInstalled(packageName: String, deviceSerial: String?): Boolean = withContext(Dispatchers.IO) {
        if (packageName.isBlank()) return@withContext false
        try {
            val commands = mutableListOf(actualAdb)
            if (deviceSerial != null) commands.addAll(listOf("-s", deviceSerial))
            commands.addAll(listOf("shell", "pm", "list", "packages", packageName))
            
            val process = ProcessBuilder(commands).start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line == "package:$packageName") return@withContext true
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun launchApp(packageName: String, deviceSerial: String?): Boolean = withContext(Dispatchers.IO) {
        if (packageName.isBlank()) return@withContext false
        try {
            val commands = mutableListOf(actualAdb)
            if (deviceSerial != null) commands.addAll(listOf("-s", deviceSerial))
            commands.addAll(listOf("shell", "monkey", "-p", packageName, "-c", "android.intent.category.LAUNCHER", "1"))
            
            val process = ProcessBuilder(commands).start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun getDeviceModel(serial: String): String = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(actualAdb, "-s", serial, "shell", "getprop", "ro.product.model").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLine()?.trim() ?: "Unknown Device"
        } catch (e: Exception) {
            "Unknown Device"
        }
    }

    private fun parseLogLine(line: String): LogEntry? {
        // Use :\s instead of :\s+ to preserve leading spaces in the message
        val regex = """(\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+(.*?):\s(.*)""".toRegex()
        val matchResult = regex.find(line) ?: return null
        
        val (timestamp, pidStr, tidStr, levelStr, tag, message) = matchResult.destructured
        val pid = pidStr.toInt()
        
        return LogEntry(
            id = ++lastId,
            timestamp = timestamp,
            processId = pid,
            threadId = tidStr.toInt(),
            level = LogLevel.fromLetter(levelStr),
            tag = tag.trim(),
            message = message,
            packageName = pidToPackage[pid]
        )
    }
}
