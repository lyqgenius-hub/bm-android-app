package org.bm.app

import android.graphics.Bitmap
import android.graphics.Point
import android.util.Log
import java.lang.ref.WeakReference

/**
 * 图色工具类 - 对应 JS 中的 findImage 和 screenImageCache 逻辑
 */
object ImageUtils {
    private const val TAG = "ImageUtils"

    // 截图缓存，使用 WeakReference 防止内存泄漏
    private var screenCache: WeakReference<Bitmap>? = null

    /**
     * 清空截图缓存
     */
    fun clearCache() {
        screenCache?.get()?.recycle()
        screenCache = null
        Log.d(TAG, "截屏缓存已清空")
    }

    /**
     * 获取截图（支持缓存）
     */
    suspend fun getScreenshot(service: FloatWindowService, useCache: Boolean): Bitmap? {
        if (useCache) {
            val cached = screenCache?.get()
            if (cached != null && !cached.isRecycled) {
                Log.d(TAG, "使用缓存的截屏进行图片查找...")
                return cached
            }
        }

        // 这里需要通过 FloatWindowService 调用系统的 takeScreenshot (API 30+)
        // 注意：由于你的 FloatWindowService 目前是 LifecycleService，
        // 建议后续将其升级为 AccessibilityService 才能调用此方法。
        return null // 占位：实际需实现截图逻辑
    }

    /**
     * 查找图片坐标 - 对应 JS 中的 findImage
     */
    suspend fun findImage(
        service: FloatWindowService,
        templatePath: String,
        useCache: Boolean = false,
        threshold: Float = 0.8f
    ): Point? {
        val screen = getScreenshot(service, useCache) ?: return null

        // 更新缓存
        if (useCache) {
            screenCache = WeakReference(screen)
        }

        Log.d(TAG, "正在查找并对比图片: $templatePath")

        // TODO: 这里需要集成 OpenCV 或使用像素点对比算法
        // 1. 加载 templatePath 图片
        // 2. 调用模板匹配算法
        // 3. 返回匹配到的 Point

        return null
    }
}