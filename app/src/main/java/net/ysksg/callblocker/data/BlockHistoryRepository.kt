package net.ysksg.callblocker.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * 着信履歴データ。
 */
data class BlockHistory(
    val number: String,
    val timestamp: Long,
    val reason: String? = null,
    val aiResult: String? = null
)

/**
 * 着信履歴をSharedPreferencesに保存・読み出しするリポジトリ。
 * 簡易的なJSON形式で保存します。
 */
class BlockHistoryRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("block_history", Context.MODE_PRIVATE)

    /**
     * 新しい履歴を追加します。
     * 最大100件まで保持し、古いものは削除されます。
     */
    fun addHistory(number: String, timestamp: Long, reason: String?, aiResult: String? = null) {
        val currentList = getHistory().toMutableList()
        // 新しいものを先頭に
        currentList.add(0, BlockHistory(number, timestamp, reason, aiResult))
        
        // 最大100件まで保存
        if (currentList.size > 100) {
            currentList.removeAt(currentList.lastIndex)
        }
        
        saveHistory(currentList)
    }

    /**
     * 既存の履歴（AI解析結果など）を更新します。
     */
    fun updateHistory(timestamp: Long, aiResult: String) {
        val currentList = getHistory().toMutableList()
        val index = currentList.indexOfFirst { it.timestamp == timestamp }
        if (index >= 0) {
            val oldItem = currentList[index]
            currentList[index] = oldItem.copy(aiResult = aiResult)
            saveHistory(currentList)
        }
    }

    /**
     * 保存されている履歴リストを取得します。
     */
    fun getHistory(): List<BlockHistory> {
        val jsonString = prefs.getString("history_json", "[]") ?: "[]"
        val list = mutableListOf<BlockHistory>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    BlockHistory(
                        number = obj.getString("number"),
                        timestamp = obj.getLong("timestamp"),
                        reason = if (obj.isNull("reason")) null else obj.getString("reason"),
                        aiResult = if (obj.isNull("aiResult")) null else obj.getString("aiResult")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("BlockHistoryRepository", "履歴のパースに失敗しました", e)
        }
        return list
    }

    /**
     * 履歴を全消去します（デバッグ・テスト用）。
     */
    fun clearHistory() {
        prefs.edit { remove("history_json") }
    }

    private fun saveHistory(list: List<BlockHistory>) {
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
