package net.ysksg.callblocker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

import android.content.pm.ServiceInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf

import net.ysksg.callblocker.repository.GeminiRepository
import net.ysksg.callblocker.repository.SearchSettingsRepository
import net.ysksg.callblocker.repository.BlockHistoryRepository
import net.ysksg.callblocker.repository.OverlaySettingsRepository
import net.ysksg.callblocker.model.BlockRule
import net.ysksg.callblocker.model.RegexCondition
import net.ysksg.callblocker.model.BlockResult
import net.ysksg.callblocker.util.PhoneNumberFormatter
import net.ysksg.callblocker.repository.ThemeRepository
import net.ysksg.callblocker.ui.theme.CallBlockerTheme
import net.ysksg.callblocker.repository.AiStatus
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.ui.draw.shadow

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

@Suppress("DEPRECATION")
class OverlayService : LifecycleService(), ViewModelStoreOwner, SavedStateRegistryOwner {
    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams
    
    // ViewModelStoreOwnerの実装
    private val appViewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = appViewModelStore

    // SavedStateRegistryOwnerの実装
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1, 
                createNotification(), 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
        } else {
            startForeground(1, createNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        val phoneNumber = intent?.getStringExtra("PHONE_NUMBER") ?: "Unknown"
        val timestamp = intent?.getLongExtra("TIMESTAMP", 0L) ?: 0L
        showOverlay(phoneNumber, timestamp)
        
        return START_NOT_STICKY
    }

    private fun showOverlay(phoneNumber: String, timestamp: Long) {
        if (overlayView != null) return

        val overlayRepo = OverlaySettingsRepository(this)
        val prefs = getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)
        val metrics = resources.displayMetrics
        
        // 詳細表示時のデフォルト位置（中央上部）
        val defaultX = 0 
        val defaultY = metrics.heightPixels / 4
        
        // 最小化（アイコン）表示時のデフォルト位置（右端中央）
        val defaultMinX = metrics.widthPixels - 200
        val defaultMinY = metrics.heightPixels / 2

        val isPositionSaveEnabled = overlayRepo.isPositionSaveEnabled()
        
        // それぞれの状態の保存位置を読み込み（なければデフォルト）
        val savedX = if (isPositionSaveEnabled) prefs.getInt("overlay_x", defaultX) else defaultX
        val savedY = if (isPositionSaveEnabled) prefs.getInt("overlay_y", defaultY) else defaultY
        val savedMinX = if (isPositionSaveEnabled) prefs.getInt("minimized_x", defaultMinX) else defaultMinX
        val savedMinY = if (isPositionSaveEnabled) prefs.getInt("minimized_y", defaultMinY) else defaultMinY

        // Register PhoneStateListener for Auto Close
        if (overlayRepo.isAutoCloseEnabled()) {
             registerPhoneStateListener()
        }

        val isExpandedStart = (overlayRepo.getDefaultOverlayState() == OverlaySettingsRepository.STATE_EXPANDED)
        val initialWidth = if (isExpandedStart) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT

        params = WindowManager.LayoutParams(
            initialWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (isExpandedStart) {
                x = savedX
                y = savedY
            } else {
                x = savedMinX
                y = savedMinY
            }
        }

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            setContent {
                val themeRepo = remember { ThemeRepository(this@OverlayService) }
                val themeMode = themeRepo.getThemeMode()
                val darkTheme = when (themeMode) {
                    ThemeRepository.THEME_DARK -> true
                    ThemeRepository.THEME_LIGHT -> false
                    else -> isSystemInDarkTheme()
                }

                CallBlockerTheme(darkTheme = darkTheme) {
                    OverlayScreen(
                        phoneNumber = phoneNumber,
                        timestamp = timestamp,
                        initialExpanded = isExpandedStart,
                        onClose = { stopSelf() },
                        onSearch = { searchNumber(phoneNumber) },
                        onAnswer = { acceptCall() },
                        onReject = { rejectCall() },
                        onDrag = { x, y -> updateLocation(x, y) },
                        onPositionChange = { x, y, isExpanded -> savePosition(x, y, isExpanded) },
                        onToggleExpand = { expanded -> toggleOverlaySize(expanded) }
                    )
                }
            }
        }

        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun updateLocation(deltaX: Float, deltaY: Float) {
        params.x += deltaX.roundToInt()
        params.y += deltaY.roundToInt()
        // 画面外に出ないように制限（簡易的）
        val metrics = resources.displayMetrics
        params.x = params.x.coerceIn(0, metrics.widthPixels)
        params.y = params.y.coerceIn(0, metrics.heightPixels)

        try {
            windowManager.updateViewLayout(overlayView, params)
        } catch (e: Exception) {
            // Ignore if view is removed
        }
    }

    private fun toggleOverlaySize(isExpanded: Boolean) {
        val prefs = getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)
        val metrics = resources.displayMetrics

        params.width = if (isExpanded) {
            WindowManager.LayoutParams.MATCH_PARENT
        } else {
            WindowManager.LayoutParams.WRAP_CONTENT
        }

        // 切り替え先の保存済みの位置へ戻す
        if (isExpanded) {
            params.x = prefs.getInt("overlay_x", 0)
            params.y = prefs.getInt("overlay_y", metrics.heightPixels / 4)
        } else {
            params.x = prefs.getInt("minimized_x", metrics.widthPixels - 200)
            params.y = prefs.getInt("minimized_y", metrics.heightPixels / 2)
        }
        
        try {
            windowManager.updateViewLayout(overlayView, params)
        } catch (e: Exception) {
            // Ignore if view is removed
        }
    }
    
    // Save position on drag end
    private fun savePosition(x: Int, y: Int, isExpanded: Boolean) {
        val overlayRepo = OverlaySettingsRepository(this)
        if (!overlayRepo.isPositionSaveEnabled()) return

        val prefs = getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        if (isExpanded) {
            editor.putInt("overlay_x", params.x)
            editor.putInt("overlay_y", params.y)
        } else {
            editor.putInt("minimized_x", params.x)
            editor.putInt("minimized_y", params.y)
        }
        editor.apply()
    }

    private fun acceptCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
            try {
                @Suppress("MissingPermission")
                telecomManager.acceptRingingCall()
                android.widget.Toast.makeText(this, "応答しました", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("OverlayService", "通話応答エラー", e)
            }
        }
        stopSelf()
    }

    private fun rejectCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
            try {
                @Suppress("MissingPermission")
                telecomManager.endCall()
                android.widget.Toast.makeText(this, "切断しました", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("OverlayService", "通話終了エラー", e)
            }
        }
        stopSelf()
    }

    private fun searchNumber(number: String) {
        val repo = SearchSettingsRepository(this)
        val template = repo.getSearchUrlTemplate()
        val url = template.replace("{number}", number)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        stopSelf()
    }

    // PhoneStateListener
    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null

    private fun registerPhoneStateListener() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                super.onCallStateChanged(state, phoneNumber)
                if (state == TelephonyManager.CALL_STATE_IDLE) {
                    stopSelf()
                }
            }
        }
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (overlayView != null) {
            windowManager.removeView(overlayView)
            overlayView = null
        }
        
        if (phoneStateListener != null && telephonyManager != null) {
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
            phoneStateListener = null
        }
        appViewModelStore.clear()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "overlay_service",
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "overlay_service")
            .setContentTitle("着信監視中")
            .setContentText("着信情報を表示しています")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
            .setContentTitle("着信監視中")
            .setContentText("着信情報を表示しています")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .build()
        }
    }
}

