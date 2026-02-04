package net.ysksg.callblocker.data

import java.util.UUID

data class BlockRule(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val conditions: MutableList<RuleCondition> = mutableListOf(),
    var isEnabled: Boolean = true,
    var isAllowRule: Boolean = false // trueならホワイトリストルール(許可ルール)
)

interface RuleCondition {
    val type: String // "regex", "contact"
    val isInverse: Boolean // NOT条件
    fun getDescription(): String
}

data class RegexCondition(
    val pattern: String,
    override val isInverse: Boolean = false
) : RuleCondition {
    override val type = "regex"
    override fun getDescription(): String {
        return if (isInverse) "正規表現(不一致): $pattern" else "正規表現(一致): $pattern"
    }
}

// CountryCodeCondition removed as per user request (use Regex instead)

data class ContactCondition(
    override val isInverse: Boolean = false // false=登録済み, true=未登録
) : RuleCondition {
    override val type = "contact"
    override fun getDescription(): String {
        return if (isInverse) "連絡先に登録されていない" else "連絡先に登録されている"
    }
}

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
