package net.ysksg.callblocker.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.regex.Pattern
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 着信ブロック判定結果データクラス。
 *
 * @param shouldBlock ブロックすべきかどうか (true=ブロック, false=許可)
 * @param reason ブロック理由（ログ用）
 * @param matchedRuleName 適合したルールの名前
 */
data class BlockResult(
    val shouldBlock: Boolean,
    val reason: String? = null,
    val matchedRuleName: String? = null
)

/**
 * ブロックルールの管理と実行を行うリポジトリクラス。
 * ルールの保存、読み込み、および着信番号に対する判定ロジックを提供します。
 */
class BlockRuleRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("block_rules_v3", Context.MODE_PRIVATE)

    init {
        if (!prefs.contains("rules_json")) {
            val defaultRules = listOf(
                BlockRule(name = "連絡先許可", isAllowRule = true).apply { 
                    conditions.add(ContactCondition(isInverse = false)) 
                },
                BlockRule(name = "海外着信ブロック", isAllowRule = false).apply { 
                    conditions.add(RegexCondition("^\\+(?!81).*", isInverse = false)) 
                },
                BlockRule(name = "0800ブロック", isAllowRule = false).apply { 
                    conditions.add(RegexCondition("^0800.*", isInverse = false)) 
                },
                BlockRule(name = "050ブロック", isAllowRule = false).apply { 
                    conditions.add(RegexCondition("^050.*", isInverse = false)) 
                }
            )
            saveRulesJson(defaultRules)
        }
    }

    /**
     * 保存されているルール一覧を取得します。
     *
     * @return ルールのリスト
     */
    fun getRules(): List<BlockRule> {
        val jsonString = prefs.getString("rules_json", "[]") ?: "[]"
        return parseRulesJson(jsonString)
    }

    /**
     * ルールを保存します（新規または更新）。
     * IDが一致するルールがあれば更新し、なければ末尾に追加します。
     *
     * @param rule 保存するルール
     */
    fun saveRule(rule: BlockRule) {
        val currentRules = getRules().toMutableList()
        val index = currentRules.indexOfFirst { it.id == rule.id }
        if (index >= 0) {
            currentRules[index] = rule
        } else {
            // 新規ルールは末尾に追加（評価順序が重要であるため）
            currentRules.add(rule)
        }
        Log.i("BlockRepo", "ルールを保存/更新しました: ${rule.name} (ID: ${rule.id})")
        saveRulesJson(currentRules)
    }

    /**
     * 指定したIDのルールを削除します。
     *
     * @param ruleId 削除対象のルールID
     */
    fun deleteRule(ruleId: String) {
        val currentRules = getRules().toMutableList()
        currentRules.removeAll { it.id == ruleId }
        Log.i("BlockRepo", "ルールを削除しました: ID=$ruleId")
        saveRulesJson(currentRules)
    }
    
    /**
     * ルールの順番を入れ替えます（優先度変更）。
     *
     * @param fromPosition 移動元のインデックス
     * @param toPosition 移動先のインデックス
     */
    fun swapRules(fromPosition: Int, toPosition: Int) {
        val currentRules = getRules().toMutableList()
        if (fromPosition in currentRules.indices && toPosition in currentRules.indices) {
            Collections.swap(currentRules, fromPosition, toPosition)
            saveRulesJson(currentRules)
        }
    }

    /**
     * 全ルールを一括保存します。
     * ドラッグ＆ドロップ後の保存などで使用します。
     *
     * @param rules 保存するルールリスト
     */
    fun saveRules(rules: List<BlockRule>) {
        saveRulesJson(rules)
    }

    private fun saveRulesJson(rules: List<BlockRule>) {
        val jsonArray = JSONArray()
        rules.forEach { rule ->
            val ruleObj = JSONObject().apply {
                put("id", rule.id)
                put("name", rule.name)
                put("isEnabled", rule.isEnabled)
                put("isAllowRule", rule.isAllowRule)
                
                val conditionsArray = JSONArray()
                rule.conditions.forEach { condition ->
                    val condObj = JSONObject()
                    condObj.put("type", condition.type)
                    condObj.put("isInverse", condition.isInverse)
                    when (condition) {
                        is RegexCondition -> {
                            condObj.put("pattern", condition.pattern)
                        }
                        is ContactCondition -> {
                            // 追加プロパティなし
                        }
                        is TimeCondition -> {
                            if (condition.startDate != null) condObj.put("startDate", condition.startDate)
                            if (condition.endDate != null) condObj.put("endDate", condition.endDate)
                            if (condition.startHour != null) condObj.put("startHour", condition.startHour)
                            if (condition.startMinute != null) condObj.put("startMinute", condition.startMinute)
                            if (condition.endHour != null) condObj.put("endHour", condition.endHour)
                            if (condition.endMinute != null) condObj.put("endMinute", condition.endMinute)
                            if (condition.daysOfWeek.isNotEmpty()) {
                                val daysArray = JSONArray()
                                condition.daysOfWeek.forEach { daysArray.put(it) }
                                condObj.put("daysOfWeek", daysArray)
                            }
                        }
                    }
                    conditionsArray.put(condObj)
                }
                put("conditions", conditionsArray)
            }
            jsonArray.put(ruleObj)
        }
        prefs.edit { putString("rules_json", jsonArray.toString()) }
    }

    private fun parseRulesJson(jsonString: String): List<BlockRule> {
        val list = mutableListOf<BlockRule>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val rule = BlockRule(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    isEnabled = obj.optBoolean("isEnabled", true),
                    isAllowRule = obj.optBoolean("isAllowRule", false),
                    conditions = mutableListOf()
                )
                
                val conditionsArray = obj.getJSONArray("conditions")
                for (j in 0 until conditionsArray.length()) {
                    val condObj = conditionsArray.getJSONObject(j)
                    val type = condObj.getString("type")
                    val isInverse = condObj.optBoolean("isInverse", false)
                    
                    when (type) {
                        "regex" -> {
                            rule.conditions.add(RegexCondition(condObj.getString("pattern"), isInverse))
                        }
                        "contact" -> {
                            // マイグレーション: 旧形式 "isRegistered" のサポート
                            var effectiveInverse = isInverse
                            if (condObj.has("isRegistered")) {
                                val isRegistered = condObj.getBoolean("isRegistered")
                                if (!isRegistered) effectiveInverse = true
                            }
                            rule.conditions.add(ContactCondition(effectiveInverse))
                        }
                        "country" -> {
                            // countryルールはサポート外のためスキップ
                        }
                        "time" -> {
                            val daysList = mutableListOf<Int>()
                            if (condObj.has("daysOfWeek")) {
                                val arr = condObj.getJSONArray("daysOfWeek")
                                for (k in 0 until arr.length()) {
                                    daysList.add(arr.getInt(k))
                                }
                            }
                            rule.conditions.add(TimeCondition(
                                startDate = if(condObj.has("startDate")) condObj.getString("startDate") else null,
                                endDate = if(condObj.has("endDate")) condObj.getString("endDate") else null,
                                startHour = if(condObj.has("startHour")) condObj.getInt("startHour") else null,
                                startMinute = if(condObj.has("startMinute")) condObj.getInt("startMinute") else null,
                                endHour = if(condObj.has("endHour")) condObj.getInt("endHour") else null,
                                endMinute = if(condObj.has("endMinute")) condObj.getInt("endMinute") else null,
                                daysOfWeek = daysList,
                                isInverse = isInverse
                            ))
                        }
                    }
                }
                list.add(rule)
            }
        } catch (e: Exception) {
            Log.e("BlockRuleRepository", "Error parsing rules", e)
        }
        return list
    }

    /**
     * 着信番号に対してブロックルールを適用し、結果を返します。
     *
     * @param number 着信番号
     * @return 判定結果
     */
    fun checkBlock(number: String): BlockResult {
        // ハイフン除去などの単純正規化
        val normalizedNumber = number.replace(Regex("[^0-9+]"), "")
        val phoneUtil = PhoneNumberUtil.getInstance()

        // マッチング候補リスト作成
        val formattedList = mutableListOf<String>()
        formattedList.add(number) // 元の入力
        formattedList.add(normalizedNumber) // 記号除去

        try {
            // libphonenumber で解析し、各種フォーマットを生成
            val numberProto = phoneUtil.parse(number, "JP")
            
            // 1. National Format (090-1234-5678)
            formattedList.add(phoneUtil.format(numberProto, PhoneNumberFormat.NATIONAL))
            // ハイフンなしのNational (09012345678)
            formattedList.add(phoneUtil.format(numberProto, PhoneNumberFormat.NATIONAL).replace(Regex("[^0-9]"), ""))

            // 2. International Format (+81 90-1234-5678)
            formattedList.add(phoneUtil.format(numberProto, PhoneNumberFormat.INTERNATIONAL))
            
            // 3. E164 Format (+819012345678)
            formattedList.add(phoneUtil.format(numberProto, PhoneNumberFormat.E164))

        } catch (e: Exception) {
            Log.w("BlockRepo", "Failed to parse number with libphonenumber: $number")
            // パース失敗しても、最低限のローカル変換などは試す
            if (normalizedNumber.startsWith("+81")) {
                formattedList.add("0" + normalizedNumber.substring(3))
            }
        }
        
        // 重複除去
        val uniqueList = formattedList.distinct()

        Log.d("BlockRepo", "Check: $uniqueList")

        // ルール取得 (有効なもののみ)
        val allRules = getRules().filter { it.isEnabled }
        
        for (rule in allRules) {
            if (isRuleMatched(rule, uniqueList)) {
                // マッチしたルールが「許可ルール」ならブロックしない
                // 「拒否ルール」ならブロックする
                if (rule.isAllowRule) {
                    return BlockResult(shouldBlock = false, matchedRuleName = rule.name)
                } else {
                    return BlockResult(shouldBlock = true, reason = rule.name, matchedRuleName = rule.name)
                }
            }
        }

        // どのルールにもマッチしなければ、デフォルトは許可
        return BlockResult(shouldBlock = false)
    }

    private fun isRuleMatched(rule: BlockRule, uniqueList: List<String>): Boolean {
        if (rule.conditions.isEmpty()) return false
        
        Log.d("BlockRepo", "判定中のルール: ${rule.name} (許可=${rule.isAllowRule})")
        
        // すべての条件を満たす必要がある (AND条件)
        for (condition in rule.conditions) {
            val condMatched = when (condition) {
                is RegexCondition -> {
                    val pattern = try { Pattern.compile(condition.pattern) } catch(e:Exception){ null }
                    pattern?.let { p ->
                        uniqueList.any { p.matcher(it).find() }
                    } ?: false
                }
                is ContactCondition -> {
                    val hasContact = uniqueList.any { isContactExists(it) }
                    hasContact
                }
                is TimeCondition -> {
                     isTimeMatched(condition)
                }
                else -> false
            }
            
            // 条件の反転 (NOT条件) 処理
            val finalMatched = if (condition.isInverse) !condMatched else condMatched
            
            Log.d("BlockRepo", "  条件判定 [${condition.getDescription()}]: マッチ=$condMatched, 反転=${condition.isInverse} -> 結果=$finalMatched")
            
            if (!finalMatched) {
                Log.d("BlockRepo", "  -> 不適合")
                return false
            }
        }
        Log.d("BlockRepo", "  -> 適合")
        return true
    }

    private fun isTimeMatched(condition: TimeCondition): Boolean {
        val current = Calendar.getInstance()
        
        // 1. Check Date Range
        if (condition.startDate != null || condition.endDate != null) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = sdf.format(current.time)
            
            // String comparison works for yyyy-MM-dd
            if (condition.startDate != null && todayStr < condition.startDate) return false
            if (condition.endDate != null && todayStr > condition.endDate) return false
        }
        
        // 2. Check Day of Week
        if (condition.daysOfWeek.isNotEmpty()) {
            val dayOfWeek = current.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek !in condition.daysOfWeek) return false
        }
        
        // 3. Check Time Range
        if (condition.startHour != null && condition.startMinute != null && 
            condition.endHour != null && condition.endMinute != null) {
            
            val currentMinutes = current.get(Calendar.HOUR_OF_DAY) * 60 + current.get(Calendar.MINUTE)
            val startMinutes = condition.startHour * 60 + condition.startMinute
            val endMinutes = condition.endHour * 60 + condition.endMinute
            
            if (startMinutes <= endMinutes) {
                 if (currentMinutes < startMinutes || currentMinutes > endMinutes) return false
            } else {
                 // Overnight (e.g. 22:00 - 06:00)
                 // Valid if >= 22:00 OR <= 06:00
                 if (currentMinutes < startMinutes && currentMinutes > endMinutes) return false
            }
        }
        
        return true
    }

    private fun isContactExists(number: String): Boolean {
         if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != 
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return false 
        }

        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(ContactsContract.PhoneLookup._ID)
        
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) return true
            }
        } catch (e: Exception) {
            Log.e("BlockRuleRepository", "Error checking contact", e)
        }
        return false
    }
}
