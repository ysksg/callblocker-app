package net.ysksg.callblocker.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.ysksg.callblocker.data.GeminiRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen() {
    val context = LocalContext.current
    val geminiRepo = remember { GeminiRepository(context) }
    
    var apiKey by remember { mutableStateOf(geminiRepo.getApiKey() ?: "") }
    var searchUrl by remember { mutableStateOf(geminiRepo.getSearchUrlTemplate()) }
    
    var isVerifying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        Text("アプリ設定", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("電話番号検索設定", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = searchUrl,
            onValueChange = { 
                searchUrl = it
                geminiRepo.setSearchUrlTemplate(it)
            },
            label = { Text("検索URLテンプレート") },
            placeholder = { Text("https://google.com/search?q={number}") },
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("{number} が電話番号に置換されます") },
            enabled = !isVerifying
        )
        
        
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        Text("AI設定 (Gemini)", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        // ... (AI Settings content) ...
        // Model Selection Dropdown
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

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedModel,
                onValueChange = {},
                readOnly = true,
                label = { Text("使用モデル") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                enabled = !isVerifying
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                models.forEach { model ->
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
            enabled = !isVerifying
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { 
                apiKey = it
                geminiRepo.setApiKey(it)
            },
            label = { Text("Gemini API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isVerifying
        )

        Spacer(modifier = Modifier.height(16.dp))

        // API Verification Button
        Button(
            onClick = {
                isVerifying = true
                scope.launch {
                    // Verification uses current input values
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
            enabled = !isVerifying,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
                if (isVerifying) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onSecondary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("検証中")
                } else {
                    Text("APIキー検証")
                }
        }
        
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        Text("オーバーレイ設定", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        val overlayRepo = remember { net.ysksg.callblocker.data.OverlaySettingsRepository(context) }
        var isAutoCloseEnabled by remember { mutableStateOf(overlayRepo.isAutoCloseEnabled()) }
        var defaultState by remember { mutableStateOf(overlayRepo.getDefaultOverlayState()) }

        // Card removed to match other sections style
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("通話終了時に自動で閉じる", style = MaterialTheme.typography.bodyLarge)
                    Text("通話切断を検知してオーバーレイを消去します", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Switch(
                    checked = isAutoCloseEnabled,
                    onCheckedChange = { 
                        isAutoCloseEnabled = it
                        overlayRepo.setAutoCloseEnabled(it)
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("初期表示状態", style = MaterialTheme.typography.bodyLarge)
            Row(
               modifier = Modifier.fillMaxWidth(),
               horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isMinimized = defaultState == net.ysksg.callblocker.data.OverlaySettingsRepository.STATE_MINIMIZED
                FilterChip(
                    selected = isMinimized,
                    onClick = { 
                        defaultState = net.ysksg.callblocker.data.OverlaySettingsRepository.STATE_MINIMIZED
                        overlayRepo.setDefaultOverlayState(defaultState)
                    },
                    label = { Text("アイコン (最小化)") },
                    leadingIcon = { if(isMinimized) Icon(Icons.Default.Check, null) }
                )
                
                val isExpanded = defaultState == net.ysksg.callblocker.data.OverlaySettingsRepository.STATE_EXPANDED
                FilterChip(
                    selected = isExpanded,
                    onClick = { 
                        defaultState = net.ysksg.callblocker.data.OverlaySettingsRepository.STATE_EXPANDED
                        overlayRepo.setDefaultOverlayState(defaultState)
                    },
                    label = { Text("詳細表示 (展開)") },
                    leadingIcon = { if(isExpanded) Icon(Icons.Default.Check, null) }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
    }
}

