package com.mrsohn.mrsohnlogcat

import com.mrsohn.mrsohnlogcat.expect.SettingsRepository
import java.util.prefs.Preferences

class JvmSettingsRepository : SettingsRepository {
    private val prefs = Preferences.userNodeForPackage(JvmSettingsRepository::class.java)

    override fun getString(key: String, defaultValue: String): String {
        return prefs.get(key, defaultValue)
    }

    override fun setString(key: String, value: String) {
        prefs.put(key, value)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    override fun setBoolean(key: String, value: Boolean) {
        prefs.putBoolean(key, value)
    }
}

actual fun getSettingsRepository(): SettingsRepository = JvmSettingsRepository()
