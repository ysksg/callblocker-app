package net.ysksg.callblocker.data

import android.content.Context
import android.content.SharedPreferences

class OverlaySettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_AUTO_CLOSE_ENABLED = "is_auto_close_enabled"
        private const val KEY_DEFAULT_OVERLAY_STATE = "default_overlay_state"
        
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
}
