package com.mrsohn.mrsohnlogcat

actual fun getLogRepository(adbPath: String): LogRepository = JvmLogRepository(adbPath)