@Composable
fun OverlayScreen(
    phoneNumber: String,
    timestamp: Long,
    initialExpanded: Boolean = false,
    onClose: () -> Unit,
    onSearch: () -> Unit,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onPositionChange: (Int, Int, Boolean) -> Unit,
    onToggleExpand: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val geminiRepo = remember { GeminiRepository(context) }
    val historyRepo = remember { BlockHistoryRepository(context) }
    
    var isExpanded by remember { mutableStateOf<Boolean>(initialExpanded) }
    var geminiResult by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf<Boolean>(true) }
    var aiStatus by remember { mutableStateOf<AiStatus>(AiStatus.PENDING) }

    androidx.compose.runtime.LaunchedEffect(timestamp) {
        val item = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            historyRepo.getHistoryByTimestamp(timestamp)
        }
        
        // キャッシュが有効な場合は、同じ番号の最新の結果を探す
        val cacheResult = if (geminiRepo.isAiCacheEnabled() && (item?.aiResult == null || item.aiStatus == AiStatus.PENDING)) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                historyRepo.getHistory().firstOrNull { it.number == phoneNumber && it.aiResult != null && it.aiStatus != AiStatus.PENDING }
            }
        } else null

        if (item?.aiResult != null && item.aiStatus != AiStatus.PENDING) {
            geminiResult = item.aiResult
            aiStatus = item.aiStatus
            isLoading = false
        } else if (cacheResult != null) {
            geminiResult = cacheResult.aiResult
            aiStatus = cacheResult.aiStatus
            isLoading = false
            // キャッシュから取得した場合は履歴も更新
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                historyRepo.updateHistory(timestamp, cacheResult.aiResult!!, cacheResult.aiStatus)
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == "net.ysksg.callblocker.AI_ANALYSIS_COMPLETED") {
                    val ts = intent.getLongExtra("TIMESTAMP", 0L)
                    android.util.Log.d("OverlayService", "AI解析結果受信: $ts")
                    if (ts == timestamp) {
                         geminiResult = intent.getStringExtra("AI_RESULT")
                         val statusStr = intent.getStringExtra("AI_STATUS")
                         aiStatus = try { AiStatus.valueOf(statusStr ?: AiStatus.ERROR.name) } catch(e: Exception) { AiStatus.ERROR }
                         isLoading = false
                    }
                }
            }
        }
        val filter = android.content.IntentFilter("net.ysksg.callblocker.AI_ANALYSIS_COMPLETED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Box(
        modifier = (if (isExpanded) Modifier.fillMaxWidth() else Modifier.wrapContentSize())
            .padding(8.dp)
            .pointerInput(isExpanded) { // 状態が変わったら入力をリセット
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = { onPositionChange(0, 0, isExpanded) }
                ) { change, dragAmount ->
                    change.consume()
                    // 展開時は上下のみ、最小化時は上下左右
                    val dx = if (isExpanded) 0f else dragAmount.x
                    onDrag(dx, dragAmount.y)
                }
            }
    ) {
        if (!isExpanded) {
            // Minimized View
            FloatingActionButton(
                onClick = { 
                    onToggleExpand(true)
                    isExpanded = true 
                },
                containerColor = Color.Transparent,
                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                shape = CircleShape,
                modifier = Modifier.shadow(6.dp, CircleShape).size(64.dp)
            ) {
                AndroidView(
                    factory = { ctx ->
                        android.widget.ImageView(ctx).apply {
                            setImageDrawable(ctx.packageManager.getApplicationIcon(ctx.packageName))
                            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Expanded View
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (phoneNumber.isEmpty()) "（非通知）" else PhoneNumberFormatter.format(phoneNumber),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // AI Result Section
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        if (isLoading && phoneNumber.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp), 
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("AI調査中...", color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            Column {
                                Text("Gemini AI解析:", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = geminiResult ?: (if (phoneNumber.isEmpty()) "非通知のため解析対象外" else "情報なし"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
 
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 通話操作アクション
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onReject,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                             Text("切断", style = MaterialTheme.typography.labelLarge)
                        }
                        
                        Button(
                            onClick = onAnswer,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Call, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("応答", style = MaterialTheme.typography.labelLarge)
                        }
                    }
 
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSearch,
                            enabled = phoneNumber.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("検索", style = MaterialTheme.typography.labelSmall)
                        }
                        
                        OutlinedButton(
                            onClick = { 
                                val ruleRepo = net.ysksg.callblocker.repository.BlockRuleRepository(context)
                                if (phoneNumber.isNotEmpty()) {
                                    val rule = BlockRule(
                                        name = "クイックブロック: $phoneNumber",
                                        conditions = mutableListOf(RegexCondition(pattern = phoneNumber))
                                    )
                                    ruleRepo.saveRule(rule)
                                    android.widget.Toast.makeText(context, "ルールに追加しました", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                onClose()
                            },
                            modifier = Modifier.weight(1.2f),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ルール追加", style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = onClose,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("閉じる", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
