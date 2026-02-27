package net.ysksg.callblocker.repository

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * AI解析のステータスフラグ
 */
enum class AiStatus {
    PENDING,  // 解析中
    SUCCESS,  // 成功（キャッシュ対象）
    ERROR,    // 通信エラー・パース失敗等
    NONE      // 解析対象外（非通知など）
}

/**
 * ブロックの種別
 */
enum class BlockType {
    ALLOWED,  // 許可（ブロックなし）
    REJECTED, // 拒否（切断）
    SILENCED  // 無音化（呼び出し継続）
}

/**
 * 着信履歴データ。
 */
data class BlockHistory(
    val number: String,
    val timestamp: Long,
    val reason: String? = null,
    val aiResult: String? = null,
    val aiStatus: AiStatus = AiStatus.NONE,
    val blockType: BlockType = BlockType.ALLOWED
)

/**
 * 着信履歴をSharedPreferencesに保存・読み出しするリポジトリ。
 */
class BlockHistoryRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("block_history", Context.MODE_PRIVATE)

    fun addHistory(
        number: String,
        timestamp: Long,
        reason: String?,
        aiResult: String? = null,
        aiStatus: AiStatus = AiStatus.NONE,
        blockType: BlockType = BlockType.ALLOWED
    ) {
        Log.d("BlockHistoryRepository", "履歴追加: $number (Status: $aiStatus, Type: $blockType)")
        val currentList = getHistory().toMutableList()
        currentList.add(0, BlockHistory(number, timestamp, reason, aiResult, aiStatus, blockType))
        
        if (currentList.size > 100) {
            currentList.removeAt(currentList.lastIndex)
        }
        
        saveHistory(currentList)
    }

    fun updateHistory(timestamp: Long, aiResult: String, aiStatus: AiStatus) {
        val currentList = getHistory().toMutableList()
        val index = currentList.indexOfFirst { it.timestamp == timestamp }
        if (index >= 0) {
            val oldItem = currentList[index]
            currentList[index] = oldItem.copy(aiResult = aiResult, aiStatus = aiStatus)
            Log.d("BlockHistoryRepository", "履歴更新(AI結果): $aiResult, Status: $aiStatus")
            saveHistory(currentList)
        }
    }

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
                        aiResult = if (obj.isNull("aiResult")) null else obj.getString("aiResult"),
                        aiStatus = try { AiStatus.valueOf(obj.optString("aiStatus", AiStatus.NONE.name)) } catch (e: Exception) { AiStatus.NONE },
                        blockType = try {
                            if (obj.has("blockType")) {
                                BlockType.valueOf(obj.getString("blockType"))
                            } else {
                                // 以前のバージョンの互換性対応
                                val reasonStr = if (obj.isNull("reason")) null else obj.getString("reason")
                                if (reasonStr == null || reasonStr == "許可") {
                                    BlockType.ALLOWED
                                } else {
                                    BlockType.REJECTED // 過去の履歴で理由があるものは拒否されていたとみなす
                                }
                            }
                        } catch (e: Exception) { 
                            BlockType.ALLOWED 
                        }
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("BlockHistoryRepository", "履歴のパースに失敗しました", e)
        }
        return list
    }
    
    /**
     * 特定の番号に対する直近の成功したAI解析結果（キャッシュ）を取得。
     */
    fun getCachedAiResult(number: String): String? {
        if (number.isEmpty()) return null
        return getHistory().find { it.number == number && it.aiStatus == AiStatus.SUCCESS }?.aiResult
    }

    fun getHistoryByTimestamp(timestamp: Long): BlockHistory? {
        val list = getHistory()
        return list.find { it.timestamp == timestamp }
    }

    fun clearHistory() {
        Log.i("BlockHistoryRepository", "着信履歴を全消去しました")
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
                put("aiStatus", item.aiStatus.name)
                put("blockType", item.blockType.name)
            }
            jsonArray.put(obj)
        }
        prefs.edit { putString("history_json", jsonArray.toString()) }
    }
}
