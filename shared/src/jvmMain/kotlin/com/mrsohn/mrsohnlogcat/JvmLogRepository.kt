package com.mrsohn.mrsohnlogcat

import com.mrsohn.mrsohnlogcat.data.DeviceInfo
import com.mrsohn.mrsohnlogcat.data.LogEntry
import com.mrsohn.mrsohnlogcat.data.LogLevel
import com.mrsohn.mrsohnlogcat.data.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

class JvmLogRepository(private val adbPath: String) : LogRepository {
    private var process: Process? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val pidToPackage = ConcurrentHashMap<Int, String>()
    private val lastId = AtomicLong(0)

    private val actualAdb = if (adbPath.isBlank()) "adb" else adbPath

    init {
        scope.launch {
            while (isActive) {
                updatePidMap()
                delay(5000.milliseconds)
            }
        }
    }

    private suspend fun updatePidMap() = withContext(Dispatchers.IO) {
        var proc: Process? = null
        try {
            withTimeout(5000.milliseconds) {
                proc = ProcessBuilder(actualAdb, "shell", "ps", "-A", "-o", "PID,NAME").apply {
                    redirectErrorStream(true)
                }.start()
                
                val reader = BufferedReader(InputStreamReader(proc!!.inputStream))
                reader.use { r ->
                    r.readLine() // skip header
                    var line: String?
                    while (r.readLine().also { line = it } != null) {
                        val parts = line!!.trim().split(Regex("\\s+"))
                        if (parts.size >= 2) {
                            val pid = parts[0].toIntOrNull()
                            if (pid != null) {
                                pidToPackage[pid] = parts[1]
                            }
                        }
                    }
                }
                proc!!.waitFor()
            }
        } catch (e: Exception) {
            // Ignore
        } finally {
            proc?.destroy()
        }
    }

