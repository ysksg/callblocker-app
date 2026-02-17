package net.ysksg.callblocker.ui.rules

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.ysksg.callblocker.model.BlockRule
import net.ysksg.callblocker.ui.common.EmptyListMessage

/**
 * ルール一覧を表示・管理する画面。
 * RecyclerViewを使用してドラッグ＆ドロップによる並び替えをサポートしています。
 */
@Composable
fun RuleListScreen(
    rules: List<BlockRule>,
    onUpdateRule: (BlockRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onCreateRule: (BlockRule) -> Unit,
    onSwapRules: (List<BlockRule>) -> Unit
) {
    var ruleToDelete by remember { mutableStateOf<BlockRule?>(null) } // 削除確認ダイアログ用
    var showDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<BlockRule?>(null) }
    var defaultIsAllowForNew by remember { mutableStateOf(false) }

    val currentRules = rememberUpdatedState(rules)

    // RecyclerView用アダプター
    val adapter = remember { 
        RuleAdapter(
            initialRules = rules.toMutableList(),
            onUpdateRule = onUpdateRule,
            onDeleteRule = { ruleId ->
                // 直接削除せず確認ダイアログを表示
                // 最新のリストから検索
                val rule = currentRules.value.find { it.id == ruleId }
                if (rule != null) {
                    ruleToDelete = rule
                }
            },
            onEditRule = { rule ->
                editingRule = rule
                showDialog = true
            },
            onSwapRules = onSwapRules
        ) 
    }

    // 外部からのルール変更をアダプターに同期
    SideEffect {
        adapter.updateRules(rules)
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val horizontalPaddingPx = remember(density) { with(density) { 16.dp.roundToPx() } }
    val bottomPaddingPx = remember(density) { with(density) { 0.dp.roundToPx() } }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { 
                    editingRule = null
                    defaultIsAllowForNew = false
                    showDialog = true 
                },
                icon = { Icon(Icons.Default.Add, "Add") },
                text = { Text("ルール追加") },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        },
        topBar = {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "ルールは上から順に評価されます。\n長押し・ドラッグで並べ替えが可能です。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    ) { padding ->
        Column(
             modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (rules.isEmpty()) {
                EmptyListMessage("ルールはありません。")
            } else {
                 AndroidView(
                    factory = { context ->
                        RecyclerView(context).apply {
                            layoutManager = LinearLayoutManager(context)
                            this.adapter = adapter
                            val callback = RuleTouchHelperCallback(adapter)
                            val touchHelper = ItemTouchHelper(callback)
                            touchHelper.attachToRecyclerView(this)
                            
                            // パディングの設定 (端までスクロールさせるが、ヘッダー/フッター部分は透過させるなど)
                            clipToPadding = true
                            setPadding(horizontalPaddingPx, 0, horizontalPaddingPx, bottomPaddingPx) 
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    update = { recyclerView ->
                        // 必要に応じて動的に更新
                    }
                )
            }
        }
    }

    // 編集・作成ダイアログ
    if (showDialog) {
        RuleEditDialog(
            initialRule = editingRule,
            defaultIsAllow = defaultIsAllowForNew,
            onDismiss = { showDialog = false },
            onSave = { 
                if (editingRule == null) onCreateRule(it) else onUpdateRule(it)
                showDialog = false 
            }
        )
    }

    // 削除確認ダイアログ
    if (ruleToDelete != null) {
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("ルールの削除") },
            text = { Text("「${ruleToDelete?.name}」を削除してもよろしいですか？") },
            confirmButton = {
                Button(
                    onClick = {
                        ruleToDelete?.let { onDeleteRule(it.id) }
                        ruleToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) { Text("キャンセル") }
            }
        )
    }
}
