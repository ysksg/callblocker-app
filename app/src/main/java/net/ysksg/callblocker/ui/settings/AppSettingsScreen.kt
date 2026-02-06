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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.ysksg.callblocker.data.GeminiRepository
import net.ysksg.callblocker.data.OverlaySettingsRepository
import net.ysksg.callblocker.data.BackupManager
import net.ysksg.callblocker.BuildConfig
import net.ysksg.callblocker.data.ThemeRepository
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.semantics.Role

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
    val geminiRepo = remember { GeminiRepository(context) }
    var searchUrl by remember { mutableStateOf(geminiRepo.getSearchUrlTemplate()) }

    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        OutlinedTextField(
            value = searchUrl,
            onValueChange = { 
                searchUrl = it
                geminiRepo.setSearchUrlTemplate(it)
            },
            label = { Text("検索URLテンプレート") },
            placeholder = { Text("https://google.com/search?q={number}") },
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("{number} が電話番号に置換されます") }
        )
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
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        
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
                        Toast.makeText(context, "検証成功: $message", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "検証失敗: $message", Toast.LENGTH_LONG).show()
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
    val backupManager = remember { BackupManager(context) }
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
            supportingContent = { Text(BuildConfig.VERSION_NAME) },
            leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
        )
    }
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
