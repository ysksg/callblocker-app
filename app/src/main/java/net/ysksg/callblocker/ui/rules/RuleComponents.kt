package net.ysksg.callblocker.ui.rules

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

import net.ysksg.callblocker.model.BlockRule
import net.ysksg.callblocker.model.ContactCondition
import net.ysksg.callblocker.model.RegexCondition
import net.ysksg.callblocker.model.RuleCondition
import net.ysksg.callblocker.model.TimeCondition
import net.ysksg.callblocker.repository.BlockRuleRepository
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import java.util.Calendar
import java.util.Locale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType

val BLOCK_PRESETS = listOf(
    "連絡先に登録されている番号" to "CONTACT_REGISTERED",
    "連絡先に登録されていない番号" to "CONTACT_NOT_REGISTERED",
    "深夜の着信 (00:00〜06:00)" to TimeCondition(startHour = 0, startMinute = 0, endHour = 6, endMinute = 0),
    "平日の着信 (月〜金)" to TimeCondition(daysOfWeek = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)),
    "週末の着信 (土日)" to TimeCondition(daysOfWeek = listOf(Calendar.SATURDAY, Calendar.SUNDAY)),
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

/**
 * ルール一覧に表示するカードコンポーネント。
 */
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
    
    val alpha = if (rule.isEnabled) 1.0f else 0.5f

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .graphicsLayer { this.alpha = alpha }
            .clickable { onEdit() }
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
                                     text = cond.getDescription(),
                                     style = MaterialTheme.typography.bodySmall,
                                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                                     maxLines = 2,
                                     overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                 )
                             }
                        }
                    } else {
                         Text("条件なし (無効)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = rule.isEnabled,
                        onCheckedChange = { onToggle() },
                        modifier = Modifier.scale(0.8f) 
                    )
                }
            }
        }
    }
}

