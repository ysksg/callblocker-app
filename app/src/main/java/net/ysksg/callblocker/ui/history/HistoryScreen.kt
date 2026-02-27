package net.ysksg.callblocker.ui.history

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.combinedClickable
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import android.provider.CallLog
import net.ysksg.callblocker.repository.BlockHistory
import net.ysksg.callblocker.ui.common.EmptyListMessage
import net.ysksg.callblocker.util.PhoneNumberFormatter
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 着信履歴を表示する画面。
 * AI解析やWeb検索、履歴の削除機能を提供します。
 */
@Composable

fun HistoryScreen(
    history: List<BlockHistory>,
    loadingItems: List<Long>,
    isAiEnabled: Boolean,
    onClearHistory: () -> Unit,
    onAnalyze: (String, Long) -> Unit,
    onWebSearch: (String) -> Unit,
    onAddToRule: (String) -> Unit
) {
    var showDeleteHistoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Column(
                 modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 16.dp)
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
                contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp),
                modifier = Modifier.fillMaxSize()
             ) {
                // App History
                if (history.isEmpty()) {
                    item { EmptyListMessage("履歴はありません") }
                }
                items(history) { item ->
                    HistoryItemCard(
                        number = item.number,
                        timestamp = item.timestamp,
                        reason = item.reason,
                        aiResult = item.aiResult,
                        aiStatus = item.aiStatus,
                        blockType = item.blockType,
                        isAnalyzing = loadingItems.contains(item.timestamp),
                        isAiEnabled = isAiEnabled,
                        onAnalyze = { onAnalyze(item.number, item.timestamp) },
                        onWebSearch = { onWebSearch(item.number) },
                        onAddToRule = { onAddToRule(item.number) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItemCard(
    number: String,
    timestamp: Long,
    reason: String?,
    aiResult: String?,
    aiStatus: net.ysksg.callblocker.repository.AiStatus,
    blockType: net.ysksg.callblocker.repository.BlockType,
    isAnalyzing: Boolean,
    isAiEnabled: Boolean,
    onAnalyze: () -> Unit,
    onWebSearch: () -> Unit,
    onAddToRule: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isExpanded by remember { mutableStateOf(false) }
    
    // Logic for determining status color (Green for Allowed, Red for Blocked, Orange for Silenced)
    val stateColor = when (blockType) {
        net.ysksg.callblocker.repository.BlockType.ALLOWED -> Color(0xFF4CAF50)
        net.ysksg.callblocker.repository.BlockType.SILENCED -> Color(0xFFFF9800) // Orange
        net.ysksg.callblocker.repository.BlockType.REJECTED -> MaterialTheme.colorScheme.error
    }
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 4.dp)
            .combinedClickable(
                onClick = { isExpanded = !isExpanded },
                onLongClick = {
                    clipboardManager.setText(AnnotatedString(number))
                    Toast.makeText(context, "コピーしました: ${number}", Toast.LENGTH_SHORT).show()
                }
            ),
        elevation = CardDefaults.cardElevation(1.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column {
            // Main Row (Always visible)
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                // Left Accent Bar
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(6.dp)
                        .background(stateColor)
                )

                // Content
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Leading Icon (Status)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(stateColor.copy(alpha = 0.1f), androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (blockType) {
                                net.ysksg.callblocker.repository.BlockType.ALLOWED -> Icons.Default.Check
                                net.ysksg.callblocker.repository.BlockType.SILENCED -> Icons.Default.Call // Consider a proper silent icon if available
                                else -> Icons.Default.Close
                            },
                            contentDescription = blockType.name,
                            tint = stateColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Text Content
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = PhoneNumberFormatter.format(number),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(
                                    Date(timestamp)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (reason != null) {
                                Text(
                                    text = " • $reason", 
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (blockType == net.ysksg.callblocker.repository.BlockType.ALLOWED) MaterialTheme.colorScheme.onSurfaceVariant else stateColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // AI Status Indicator
                        if (isAiEnabled && number.isNotEmpty()) {
                            val aiStatusColor = when (aiStatus) {
                                net.ysksg.callblocker.repository.AiStatus.SUCCESS -> MaterialTheme.colorScheme.tertiary
                                net.ysksg.callblocker.repository.AiStatus.ERROR -> MaterialTheme.colorScheme.error
                                net.ysksg.callblocker.repository.AiStatus.PENDING -> Color.Gray
                                else -> Color.Gray
                            }
                            Text(
                                text = "AI: ${aiResult ?: "未解析"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = aiStatusColor,
                                fontWeight = if (aiStatus == net.ysksg.callblocker.repository.AiStatus.SUCCESS) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                    
                    // Expand/Collapse Indicator (Optional, but good for UX)
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded Actions
            androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dial Button
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:$number")
                                }
                                context.startActivity(intent)
                            },
                            enabled = number.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("発信")
                        }

                        // Web Search Button
                        TextButton(
                            onClick = onWebSearch,
                            enabled = number.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("検索")
                        }

                        // AI Analyze Button
                        TextButton(
                            onClick = { if (!isAnalyzing && isAiEnabled) onAnalyze() },
                            enabled = !isAnalyzing && isAiEnabled && number.isNotEmpty(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if(isAiEnabled && number.isNotEmpty()) MaterialTheme.colorScheme.tertiary else Color.Gray
                            )
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AI解析")
                        }

                        // Add to Rule Button
                        TextButton(onClick = onAddToRule) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ルール登録")
                        }
                    }
                }
            }
        }
    }
}
