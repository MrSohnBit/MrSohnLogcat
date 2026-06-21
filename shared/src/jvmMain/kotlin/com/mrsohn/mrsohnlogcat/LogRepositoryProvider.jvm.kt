package com.mrsohn.mrsohnlogcat

import com.mrsohn.mrsohnlogcat.data.LogRepository

actual fun getLogRepository(adbPath: String): LogRepository = JvmLogRepository(adbPath)
