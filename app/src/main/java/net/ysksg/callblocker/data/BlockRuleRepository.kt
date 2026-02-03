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

data class BlockResult(
    val shouldBlock: Boolean,
    val reason: String? = null,
    val matchedRuleName: String? = null
)

class BlockRuleRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("block_rules_v3", Context.MODE_PRIVATE) // Version bumped for breaking changes

    // ルールリストの保存と読み出し
    fun getRules(): List<BlockRule> {
        val jsonString = prefs.getString("rules_json", "[]") ?: "[]"
        return parseRulesJson(jsonString)
    }

    fun saveRule(rule: BlockRule) {
        val currentRules = getRules().toMutableList()
        val index = currentRules.indexOfFirst { it.id == rule.id }
        if (index >= 0) {
            currentRules[index] = rule
        } else {
            // 新規ルールは先頭に追加するか、末尾に追加するか。
            // 評価順序が重要になったため、ユーザーが意図を把握しやすいよう末尾に追加するのが自然。
            currentRules.add(rule)
        }
        saveRulesJson(currentRules)
    }

    fun deleteRule(ruleId: String) {
        val currentRules = getRules().toMutableList()
        currentRules.removeAll { it.id == ruleId }
        saveRulesJson(currentRules)
    }
    
    fun swapRules(fromPosition: Int, toPosition: Int) {
        val currentRules = getRules().toMutableList()
        if (fromPosition in currentRules.indices && toPosition in currentRules.indices) {
            Collections.swap(currentRules, fromPosition, toPosition)
            saveRulesJson(currentRules)
        }
    }

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
                            // No extra properties
                        }
                        // CountryCodeCondition is removed
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
                            // Migration logic: old "isRegistered"
                            // if isRegistered exists and is false -> isInverse = true
                            // else use loaded isInverse
                            var effectiveInverse = isInverse
                            if (condObj.has("isRegistered")) {
                                val isRegistered = condObj.getBoolean("isRegistered")
                                if (!isRegistered) effectiveInverse = true
                            }
                            rule.conditions.add(ContactCondition(effectiveInverse))
                        }
                        "country" -> {
                            // Convert to Regex if possible, or skip
                            // User requested removal, but converting to regex keeps functionality.
                            // For simplicity and per request to remove, we will just SKIP loading country rules.
                            // Or better, convert to a Regex that likely matches nothing or warn user.
                            // Here we just skip it, effectively deleting the condition.
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

    fun checkBlock(number: String): BlockResult {
        // ハイフン除去などの単純正規化
        val normalizedNumber = number.replace(Regex("[^0-9+]"), "")
        val phoneUtil = PhoneNumberUtil.getInstance()

        // マッチング候補リスト作成
        val formattedList = mutableListOf<String>()
        formattedList.add(number) // 元の入力
        formattedList.add(normalizedNumber) // 記号除去

        try {
            // libphonenumber で解析
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
        // 以前のように allow/block で分けず、リスト順に評価する
        val allRules = getRules().filter { it.isEnabled }
        
        for (rule in allRules) {
            if (isRuleMatched(rule, uniqueList)) {
                // 返り値：マッチしたルールが「許可ルール」ならブロックしない(false)
                // 「拒否ルール」ならブロックする(true)
                if (rule.isAllowRule) {
                    return BlockResult(shouldBlock = false, matchedRuleName = rule.name)
                } else {
                    return BlockResult(shouldBlock = true, reason = rule.name, matchedRuleName = rule.name)
                }
            }
        }

        // どのルールにもマッチしなければ、デフォルトは許可 (ブロックしない)
        return BlockResult(shouldBlock = false)
    }

    private fun isRuleMatched(rule: BlockRule, uniqueList: List<String>): Boolean {
        if (rule.conditions.isEmpty()) return false
        
        // すべての条件を満たす必要がある (AND)
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
                else -> false
            }
            
            // isInverse (NOT条件) の処理
            // condition.isInverse == true なら、condMatched == false であれば「条件成立」
            val finalMatched = if (condition.isInverse) !condMatched else condMatched
            
            if (!finalMatched) return false
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
