package org.bm.app

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Maximize
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

// 自定义Dialog类，实现所有三个必需的接口
class FloatWindowDialog(context: Context) : Dialog(context), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val _viewModelStore = ViewModelStore()
    private val savedStateRegistryController = androidx.savedstate.SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = _viewModelStore

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 在Dialog的onCreate中初始化SavedStateRegistryController
        // 这是正确的时机，符合生命周期时序要求
        savedStateRegistryController.performRestore(savedInstanceState)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun show() {
        super.show()
        // 显示时更新生命周期状态
        if (lifecycleRegistry.currentState < Lifecycle.State.STARTED) {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        }
        if (lifecycleRegistry.currentState < Lifecycle.State.RESUMED) {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }
    }

    override fun dismiss() {
        if (lifecycleRegistry.currentState == Lifecycle.State.RESUMED) {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        }
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.dismiss()
    }

    override fun onSaveInstanceState(): Bundle {
        val bundle = super.onSaveInstanceState()
        savedStateRegistryController.performSave(bundle)
        return bundle
    }
}

class FloatWindowService : AccessibilityService(), LifecycleOwner {

    // 手动管理生命周期（为了给协程 lifecycleScope 用）
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private var windowManager: WindowManager? = null
    private var floatDialog: FloatWindowDialog? = null
    private var composeView: ComposeView? = null
    private var floatingDotView: ImageView? = null // 浮动圆点视图
    private var isPanelShowing = true
    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()
    private var isAutomationRunning by mutableStateOf(false)
    private var automationJob: Job? = null
    private var isMaximized by mutableStateOf(true) // 悬浮窗默认最大化
    private var lastClickTime = 0L
    private val CLICK_INTERVAL = 500L // 500ms内禁止重复点击
    private var isHandling = false    // 标记是否正在处理点击请求
//    在开始自动化任务时获取 CPU 唤醒锁，保证 CPU 不休眠；任务结束或用户点击停止时释放
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val CHANNEL_ID = "float_window_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 这里可以监听到界面的变化、点击事件等，目前暂时留空
    }

    override fun onInterrupt() {
        // 当无障碍服务被打断时触发
        addLog("⚠️ 无障碍服务被打断")
    }

    override fun onCreate() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        super.onCreate()
        // 创建前台服务通知通道
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        windowManager = getSystemService<WindowManager>() ?: run {
            Log.e("FloatWindow", "获取WindowManager失败")
            stopSelf() // 获取失败直接停止服务
            return
        }
        showFloatWindow()
        createFloatingDot()
        floatingDotView?.visibility = View.GONE
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        showFloatWindow()
        addLog("✅ 无障碍服务已连接并获取特权")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (floatDialog?.isShowing == false && floatingDotView?.visibility != View.VISIBLE) {
            showFloatWindow()
        }
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        stopAutomation()
        hideFloatWindow()
        hideFloatingDot() // 确保销毁时隐藏圆点
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        super.onDestroy()
    }

