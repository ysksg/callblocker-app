package net.ysksg.callblocker.data

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
    
    companion object {
        // 同じ番号に対するAPIコールの繰り返しを防ぐためのキャッシュ
        private val cache = ConcurrentHashMap<String, String>()
    }
    
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun getApiKey(): String? {
        return prefs.getString("gemini_api_key", null)
    }

    fun setApiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key).apply()
    }

    /**
     * Web検索ボタン用のURLテンプレートを取得。
     */
    fun getSearchUrlTemplate(): String {
        return prefs.getString("search_url_template", "https://www.google.com/search?q={number}") 
            ?: "https://www.google.com/search?q={number}"
    }

    fun setSearchUrlTemplate(url: String) {
        prefs.edit().putString("search_url_template", url).apply()
    }

    /**
     * 使用するAIモデル名を取得。
     */
    fun getModel(): String {
        return prefs.getString("gemini_model", "gemini-2.5-flash-lite") ?: "gemini-2.5-flash-lite"
    }

    fun setModel(model: String) {
        prefs.edit().putString("gemini_model", model).apply()
    }

    /**
     * AI解析時に使用するプロンプトの設定を取得。
     */
    fun getPrompt(): String {
        return prefs.getString("gemini_prompt", 
            "電話番号 {number} について、迷惑電話の可能性がありますか？「危険性: 高/中/低」という形式で始め、どのような内容の電話がされているのか1行で簡潔に答えてください。情報がない場合は「情報なし」と答えて。"
        ) ?: "電話番号 {number} について、迷惑電話の可能性がありますか？「危険性: 高/中/低」という形式で始め、どのような内容の電話がされているのか1行で簡潔に答えてください。情報がない場合は「情報なし」と答えて。"
    }

    fun setPrompt(prompt: String) {
        prefs.edit().putString("gemini_prompt", prompt).apply()
    }

    /**
     * 指定された電話番号をAIで解析します。
     *
     * @param number 対象の電話番号
     * @param ignoreCache trueの場合、キャッシュを使用せず強制的にAPIを呼び出します。
     * @return 解析結果の文字列（エラー時はエラーメッセージ）
     */
    suspend fun checkPhoneNumber(number: String, ignoreCache: Boolean = false): String {
        val apiKey = getApiKey()
        if (apiKey.isNullOrBlank()) {
            return "[設定エラー] APIキーが設定されていません"
        }

        val model = getModel()

        // キャッシュチェック
        if (!ignoreCache && cache.containsKey(number)) {
            Log.d("GeminiRepo", "Cache hit for $number")
            return cache[number]!!
        }

        return withContext(Dispatchers.IO) {
            try {
                // モデル名を動的にURLに組み込む
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-goog-api-key", apiKey)

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
                if (responseCode == 200) {
                    BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                        val response = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            response.append(line)
                        }
                        val result = parseGeminiResponse(response.toString())
                        cache[number] = result
                        return@withContext result
                    }
                } else {
                    return@withContext "[通信エラー] サーバー応答: $responseCode"
                }

            } catch (e: Exception) {
                Log.e("GeminiRepo", "Error", e)
                return@withContext "[通信エラー] 通信障害: ${e.message}"
            }
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
                Log.e("GeminiRepo", "Verification Error", e)
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
            Log.e("GeminiRepo", "Parse Error", e)
        }
        return "[解析エラー] 応答形式が不正です"
    }
}
