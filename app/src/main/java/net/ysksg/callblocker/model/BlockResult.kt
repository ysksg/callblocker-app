package net.ysksg.callblocker.model

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
    val matchedRuleName: String? = null,
    val ruleAction: RuleAction = RuleAction.REJECT
)
