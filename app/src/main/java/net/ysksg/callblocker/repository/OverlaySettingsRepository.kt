package net.ysksg.callblocker.repository

import android.content.Context
import android.content.SharedPreferences

class OverlaySettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_AUTO_CLOSE_ENABLED = "is_auto_close_enabled"
        private const val KEY_DEFAULT_OVERLAY_STATE = "default_overlay_state"
        private const val KEY_IS_OVERLAY_ENABLED = "is_overlay_enabled"
        private const val KEY_IS_POSITION_SAVE_ENABLED = "is_position_save_enabled"
        
        const val STATE_MINIMIZED = "minimized"
        const val STATE_EXPANDED = "expanded"
    }

    fun isAutoCloseEnabled(): Boolean {
        return prefs.getBoolean(KEY_IS_AUTO_CLOSE_ENABLED, true) // Default true
    }

    fun setAutoCloseEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_AUTO_CLOSE_ENABLED, enabled).apply()
    }

    fun getDefaultOverlayState(): String {
        return prefs.getString(KEY_DEFAULT_OVERLAY_STATE, STATE_MINIMIZED) ?: STATE_MINIMIZED
    }

    fun setDefaultOverlayState(state: String) {
        prefs.edit().putString(KEY_DEFAULT_OVERLAY_STATE, state).apply()
    }

    fun isOverlayEnabled(): Boolean {
        return prefs.getBoolean(KEY_IS_OVERLAY_ENABLED, true)
    }

    fun setOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_OVERLAY_ENABLED, enabled).apply()
    }

    fun isPositionSaveEnabled(): Boolean {
        return prefs.getBoolean(KEY_IS_POSITION_SAVE_ENABLED, true)
    }

    fun setPositionSaveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_POSITION_SAVE_ENABLED, enabled).apply()
    }
}
