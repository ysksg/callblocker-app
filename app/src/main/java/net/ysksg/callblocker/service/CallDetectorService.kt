package net.ysksg.callblocker.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.content.Intent
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.telecom.CallScreeningService.CallResponse
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.launch
import net.ysksg.callblocker.MainActivity
import net.ysksg.callblocker.data.BlockRuleRepository
import net.ysksg.callblocker.data.BlockHistoryRepository
import net.ysksg.callblocker.data.GeminiRepository
import net.ysksg.callblocker.util.PhoneNumberFormatter

/**
 * 着信時にシステムから呼び出され、着信の許可・拒否を判定するサービス。
 * CallScreeningServiceを継承し、OS標準の着信ブロック機能と連携します。
 */
class CallDetectorService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val originalRawNumber = callDetails.handle?.schemeSpecificPart
        Log.i("CallDetectorService", "着信検知: $originalRawNumber, 方向: ${callDetails.callDirection}")
        
        // 発信時は何もしない (ブロック判定もオーバーレイ表示もしない)
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
             Log.i("CallDetectorService", "発信のため無視します")
             // デフォルトの応答（何もしない＝許可）を返す
             respondToCall(callDetails, CallResponse.Builder().build())
             return
        }
        
        val rawNumber = originalRawNumber ?: ""
        if (originalRawNumber == null) {
            Log.w("CallDetectorService", "電話番号がnullです。空文字としてルール判定を行います。")
        }

        val repository = BlockRuleRepository(applicationContext)
        val blockResult = repository.checkBlock(rawNumber)
        Log.i("CallDetectorService", "判定結果: ${blockResult.shouldBlock}, 理由: ${blockResult.reason}")

        val timestamp = System.currentTimeMillis()
        val historyRepo = BlockHistoryRepository(applicationContext)

        // 1. ブロック判定
        if (blockResult.shouldBlock) {
            Log.i("CallDetectorService", "$rawNumber からの着信をブロックします")
            
            // 表示用にフォーマット
            val formattedNumber = PhoneNumberFormatter.format(rawNumber)
            
            // AI解析中として保存
            historyRepo.addHistory(rawNumber, timestamp, blockResult.reason, "AI解析中...") 

            // 非同期でAI解析実行
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val geminiRepo = GeminiRepository(applicationContext)
                    val aiResult = geminiRepo.checkPhoneNumber(rawNumber)
                    historyRepo.updateHistory(timestamp, aiResult)
                } catch (e: Exception) {
                    Log.e("CallDetectorService", "AI解析に失敗しました", e)
                    historyRepo.updateHistory(timestamp, "AI解析失敗")
                }
            }

            // 通知を表示
            showBlockNotification(formattedNumber, blockResult.reason)

            // ブロック実行
            val response = CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(false) // アプリ側で通知を出すためfalseも可だが、念のためシステム通知も許可
                .build()
            
            respondToCall(callDetails, response)
            return
        }
        
        // 2. オーバーレイ表示 (ブロックされなかった場合)
        Log.i("CallDetectorService", "$rawNumber からの着信を許可し、オーバーレイを表示します")
        
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
            Log.e("CallDetectorService", "オーバーレイサービスの起動に失敗しました", e)
        }

        // 通話は許可
        val response = CallResponse.Builder()
            .setDisallowCall(false) // ブロックしない
            .build()
        
        respondToCall(callDetails, response)
    }

    private fun showBlockNotification(phoneNumber: String, reason: String?) {
        val channelId = "blocked_call_channel_popup"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // チャンネルが存在しない場合は作成
            if (manager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(
                    channelId,
                    "ブロック着信通知 (ポップアップ)",
                    NotificationManager.IMPORTANCE_HIGH // ヘッドアップ通知用
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
            .setPriority(NotificationCompat.PRIORITY_HIGH) 
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