/**
 * ルールの編集・作成ダイアログ。
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var editingConditionIndex by remember { mutableStateOf<Int?>(null) } 

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
                
                Text("ポリシー:", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !isAllowRule,
                        onClick = { isAllowRule = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Default.Close, null) },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.errorContainer, 
                            activeContentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("拒否")
                    }
                    SegmentedButton(
                        selected = isAllowRule,
                        onClick = { isAllowRule = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.Default.Check, null) },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = Color(0xFFE8F5E9), 
                            activeContentColor = Color(0xFF2E7D32)
                        )
                    ) {
                        Text("許可")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("条件設定 (タップして編集)", style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                
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
                        HorizontalDivider()
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
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("キャンセル") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val newRule = if (initialRule != null) {
                                initialRule.copy(name = name, conditions = conditions.toMutableList(), isAllowRule = isAllowRule)
                            } else {
                                BlockRule(name = name, conditions = conditions.toMutableList(), isAllowRule = isAllowRule)
                            }
                            onSave(newRule)
                        },
                        enabled = name.isNotBlank() && conditions.isNotEmpty()
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
    
    if (showConditionAdder) {
        ConditionAdderDialog(
            initialCondition = if (editingConditionIndex != null) conditions[editingConditionIndex!!] else null,
            onApply = { newCond ->
                if (editingConditionIndex != null) {
                    val mutable = conditions.toMutableList()
                    mutable[editingConditionIndex!!] = newCond
                    conditions = mutable
                } else {
                    conditions = conditions + newCond
                }
                showConditionAdder = false
            },
            onCancel = { showConditionAdder = false }
        )
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
            else if (initialCondition is TimeCondition) initialCondition.isInverse
            else false
        )
    }

    var selectedPresetIndex by remember { 
        mutableStateOf<Int>(
            if (initialCondition is RegexCondition) {
                val idx = BLOCK_PRESETS.indexOfFirst { it.second == initialCondition.pattern }
                if (idx >= 0) { type = "preset"; idx } else 0
            } else if (initialCondition is ContactCondition) {
                type = "preset"
                BLOCK_PRESETS.indexOfFirst { 
                    it.second == (if(initialCondition.isInverse) "CONTACT_NOT_REGISTERED" else "CONTACT_REGISTERED") 
                }.let { if(it >= 0) it else 0 }
            } else if (initialCondition is TimeCondition) {
                type = "time"
                0
            } else 0
        ) 
    }
    
    // Time Condition States
    var startDate by remember { mutableStateOf(if (initialCondition is TimeCondition) initialCondition.startDate else null) }
    var endDate by remember { mutableStateOf(if (initialCondition is TimeCondition) initialCondition.endDate else null) }
    var startTime by remember { mutableStateOf(if (initialCondition is TimeCondition && initialCondition.startHour != null) initialCondition.startHour to initialCondition.startMinute!! else null) }
    var endTime by remember { mutableStateOf(if (initialCondition is TimeCondition && initialCondition.endHour != null) initialCondition.endHour to initialCondition.endMinute!! else null) }
    var selectedDays by remember { mutableStateOf(if (initialCondition is TimeCondition) initialCondition.daysOfWeek.toSet() else emptySet()) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val calendar = Calendar.getInstance()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), 
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
             Text(if (initialCondition == null) "条件の追加" else "条件の編集", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(selected = type == "preset", onClick = { type = "preset" }, label = { Text("プリセット") }, modifier = Modifier.padding(end=4.dp))
                FilterChip(selected = type == "regex", onClick = { type = "regex" }, label = { Text("正規表現") }, modifier = Modifier.padding(end=4.dp))
                FilterChip(selected = type == "time", onClick = { type = "time" }, label = { Text("日時・曜日") }, modifier = Modifier.padding(end=4.dp))
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical=8.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (type == "preset") {
                     val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                     LazyColumn(
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
                                     .padding(vertical = 12.dp)
                             ) {
                                 RadioButton(
                                     selected = (selectedPresetIndex == index),
                                     onClick = { selectedPresetIndex = index },
                                     modifier = Modifier.size(20.dp)
                                 )
                                 Spacer(modifier = Modifier.width(12.dp))
                                 Text(text = label, style = MaterialTheme.typography.bodyMedium)
                             }
                             HorizontalDivider()
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
                } else if (type == "time") {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("日付範囲 (任意)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = {
                                DatePickerDialog(context, { _, y, m, d ->
                                    startDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
                                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                            }, modifier = Modifier.weight(1f)) {
                                Text(startDate ?: "開始日なし")
                            }
                            Text(" ～ ", modifier = Modifier.padding(horizontal = 8.dp))
                            OutlinedButton(onClick = {
                                DatePickerDialog(context, { _, y, m, d ->
                                    endDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d)
                                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                            }, modifier = Modifier.weight(1f)) {
                                Text(endDate ?: "終了日なし")
                            }
                            if (startDate != null || endDate != null) {
                                IconButton(onClick = { startDate = null; endDate = null }) {
                                    Icon(Icons.Default.Clear, "クリア")
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("曜日指定 (任意)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val days = listOf("日", "月", "火", "水", "木", "金", "土")
                            val dayValues = listOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY)
                            
                            days.forEachIndexed { index, label ->
                                val dayValue = dayValues[index]
                                val isSelected = selectedDays.contains(dayValue)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { 
                                        selectedDays = if(isSelected) selectedDays - dayValue else selectedDays + dayValue
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("時間帯 (任意)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = {
                                val currentH = startTime?.first ?: 9
                                val currentM = startTime?.second ?: 0
                                TimePickerDialog(context, { _, h, m ->
                                    startTime = h to m
                                }, currentH, currentM, true).show()
                            }, modifier = Modifier.weight(1f)) {
                                Text(startTime?.let { String.format("%02d:%02d", it.first, it.second) } ?: "開始なし")
                            }
                            Text(" ～ ", modifier = Modifier.padding(horizontal = 8.dp))
                            OutlinedButton(onClick = {
                                val currentH = endTime?.first ?: 17
                                val currentM = endTime?.second ?: 0
                                TimePickerDialog(context, { _, h, m ->
                                    endTime = h to m
                                }, currentH, currentM, true).show()
                            }, modifier = Modifier.weight(1f)) {
                                Text(endTime?.let { String.format("%02d:%02d", it.first, it.second) } ?: "終了なし")
                            }
                            if (startTime != null || endTime != null) {
                                IconButton(onClick = { startTime = null; endTime = null }) {
                                    Icon(Icons.Default.Clear, "クリア")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                           Checkbox(checked = isInverse, onCheckedChange = { isInverse = it })
                           Text("条件の反転 (指定日時【以外】)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onCancel) { Text("中止") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    if (type == "preset") {
                        val (_, presetObj) = BLOCK_PRESETS[selectedPresetIndex]
                        when (presetObj) {
                            "CONTACT_REGISTERED" -> onApply(ContactCondition(isInverse = false))
                            "CONTACT_NOT_REGISTERED" -> onApply(ContactCondition(isInverse = true))
                            is String -> onApply(RegexCondition(presetObj, isInverse = false))
                            is TimeCondition -> onApply(presetObj.copy(isInverse = isInverse))
                        }
                    } else if (type == "regex" && regexPattern.isNotBlank()) {
                        onApply(RegexCondition(regexPattern, isInverse))
                    } else if (type == "time") {
                        onApply(TimeCondition(
                            startDate = startDate,
                            endDate = endDate,
                            startHour = startTime?.first,
                            startMinute = startTime?.second,
                            endHour = endTime?.first,
                            endMinute = endTime?.second,
                            daysOfWeek = selectedDays.sorted(),
                            isInverse = isInverse
                        ))
                    }
                }) { Text(if(initialCondition==null) "追加" else "更新") }
            }
        }
    }
}

@Composable
fun RuleTestDialog(
    repository: BlockRuleRepository,
    onDismiss: () -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("ルールテスト", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("電話番号") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val result = repository.checkBlock(phoneNumber)
                        val status = if (result.shouldBlock) "拒否" else "許可"
                        val reason = result.matchedRuleName ?: (if(result.shouldBlock) "不明" else "デフォルト")
                        resultText = "判定結果: $status\n適用ルール: $reason"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("判定実行")
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                if (resultText.isNotEmpty()) {
                    Text(resultText, style = MaterialTheme.typography.bodyLarge)
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("閉じる")
                }
            }
        }
    }
}
