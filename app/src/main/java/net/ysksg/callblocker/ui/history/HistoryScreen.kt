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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
    isAiEnabled: Boolean,
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
                        isAiEnabled = isAiEnabled,
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
    isAiEnabled: Boolean,
    onAnalyze: () -> Unit,
    onWebSearch: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    // Logic for determining status color (Green for Allowed, Red for Blocked/Unknown)
    val isAllowed = item.reason == "許可"
    val stateColor = if (isAllowed) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 4.dp)
            .clickable { isExpanded = !isExpanded },
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
                            imageVector = if (isAllowed) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = if (isAllowed) "Allowed" else "Blocked",
                            tint = stateColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Text Content
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = PhoneNumberFormatter.format(item.number),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        Spacer(modifier = Modifier.height(2.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(item.timestamp)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (item.reason != null) {
                                Text(
                                    text = " • ${item.reason}", 
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isAllowed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        
                        if (item.aiResult != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "AI: ${item.aiResult}", 
                                style = MaterialTheme.typography.bodySmall, 
                                color = MaterialTheme.colorScheme.tertiary, 
                                fontWeight = FontWeight.Bold
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
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dial Button
                        val context = LocalContext.current
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${item.number}")
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("発信")
                        }

                        // Web Search Button
                        TextButton(onClick = onWebSearch) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("検索")
                        }

                        // AI Analyze Button
                        TextButton(
                            onClick = { if (!isAnalyzing && isAiEnabled) onAnalyze() },
                            enabled = !isAnalyzing && isAiEnabled,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if(isAiEnabled) MaterialTheme.colorScheme.tertiary else Color.Gray
                            )
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI解析")
                        }
                    }
                }
            }
        }
    }
}
