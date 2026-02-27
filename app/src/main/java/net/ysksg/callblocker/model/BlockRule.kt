package net.ysksg.callblocker.model

import java.util.UUID

/**
 * ルールに合致した時のアクション
 */
enum class RuleAction {
    REJECT,  // 拒否 (切断)
    SILENCE  // 無音化 (呼び出しのみ停止)
}

/**
 * 着信拒否・許可のルール定義。
 */
data class BlockRule(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val conditions: MutableList<RuleCondition> = mutableListOf(),
    var isEnabled: Boolean = true,
    var isAllowRule: Boolean = false, // trueなら許可リスト(ホワイトリスト)のルールとして扱う
    var ruleAction: RuleAction = RuleAction.REJECT  // 拒否時のデフォルトアクション
)

/**
 * ルールの条件を表すインターフェース。
 */
interface RuleCondition {
    val type: String // "regex" (正規表現), "contact" (連絡先)
    val isInverse: Boolean // 条件を反転するか (NOT条件)
    fun getDescription(): String
}

/**
 * 正規表現による条件。
 */
data class RegexCondition(
    val pattern: String,
    override val isInverse: Boolean = false
) : RuleCondition {
    override val type = "regex"
    override fun getDescription(): String {
        return (if (isInverse) "【以外】 " else "") + "正規表現: $pattern"
    }
}

/**
 * 連絡先の登録有無による条件。
 */
data class ContactCondition(
    override val isInverse: Boolean = false // false=登録済み, true=未登録
) : RuleCondition {
    override val type = "contact"
    override fun getDescription(): String {
        return (if (isInverse) "【以外】 " else "") + "連絡先: 登録済み"
    }
}

// AiCondition removed

/**
 * 日時・曜日による条件。
 * 指定された期間、時間帯、曜日のすべてに合致する場合にマッチします。
 * 各項目が null または空の場合は「指定なし（常にマッチ）」として扱います。
 */
data class TimeCondition(
    val startDate: String? = null, // "yyyy-MM-dd"
    val endDate: String? = null,   // "yyyy-MM-dd"
    val startHour: Int? = null,
    val startMinute: Int? = null,
    val endHour: Int? = null,
    val endMinute: Int? = null,
    val daysOfWeek: List<Int> = emptyList(), // Calendar.SUNDAY(1) ... SATURDAY(7)
    override val isInverse: Boolean = false
) : RuleCondition {
    override val type = "time"
    override fun getDescription(): String {
        val parts = mutableListOf<String>()
        
        // Date
        if (startDate != null || endDate != null) {
            val s = startDate?.replace("-", "/") ?: "..."
            val e = endDate?.replace("-", "/") ?: "..."
            parts.add("期間:$s～$e")
        }
        
        // Days
        if (daysOfWeek.isNotEmpty()) {
            val days = daysOfWeek.map { 
                when(it) {
                    java.util.Calendar.SUNDAY -> "日"
                    java.util.Calendar.MONDAY -> "月"
                    java.util.Calendar.TUESDAY -> "火"
                    java.util.Calendar.WEDNESDAY -> "水"
                    java.util.Calendar.THURSDAY -> "木"
                    java.util.Calendar.FRIDAY -> "金"
                    java.util.Calendar.SATURDAY -> "土"
                    else -> "?"
                }
            }.joinToString("")
            parts.add("曜日:$days")
        }
        
        // Time
        if (startHour != null && startMinute != null && endHour != null && endMinute != null) {
            parts.add(String.format("時間:%02d:%02d-%02d:%02d", startHour, startMinute, endHour, endMinute))
        }
        
        if (parts.isEmpty()) {
            parts.add("日時指定なし")
        }
        
        val body = parts.joinToString(" / ")
        return if (isInverse) "【以外】 $body" else body
    }
}