//    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        // minSdk=32，Oreo以上版本，直接创建
        val channel = NotificationChannel(
            CHANNEL_ID,
            "浮动窗服务",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "BM悬浮窗服务"
        channel.enableLights(false)
        channel.enableVibration(false)
        val notificationManager = getSystemService<NotificationManager>()
        notificationManager?.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        // minSdk=32，Oreo以上版本，直接使用新API
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("BM")
            .setContentText("悬浮窗服务运行中")
            .setSmallIcon(android.R.drawable.ic_notification_overlay)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    // 切换悬浮窗最大化/最小化状态
    private fun toggleMaximized() {
        isMaximized = !isMaximized
        floatDialog?.window?.let { window ->
            val params = window.attributes
            if (isMaximized) {
                // 最大化：铺满屏幕
                params.width = WindowManager.LayoutParams.MATCH_PARENT
                params.height = WindowManager.LayoutParams.MATCH_PARENT
                params.gravity = Gravity.FILL
                params.x = 0
                params.y = 0
            } else {
                // 恢复原始大小
                params.width = WindowManager.LayoutParams.WRAP_CONTENT
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
                params.gravity = Gravity.TOP or Gravity.START
                params.x = 100
                params.y = 100
            }
            window.attributes = params
            window.setLayout(params.width, params.height)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            // PARTIAL_WAKE_LOCK 保证 CPU 运转，即使屏幕关闭
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "BMApp::AutomationWakeLock"
            )
        }
        wakeLock?.acquire(10 * 60 * 1000L /* 可选：设置最大超时时间，例如10分钟，防止死锁耗尽电量 */)
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    // 结束整个应用
    private fun exitApp() {
        // 停止自动化任务
        stopAutomation()
        hideFloatWindow()
        hideFloatingDot()
        stopSelf()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    // 获取真实设备型号
    private fun getDeviceModel(): String {
        return Build.MANUFACTURER + " " + Build.MODEL
    }

    // 获取真实设备分辨率
    private fun getDeviceResolution(): String {
        val displayMetrics = DisplayMetrics()
        val windowManager = getSystemService<WindowManager>()
        // 使用新的显示获取方式
        val display = windowManager?.defaultDisplay
        display?.getRealMetrics(displayMetrics)
        return "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}"
    }

//    判断是否允许点击（防抖核心方法）
    private fun canClick(): Boolean {
        val now = System.currentTimeMillis()
        // 正在处理中 或 点击间隔小于500ms → 拒绝点击
        if (isHandling || now - lastClickTime < CLICK_INTERVAL) {
            return false
        }
        lastClickTime = now
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatWindow() {
        isMaximized = true
        if (floatDialog != null) {
            if (floatDialog?.isShowing == false) {
                try {
                    // 1. 恢复Window参数（dismiss后会丢失）
                    floatDialog?.window?.apply {
                        setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                        setFlags(
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        )
                        setGravity(if (isMaximized) Gravity.FILL else Gravity.TOP or Gravity.START)

                        // 恢复尺寸和位置
                        attributes.width = if (isMaximized)
                            WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT
                        attributes.height = if (isMaximized)
                            WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT
                        attributes.x = if (isMaximized) 0 else 100
                        attributes.y = if (isMaximized) 0 else 100

                        attributes = attributes
                        setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                        setDimAmount(0f)
                    }
                    // 2. 显示Dialog（复用已有实例）
                    floatDialog?.show()
                    addLog("复用已有浮动窗口（状态：${if (isMaximized) "最大化" else "普通"}）")
                } catch (e: Exception) {
                    Log.e("FloatWindow", "复用窗口失败，重新创建", e)
                    floatDialog = null // 复用失败才重置，避免死循环
                    Handler(Looper.getMainLooper()).post {
                        showFloatWindow()
                    }
                }
            }
            return
        }
        if (floatDialog?.isShowing == true) return
        // 确保isMaximized为true
        isMaximized = true
        // 创建自定义Dialog作为系统级悬浮窗
        floatDialog = FloatWindowDialog(this).apply {
            val dialogInstance = this
            // 设置Dialog的Window参数
            window?.apply {
                // minSdk=32，直接使用TYPE_APPLICATION_OVERLAY
                setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)

                // 最大化状态设置
                setFlags(
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                )
                setGravity(Gravity.FILL)

                // 强制最大化 - 使用固定值确保最大化
                attributes.width = WindowManager.LayoutParams.MATCH_PARENT
                attributes.height = WindowManager.LayoutParams.MATCH_PARENT
                attributes.x = 0
                attributes.y = 0

                // 应用修改
                attributes = attributes

                setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                setDimAmount(0f) // 不显示遮罩
            }

            // 设置无标题
            requestWindowFeature(Window.FEATURE_NO_TITLE)

            // 创建ComposeView直接作为Dialog内容
            if (composeView == null) {
                composeView = ComposeView(context).apply {
                    // 设置所有必要的Owner为Dialog自身
                    // Dialog现在实现了所有三个接口，并在正确时机初始化了SavedStateRegistryController
                    setViewTreeLifecycleOwner(dialogInstance)
                    setViewTreeViewModelStoreOwner(dialogInstance)
                    setViewTreeSavedStateRegistryOwner(dialogInstance)
                    // 设置ComposeView的生命周期策略
                    setViewCompositionStrategy(
                        ViewCompositionStrategy.DisposeOnLifecycleDestroyed(
                            dialogInstance
                        )
                    )
                    // 设置Compose内容
                    setContent {
                        MaterialTheme {
                            FloatWindowContent(
                                logMessages = logMessages.collectAsState().value,
                                isRunning = isAutomationRunning,
                                isMaximized = isMaximized,
                                onStart = { startAutomation() },
                                onStop = { stopAutomation() },
                                onClearLog = { clearLog() },
                                onCopyLog = { copyLog() },
                                onMaximize = { toggleMaximized() },
                                onClose = { exitApp() }
                            )
                        }
                    }

                    // 实现拖拽功能
                    setOnTouchListener(object : View.OnTouchListener {
                        private var downX = 0f
                        private var downY = 0f
                        private var startX = 0
                        private var startY = 0

                        override fun onTouch(v: View, event: MotionEvent): Boolean {
                            val window = floatDialog?.window ?: return false
                            val params = window.attributes

                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    downX = event.rawX
                                    downY = event.rawY
                                    startX = params.x
                                    startY = params.y
                                    return true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    val dx = (event.rawX - downX).toInt()
                                    val dy = (event.rawY - downY).toInt()
                                    params.x = startX + dx
                                    params.y = startY + dy
                                    window.attributes = params
                                    return true
                                }
                                else -> return false
                            }
                        }
                    })
                }
            }
            // 直接将ComposeView设置为Dialog的内容
            composeView?.let { setContentView(it) }
        }

        try {
            floatDialog?.show()
            if (_logMessages.value.isEmpty()) {
                addLog("浮动窗口已显示")
                addLog("设备型号: ${getDeviceModel()}")
                addLog("设备名称: ${Build.PRODUCT}")
                addLog("Android版本: ${Build.VERSION.RELEASE}")
                addLog("SDK版本(API级别): ${Build.VERSION.SDK_INT}")
                addLog("设备分辨率: ${getDeviceResolution()}")
                addLog("初始化完成")
            }

        } catch (e: Exception) {
            Log.e("FloatWindow", "Error showing float window", e)
            addLog("显示浮动窗口失败: ${e.message}")
        }
    }

    private fun hideFloatWindow() {
        floatDialog?.let {
            try {
                if (it.isShowing) {
                    it.dismiss()
                }
            } catch (e: Exception) {
                Log.e("FloatWindow", "Error hiding float window", e)
            }
        }
    }

    private fun switchToDot() {
        isPanelShowing = false
        // 隐藏主面板，显示悬浮球
        floatDialog?.hide()
        floatingDotView?.visibility = View.VISIBLE
    }

    private fun switchToPanel() {
        isPanelShowing = true
        // 隐藏悬浮球，显示主面板
        floatingDotView?.visibility = View.GONE
        floatDialog?.show()

        // 强制将主面板层级提升到最前
        floatDialog?.window?.let {
            windowManager?.updateViewLayout(it.decorView, it.attributes)
        }
    }

    // 超小尺寸+透明背景+自定义文字色悬浮球
    private fun createFloatingDot() {
        if (floatingDotView != null) return
        floatingDotView = object : ImageView(this) {
            override fun performClick(): Boolean {
                super.performClick()
                if (isAutomationRunning) {
                    stopAutomation() // 内部包含了 switchToPanel 的逻辑
                } else {
                    switchToPanel()
                }
                return true
            }
        }.apply {
            // 1. 核心设置：24dp超小尺寸 + 完全透明背景 + 自定义文字色
            val dotSize = 24.dpToPx() // 超小尺寸（保证可点击）
            val bitmap = Bitmap.createBitmap(dotSize, dotSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                // 背景透明：0x00表示alpha为0（全透），后面颜色无意义
                color = 0x20FFFFFF
            }
            // 绘制透明圆角背景（仅占位，视觉上完全看不见）
            val rect = RectF(0f, 0f, dotSize.toFloat(), dotSize.toFloat())
            canvas.drawRoundRect(rect, 4.dpToPx().toFloat(), 4.dpToPx().toFloat(), paint)

            // 2. 自定义文字颜色（重点）：推荐用醒目但不刺眼的橙色，可自行替换
            paint.apply {
                // 可选文字色（挑一个喜欢的，取消注释即可）：
                color = 0xFFFF5722.toInt() // 醒目橙色（推荐）

                textSize = 10.dpToPx().toFloat() // 适配24dp尺寸的文字
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD // 加粗保证文字清晰可见
                isAntiAlias = true // 抗锯齿，文字更顺滑
            }
            val textY = dotSize / 2f - (paint.descent() + paint.ascent()) / 2
            canvas.drawText("BM", dotSize / 2f, textY, paint)
            setImageBitmap(bitmap)

            // 3. 布局参数：适配24dp超小尺寸
            val params = WindowManager.LayoutParams(
                dotSize, dotSize,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END // 默认右侧贴边
                x = 0
                y = 300.dpToPx()
            }
            layoutParams = params

            // 4. 拖拽逻辑：适配超小尺寸，保留贴边交互
            setOnTouchListener(object : View.OnTouchListener {
                private var downX = 0f
                private var downY = 0f
                private var startX = 0
                private var startY = 0
                private var isDragging = false
                private val screenWidth = resources.displayMetrics.widthPixels
                private val screenHeight = resources.displayMetrics.heightPixels

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    val window = windowManager ?: return false
                    val params = v.layoutParams as WindowManager.LayoutParams

                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.rawX
                            downY = event.rawY
                            startX = params.x
                            startY = params.y
                            isDragging = false
                            // 超小尺寸仅轻微缩放反馈
                            v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(60).start()
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - downX).toInt()
                            val dy = (event.rawY - downY).toInt()
                            if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                                params.x = startX + dx
                                params.y = startY + dy
                                window.updateViewLayout(v, params)
                                isDragging = true
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            v.animate().scaleX(1f).scaleY(1f).setDuration(60).start()

                            if (!isDragging) {
                                performClick()
                            } else {
                                // 自动贴边逻辑不变
                                val centerX = params.x + dotSize / 2
                                val targetX = if (centerX < screenWidth / 2) 0 else screenWidth - dotSize
                                v.animate()
                                    .x(targetX.toFloat())
                                    .setDuration(150)
                                    .withEndAction {
                                        params.x = targetX
                                        params.gravity = Gravity.TOP or if (targetX == 0) Gravity.START else Gravity.END
                                        window.updateViewLayout(v, params)
                                    }
                                    .start()
                            }
                            return true
                        }
                        else -> return false
                    }
                }
            })
        }
        try {
            windowManager?.addView(floatingDotView, floatingDotView?.layoutParams as WindowManager.LayoutParams)
            addLog("浮动圆点已显示")
        } catch (e: Exception) {
            Log.e("FloatDot", "创建浮动圆点失败", e)
            floatingDotView = null // 失败则重置
        }
    }

    // 辅助方法：dp转px
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    // 隐藏浮动圆点
    private fun hideFloatingDot() {
        floatingDotView?.let { view ->
            try {
                if (view.isAttachedToWindow) { // 检查是否已附加到窗口
                    windowManager?.removeView(view)
                    addLog("浮动圆点已隐藏")
                }
            } catch (e: Exception) {
                Log.e("FloatDot", "隐藏浮动圆点失败", e)
            } finally {
                floatingDotView = null // 无论是否成功都重置引用
            }
        }
    }

    private fun startAutomation() {
        // 防抖+状态校验，防止重复启动
        if (!canClick() || isAutomationRunning) return
        isHandling = true

        isAutomationRunning = true
        addLog("自动化任务启动")
        isMaximized = true
        switchToDot()
        acquireWakeLock()
        isHandling = false
        automationJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // 模拟自动化操作
                repeat(10) {
                    addLog("执行自动化操作 ${it + 1}")
                    delay(1000)
                }
            } catch (e: CancellationException) {
                addLog("✅ 任务正常取消")
            } catch (e: Exception) {
                addLog("❌ 任务异常崩溃: ${e.message}")
            } finally {
                withContext(NonCancellable +Dispatchers.Main) {
                    isAutomationRunning = false
                    isHandling = false
                    switchToPanel()
                    releaseWakeLock()
                    addLog("自动化任务完成")
                }
            }
        }
    }

    private fun stopAutomation() {
        // 新增：防抖+状态校验，防止重复停止（加这2行）
        if (!canClick() || !isAutomationRunning) return
        isHandling = true

        automationJob?.cancel()
        automationJob = null
        isAutomationRunning = false
        addLog("自动化任务停止")
        isMaximized = true
        releaseWakeLock()
        lifecycleScope.launch {
            delay(50)
            switchToPanel()
            isHandling = false
        }
    }

    private fun addLogSafe(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val newLog = "[$time] $message"
        // .update 是线程安全的，它会自动获取当前列表并生成新列表
        _logMessages.update { currentList ->
            // 将新日志加入列表，并只保留最新的 500 条防止 OOM
            (currentList + newLog).takeLast(500)
        }
    }

    private fun clearLogSafe() {
        _logMessages.update { emptyList() }
    }

    fun addLog(message: String) {
        addLogSafe(message)
    }

    private fun clearLog() {
        clearLogSafe()
    }

    private fun copyLog() {
        val logText = logMessages.value.joinToString("\n")
        // 复制到剪贴板
        val clipboard = getSystemService<ClipboardManager>()
        val clip = ClipData.newPlainText("自动化日志", logText)
        clipboard?.setPrimaryClip(clip)
        addLog("日志已复制到剪贴板")
    }
}

