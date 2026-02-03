package net.ysksg.callblocker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import net.ysksg.callblocker.data.BlockRuleRepository

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

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
                TextButton(onClick = onDismiss, modifier = Modifier.align(androidx.compose.ui.Alignment.End)) {
                    Text("閉じる")
                }
            }
        }
    }
}
