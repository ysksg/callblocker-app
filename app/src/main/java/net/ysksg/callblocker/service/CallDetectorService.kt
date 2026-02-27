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
import net.ysksg.callblocker.MainActivity
import net.ysksg.callblocker.util.PhoneNumberFormatter
import net.ysksg.callblocker.model.RuleAction
import net.ysksg.callblocker.repository.BlockRuleRepository
import net.ysksg.callblocker.repository.BlockHistoryRepository
import net.ysksg.callblocker.repository.GeminiRepository
import net.ysksg.callblocker.repository.BlockType
import net.ysksg.callblocker.repository.AiStatus
import kotlinx.coroutines.launch


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
        val geminiRepo = GeminiRepository(applicationContext)
        val historyRepo = BlockHistoryRepository(applicationContext)
        val isAiEnabled = geminiRepo.isAiAnalysisEnabled()
        val blockResult = repository.checkBlock(rawNumber)
        Log.i("CallDetectorService", "判定結果: ${blockResult.shouldBlock}, 理由: ${blockResult.reason}")

        val timestamp = System.currentTimeMillis()

        // 共通: 履歴への初期登録とAI解析開始
        val historyReason = if (blockResult.shouldBlock) blockResult.reason else "許可"

        if (blockResult.shouldBlock) {
             val actionName = if (blockResult.ruleAction == RuleAction.SILENCE) "無音化" else "ブロック"
             Log.i("CallDetectorService", "$rawNumber からの着信を ${actionName} します")
        } else {
             Log.i("CallDetectorService", "$rawNumber からの着信を許可し、オーバーレイを表示します")
        }
        
        val historyBlockType = when {
            !blockResult.shouldBlock -> BlockType.ALLOWED
            blockResult.ruleAction == RuleAction.SILENCE -> BlockType.SILENCED
            else -> BlockType.REJECTED
        }

        val initialAiStatus = if (rawNumber.isEmpty()) AiStatus.NONE else AiStatus.PENDING
        
        // キャッシュチェック
        var cachedResult: String? = null
        if (isAiEnabled && rawNumber.isNotEmpty() && geminiRepo.isAiCacheEnabled()) {
             cachedResult = historyRepo.getCachedAiResult(rawNumber)
        }

        if (cachedResult != null) {
            Log.i("CallDetectorService", "キャッシュされた解析結果を使用します: $cachedResult")
            historyRepo.addHistory(rawNumber, timestamp, historyReason, cachedResult, AiStatus.SUCCESS, historyBlockType)
        } else {
            val initialAiResult = ""
            val initialAiStatus = if (rawNumber.isEmpty()) AiStatus.NONE else AiStatus.PENDING
            historyRepo.addHistory(rawNumber, timestamp, historyReason, initialAiResult, initialAiStatus, historyBlockType)
            if (isAiEnabled && rawNumber.isNotEmpty()) {
                startAiAnalysis(rawNumber, timestamp)
            }
        }

        if (blockResult.shouldBlock) {
            // ブロック処理
            val isSilence = blockResult.ruleAction == RuleAction.SILENCE
            val formattedNumber = PhoneNumberFormatter.format(rawNumber)
            showBlockNotification(formattedNumber, blockResult.reason, isSilence)

            val response = CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(!isSilence) // 無音化の場合は拒否（切断）しない
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
            
            respondToCall(callDetails, response)
        } else {
            // 許可処理 (オーバーレイ)
            // 設定で有効な場合のみオーバーレイを表示
            val overlayRepo = net.ysksg.callblocker.repository.OverlaySettingsRepository(applicationContext)
            if (overlayRepo.isOverlayEnabled()) {
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
            } else {
                Log.i("CallDetectorService", "オーバーレイ表示は設定により無効化されています")
            }
 
            // 通話は許可
            val response = CallResponse.Builder()
                .setDisallowCall(false) // ブロックしない
                .build()
            
            respondToCall(callDetails, response)
        }
    }

    private fun startAiAnalysis(number: String, timestamp: Long) {
         Log.i("CallDetectorService", "AI解析を開始します: $number")
         kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val historyRepo = BlockHistoryRepository(applicationContext)
            var resultString = ""
            var status = AiStatus.ERROR
            try {
                val geminiRepo = GeminiRepository(applicationContext)
                val (aiResult, aiStatus) = geminiRepo.checkPhoneNumber(number)
                Log.i("CallDetectorService", "AI解析完了: $aiResult (Status: $aiStatus)")
                historyRepo.updateHistory(timestamp, aiResult, aiStatus)
                resultString = aiResult
                status = aiStatus
            } catch (e: Exception) {
                Log.e("CallDetectorService", "AI解析失敗: ${e.message}", e)
                resultString = "AI解析失敗"
                historyRepo.updateHistory(timestamp, resultString, AiStatus.ERROR)
            }
            
            // Overlay等への通知
            val intent = Intent("net.ysksg.callblocker.AI_ANALYSIS_COMPLETED").apply {
                putExtra("PHONE_NUMBER", number)
                putExtra("TIMESTAMP", timestamp)
                putExtra("AI_RESULT", resultString)
                putExtra("AI_STATUS", status.name)
                setPackage(applicationContext.packageName)
            }
            sendBroadcast(intent)
        }
    }

    private fun showBlockNotification(phoneNumber: String, reason: String?, isSilence: Boolean = false) {
        val channelId = "blocked_call_channel_popup"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val actionLabel = if (isSilence) "無音化" else "ブロック"

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

        // 通知タップ時にMainActivityを開く (履歴タブを指定)
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("EXTRA_NAVIGATE_TO", "HISTORY")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = if (reason != null) "電話番号: $phoneNumber\n理由: $reason" else "電話番号: $phoneNumber"

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(if (isSilence) android.R.drawable.ic_lock_silent_mode else android.R.drawable.ic_menu_close_clear_cancel)
            .setContentTitle("着信を${actionLabel}しました")
            .setContentText("${actionLabel}理由: ${reason ?: "不明"}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)) // 詳細表示用にBigText
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH) 
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
