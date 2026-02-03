package net.ysksg.callblocker

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import net.ysksg.callblocker.data.*
import net.ysksg.callblocker.ui.theme.CallBlockerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CallBlockerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val repository = remember { BlockRuleRepository(context) }
    val historyRepo = remember { BlockHistoryRepository(context) }
    val geminiRepo = remember { GeminiRepository(context) }

    // State
    var isOverlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isCallScreeningGranted by remember { mutableStateOf(checkCallScreeningRole(context)) }
    var isContactPermissionGranted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    var isPhoneStatePermissionGranted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    var isNotificationPermissionGranted by remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mutableStateOf(context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        } else {
            mutableStateOf(true)
        }
    }

    var rules by remember { mutableStateOf(repository.getRules()) }
    var selectedTab by remember { mutableStateOf(0) }
    var blockHistory by remember { mutableStateOf(historyRepo.getHistory()) }
    var showRuleTestDialog by remember { mutableStateOf(false) }
    var showDeleteHistoryDialog by remember { mutableStateOf(false) }
    val loadingItems = remember { mutableStateListOf<Long>() }

    // Permission Launchers
    val overlayPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isOverlayGranted = Settings.canDrawOverlays(context) }
    val callScreeningLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isCallScreeningGranted = checkCallScreeningRole(context) }
    val contactPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isContactPermissionGranted = it }
    val phoneStatePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isPhoneStatePermissionGranted = it }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isNotificationPermissionGranted = it }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "設定") },
                    label = { Text("ルール") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "履歴") },
                    label = { Text("履歴") },
                    selected = selectedTab == 1,
                    onClick = {
                        blockHistory = historyRepo.getHistory()
                        selectedTab = 1
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Build, contentDescription = "アプリ設定") },
                    label = { Text("設定") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 1) { // 履歴タブのFAB
                FloatingActionButton(onClick = {
                    showDeleteHistoryDialog = true
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear History")
                }
            }
        }
    ) { innerPadding ->
        if (showDeleteHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteHistoryDialog = false },
                title = { Text("履歴の削除") },
                text = { Text("着信履歴をすべて削除してもよろしいですか？\nこの操作は元に戻せません。") },
                confirmButton = {
                    Button(
                        onClick = {
                            historyRepo.clearHistory()
                            blockHistory = emptyList()
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

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (selectedTab == 0) {
                 Column(modifier = Modifier.padding(16.dp)) {
                    // 権限ステータスエリア
                    if (!isOverlayGranted || !isCallScreeningGranted || !isContactPermissionGranted || !isPhoneStatePermissionGranted || !isNotificationPermissionGranted) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("必要な権限が不足しています", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                                Row(modifier = Modifier.wrapContentSize().horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                                    if (!isOverlayGranted) Button(onClick = { overlayPermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }, modifier = Modifier.padding(end=4.dp)) { Text("オーバーレイ") }
                                    if (!isCallScreeningGranted) Button(onClick = { requestCallScreeningRole(context as Activity, callScreeningLauncher) }, modifier = Modifier.padding(end=4.dp)) { Text("着信ブロック") }
                                    if (!isContactPermissionGranted) Button(onClick = { contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) }, modifier = Modifier.padding(end=4.dp)) { Text("連絡先") }
                                    if (!isPhoneStatePermissionGranted) Button(onClick = { phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE) }, modifier = Modifier.padding(end=4.dp)) { Text("電話状態") }
                                    if (!isNotificationPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Button(onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("通知") }
                                }
                            }
                        }
                    }
                    
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom=8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ブロッキングルール", style = MaterialTheme.typography.titleLarge)
                        OutlinedButton(onClick = { showRuleTestDialog = true }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("テスト")
                        }
                    }

                    // Rule Settings with Drag & Drop
                    RuleSettingsScreen(
                        rules = rules,
                        onUpdateRule = { updatedRule ->
                            repository.saveRule(updatedRule)
                            rules = repository.getRules()
                        },
                        onDeleteRule = { ruleId ->
                            repository.deleteRule(ruleId)
                            rules = repository.getRules()
                        },
                        onCreateRule = { newRule ->
                             repository.saveRule(newRule)
                             rules = repository.getRules()
                        },
                        onSwapRules = {
                             repository.saveRules(it) // Save reordered list
                             rules = repository.getRules()
                        }
                    )
                 }
            } else if (selectedTab == 1) {
                // --- 履歴画面 ---
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    item { 
                        Text("着信履歴", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (blockHistory.isEmpty()) {
                        item { EmptyStateMessage("履歴はありません") }
                    }
                    items(blockHistory) { item ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = CardDefaults.cardElevation(2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = net.ysksg.callblocker.util.PhoneNumberFormatter.format(item.number),
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        text = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(item.timestamp)),
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
                                
                                // Loading State for AI Analysis
                                val isAnalyzing = loadingItems.contains(item.timestamp)
                                
                                OutlinedButton(
                                    onClick = {
                                        if (isAnalyzing) return@OutlinedButton
                                        
                                        loadingItems.add(item.timestamp)
                                        val coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                                        coroutineScope.launch {
                                            try {
                                                val result = geminiRepo.checkPhoneNumber(item.number)
                                                historyRepo.updateHistory(item.timestamp, result)
                                                // Refresh list on Main thread
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    blockHistory = historyRepo.getHistory()
                                                    loadingItems.remove(item.timestamp)
                                                }
                                            } catch (e: Exception) {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    loadingItems.remove(item.timestamp)
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(end = 4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    enabled = !isAnalyzing // Optional: disable user interaction visually or just logic
                                ) {
                                    if (isAnalyzing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    } else {
                                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("AI", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                
                                OutlinedButton(
                                    onClick = {
                                        val template = geminiRepo.getSearchUrlTemplate()
                                        val url = template.replace("{number}", item.number)
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            // Ignore
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Web", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            } else {
                // --- アプリ設定画面 ---
                net.ysksg.callblocker.ui.AppSettingsScreen()
            }
        }
    }

    if (showRuleTestDialog) {
        net.ysksg.callblocker.ui.RuleTestDialog(
            repository = repository,
            onDismiss = { showRuleTestDialog = false }
        )
    }
}

// Block Rule Presets (Same as before)
val BLOCK_PRESETS = listOf(
    "連絡先に登録されている番号" to "CONTACT_REGISTERED",
    "連絡先に登録されていない番号" to "CONTACT_NOT_REGISTERED",
    "海外からの着信" to "^\\+(?!81).*",
    "ナビダイヤル (0570)" to "^0570.*",
    "非通知・番号不明" to "^(Unknown|Private|)$",
    "フリーダイヤル (0120)" to "^0120.*",
    "フリーダイヤル (0800)" to "^0800.*",
    "IP電話 (050)" to "^050.*",
    "携帯電話 (090/080/070)" to "^(090|080|070).*",
    "東京 (03)" to "^03.*",
    "大阪 (06)" to "^06.*",
    "札幌 (011)" to "^011.*",
    "横浜 (045)" to "^045.*",
    "名古屋 (052)" to "^052.*",
    "福岡 (092)" to "^092.*",
    "国際プレフィックス (010)" to "^010.*"
)

@Composable
fun RuleSettingsScreen(
    rules: List<BlockRule>,
    onUpdateRule: (BlockRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onCreateRule: (BlockRule) -> Unit,
    onSwapRules: (List<BlockRule>) -> Unit
) {
    var ruleToDelete by remember { mutableStateOf<BlockRule?>(null) } // Confirmation dialog state
    var showDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<BlockRule?>(null) }
    var defaultIsAllowForNew by remember { mutableStateOf(false) }

    // Reorder Logic States
    // State for RecyclerView
    // We use a remembered lambda to hold the adapter instance to survive recompositions but update its callbacks
    val adapter = remember { 
        RuleAdapter(
            initialRules = rules.toMutableList(),
            onUpdateRule = onUpdateRule,
            onDeleteRule = { ruleId ->
                // Trigger confirmation dialog instead of direct delete
                val rule = rules.find { it.id == ruleId }
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

    // Sync adapter data when external rules change (e.g. added/deleted/toggled)
    LaunchedEffect(rules) {
        adapter.updateRules(rules)
    }

    Scaffold(
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
        }
    ) { padding ->
        Column(
             modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ルールは上から順に評価されます。\n長押ししてドラッグで並べ替えられます。", style = MaterialTheme.typography.bodySmall)
                }
            }
            
            if (rules.isEmpty()) {
                EmptyStateMessage("ルールはありません。")
            } else {
                 AndroidView(
                    factory = { context ->
                        RecyclerView(context).apply {
                            layoutManager = LinearLayoutManager(context)
                            this.adapter = adapter
                            val callback = RuleTouchHelperCallback(adapter)
                            val touchHelper = ItemTouchHelper(callback)
                            touchHelper.attachToRecyclerView(this)
                        }
                    },
                    modifier = Modifier.fillMaxSize().padding(bottom=80.dp)
                )
            }
        }
    }

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

// RecyclerView Adapter and Helper Classes

class RuleAdapter(
    initialRules: MutableList<BlockRule>,
    private val onUpdateRule: (BlockRule) -> Unit,
    private val onDeleteRule: (String) -> Unit,
    private val onEditRule: (BlockRule) -> Unit,
    private val onSwapRules: (List<BlockRule>) -> Unit
) : RecyclerView.Adapter<RuleAdapter.RuleViewHolder>() {

    private val rules = initialRules

    fun updateRules(newRules: List<BlockRule>) {
        if (rules != newRules) {
            rules.clear()
            rules.addAll(newRules)
            notifyDataSetChanged()
        }
    }

    class RuleViewHolder(val composeView: androidx.compose.ui.platform.ComposeView) : RecyclerView.ViewHolder(composeView)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RuleViewHolder {
        val composeView = androidx.compose.ui.platform.ComposeView(parent.context).apply {
             layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return RuleViewHolder(composeView)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        val rule = rules[position]
        holder.composeView.setContent {
            // Re-use existing RuleCard composable
            RuleCard(
                rule = rule,
                index = position,
                totalCount = rules.size,
                onToggle = { 
                    val updated = rule.copy(isEnabled = !rule.isEnabled)
                    onUpdateRule(updated)
                },
                onEdit = { onEditRule(rule) },
                onDelete = { onDeleteRule(rule.id) }
            )
        }
    }

    override fun getItemCount(): Int = rules.size

    fun moveItem(from: Int, to: Int) {
        if (from < to) {
            for (i in from until to) {
                java.util.Collections.swap(rules, i, i + 1)
            }
        } else {
            for (i in from downTo to + 1) {
                java.util.Collections.swap(rules, i, i - 1)
            }
        }
        notifyItemMoved(from, to)
    }

    fun onDragComplete() {
        onSwapRules(rules.toList())
    }
}

class RuleTouchHelperCallback(private val adapter: RuleAdapter) : ItemTouchHelper.Callback() {
    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        return makeMovementFlags(dragFlags, 0) // No swipe
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
        // Not used
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        adapter.onDragComplete()
        
        // Visual reset if we customized view during drag
        viewHolder.itemView.alpha = 1.0f
    }
    
    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            viewHolder?.itemView?.alpha = 0.7f
        }
    }
}



@Composable
fun RuleCard(
    rule: BlockRule, 
    index: Int,
    totalCount: Int,
    onToggle: () -> Unit, 
    onEdit: () -> Unit, 
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allowColor = Color(0xFF00C853)
    val containerColor = MaterialTheme.colorScheme.surface
    val isAllowList = rule.isAllowRule
    val borderColor = if (isAllowList) allowColor.copy(alpha=0.5f) else MaterialTheme.colorScheme.error.copy(alpha=0.5f)
    val icon = if (isAllowList) Icons.Default.Check else Icons.Default.Close
    
    val iconBg = if (isAllowList) allowColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.errorContainer
    val iconTint = if (isAllowList) allowColor else MaterialTheme.colorScheme.onErrorContainer
    
    // Alpha for disabled state
    val alpha = if (rule.isEnabled) 1.0f else 0.5f

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth().padding(bottom=4.dp).graphicsLayer { this.alpha = alpha }.clickable { onEdit() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(iconBg, androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint)
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = rule.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isAllowList) {
                            Text("許可", color = allowColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        } else {
                            Text("拒否", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    if (rule.conditions.isNotEmpty()) {
                        rule.conditions.forEach { cond ->
                             Row(verticalAlignment = Alignment.CenterVertically) {
                                 Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                 Spacer(modifier = Modifier.width(4.dp))
                                 Text(
                                     text = cond.getDescription().take(30) + if(cond.getDescription().length>30)"..." else "",
                                     style = MaterialTheme.typography.bodySmall,
                                     color = MaterialTheme.colorScheme.onSurfaceVariant
                                 )
                             }
                        }
                    } else {
                         Text("条件なし (無効)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // No arrows here
                    Switch(
                        checked = rule.isEnabled,
                        onCheckedChange = { onToggle() },
                        modifier = Modifier.scale(0.8f) 
                    )
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "削除", tint = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun RuleEditDialog(
    initialRule: BlockRule?,
    defaultIsAllow: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (BlockRule) -> Unit
) {
    var name by remember { mutableStateOf(initialRule?.name ?: "") }
    var conditions by remember { mutableStateOf(initialRule?.conditions?.toList() ?: emptyList<RuleCondition>()) }
    var isAllowRule by remember { mutableStateOf(initialRule?.isAllowRule ?: defaultIsAllow) }
    
    var showConditionAdder by remember { mutableStateOf(false) }
    var editingConditionIndex by remember { mutableStateOf<Int?>(null) } // For editing

    val allowColor = Color(0xFF00C853)

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(if (initialRule == null) "ルール作成" else "ルール編集", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("ルール名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Text("種類:", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isAllowRule = false }) {
                            RadioButton(selected = !isAllowRule, onClick = { isAllowRule = false })
                            Text("拒否", color = Color.Red, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { isAllowRule = true }) {
                            RadioButton(selected = isAllowRule, onClick = { isAllowRule = true })
                            Text("許可", color = allowColor, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("条件設定 (タップして編集)", style = MaterialTheme.typography.titleMedium)
                Divider()
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (conditions.isEmpty()) {
                        item {
                            Text("条件が設定されていません。\n下のボタンから条件を追加してください。", 
                                modifier = Modifier.padding(16.dp), color = Color.Gray, textAlign = TextAlign.Center)
                        }
                    }
                    itemsIndexed(conditions) { index, cond ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically, 
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                     editingConditionIndex = index
                                     showConditionAdder = true
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text("・${cond.getDescription()}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { conditions = conditions - cond }) {
                                Icon(Icons.Default.Close, contentDescription = "削除", tint = Color.Gray)
                            }
                        }
                        Divider()
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = { 
                    editingConditionIndex = null
                    showConditionAdder = true 
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("条件を追加")
                }
                
                if (showConditionAdder) {
                    val initialCond = editingConditionIndex?.let { conditions.getOrNull(it) }
                    ConditionAdderDialog(
                        initialCondition = initialCond,
                        onApply = { newCond ->
                            if (editingConditionIndex != null) {
                                val mutable = conditions.toMutableList()
                                mutable[editingConditionIndex!!] = newCond
                                conditions = mutable
                            } else {
                                conditions = conditions + newCond
                            }
                            showConditionAdder = false
                            editingConditionIndex = null
                        }, 
                        onCancel = { 
                            showConditionAdder = false
                            editingConditionIndex = null
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("キャンセル") }
                    Button(onClick = {
                        if (name.isNotBlank()) {
                            onSave(BlockRule(
                                id = initialRule?.id ?: java.util.UUID.randomUUID().toString(),
                                name = name,
                                conditions = conditions.toMutableList(),
                                isAllowRule = isAllowRule
                            ))
                        }
                    }) { Text("保存") }
                }
            }
        }
    }
}

@Composable
fun ConditionAdderDialog(
    initialCondition: RuleCondition? = null,
    onApply: (RuleCondition) -> Unit, 
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ConditionAdderContent(initialCondition, onApply, onCancel)
    }
}

@Composable
fun ConditionAdderContent(
    initialCondition: RuleCondition? = null,
    onApply: (RuleCondition) -> Unit, 
    onCancel: () -> Unit
) {
    var type by remember { mutableStateOf(if(initialCondition is RegexCondition) "regex" else "preset") }
    var regexPattern by remember { 
        mutableStateOf(if (initialCondition is RegexCondition) initialCondition.pattern else "") 
    }
    var isInverse by remember { 
        mutableStateOf(
            if (initialCondition is RegexCondition) initialCondition.isInverse 
            else if (initialCondition is ContactCondition) initialCondition.isInverse 
            else false
        )
    }

    var selectedPresetIndex by remember { 
        mutableStateOf(
            if (initialCondition is RegexCondition) {
                // Try to find matching preset
                val idx = BLOCK_PRESETS.indexOfFirst { it.second == initialCondition.pattern }
                if (idx >= 0) { type = "preset"; idx } else 0
            } else if (initialCondition is ContactCondition) {
                type = "preset"
                BLOCK_PRESETS.indexOfFirst { 
                    it.second == (if(initialCondition.isInverse) "CONTACT_NOT_REGISTERED" else "CONTACT_REGISTERED") 
                }.let { if(it >= 0) it else 0 }
            } else 0
        ) 
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), 
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) { // Increased padding
            Text(if (initialCondition == null) "条件の追加" else "条件の編集", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            
            // Tabs
            Row(modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                FilterChip(selected = type == "preset", onClick = { type = "preset" }, label = { Text("プリセット") }, modifier = Modifier.padding(end=4.dp))
                FilterChip(selected = type == "regex", onClick = { type = "regex" }, label = { Text("正規表現") }, modifier = Modifier.padding(end=4.dp))
            }
            
            Divider(modifier = Modifier.padding(vertical=8.dp))

            // Body
            Box(modifier = Modifier.weight(1f)) {
                if (type == "preset") {
                     val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                     androidx.compose.foundation.lazy.LazyColumn(
                         state = listState,
                         modifier = Modifier.fillMaxSize()
                     ) {
                         item {
                             Text("よく使われる条件を選択", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                         }
                         itemsIndexed(BLOCK_PRESETS) { index, (label, _) ->
                             Row(
                                 verticalAlignment = Alignment.CenterVertically,
                                 modifier = Modifier
                                     .fillMaxWidth()
                                     .clickable { selectedPresetIndex = index }
                                     .padding(vertical = 12.dp) // Increased tap target size
                             ) {
                                 RadioButton(
                                     selected = (selectedPresetIndex == index),
                                     onClick = { selectedPresetIndex = index },
                                     modifier = Modifier.size(20.dp)
                                 )
                                 Spacer(modifier = Modifier.width(12.dp))
                                 Text(text = label, style = MaterialTheme.typography.bodyMedium)
                             }
                             Divider()
                         }
                     }
                } else if (type == "regex") {
                    Column {
                        OutlinedTextField(
                            value = regexPattern,
                            onValueChange = { regexPattern = it },
                            label = { Text("正規表現 (例: ^0120.*)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                           Checkbox(checked = isInverse, onCheckedChange = { isInverse = it })
                           Text("条件の反転 (NOT条件)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            
            // Footer Actions
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onCancel) { Text("中止") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    if (type == "preset") {
                        val (_, pattern) = BLOCK_PRESETS[selectedPresetIndex]
                        if (pattern == "CONTACT_REGISTERED") {
                             onApply(ContactCondition(isInverse = false)) 
                        } else if (pattern == "CONTACT_NOT_REGISTERED") {
                             onApply(ContactCondition(isInverse = true))
                        } else {
                             onApply(RegexCondition(pattern, isInverse = false))
                        }
                    } else if (type == "regex" && regexPattern.isNotBlank()) {
                        onApply(RegexCondition(regexPattern, isInverse))
                    }
                }) { Text(if(initialCondition==null) "追加" else "更新") }
            }
        }
    }
}

@Composable
fun EmptyStateMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Color.Gray, textAlign = TextAlign.Center)
    }
}

// Utils
fun checkCallScreeningRole(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
        return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }
    return true
}

fun requestCallScreeningRole(activity: Activity, launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as RoleManager
        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
        launcher.launch(intent)
    }
}
