package com.mrsohn.mrsohnlogcat

import com.mrsohn.mrsohnlogcat.data.LogRepository

expect fun getLogRepository(adbPath: String): LogRepository
