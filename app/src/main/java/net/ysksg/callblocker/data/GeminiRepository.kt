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

class GeminiRepository(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun getApiKey(): String? {
        return prefs.getString("gemini_api_key", null)
    }

    fun setApiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key).apply()
    }

    // 検索エンジンURLテンプレート
    fun getSearchUrlTemplate(): String {
        return prefs.getString("search_url_template", "https://www.google.com/search?q={number}") 
            ?: "https://www.google.com/search?q={number}"
    }

    fun setSearchUrlTemplate(url: String) {
        prefs.edit().putString("search_url_template", url).apply()
    }

    // AIモデル
    fun getModel(): String {
        return prefs.getString("gemini_model", "gemini-2.5-flash-lite") ?: "gemini-2.5-flash-lite"
    }

    fun setModel(model: String) {
        prefs.edit().putString("gemini_model", model).apply()
    }

    // AI解析プロンプト
    fun getPrompt(): String {
        return prefs.getString("gemini_prompt", 
            "電話番号 {number} について、迷惑電話の可能性がありますか？「危険性: 高/中/低」という形式で始め、どのような内容の電話がされているのか1行で簡潔に答えてください。情報がない場合は「情報なし」と答えて。"
        ) ?: "電話番号 {number} について、迷惑電話の可能性がありますか？「危険性: 高/中/低」という形式で始め、どのような内容の電話がされているのか1行で簡潔に答えてください。情報がない場合は「情報なし」と答えて。"
    }

    fun setPrompt(prompt: String) {
        prefs.edit().putString("gemini_prompt", prompt).apply()
    }

    suspend fun checkPhoneNumber(number: String): String {
        val apiKey = getApiKey()
        if (apiKey.isNullOrBlank()) {
            return "APIキーが設定されていません"
        }

        val model = getModel()

        return withContext(Dispatchers.IO) {
            try {
                // Use dynamic model
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

                // Add Google Search Grounding Tool
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
                        return@withContext parseGeminiResponse(response.toString())
                    }
                } else {
                    return@withContext "エラー: $responseCode"
                }

            } catch (e: Exception) {
                Log.e("GeminiRepo", "Error", e)
                return@withContext "通信エラー: ${e.message}"
            }
        }
    }

    suspend fun verifyApiKey(apiKey: String): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val model = getModel() // Use currently saved model, or we could pass it in. 
                // However, verify is usually done BEFORE saving. So we should probably allow verify to take a model param?
                // For now, let's assume we use the saved model, BUT the user might change dropdown and click verify/save.
                // So verify shouldn't rely on saved pref if we want to test the selection.
                // Let's rely on the caller setting the pref OR overload/update this method. 
                // Simpler: Just use the saved pref. The UI flow will be: Select model -> entering text -> Save (which verifies). 
                // Actually, saving happens at the end. We should verify what is currently ON SCREEN.
                // I will add an optional model parameter to verifyApiKey, defaulting to saved.
                
                // Wait, I cannot change the signature easily if used elsewhere.
                // Let's stick to using the saved preference for now, OR better, update `verifyApiKey` to take `modelName`.
                // But AppSettingsScreen calls it. I will update AppSettingsScreen too.
                
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
                    return@withContext true to "Success"
                } else if (code == 429) {
                    return@withContext true to "Rate Limit (429)" // Authenticated but limited
                } else {
                    return@withContext false to "HTTP ERROR: $code"
                }
            } catch (e: Exception) {
                Log.e("GeminiRepo", "Verification Error", e)
                return@withContext false to "Error: ${e.message}"
            }
        }
    }

    // Overload for verifying a specific model before saving
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
                    return@withContext true to "Success"
                } else if (code == 429) {
                    return@withContext true to "Rate Limit (429)"
                } else {
                    return@withContext false to "HTTP ERROR: $code"
                }
            } catch (e: Exception) {
                Log.e("GeminiRepo", "Verification Error", e)
                return@withContext false to "Error: ${e.message}"
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
        return "解析不能"
    }
}
