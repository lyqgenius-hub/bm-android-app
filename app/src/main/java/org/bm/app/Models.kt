package org.bm.app

import android.os.Build
import androidx.annotation.Keep

@Keep
data class Step(
    val id: Int,                    // 步骤 ID
    val name: String,               // 步骤名称
    val func: String,               // 执行函数名 (如 "launch", "touch", "swipe")
    val args: List<Any> = emptyList(), // 位置参数
    val kwargs: Map<String, Any> = emptyMap(), // 键值对参数 (如 expected, current)
    val next: Int,                  // 成功后跳转 ID
    val failNext: Int               // 失败后跳转 ID
)

@Keep
data class MobileContext(
    val uuid: String? = null,
    val mobileName: String = Build.MODEL, // 设备名称
    val ip: String? = null,
    val wxName: String? = null,           // 微信名称
    val wxPhone: String? = null,          // 微信手机号
    val fileName: String? = null,         // 文件名
    val step: Int = 1,                    // 当前步骤
    val lastError: String? = null,        // 最后错误信息
    val result: String? = null,           // 执行结果

    // 状态控制
    val isRunning: Boolean = false,
    val loopFailedCount: Int = 0,         // 循环失败计数
    val sameStepCounter: Int = 0          // 同步骤执行计数
)