package net.ysksg.callblocker.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import net.ysksg.callblocker.repository.GeminiRepository
import net.ysksg.callblocker.repository.SearchSettingsRepository
import net.ysksg.callblocker.repository.OverlaySettingsRepository
import net.ysksg.callblocker.repository.BackupRepository
import net.ysksg.callblocker.BuildConfig
import net.ysksg.callblocker.repository.ThemeRepository
import net.ysksg.callblocker.repository.BlockRuleRepository
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import androidx.compose.foundation.shape.RoundedCornerShape
import net.ysksg.callblocker.repository.UpdateRepository
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.FileProvider
import io.noties.markwon.Markwon
import java.io.File

enum class SettingsRoute {
    Main, Overlay, Theme, Search, AI, Data
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen() {
    var currentRoute by remember { mutableStateOf(SettingsRoute.Main) }
    
    BackHandler(enabled = currentRoute != SettingsRoute.Main) {
        currentRoute = SettingsRoute.Main
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        when(currentRoute) {
                            SettingsRoute.Main -> "設定"
                            SettingsRoute.Overlay -> "オーバーレイ設定"
                            SettingsRoute.Theme -> "テーマ設定"
                            SettingsRoute.Search -> "Web検索設定"
                            SettingsRoute.AI -> "AI設定 (Gemini)"
                            SettingsRoute.Data -> "データと情報"
                        }
                    ) 
                },
                navigationIcon = {
                    if (currentRoute != SettingsRoute.Main) {
                        IconButton(onClick = { currentRoute = SettingsRoute.Main }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            AnimatedContent(
                targetState = currentRoute,
                transitionSpec = {
                    if (initialState == SettingsRoute.Main) {
                        // Main -> Sub: Right to Left
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    } else {
                        // Sub -> Main: Left to Right
                        slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                    }
                },
                label = "SettingsNav"
            ) { targetRoute ->
                when (targetRoute) {
                    SettingsRoute.Main -> MainSettingsList(onNavigate = { currentRoute = it })
                    SettingsRoute.Overlay -> OverlaySettingsScreen()
                    SettingsRoute.Theme -> ThemeSettingsScreen()
                    SettingsRoute.Search -> SearchSettingsScreen()
                    SettingsRoute.AI -> AISettingsScreen()
                    SettingsRoute.Data -> DataSettingsScreen()
                }
            }
        }
    }
}

@Composable
fun MainSettingsList(onNavigate: (SettingsRoute) -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SettingsCategoryItem(
            icon = Icons.Default.Phone,
            title = "オーバーレイ設定",
            subtitle = "表示・非表示、自動クローズ、位置保存",
            onClick = { onNavigate(SettingsRoute.Overlay) }
        )
        HorizontalDivider()
        SettingsCategoryItem(
            icon = Icons.Default.Settings,
            title = "テーマ設定",
            subtitle = "ライト / ダーク / システム",
            onClick = { onNavigate(SettingsRoute.Theme) }
        )
        HorizontalDivider()
        SettingsCategoryItem(
            icon = Icons.Default.Search,
            title = "Web検索設定",
            subtitle = "検索URLテンプレート",
            onClick = { onNavigate(SettingsRoute.Search) }
        )
        HorizontalDivider()
        SettingsCategoryItem(
            icon = Icons.Default.Star,
            title = "AI設定 (Gemini)",
            subtitle = "APIキー、モデル、プロンプト",
            onClick = { onNavigate(SettingsRoute.AI) }
        )
        HorizontalDivider()
        SettingsCategoryItem(
            icon = Icons.Default.Share,
            title = "データと情報",
            subtitle = "バックアップ、復元、アプリ情報",
            onClick = { onNavigate(SettingsRoute.Data) }
        )
    }
}

