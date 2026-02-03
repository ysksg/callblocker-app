package net.ysksg.callblocker

import android.telecom.Call
import android.telecom.CallScreeningService
import android.content.Intent
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.launch


class CallDetectorService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val rawNumber = callDetails.handle?.schemeSpecificPart
        Log.i("CallDetectorService", "onScreenCall received. Raw number: $rawNumber, Direction: ${callDetails.callDirection}")
        
        // 発信時は何もしない (ブロック判定もオーバーレイ表示もしない)
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
             Log.i("CallDetectorService", "Ignoring outgoing call.")
             // デフォルトの応答（何もしない＝許可）を返す必要はないかもしれないが、
             // onScreenCallは応答を期待されるため、念のため許可応答を返しておくのが安全。
             respondToCall(callDetails, CallResponse.Builder().build())
             return
        }
        
        if (rawNumber == null) {
            Log.w("CallDetectorService", "Phone number is null, allowing call.")
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val repository = net.ysksg.callblocker.data.BlockRuleRepository(applicationContext)
        val blockResult = repository.checkBlock(rawNumber)
        Log.i("CallDetectorService", "Check result: ${blockResult.shouldBlock}, reason: ${blockResult.reason}")

        val timestamp = System.currentTimeMillis()
        val historyRepo = net.ysksg.callblocker.data.BlockHistoryRepository(applicationContext)

        // 1. ブロック判定
        if (blockResult.shouldBlock) {
            Log.i("CallDetectorService", "Blocking call from $rawNumber")
            
            // 表示用にフォーマット
            val formattedNumber = net.ysksg.callblocker.util.PhoneNumberFormatter.format(rawNumber)
            
            // AI解析中として保存
            historyRepo.addHistory(rawNumber, timestamp, blockResult.reason, "AI解析中...") 

            // 非同期でAI解析実行
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val geminiRepo = net.ysksg.callblocker.data.GeminiRepository(applicationContext)
                    val aiResult = geminiRepo.checkPhoneNumber(rawNumber)
                    historyRepo.updateHistory(timestamp, aiResult)
                } catch (e: Exception) {
                    Log.e("CallDetectorService", "AI Analysis failed", e)
                    historyRepo.updateHistory(timestamp, "AI解析失敗")
                }
            }

            // 通知を表示 (ここはフォーマット済みを表示したい)
            showBlockNotification(formattedNumber, blockResult.reason)

            // ブロック実行
            val response = CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(false) // アプリ側で出すためtrueでもいいが、システム側の通知も一応残す設定(false)
                .build()
            
            respondToCall(callDetails, response)
            return
        }
        
        // 2. オーバーレイ表示 (ブロックされなかった場合)
        Log.i("CallDetectorService", "Allowing call from $rawNumber, showing overlay.")
        
        // 許可ログ保存
        historyRepo.addHistory(rawNumber, timestamp, "許可", "AI解析中...")
        
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra("PHONE_NUMBER", rawNumber)
            putExtra("TIMESTAMP", timestamp)
        }
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e("CallDetectorService", "Failed to start overlay service", e)
        }

        // 通話は許可
        val response = CallResponse.Builder()
            .setDisallowCall(false) // ブロックしない
            .build()
        
        respondToCall(callDetails, response)
    }

    private fun showBlockNotification(phoneNumber: String, reason: String?) {
        val channelId = "blocked_call_channel_popup" // Changed ID to force new settings
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create channel if not exists
            if (manager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    "ブロック着信通知 (ポップアップ)",
                    NotificationManager.IMPORTANCE_HIGH // High importance for Heads-up
                ).apply {
                    description = "着信ブロック時にポップアップで通知します"
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }

        // 通知タップ時にMainActivityを開く
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = if (reason != null) "電話番号: $phoneNumber\n理由: $reason" else "電話番号: $phoneNumber"

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel) // ×アイコン
            .setContentTitle("着信をブロックしました")
            .setContentText("ブロック理由: ${reason ?: "不明"}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)) // 詳細表示用にBigText
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // High priority for pre-Oreo and heuristic
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
