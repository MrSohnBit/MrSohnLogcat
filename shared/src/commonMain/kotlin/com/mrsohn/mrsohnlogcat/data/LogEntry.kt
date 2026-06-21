package com.mrsohn.mrsohnlogcat.data

data class LogEntry(
    val id: Long, // Unique ID for each log entry
    val timestamp: String,
    val processId: Int,
    val threadId: Int,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val packageName: String? = null
)

enum class LogLevel {
    INFO,DEBUG, VERBOSE, FATAL, WARN, ERROR;

    companion object {
        fun fromLetter(letter: String): LogLevel {
            return when (letter.uppercase()) {
                "I" -> INFO
                "D" -> DEBUG
                "V" -> VERBOSE
                "F" -> FATAL
                "W" -> WARN
                "E" -> ERROR
                else -> DEBUG
            }
        }
    }
}
