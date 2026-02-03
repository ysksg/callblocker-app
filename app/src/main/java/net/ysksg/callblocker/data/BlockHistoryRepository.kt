package net.ysksg.callblocker.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class BlockHistoryItem(
    val number: String,
    val timestamp: Long,
    val reason: String? = null,
    val aiResult: String? = null
)

class BlockHistoryRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("block_history", Context.MODE_PRIVATE)

    fun addHistory(number: String, timestamp: Long, reason: String?, aiResult: String? = null) {
        val currentList = getHistory().toMutableList()
        // 新しいものを先頭に
        currentList.add(0, BlockHistoryItem(number, timestamp, reason, aiResult))
        
        // 最大100件まで保存
        if (currentList.size > 100) {
            currentList.removeAt(currentList.lastIndex)
        }
        
        saveHistory(currentList)
    }

    fun updateHistory(timestamp: Long, aiResult: String) {
        val currentList = getHistory().toMutableList()
        val index = currentList.indexOfFirst { it.timestamp == timestamp }
        if (index >= 0) {
            val oldItem = currentList[index]
            currentList[index] = oldItem.copy(aiResult = aiResult)
            saveHistory(currentList)
        }
    }

    fun getHistory(): List<BlockHistoryItem> {
        val jsonString = prefs.getString("history_json", "[]") ?: "[]"
        val list = mutableListOf<BlockHistoryItem>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    BlockHistoryItem(
                        number = obj.getString("number"),
                        timestamp = obj.getLong("timestamp"),
                        reason = obj.optString("reason", null), // null許容で読み込み
                        aiResult = obj.optString("aiResult", null)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("BlockHistoryRepository", "Error parsing history", e)
        }
        return list
    }

    // デバッグやテスト用のクリア機能
    fun clearHistory() {
        prefs.edit { remove("history_json") }
    }

    private fun saveHistory(list: List<BlockHistoryItem>) {
        val jsonArray = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("number", item.number)
                put("timestamp", item.timestamp)
                put("reason", item.reason)
                put("aiResult", item.aiResult)
            }
            jsonArray.put(obj)
        }
        prefs.edit { putString("history_json", jsonArray.toString()) }
    }
}
