package net.ysksg.callblocker.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ysksg.callblocker.repository.BlockHistory
import net.ysksg.callblocker.repository.BlockHistoryRepository
import net.ysksg.callblocker.repository.BlockRuleRepository
import net.ysksg.callblocker.repository.GeminiRepository
import net.ysksg.callblocker.ui.rules.RuleTestDialog
import net.ysksg.callblocker.ui.history.HistoryScreen
import net.ysksg.callblocker.ui.rules.RuleListScreen
import net.ysksg.callblocker.ui.settings.AppSettingsScreen
import net.ysksg.callblocker.util.PermissionUtils
import net.ysksg.callblocker.model.BlockRule
import net.ysksg.callblocker.model.RegexCondition
import net.ysksg.callblocker.ui.rules.RuleEditDialog
import net.ysksg.callblocker.repository.UpdateRepository
import net.ysksg.callblocker.ui.settings.UpdateCheckDialog

import android.widget.Toast

@Composable
fun MainScreen(
    initialNavigation: String? = null,
    onNavigationHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { BlockRuleRepository(context) }
    val historyRepo = remember { BlockHistoryRepository(context) }
    val geminiRepo = remember { GeminiRepository(context) }

    // State
    var isOverlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isCallScreeningGranted by remember { mutableStateOf<Boolean>(PermissionUtils.checkCallScreeningRole(context)) }
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

    // External Navigation Handling
    LaunchedEffect(initialNavigation) {
        if (initialNavigation == "HISTORY") {
            selectedTab = 1
            blockHistory = historyRepo.getHistory()
            onNavigationHandled()
        }
    }
    
    // Rule Edit from History
    var showRuleEditDialogFromHistory by remember { mutableStateOf(false) }
    var editingRuleFromHistory by remember { mutableStateOf<BlockRule?>(null) }

    // AI Analysis Loading State for History
    val loadingItems = remember { mutableStateListOf<Long>() }

    // Update Check State
    val updateRepo = remember { UpdateRepository(context) }
    var latestReleaseForAutoCheck by remember { mutableStateOf<UpdateRepository.ReleaseInfo?>(null) }
    var showAutoUpdateDialog by remember { mutableStateOf(false) }

    // Automatic Update Check on Startup
    LaunchedEffect(Unit) {
        if (updateRepo.isAutoUpdateCheckEnabled()) {
            val result = updateRepo.checkForUpdate()
            if (result != null && result.isNewer) {
                latestReleaseForAutoCheck = result
                showAutoUpdateDialog = true
            }
        }
    }

    // Permission Launchers
    val overlayPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { 
        isOverlayGranted = Settings.canDrawOverlays(context) 
    }
    val callScreeningLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { 
        isCallScreeningGranted = PermissionUtils.checkCallScreeningRole(context) 
    }
    
    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        isContactPermissionGranted = results[Manifest.permission.READ_CONTACTS] ?: isContactPermissionGranted
        isPhoneStatePermissionGranted = results[Manifest.permission.READ_PHONE_STATE] ?: isPhoneStatePermissionGranted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isNotificationPermissionGranted = results[Manifest.permission.POST_NOTIFICATIONS] ?: isNotificationPermissionGranted
        }
    }

    // 自動権限リクエストチェーン
    // 状態が変化するたびに再評価され、未許可のものがあればリクエストを投げます。
    LaunchedEffect(isOverlayGranted, isCallScreeningGranted, isContactPermissionGranted, isPhoneStatePermissionGranted, isNotificationPermissionGranted) {
        // 1. 着信ブロック (最重要)
        if (!isCallScreeningGranted) {
             PermissionUtils.requestCallScreeningRole(context as Activity, callScreeningLauncher)
             return@LaunchedEffect
        }
        
        // 2. オーバーレイ
        if (!isOverlayGranted) {
             val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
             overlayPermissionLauncher.launch(intent)
             return@LaunchedEffect
        }
        
        // 3. 標準権限 (まとめてリクエスト)
        val missing = mutableListOf<String>()
        if (!isContactPermissionGranted) missing.add(Manifest.permission.READ_CONTACTS)
        if (!isPhoneStatePermissionGranted) missing.add(Manifest.permission.READ_PHONE_STATE)
        if (!isNotificationPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        if (missing.isNotEmpty()) {
            multiplePermissionLauncher.launch(missing.toTypedArray())
        }
    }

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
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "履歴") },
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
        }
    ) { innerPadding ->
        // Refresh data when switching tabs (e.g. after restore)
        LaunchedEffect(selectedTab) {
            if (selectedTab == 0) {
                rules = repository.getRules()
            } else if (selectedTab == 1) {
                blockHistory = historyRepo.getHistory()
            }
        }
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (selectedTab == 0) {
                 Column {
                    // 権限ステータスエリア
                    if (!isOverlayGranted || !isCallScreeningGranted || !isContactPermissionGranted || !isPhoneStatePermissionGranted || !isNotificationPermissionGranted) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("初期セットアップを実行中...", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                                Text("表示されるダイアログですべての権限を許可してください。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                // 手動フォールバック用ボタン (自動で進まない場合用)
                                Row(modifier = Modifier.wrapContentSize().horizontalScroll(rememberScrollState())) {
                                    if (!isCallScreeningGranted) Button(onClick = { PermissionUtils.requestCallScreeningRole(context as Activity, callScreeningLauncher) }, modifier = Modifier.padding(end=4.dp)) { Text("着信ブロック") }
                                    if (!isOverlayGranted) Button(onClick = { overlayPermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) }, modifier = Modifier.padding(end=4.dp)) { Text("オーバーレイ") }
                                    
                                    if (!isContactPermissionGranted) Button(onClick = { multiplePermissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS)) }, modifier = Modifier.padding(end=4.dp)) { Text("連絡先") }
                                    if (!isPhoneStatePermissionGranted) Button(onClick = { multiplePermissionLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE)) }, modifier = Modifier.padding(end=4.dp)) { Text("電話状態") }
                                    if (!isNotificationPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Button(onClick = { multiplePermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }) { Text("通知") }
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    // Header for Rules
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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

                    // RuleListScreen (No padding on container to allow edge-to-edge with clipToPadding=false)
                    Box(modifier = Modifier.weight(1f)) {
                        RuleListScreen(
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
                 }


            } else if (selectedTab == 1) {
                // History Screen
                HistoryScreen(
                    history = blockHistory,
                    loadingItems = loadingItems,
                    isAiEnabled = geminiRepo.isAiAnalysisEnabled(),
                    onClearHistory = {
                        historyRepo.clearHistory()
                        blockHistory = emptyList()
                    },
                    onAnalyze = { item ->
                        if (!geminiRepo.isAiAnalysisEnabled()) {
                             Toast.makeText(context, "AI解析は設定で無効になっています", Toast.LENGTH_SHORT).show()
                             return@HistoryScreen
                        }
                        loadingItems.add(item.timestamp)
                        val coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                        coroutineScope.launch {
                            try {
                                val result = geminiRepo.checkPhoneNumber(item.number)
                                historyRepo.updateHistory(item.timestamp, result)
                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    blockHistory = historyRepo.getHistory()
                                    loadingItems.remove(item.timestamp)
                                }
                            } catch (e: Exception) {
                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    loadingItems.remove(item.timestamp)
                                }
                            }
                        }
                    },
                    onWebSearch = { item ->
                        val template = geminiRepo.getSearchUrlTemplate()
                        val url = template.replace("{number}", item.number)
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Ignore
                        }
                    },
                    onAddToRule = { item ->
                        editingRuleFromHistory = BlockRule(
                            name = "履歴から追加: ${item.number}",
                            conditions = mutableListOf(RegexCondition(pattern = item.number))
                        )
                        showRuleEditDialogFromHistory = true
                    }
                )
            } else {
                // App Settings Screen
                AppSettingsScreen()
            }
        }
    }

    if (showRuleTestDialog) {
        RuleTestDialog(
            repository = repository,
            onDismiss = { showRuleTestDialog = false }
        )
    }

    if (showRuleEditDialogFromHistory) {
        RuleEditDialog(
            initialRule = editingRuleFromHistory,
            onDismiss = { showRuleEditDialogFromHistory = false },
            onSave = { updatedRule ->
                repository.saveRule(updatedRule)
                rules = repository.getRules() // Refresh rules
                showRuleEditDialogFromHistory = false
                Toast.makeText(context, "ルールを保存しました", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showAutoUpdateDialog && latestReleaseForAutoCheck != null) {
        UpdateCheckDialog(
            releaseInfo = latestReleaseForAutoCheck!!,
            onDismiss = { showAutoUpdateDialog = false }
        )
    }
}
