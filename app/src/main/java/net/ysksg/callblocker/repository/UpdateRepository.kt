package net.ysksg.callblocker.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ysksg.callblocker.BuildConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Releases API を使用してアップデートを確認するリポジトリ。
 */
class UpdateRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    data class ReleaseInfo(
        val tagName: String,
        val body: String,
        val htmlUrl: String,
        val apkUrl: String?,
        val isNewer: Boolean
    )

    /**
     * 自動アップデート確認が有効かどうかを取得。
     */
    fun isAutoUpdateCheckEnabled(): Boolean {
        return prefs.getBoolean("is_auto_update_check_enabled", true)
    }

    /**
     * 自動アップデート確認の有効・無効を設定。
     */
    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("is_auto_update_check_enabled", enabled).apply()
    }

    /**
     * GitHub から最新のリリース情報を取得し、現在のバージョンと比較します。
     */
    suspend fun checkForUpdate(): ReleaseInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/ysksg/callblocker-app/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                    val json = JSONObject(response)
                    val tagName = json.getString("tag_name")
                    val body = json.getString("body")
                    val htmlUrl = json.getString("html_url")
                    
                    // APK URL の取得
                    var apkUrl: String? = null
                    if (json.has("assets")) {
                        val assets = json.getJSONArray("assets")
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.getString("name")
                            if (name.endsWith(".apk")) {
                                apkUrl = asset.getString("browser_download_url")
                                break
                            }
                        }
                    }

                    val currentVersion = BuildConfig.VERSION_NAME
                    val isNewer = isVersionNewer(currentVersion, tagName)

                    Log.i("UpdateRepo", "最新バージョン確認: 現在=$currentVersion, 最新=$tagName, 更新あり=$isNewer, APK=$apkUrl")
                    return@withContext ReleaseInfo(tagName, body, htmlUrl, apkUrl, isNewer)
                } else {
                    Log.e("UpdateRepo", "GitHub API エラー: $responseCode")
                }
            } catch (e: Exception) {
                Log.e("UpdateRepo", "アップデート確認エラー", e)
            }
            null
        }
    }

    /**
     * バージョン文字列を比較します。
     * @param current 現在のバージョン (例: "1.3")
     * @param latest 最新のバージョンタグ (例: "v1.4")
     * @return 最新の方が新しい場合は true
     */
    fun isVersionNewer(current: String, latest: String): Boolean {
        try {
            // v 接頭辞を除去
            val currClean = current.removePrefix("v").trim()
            val lateClean = latest.removePrefix("v").trim()

            val currParts = currClean.split(".").map { it.toIntOrNull() ?: 0 }
            val lateParts = lateClean.split(".").map { it.toIntOrNull() ?: 0 }

            val maxLength = maxOf(currParts.size, lateParts.size)
            for (i in 0 until maxLength) {
                val currPart = currParts.getOrElse(i) { 0 }
                val latePart = lateParts.getOrElse(i) { 0 }
                if (latePart > currPart) return true
                if (latePart < currPart) return false
            }
        } catch (e: Exception) {
            Log.e("UpdateRepo", "バージョン比較エラー: current=$current, latest=$latest", e)
        }
        return false
    }
}