@Composable
fun SettingsCategoryItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun OverlaySettingsScreen() {
    val context = LocalContext.current
    val overlayRepo = remember { OverlaySettingsRepository(context) }
    
    // State initialization
    var isOverlayEnabled by remember { mutableStateOf(overlayRepo.isOverlayEnabled()) }
    var isAutoCloseEnabled by remember { mutableStateOf(overlayRepo.isAutoCloseEnabled()) }
    var defaultState by remember { mutableStateOf(overlayRepo.getDefaultOverlayState()) }
    var isPositionSaveEnabled by remember { mutableStateOf(overlayRepo.isPositionSaveEnabled()) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        // Master Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("オーバーレイ機能を有効にする", style = MaterialTheme.typography.titleMedium)
                Text("着信時に情報を画面上に表示します", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Switch(
                checked = isOverlayEnabled,
                onCheckedChange = { 
                    isOverlayEnabled = it
                    overlayRepo.setOverlayEnabled(it)
                }
            )
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        
        // Other settings (disabled if master toggle is off)
        val enabled = isOverlayEnabled
        
        Text("動作設定", style = MaterialTheme.typography.titleSmall, color = if(enabled) Color.Unspecified else Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        // Position Save
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("表示位置を保存する", style = MaterialTheme.typography.bodyLarge, color = if(enabled) Color.Unspecified else Color.Gray)
                Text("移動した位置を記憶します（OFFの場合は次回デフォルト位置）", style = MaterialTheme.typography.bodySmall, color = if(enabled) Color.Gray else Color.LightGray)
            }
            Switch(
                checked = isPositionSaveEnabled,
                onCheckedChange = { 
                    isPositionSaveEnabled = it
                    overlayRepo.setPositionSaveEnabled(it)
                },
                enabled = enabled
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Auto Close
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("通話終了時に自動で閉じる", style = MaterialTheme.typography.bodyLarge, color = if(enabled) Color.Unspecified else Color.Gray)
                Text("通話切断を検知してオーバーレイを消去します", style = MaterialTheme.typography.bodySmall, color = if(enabled) Color.Gray else Color.LightGray)
            }
            Switch(
                checked = isAutoCloseEnabled,
                onCheckedChange = { 
                    isAutoCloseEnabled = it
                    overlayRepo.setAutoCloseEnabled(it)
                },
                enabled = enabled
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Default State
        Text("初期表示状態", style = MaterialTheme.typography.bodyLarge, color = if(enabled) Color.Unspecified else Color.Gray)
        Text("着信時の情報の詳細・簡易表示の初期状態を設定します。", style = MaterialTheme.typography.bodySmall, color = if(enabled) Color.Gray else Color.LightGray)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isMinimized = defaultState == OverlaySettingsRepository.STATE_MINIMIZED
            FilterChip(
                selected = isMinimized,
                onClick = { 
                    defaultState = OverlaySettingsRepository.STATE_MINIMIZED
                    overlayRepo.setDefaultOverlayState(defaultState)
                },
                label = { Text("アイコン (最小化)") },
                leadingIcon = { if(isMinimized) Icon(Icons.Default.Check, null) },
                enabled = enabled
            )
            
            val isExpanded = defaultState == OverlaySettingsRepository.STATE_EXPANDED
            FilterChip(
                selected = isExpanded,
                onClick = { 
                    defaultState = OverlaySettingsRepository.STATE_EXPANDED
                    overlayRepo.setDefaultOverlayState(defaultState)
                },
                label = { Text("詳細表示 (展開)") },
                leadingIcon = { if(isExpanded) Icon(Icons.Default.Check, null) },
                enabled = enabled
            )
        }
    }
}

@Composable
fun SearchSettingsScreen() {
    val context = LocalContext.current
    val searchRepo = remember { SearchSettingsRepository(context) }
    var searchUrl by remember { mutableStateOf(searchRepo.getSearchUrlTemplate()) }
    
    // 現在のURLがプリセットのどれかに一致するか確認
    val presets = SearchSettingsRepository.SEARCH_PRESETS
    var selectedPresetIndex by remember {
        val idx = presets.indexOfFirst { it.second == searchUrl }
        mutableStateOf(if (idx >= 0 && searchUrl.isNotEmpty()) idx else presets.size - 1)
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("検索エンジンを選択", style = MaterialTheme.typography.titleMedium)
        Text("Web検索ボタンを押した際に使用するサイトを選択します。", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        presets.forEachIndexed { index, (name, url) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        selectedPresetIndex = index
                        if (url.isNotEmpty()) {
                            searchUrl = url
                            searchRepo.setSearchUrlTemplate(url)
                        }
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (selectedPresetIndex == index),
                    onClick = { 
                        selectedPresetIndex = index
                        if (url.isNotEmpty()) {
                            searchUrl = url
                            searchRepo.setSearchUrlTemplate(url)
                        }
                    }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(name, style = MaterialTheme.typography.bodyLarge)
            }
            HorizontalDivider(modifier = Modifier.padding(start = 32.dp))
        }

        if (selectedPresetIndex == presets.size - 1) { // カスタム
            Spacer(modifier = Modifier.height(24.dp))
            Text("カスタムURL設定", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = searchUrl,
                onValueChange = { 
                    searchUrl = it
                    searchRepo.setSearchUrlTemplate(it)
                },
                label = { Text("検索URLテンプレート") },
                placeholder = { Text("https://example.com/search?q={number}") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("{number} が電話番号に置換されます") }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettingsScreen() {
    val context = LocalContext.current
    val geminiRepo = remember { GeminiRepository(context) }
    
    var apiKey by remember { mutableStateOf(geminiRepo.getApiKey() ?: "") }
    var isVerifying by remember { mutableStateOf(false) }
    var isAiEnabled by remember { mutableStateOf(geminiRepo.isAiAnalysisEnabled()) }
    var isAiCacheEnabled by remember { mutableStateOf(geminiRepo.isAiCacheEnabled()) }
    val scope = rememberCoroutineScope()

    var expanded by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(geminiRepo.getModel()) }
    var prompt by remember { mutableStateOf(geminiRepo.getPrompt()) }
    
    val models = listOf(
        "gemini-3-pro-preview",
        "gemini-3-flash-preview",
        "gemini-2.5-pro",
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite"
    )

    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        // Master Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("AI解析を有効にする", style = MaterialTheme.typography.titleMedium)
                Text("着信時に番号を解析し、結果を表示・履歴に保存します", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Switch(
                checked = isAiEnabled,
                onCheckedChange = { 
                    isAiEnabled = it
                    geminiRepo.setAiAnalysisEnabled(it)
                }
            )
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Cache Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("解析結果をキャッシュする", style = MaterialTheme.typography.titleMedium)
                Text("過去に解析した番号は再度解析せず履歴から引用します", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Switch(
                checked = isAiCacheEnabled,
                onCheckedChange = {
                    isAiCacheEnabled = it
                    geminiRepo.setAiCacheEnabled(it)
                },
                enabled = isAiEnabled
            )
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        
        val fieldsEnabled = isAiEnabled && !isVerifying

        // API Key
        OutlinedTextField(
            value = apiKey,
            onValueChange = { 
                apiKey = it
                geminiRepo.setApiKey(it)
            },
            label = { Text("Gemini API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = fieldsEnabled
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Model Selection
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (fieldsEnabled) expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedModel,
                onValueChange = {},
                readOnly = true,
                label = { Text("使用モデル") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = fieldsEnabled).fillMaxWidth(),
                enabled = fieldsEnabled
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                // Hardcoded consistent list + current selection if not in list
                 val displayModels = (listOf(selectedModel) + models).distinct()
                 displayModels.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(text = model) },
                        onClick = {
                            selectedModel = model
                            geminiRepo.setModel(model)
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Prompt
        OutlinedTextField(
            value = prompt,
            onValueChange = { 
                prompt = it
                geminiRepo.setPrompt(it)
            },
            label = { Text("AI解析プロンプト") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            supportingText = { Text("{number} が電話番号に置換されます") },
            enabled = fieldsEnabled
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                isVerifying = true
                scope.launch {
                    val (isValid, message) = geminiRepo.verifyApiKey(apiKey, selectedModel)
                    if (isValid) {
                        Toast.makeText(context, "APIキーの検証に成功しました。", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "APIキーの検証に失敗しました。: $message", Toast.LENGTH_LONG).show()
                    }
                    isVerifying = false
                }
            },
            modifier = Modifier.align(Alignment.End),
            enabled = fieldsEnabled
        ) {
            if (isVerifying) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("検証中")
            } else {
                Text("APIキー検証")
            }
        }
    }
}

@Composable
fun DataSettingsScreen() {
    val context = LocalContext.current
    val blockRuleRepo = remember { BlockRuleRepository(context) }
    val backupManager = remember { BackupRepository(context, blockRuleRepo) }
    val scope = rememberCoroutineScope()
    
    // Launchers
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val result = backupManager.exportData(uri)
                result.onSuccess { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                result.onFailure { Toast.makeText(context, "エクスポート失敗: ${it.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }
    
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = backupManager.importData(uri)
                result.onSuccess { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                result.onFailure { Toast.makeText(context, "インポート失敗: ${it.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("バックアップと復元", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = { 
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm").format(java.util.Date())
                exportLauncher.launch("callblocker_backup_$timestamp.json")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("設定とデータをバックアップ (保存)")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/json")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("バックアップから復元")
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))
        
        Text("アプリ情報", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        ListItem(
            headlineContent = { Text("バージョン") },
            supportingContent = { Text("${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})") },
            leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
        )

        val updateRepo = remember { UpdateRepository(context) }
        var isAutoUpdateEnabled by remember { mutableStateOf(updateRepo.isAutoUpdateCheckEnabled()) }
        var isChecking by remember { mutableStateOf(false) }
        var latestRelease by remember { mutableStateOf<UpdateRepository.ReleaseInfo?>(null) }
        var showUpdateDialog by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("起動時にアップデートを確認", style = MaterialTheme.typography.bodyLarge)
                Text("新しいバージョンがある場合に通知します", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Switch(
                checked = isAutoUpdateEnabled,
                onCheckedChange = { 
                    isAutoUpdateEnabled = it
                    updateRepo.setAutoUpdateCheckEnabled(it)
                }
            )
        }

        Button(
            onClick = {
                isChecking = true
                scope.launch {
                    val result = updateRepo.checkForUpdate()
                    isChecking = false
                    if (result != null && result.isNewer) {
                        latestRelease = result
                        showUpdateDialog = true
                    } else if (result != null) {
                        Toast.makeText(context, "最新のバージョンです", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "アップデート情報の取得に失敗しました", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isChecking
        ) {
            if (isChecking) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("確認中...")
            } else {
                Text("アップデートを確認")
            }
        }

        if (showUpdateDialog && latestRelease != null) {
            UpdateCheckDialog(
                releaseInfo = latestRelease!!,
                onDismiss = { showUpdateDialog = false }
            )
        }
    }
}

@Composable
fun UpdateCheckDialog(
    releaseInfo: UpdateRepository.ReleaseInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isDownloading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = if (isDownloading) ({}) else onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.95f),
        title = { Text("新しいバージョンが利用可能です: ${releaseInfo.tagName}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("更新内容:", style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 500.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
                    AndroidView(
                        modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
                        factory = { ctx ->
                            TextView(ctx).apply {
                                setTextColor(textColor)
                                setLineSpacing(0f, 1.2f)
                                Markwon.create(ctx).setMarkdown(this, releaseInfo.body)
                            }
                        },
                        update = { view ->
                            view.setTextColor(textColor)
                            Markwon.create(view.context).setMarkdown(view, releaseInfo.body)
                        }
                    )
                }
                if (isDownloading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("ダウンロード中...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (releaseInfo.apkUrl != null) {
                            isDownloading = true
                            downloadAndInstallApk(context, releaseInfo.apkUrl, releaseInfo.tagName) { success, message ->
                                isDownloading = false
                                if (!success) {
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "APKのダウンロードURLが見つかりません", Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = !isDownloading && releaseInfo.apkUrl != null
                ) {
                    Text("インストール")
                }
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseInfo.htmlUrl))
                        context.startActivity(intent)
                        onDismiss()
                    },
                    enabled = !isDownloading
                ) {
                    Text("GitHubで確認")
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onDismiss,
                    enabled = !isDownloading
                ) {
                    Text("閉じる")
                }
            }
        }
    )
}

private fun downloadAndInstallApk(
    context: Context, 
    url: String, 
    tagName: String,
    onResult: (Boolean, String) -> Unit
) {
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val uri = Uri.parse(url)
    val fileName = "CallBlocker_$tagName.apk"
    
    val request = DownloadManager.Request(uri).apply {
        setTitle("Call Blocker $tagName をダウンロード中")
        setDescription("新しいバージョンをインストールします")
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setDestinationUri(Uri.fromFile(File(context.externalCacheDir, fileName)))
        setAllowedOverMetered(true)
        setAllowedOverRoaming(true)
    }

    val downloadId = try {
        downloadManager.enqueue(request)
    } catch (e: Exception) {
        onResult(false, "ダウンロードの開始に失敗しました: ${e.message}")
        return
    }

    // ダウンロード完了通知を受けるレシーバー
    val onComplete = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id == downloadId) {
                context.unregisterReceiver(this)
                
                // ダウンロードステータスの確認
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(statusIndex)
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        try {
                            installApk(context, fileName)
                            onResult(true, "")
                        } catch (e: Exception) {
                            onResult(false, "インストールの起動に失敗しました: ${e.message}")
                        }
                    } else {
                        val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val reason = cursor.getInt(reasonIndex)
                        onResult(false, "ダウンロードに失敗しました (Code: $reason)")
                    }
                } else {
                    onResult(false, "ダウンロード情報が見つかりません")
                }
                cursor.close()
            }
        }
    }
    
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
    } else {
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }
}

private fun installApk(context: Context, fileName: String) {
    val apkFile = File(context.externalCacheDir, fileName)
    
    if (!apkFile.exists()) {
        Toast.makeText(context, "ファイルが見つかりません: ${apkFile.absolutePath}", Toast.LENGTH_LONG).show()
        return
    }

    // Android 8.0 以降の「不明なアプリのインストール」権限チェック
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(context, "インストール権限を許可したあと、再度お試しください", Toast.LENGTH_LONG).show()
            return
        }
    }

    val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
    val installIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(apkUri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(installIntent)
}

@Composable
fun ThemeSettingsScreen() {
    val context = LocalContext.current
    val themeRepo = remember { ThemeRepository(context) }
    var currentTheme by remember { mutableStateOf(themeRepo.getThemeMode()) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("テーマ選択", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        val radioOptions = listOf(
            ThemeRepository.THEME_SYSTEM to "システム設定に従う",
            ThemeRepository.THEME_LIGHT to "ライトモード",
            ThemeRepository.THEME_DARK to "ダークモード"
        )

        Column(modifier = Modifier.selectableGroup()) {
            radioOptions.forEach { (mode, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .selectable(
                            selected = (currentTheme == mode),
                            onClick = { 
                                currentTheme = mode
                                themeRepo.setThemeMode(mode)
                            },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (currentTheme == mode),
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