@Composable
fun ActionIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    containerColor: Color
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
        containerColor = containerColor,
        shape = RoundedCornerShape(18.dp),
        elevation = FloatingActionButtonDefaults.elevation(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun FloatWindowContent(
    logMessages: List<String>,
    isRunning: Boolean,
    isMaximized: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClearLog: () -> Unit,
    onCopyLog: () -> Unit,
    onMaximize: () -> Unit,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState()

    // 自动滚动到最新日志
    LaunchedEffect(logMessages.size) {
        if (logMessages.isNotEmpty()) {
            listState.animateScrollToItem(logMessages.size - 1)
        }
    }

        // 使用Compose推荐的方式获取窗口尺寸
        val screenWidth = LocalConfiguration.current.screenWidthDp.dp
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // 根据是否最大化调整布局
    Card(
        modifier = Modifier
            .then(if (isMaximized) {
                Modifier
                    .width(screenWidth)
                    .height(screenHeight)
            } else {
                Modifier
                    .width(300.dp)
                    .height(360.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(20.dp),
                        clip = true
                    )
            }),
        shape = if (isMaximized) RoundedCornerShape(0.dp) else RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFFFFF)
        )
    ) {
        // 顶部标题区 - 减小高度，更改名称为BM
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp) // 减小高度
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF1976D2),
                            Color(0xFF2196F3)
                        )
                    )
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BM", // 更改应用名称
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                // 状态指示器和控制按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 状态指示器
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AnimatedContent(
                            targetState = isRunning,
                            label = "status-indicator"
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = if (it) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                        shape = CircleShape
                                    )
                            )
                        }
                        Text(
                            text = if (isRunning) "运行中" else "已停止",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }

                    // 最大化/最小化按钮
                    FloatingActionButton(
                        onClick = onMaximize,
                        modifier = Modifier.size(28.dp),
                        containerColor = Color(0x80FFFFFF),
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        Icon(
                            imageVector = if (isMaximized) Icons.Default.Minimize else Icons.Default.Maximize,
                            contentDescription = if (isMaximized) "最小化" else "最大化",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // 中间日志显示区域 - 扩大空间
        Box(
            modifier = Modifier
                .weight(1f) // 占满剩余空间
                .fillMaxWidth()
                .padding(4.dp) // 减少内边距
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF9F9F9)
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(logMessages) {
                        Text(
                            text = it,
                            fontSize = 6.sp,
                            color = Color(0xFF333333),
                            lineHeight = 8.sp
                        )
                    }

                    // 空日志提示
                    if (logMessages.isEmpty()) {
                        item {
                            Text(
                                text = "暂无日志记录",
                                fontSize = 12.sp,
                                color = Color(0xFF9E9E9E),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
        // 底部按钮区 - 全部使用图标按钮，排列成一行
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp), // 减少内边距
            horizontalArrangement = Arrangement.SpaceEvenly, // 均匀分布
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionIconButton(
                onClick = { if (!isRunning) onStart() },
                icon = Icons.Default.PlayArrow,
                contentDescription = "启动",
                containerColor = Color(0xFF2196F3)
            )
            ActionIconButton(
                onClick = { if (isRunning) onStop() },
                icon = Icons.Default.Stop,
                contentDescription = "停止",
                containerColor = Color(0xFFF44336)
            )
            ActionIconButton(
                onClick = onClearLog,
                icon = Icons.Default.Clear,
                contentDescription = "清空日志",
                containerColor = Color(0xFFFF9800)
            )
            ActionIconButton(
                onClick = onCopyLog,
                icon = Icons.Default.CopyAll,
                contentDescription = "复制日志",
                containerColor = Color(0xFF4CAF50)
            )
            ActionIconButton(
                onClick = onClose,
                icon = Icons.Default.Clear,
                contentDescription = "关闭应用",
                containerColor = Color(0xFF9E9E9E)
            )
        }
    }
}