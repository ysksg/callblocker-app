package net.ysksg.callblocker.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Gemini APIクライアントクラス。
 * GoogleのGenerative AIモデルを使用して、電話番号の解析などを行います。
 */
class GeminiRepository(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)


    fun getApiKey(): String? {
        return prefs.getString("gemini_api_key", null)
    }

    fun setApiKey(key: String) {
        Log.i("GeminiRepo", "APIキー設定変更: (長さ=${key.length})")
        prefs.edit().putString("gemini_api_key", key).apply()
    }

    fun isAiAnalysisEnabled(): Boolean {
        return prefs.getBoolean("is_ai_analysis_enabled", true)
    }

    fun setAiAnalysisEnabled(enabled: Boolean) {
        Log.i("GeminiRepo", "AI解析有効化設定変更: $enabled")
        prefs.edit().putBoolean("is_ai_analysis_enabled", enabled).apply()
    }

    /**
     * AI解析結果のキャッシュが有効かどうか。
     */
    fun isAiCacheEnabled(): Boolean {
        return prefs.getBoolean("is_ai_cache_enabled", true)
    }

    fun setAiCacheEnabled(enabled: Boolean) {
        Log.i("GeminiRepo", "AI解析キャッシュ設定変更: $enabled")
        prefs.edit().putBoolean("is_ai_cache_enabled", enabled).apply()
    }


    /**
     * 使用するAIモデル名を取得。
     */
    fun getModel(): String {
        return prefs.getString("gemini_model", "gemini-2.5-flash-lite") ?: "gemini-2.5-flash-lite"
    }

    fun setModel(model: String) {
        Log.i("GeminiRepo", "AIモデル設定変更: $model")
        prefs.edit().putString("gemini_model", model).apply()
    }

    /**
     * AI解析時に使用するプロンプトの設定を取得。
     */
    fun getPrompt(): String {
        val defaultPrompt = "電話番号 {number} について、迷惑電話の可能性を調査してください。" +
            "調査の際は電話番号をダブルクォーテーションで囲い、完全一致で検索してください。" +
            "また、検索結果を必ず複数参照し、総合的に評判を判断してください。" +
            "調査結果をもとに、回答は必ず【迷惑電話】か【情報なし】のいずれかで始めてください。" +
            "その後にどのような評判であるかを1行で簡潔に答えてください。"
        return prefs.getString("gemini_prompt", defaultPrompt) ?: defaultPrompt
    }

    fun setPrompt(prompt: String) {
        Log.i("GeminiRepo", "AIプロンプト設定変更: (長さ=${prompt.length})")
        prefs.edit().putString("gemini_prompt", prompt).apply()
    }

    /**
     * 指定された電話番号をAIで解析します。
     *
     * @param number 対象の電話番号
     * @return 解析結果の文字列とステータスのペア
     */
    suspend fun checkPhoneNumber(number: String): Pair<String, AiStatus> {
        val apiKey = getApiKey()
        if (apiKey.isNullOrBlank()) {
            Log.e("GeminiRepo", "APIキー未設定")
            return "[設定エラー] APIキーが設定されていません" to AiStatus.ERROR
        }

        val model = getModel()
        Log.i("GeminiRepo", "電話番号解析開始: $number (モデル: $model)")

        return withContext(Dispatchers.IO) {
            var lastError: String = "[通信エラー] 不明"
            // リトライ回数: 初回 + 1回リトライ
            repeat(2) { attempt ->
                try {
                    Log.d("GeminiRepo", "リクエスト送信 (試行 ${attempt + 1})")
                    // モデル名を動的にURLに組み込む
                    val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("x-goog-api-key", apiKey ?: "")

                    val promptTemplate = getPrompt()
                    val promptText = if (promptTemplate.contains("{number}")) {
                        promptTemplate.replace("{number}", number)
                    } else {
                         "$promptTemplate (対象番号: $number)"
                    }

                    val jsonBody = JSONObject()
                    val contentsArray = JSONArray()
                    val contentObj = JSONObject()
                    val partsArray = JSONArray()
                    val partObj = JSONObject()
                    partObj.put("text", promptText)
                    partsArray.put(partObj)
                    contentObj.put("parts", partsArray)
                    contentsArray.put(contentObj)
                    jsonBody.put("contents", contentsArray)
                    
                    // Google Search Grounding Toolの追加
                    val toolsArray = JSONArray()
                    val toolObj = JSONObject()
                    val googleSearchObj = JSONObject()
                    toolObj.put("google_search", googleSearchObj)
                    toolsArray.put(toolObj)
                    jsonBody.put("tools", toolsArray)

                    OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }

                    val responseCode = conn.responseCode
                    Log.d("GeminiRepo", "サーバー応答コード: $responseCode")
                    
                    if (responseCode == 200) {
                        val response = conn.inputStream.bufferedReader().use { it.readText() }
                        Log.d("GeminiRepo", "レスポンス受信完了: ${response.length}文字")
                        val parsed = parseGeminiResponse(response)
                        val status = if (parsed.startsWith("[解析エラー]")) AiStatus.ERROR else AiStatus.SUCCESS
                        return@withContext parsed to status
                    } else {
                        lastError = "[通信エラー] サーバー応答: $responseCode"
                        Log.w("GeminiRepo", "試行 ${attempt + 1} 失敗: $lastError")
                    }

                } catch (e: Exception) {
                    Log.e("GeminiRepo", "試行 ${attempt + 1} エラー", e)
                    lastError = "[通信エラー] 通信障害: ${e.message}"
                }
                
                // リトライ前の待機（最後の試行でなければ）
                if (attempt == 0) {
                    kotlinx.coroutines.delay(1000) // 1秒待機して再試行
                }
            }
            return@withContext lastError to AiStatus.ERROR
        }
    }

    /**
     * APIキーの有効性を確認します（デフォルト保存済みモデル使用）。
     */
    suspend fun verifyApiKey(apiKey: String): Pair<Boolean, String> {
         return verifyApiKey(apiKey, getModel())
    }

    /**
     * 特定のモデルを指定してAPIキーの有効性を確認します。
     *
     * @param apiKey 確認するAPIキー
     * @param model 使用するモデル名
     * @return 検証結果ペア (成功/失敗, メッセージ)
     */
    suspend fun verifyApiKey(apiKey: String, model: String): Pair<Boolean, String> {
         return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-goog-api-key", apiKey)

                val jsonBody = JSONObject()
                val contentsArray = JSONArray()
                val contentObj = JSONObject()
                val partsArray = JSONArray()
                val partObj = JSONObject()
                partObj.put("text", "Hi")
                partsArray.put(partObj)
                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
                jsonBody.put("contents", contentsArray)

                OutputStreamWriter(conn.outputStream).use { it.write(jsonBody.toString()) }
                
                val code = conn.responseCode
                if (code == 200) {
                    return@withContext true to "検証成功"
                } else if (code == 429) {
                    return@withContext true to "[API警告] レート制限 (429)" // 認証自体は成功とみなす
                } else {
                    return@withContext false to "[通信エラー] サーバー応答: $code"
                }
            } catch (e: Exception) {
                Log.e("GeminiRepo", "認証エラー", e)
                return@withContext false to "[システムエラー] 例外発生: ${e.message}"
            }
        }
    }

    private fun parseGeminiResponse(jsonString: String): String {
        try {
            val root = JSONObject(jsonString)
            val candidates = root.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.getJSONObject("content")
                val parts = content.getJSONArray("parts")
                if (parts.length() > 0) {
                    return parts.getJSONObject(0).getString("text").trim()
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiRepo", "パースエラー", e)
        }
        return "[解析エラー] 応答形式が不正です"
    }
}
