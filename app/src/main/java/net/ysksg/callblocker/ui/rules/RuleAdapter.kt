package net.ysksg.callblocker.ui.rules

import androidx.compose.ui.platform.ComposeView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import net.ysksg.callblocker.model.BlockRule
import java.util.Collections

/**
 * ルールリストを表示するためのRecyclerViewアダプター。
 * ComposeViewをViewHolderとして利用し、リストのパフォーマンスとドラッグ＆ドロップ機能を提供します。
 *
 * @param initialRules 初期ルールのリスト
 * @param onUpdateRule ルール更新時のコールバック
 * @param onDeleteRule ルール削除時のコールバック
 * @param onEditRule ルール編集時のコールバック
 * @param onSwapRules ドラッグによる並び替え完了時のコールバック
 */
class RuleAdapter(
    initialRules: MutableList<BlockRule>,
    private val onUpdateRule: (BlockRule) -> Unit,
    private val onDeleteRule: (String) -> Unit,
    private val onEditRule: (BlockRule) -> Unit,
    private val onSwapRules: (List<BlockRule>) -> Unit
) : RecyclerView.Adapter<RuleAdapter.RuleViewHolder>() {

    private val rules = initialRules

    /**
     * ルールリストを更新し、差分計算を行ってアニメーション付きで反映します。
     */
    fun updateRules(newRules: List<BlockRule>) {
        val diffCallback = RuleDiffCallback(rules, newRules)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        
        rules.clear()
        rules.addAll(newRules)
        diffResult.dispatchUpdatesTo(this)
    }

    inner class RuleViewHolder(val composeView: ComposeView) : RecyclerView.ViewHolder(composeView) {
        fun bind(rule: BlockRule) {
            composeView.setContent {
                RuleCard(
                    rule = rule,
                    index = bindingAdapterPosition,
                    totalCount = itemCount,
                    onToggle = { 
                        val updated = rule.copy(isEnabled = !rule.isEnabled)
                        onUpdateRule(updated)
                    },
                    onEdit = { onEditRule(rule) },
                    onDelete = { onDeleteRule(rule.id) }
                )
            }
        }
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RuleViewHolder {
        val composeView = ComposeView(parent.context).apply {
             layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return RuleViewHolder(composeView)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        val rule = rules[position]
        holder.bind(rule)
    }

    override fun onViewRecycled(holder: RuleViewHolder) {
        holder.composeView.disposeComposition()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = rules.size

    /**
     * アイテムを移動します（ドラッグ中）。
     */
    fun moveItem(from: Int, to: Int) {
        if (from < to) {
            for (i in from until to) {
                Collections.swap(rules, i, i + 1)
            }
        } else {
            for (i in from downTo to + 1) {
                Collections.swap(rules, i, i - 1)
            }
        }
        notifyItemMoved(from, to)
    }

    /**
     * ドラッグ完了時に呼び出され、最終的な順序を保存します。
     */
    fun onDragComplete() {
        onSwapRules(rules.toList())
    }
    /**
     * スワイプによる削除処理。
     */
    fun onItemDismiss(position: Int) {
        if (position in rules.indices) {
            onDeleteRule(rules[position].id)
            notifyItemChanged(position) 
        }
    }
}

/**
 * ルールリストの差分計算用コールバック。
 */
class RuleDiffCallback(
    private val oldList: List<BlockRule>,
    private val newList: List<BlockRule>
) : DiffUtil.Callback() {

    override fun getOldListSize(): Int = oldList.size
    override fun getNewListSize(): Int = newList.size

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // IDで同一性を判定
        return oldList[oldItemPosition].id == newList[newItemPosition].id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        // データクラスのequalsを利用して内容の一致を判定
        return oldList[oldItemPosition] == newList[newItemPosition]
    }
}

/**
 * ドラッグ＆ドロップ操作を処理するコールバック。
 */
class RuleTouchHelperCallback(private val adapter: RuleAdapter) : ItemTouchHelper.Callback() {
    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        val swipeFlags = ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT // 左右スワイプを有効化
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        adapter.onItemDismiss(viewHolder.bindingAdapterPosition)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        adapter.onDragComplete()
        
        // ドラッグ中の見た目をリセット
        viewHolder.itemView.alpha = 1.0f
    }
    
    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        // ドラッグ開始時に少し透明にする
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            viewHolder?.itemView?.alpha = 0.7f
        }
    }
}
