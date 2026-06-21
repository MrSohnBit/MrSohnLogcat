package com.mrsohn.mrsohnlogcat

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
    VERBOSE, DEBUG, INFO, WARN, ERROR, FATAL;

    companion object {
        fun fromLetter(letter: String): LogLevel {
            return when (letter.uppercase()) {
                "V" -> VERBOSE
                "D" -> DEBUG
                "I" -> INFO
                "W" -> WARN
                "E" -> ERROR
                "F" -> FATAL
                else -> DEBUG
            }
        }
    }
}
