package com.mrsohn.mrsohnlogcat.data

import kotlinx.coroutines.flow.Flow

interface LogRepository {
    fun getLogs(deviceSerial: String? = null, packageName: String? = null): Flow<LogEntry>
    fun stopLogs()
    suspend fun getPidsForPackage(packageName: String, deviceSerial: String? = null): List<Int>
    suspend fun checkAdb(adbPath: String = "adb"): Boolean
    suspend fun getDevices(): List<DeviceInfo>
    suspend fun isAppInstalled(packageName: String, deviceSerial: String? = null): Boolean
    suspend fun launchApp(packageName: String, deviceSerial: String? = null): Boolean
}

data class DeviceInfo(
    val serial: String,
    val model: String
)
