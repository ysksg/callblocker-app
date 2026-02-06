package net.ysksg.callblocker.data

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class BackupManager(private val context: Context) {

    fun exportData(uri: Uri): Result<String> {
        return try {
            val root = JSONObject()
            root.put("version", 1)
            root.put("timestamp", System.currentTimeMillis())

            // 1. Settings (Gemini & Overlay)
            val settings = JSONObject()
            
            // Gemini Settings (app_settings)
            val appPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            settings.put("gemini_api_key", appPrefs.getString("gemini_api_key", null))
            settings.put("gemini_model", appPrefs.getString("gemini_model", null))
            settings.put("gemini_prompt", appPrefs.getString("gemini_prompt", null))
            settings.put("search_url_template", appPrefs.getString("search_url_template", null))
            settings.put("theme_mode", appPrefs.getString("theme_mode", null))
            settings.put("is_ai_analysis_enabled", appPrefs.getBoolean("is_ai_analysis_enabled", true))

            // Overlay Settings (overlay_prefs)
            val overlayPrefs = context.getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)
            settings.put("is_overlay_enabled", overlayPrefs.getBoolean("is_overlay_enabled", true))
            settings.put("is_auto_close_enabled", overlayPrefs.getBoolean("is_auto_close_enabled", true))
            settings.put("default_overlay_state", overlayPrefs.getString("default_overlay_state", "minimized"))
            settings.put("is_position_save_enabled", overlayPrefs.getBoolean("is_position_save_enabled", true))
            if (overlayPrefs.contains("overlay_x")) settings.put("overlay_x", overlayPrefs.getInt("overlay_x", 0))
            if (overlayPrefs.contains("overlay_y")) settings.put("overlay_y", overlayPrefs.getInt("overlay_y", 0))

            root.put("settings", settings)

            // 2. Rules
            val blockPrefs = context.getSharedPreferences("block_rules_v3", Context.MODE_PRIVATE)
            val rulesJsonStr = blockPrefs.getString("rules_json", "[]")
            root.put("rules", JSONArray(rulesJsonStr))

            // 3. History
            val historyPrefs = context.getSharedPreferences("block_history", Context.MODE_PRIVATE)
            val historyJsonStr = historyPrefs.getString("history_json", "[]")
            root.put("history", JSONArray(historyJsonStr))

            // Write to file
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { it.write(root.toString(2)) }
            }

            Log.i("BackupManager", "データエクスポート完了")
            Result.success("バックアップを作成しました")
        } catch (e: Exception) {
            Log.e("BackupManager", "エクスポート失敗", e)
            Result.failure(e)
        }
    }

    fun importData(uri: Uri): Result<String> {
        return try {
            // Read file
            val sb = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                }
            }

            val root = JSONObject(sb.toString())
            
            // 1. Restore Settings
            if (root.has("settings")) {
                val settings = root.getJSONObject("settings")
                
                // Gemini
                val appPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit()
                if (settings.has("gemini_api_key")) appPrefs.putString("gemini_api_key", settings.optString("gemini_api_key"))
                if (settings.has("gemini_model")) appPrefs.putString("gemini_model", settings.optString("gemini_model"))
                if (settings.has("gemini_prompt")) appPrefs.putString("gemini_prompt", settings.optString("gemini_prompt"))
                if (settings.has("search_url_template")) appPrefs.putString("search_url_template", settings.optString("search_url_template"))
                if (settings.has("theme_mode")) appPrefs.putString("theme_mode", settings.optString("theme_mode"))
                if (settings.has("is_ai_analysis_enabled")) appPrefs.putBoolean("is_ai_analysis_enabled", settings.getBoolean("is_ai_analysis_enabled"))
                appPrefs.apply()

                // Overlay
                val overlayPrefs = context.getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE).edit()
                if (settings.has("is_overlay_enabled")) overlayPrefs.putBoolean("is_overlay_enabled", settings.getBoolean("is_overlay_enabled"))
                if (settings.has("is_auto_close_enabled")) overlayPrefs.putBoolean("is_auto_close_enabled", settings.getBoolean("is_auto_close_enabled"))
                if (settings.has("default_overlay_state")) overlayPrefs.putString("default_overlay_state", settings.getString("default_overlay_state"))
                if (settings.has("is_position_save_enabled")) overlayPrefs.putBoolean("is_position_save_enabled", settings.getBoolean("is_position_save_enabled"))
                if (settings.has("overlay_x")) overlayPrefs.putInt("overlay_x", settings.getInt("overlay_x"))
                if (settings.has("overlay_y")) overlayPrefs.putInt("overlay_y", settings.getInt("overlay_y"))
                overlayPrefs.apply()
            }

            // 2. Restore Rules
            if (root.has("rules")) {
                val rulesArray = root.getJSONArray("rules")
                val blockPrefs = context.getSharedPreferences("block_rules_v3", Context.MODE_PRIVATE).edit()
                blockPrefs.putString("rules_json", rulesArray.toString())
                blockPrefs.apply()
            }

            // 3. Restore History
            if (root.has("history")) {
                val historyArray = root.getJSONArray("history")
                val historyPrefs = context.getSharedPreferences("block_history", Context.MODE_PRIVATE).edit()
                historyPrefs.putString("history_json", historyArray.toString())
                historyPrefs.apply()
            }

            Log.i("BackupManager", "データインポート完了")
            Result.success("データを復元しました")
        } catch (e: Exception) {
            Log.e("BackupManager", "インポート失敗", e)
            Result.failure(e)
        }
    }
}
