package net.ysksg.callblocker.ui.history

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.ysksg.callblocker.repository.BlockHistory
import net.ysksg.callblocker.ui.common.EmptyListMessage
import net.ysksg.callblocker.util.PhoneNumberFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 着信履歴を表示する画面。
 * AI解析やWeb検索、履歴の削除機能を提供します。
 */
@Composable
fun HistoryScreen(
    history: List<BlockHistory>,
    loadingItems: List<Long>,
    onClearHistory: () -> Unit,
    onAnalyze: (BlockHistory) -> Unit,
    onWebSearch: (BlockHistory) -> Unit
) {
    var showDeleteHistoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Box(
                 modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Text("着信履歴", style = MaterialTheme.typography.titleLarge)
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                showDeleteHistoryDialog = true
            }) {
                Icon(Icons.Default.Delete, contentDescription = "Clear History")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
             LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp), // Remove top padding as it's handled by topBar
                modifier = Modifier.fillMaxSize()
             ) {
                if (history.isEmpty()) {
                    item { EmptyListMessage("履歴はありません") }
                }
                items(history) { item ->
                    HistoryItemCard(
                        item = item,
                        isAnalyzing = loadingItems.contains(item.timestamp),
                        onAnalyze = { onAnalyze(item) },
                        onWebSearch = { onWebSearch(item) }
                    )
                }
            }
        }

        if (showDeleteHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteHistoryDialog = false },
                title = { Text("履歴の削除") },
                text = { Text("着信履歴をすべて削除してもよろしいですか？\nこの操作は元に戻せません。") },
                confirmButton = {
                    Button(
                        onClick = {
                            onClearHistory()
                            showDeleteHistoryDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("削除") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteHistoryDialog = false }) { Text("キャンセル") }
                }
            )
        }
    }
}

@Composable
fun HistoryItemCard(
    item: BlockHistory,
    isAnalyzing: Boolean,
    onAnalyze: () -> Unit,
    onWebSearch: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { isExpanded = !isExpanded },
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = PhoneNumberFormatter.format(item.number),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (item.reason != null) {
                        val isAllowed = item.reason == "許可"
                        val reasonColor = if (isAllowed) Color(0xFF00C853) else MaterialTheme.colorScheme.error
                        Text("理由: ${item.reason}", style = MaterialTheme.typography.bodyMedium, color = reasonColor)
                    }
                    if (item.aiResult != null) {
                        Text("AI: ${item.aiResult}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ダイヤルボタン
                        val context = LocalContext.current
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FilledIconButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:${item.number}")
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.size(48.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Dial")
                            }
                            Text("ダイヤル", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                        }

                        // Web検索ボタン
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            OutlinedIconButton(
                                onClick = onWebSearch,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Web Search")
                            }
                            Text("Web検索", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                        }

                        // AI解析ボタン
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            OutlinedIconButton(
                                onClick = { if (!isAnalyzing) onAnalyze() },
                                modifier = Modifier.size(48.dp),
                                enabled = !isAnalyzing
                            ) {
                                if (isAnalyzing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Star, contentDescription = "AI Analyze")
                                }
                            }
                            Text("AI解析", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
