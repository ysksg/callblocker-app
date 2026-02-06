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

import net.ysksg.callblocker.data.GeminiRepository
import net.ysksg.callblocker.data.BlockHistoryRepository
import net.ysksg.callblocker.data.OverlaySettingsRepository
import net.ysksg.callblocker.util.PhoneNumberFormatter
import net.ysksg.callblocker.data.ThemeRepository
import net.ysksg.callblocker.ui.theme.CallBlockerTheme
import androidx.compose.foundation.isSystemInDarkTheme
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
        val defaultX = metrics.widthPixels - 200 // Approximate right side
        val defaultY = metrics.heightPixels / 2 - 100 // Approximate center

        val savedX = if (overlayRepo.isPositionSaveEnabled()) prefs.getInt("overlay_x", defaultX) else defaultX
        val savedY = if (overlayRepo.isPositionSaveEnabled()) prefs.getInt("overlay_y", defaultY) else defaultY

        // Register PhoneStateListener for Auto Close
        if (overlayRepo.isAutoCloseEnabled()) {
             registerPhoneStateListener()
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED, // Enable on lock screen
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
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
                    val initialState = overlayRepo.getDefaultOverlayState()
                    val isExpandedStart = (initialState == OverlaySettingsRepository.STATE_EXPANDED)

                    OverlayScreen(
                        phoneNumber = phoneNumber,
                        timestamp = timestamp,
                        initialExpanded = isExpandedStart,
                        onClose = { stopSelf() },
                        onSearch = { searchNumber(phoneNumber) },
                        onDrag = { x, y -> updateLocation(x, y) },
                        onPositionChange = { x, y -> savePosition(x, y) }
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
        try {
            windowManager.updateViewLayout(overlayView, params)
        } catch (e: Exception) {
            // Ignore if view is removed
        }
    }
    
    // Save position on drag end (simplified by saving on each move or use robust state, here we expose a callback)
    private fun savePosition(x: Int, y: Int) {
        val overlayRepo = OverlaySettingsRepository(this)
        if (!overlayRepo.isPositionSaveEnabled()) return

        val prefs = getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("overlay_x", params.x).putInt("overlay_y", params.y).apply()
    }

    private fun searchNumber(number: String) {
        val repo = GeminiRepository(this)
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
    onDrag: (Float, Float) -> Unit,
    onPositionChange: (Int, Int) -> Unit,
    onToggleExpand: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val geminiRepo = remember { GeminiRepository(context) }
    val historyRepo = remember { BlockHistoryRepository(context) }
    
    // Gemini Search State
    var geminiResult by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var isLoading by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    // Expand/Collapse State
    var isExpanded by remember { androidx.compose.runtime.mutableStateOf(initialExpanded) }

    androidx.compose.runtime.LaunchedEffect(timestamp) {
        val item = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            historyRepo.getHistoryByTimestamp(timestamp)
        }
        // すでに結果が出ている場合はそれを表示
        if (item?.aiResult != null && item.aiResult != "AI解析中...") {
            geminiResult = item.aiResult
            isLoading = false
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
        modifier = Modifier
            .padding(16.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onPositionChange(0, 0) } // Trigger save
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
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
                modifier = Modifier.widthIn(max = 300.dp) // Limit width for better appearance
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "着信情報",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall
                        )
                        IconButton(onClick = { 
                            onToggleExpand(false)
                            isExpanded = false 
                        }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                    contentDescription = "Minimize", 
                                    tint = Color.Gray 
                                )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = PhoneNumberFormatter.format(phoneNumber),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // AI Result Section
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        if (isLoading) {
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
                                    text = geminiResult ?: "情報なし",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(onClick = onSearch) {
                            Text("Web")
                        }
                        OutlinedButton(onClick = onClose) {
                            Text("閉じる")
                        }
                    }
                }
            }
        }
    }
}
