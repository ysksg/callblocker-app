package net.ysksg.callblocker.data

import java.util.UUID

/**
 * 着信拒否・許可のルール定義。
 */
data class BlockRule(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val conditions: MutableList<RuleCondition> = mutableListOf(),
    var isEnabled: Boolean = true,
    var isAllowRule: Boolean = false // trueなら許可リスト(ホワイトリスト)のルールとして扱う
)

/**
 * ルールの条件を表すインターフェース。
 */
interface RuleCondition {
    val type: String // "regex" (正規表現), "contact" (連絡先), "ai" (AI判定)
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
        return if (isInverse) "正規表現(不一致): $pattern" else "正規表現(一致): $pattern"
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
        return if (isInverse) "連絡先に登録されていない" else "連絡先に登録されている"
    }
}

/**
 * AI解析結果のキーワードによる条件。
 */
data class AiCondition(
    val keyword: String,
    override val isInverse: Boolean = false
) : RuleCondition {
    override val type = "ai"
    override fun getDescription(): String {
        val action = if (isInverse) "含まない" else "含む"
        return "AI解析結果に「$keyword」を$action"
    }
}