    override fun getLogs(deviceSerial: String?, packageName: String?, onlyNew: Boolean): Flow<LogEntry> = callbackFlow {
        val commands = mutableListOf(actualAdb)
        if (deviceSerial != null) {
            commands.addAll(listOf("-s", deviceSerial))
        }
        commands.add("logcat")
        commands.addAll(listOf("-v", "threadtime"))
        if (onlyNew) {
            commands.addAll(listOf("-T", "1"))
        }

        val processBuilder = ProcessBuilder(commands).apply {
            redirectErrorStream(true)
        }

        val proc = try {
            withContext(Dispatchers.IO) {
                processBuilder.start()
            }
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }
        process = proc

        val job = scope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                var count = 0
                reader.use { r ->
                    while (isActive) {
                        val line = r.readLine() ?: break
                        val logEntry = parseLogLine(line)
                        if (logEntry != null) {
                            send(logEntry)
                            count++
                            if (count % 1000 == 0) {
                                println("Log collected: $count entries (Last: ${logEntry.tag})")
                            }
                        }
                    }
                }
                println("Log reader loop finished normally.")
            } catch (e: Exception) {
                println("Log reader loop exception: ${e.message}")
            } finally {
                close()
            }
        }

        awaitClose {
            job.cancel()
            proc.destroy()
            println("Log collection flow closed and process destroyed.")
        }
    }.buffer(10000) // Increase buffer to prevent blocking the reader

    override fun stopLogs() {
        process?.destroy()
        process = null
    }

    override suspend fun getPidsForPackage(packageName: String, deviceSerial: String?): List<Int> = withContext(Dispatchers.IO) {
        if (packageName.isBlank()) return@withContext emptyList()
        
        pidToPackage.filter { (pid, name) ->
            name.startsWith(packageName, ignoreCase = true)
        }.keys.toList()
    }

    override suspend fun checkAdb(adbPath: String): Boolean = withContext(Dispatchers.IO) {
        val pathToCheck = if (adbPath.isBlank()) "adb" else adbPath
        var proc: Process? = null
        try {
            withTimeout(3000.milliseconds) {
                proc = ProcessBuilder(pathToCheck, "version").apply {
                    redirectErrorStream(true)
                }.start()
                proc!!.waitFor() == 0
            }
        } catch (e: Exception) {
            false
        } finally {
            proc?.destroy()
        }
    }

    override suspend fun getDevices(): List<DeviceInfo> = withContext(Dispatchers.IO) {
        var proc: Process? = null
        try {
            withTimeout(5000.milliseconds) {
                proc = ProcessBuilder(actualAdb, "devices").apply {
                    redirectErrorStream(true)
                }.start()
                
                val reader = BufferedReader(InputStreamReader(proc!!.inputStream))
                val devices = mutableListOf<String>()
                reader.use { r ->
                    r.readLine() // skip header
                    var line: String?
                    while (r.readLine().also { line = it } != null) {
                        if (line!!.isNotBlank()) {
                            val parts = line!!.split(Regex("\\s+"))
                            if (parts.size >= 2 && parts[1] == "device") {
                                devices.add(parts[0])
                            }
                        }
                    }
                }
                proc!!.waitFor()
                
                devices.map { serial ->
                    val model = getDeviceModel(serial)
                    DeviceInfo(serial, model)
                }
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            proc?.destroy()
        }
    }

    override suspend fun isAppInstalled(packageName: String, deviceSerial: String?): Boolean = withContext(Dispatchers.IO) {
        if (packageName.isBlank()) return@withContext false
        var proc: Process? = null
        try {
            withTimeout(5000.milliseconds) {
                val commands = mutableListOf(actualAdb)
                if (deviceSerial != null) commands.addAll(listOf("-s", deviceSerial))
                commands.addAll(listOf("shell", "pm", "list", "packages", packageName))
                
                proc = ProcessBuilder(commands).apply {
                    redirectErrorStream(true)
                }.start()
                
                val reader = BufferedReader(InputStreamReader(proc!!.inputStream))
                var installed = false
                reader.use { r ->
                    var line: String?
                    while (r.readLine().also { line = it } != null) {
                        if (line == "package:$packageName") {
                            installed = true
                            break
                        }
                    }
                }
                installed
            }
        } catch (e: Exception) {
            false
        } finally {
            proc?.destroy()
        }
    }

    override suspend fun launchApp(packageName: String, deviceSerial: String?): Boolean = withContext(Dispatchers.IO) {
        if (packageName.isBlank()) return@withContext false
        var proc: Process? = null
        try {
            withTimeout(5000.milliseconds) {
                val commands = mutableListOf(actualAdb)
                if (deviceSerial != null) commands.addAll(listOf("-s", deviceSerial))
                commands.addAll(listOf("shell", "monkey", "-p", packageName, "-c", "android.intent.category.LAUNCHER", "1"))
                
                proc = ProcessBuilder(commands).apply {
                    redirectErrorStream(true)
                }.start()
                proc!!.waitFor() == 0
            }
        } catch (e: Exception) {
            false
        } finally {
            proc?.destroy()
        }
    }

    private suspend fun getDeviceModel(serial: String): String = withContext(Dispatchers.IO) {
        var proc: Process? = null
        try {
            withTimeout(3000.milliseconds) {
                proc = ProcessBuilder(actualAdb, "-s", serial, "shell", "getprop", "ro.product.model").apply {
                    redirectErrorStream(true)
                }.start()
                
                val reader = BufferedReader(InputStreamReader(proc!!.inputStream))
                val model = reader.use { it.readLine()?.trim() } ?: "Unknown Device"
                model
            }
        } catch (e: Exception) {
            "Unknown Device"
        } finally {
            proc?.destroy()
        }
    }

    private fun parseLogLine(line: String): LogEntry? {
        // 1. Standard threadtime format
        val threadtimeRegex = """(\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+(.*?):\s(.*)""".toRegex()
        val ttMatch = threadtimeRegex.find(line)
        if (ttMatch != null) {
            val (timestamp, pidStr, tidStr, levelStr, tag, message) = ttMatch.destructured
            val pid = pidStr.toInt()
            return LogEntry(
                id = lastId.incrementAndGet(),
                timestamp = timestamp,
                processId = pid,
                threadId = tidStr.toInt(),
                level = LogLevel.fromLetter(levelStr),
                tag = tag.trim(),
                message = message,
                packageName = pidToPackage[pid]
            )
        }

        // 2. Special format for API SEND and others (Handle huge spaces and no colon)
        // Match: (Tag) (Lots of spaces) (Level) (Spaces) (Message)
        val flexibleRegex = """^(\s*.*?\S)\s+([VDIWEF])\s+(.*)$""".toRegex()
        val flexMatch = flexibleRegex.find(line)
        if (flexMatch != null) {
            val (tagPart, levelStr, messagePart) = flexMatch.destructured
            if (!tagPart.contains(Regex("""\d{2}-\d{2}"""))) { // Ignore lines that look like a timestamp
                return LogEntry(
                    id = lastId.incrementAndGet(),
                    timestamp = "",
                    processId = 0,
                    threadId = 0,
                    level = LogLevel.fromLetter(levelStr),
                    tag = tagPart.trim(),
                    message = messagePart.trim(),
                    packageName = ""
                )
            }
        }
        
        // 3. Fallback for unparseable lines
        if (line.isNotBlank() && !line.startsWith("---------")) {
            return LogEntry(
                id = lastId.incrementAndGet(),
                timestamp = "",
                processId = 0,
                threadId = 0,
                level = LogLevel.INFO,
                tag = "SYSTEM",
                message = line,
                packageName = ""
            )
        }
        return null
    }
}
