package com.mrsohn.mrsohnlogcat.expect

interface SettingsRepository {
    fun getString(key: String, defaultValue: String): String
    fun setString(key: String, value: String)
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun setBoolean(key: String, value: Boolean)
}

expect fun getSettingsRepository(): SettingsRepository
