package com.mrsohn.mrsohnlogcat.expect

import com.mrsohn.mrsohnlogcat.data.LogRepository

expect fun getLogRepository(adbPath: String): LogRepository
