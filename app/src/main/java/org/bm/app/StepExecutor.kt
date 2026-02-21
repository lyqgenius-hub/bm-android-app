package org.bm.app

import android.content.Intent
import android.util.Log
import kotlinx.coroutines.delay

/**
 * 执行器 - 负责将 Step 转化为具体的系统动作
 * 对应 JS 中的 commonFunctions
 */
class StepExecutor(private val service: FloatWindowService) {

    /**
     * 执行步骤动作的主入口
     */
    suspend fun execute(step: Step): Boolean {
        return try {
            when (step.func) {
                "launch" -> performLaunch(step.args)
                "touch" -> performTouch(step.args, step.kwargs)
                "swipe" -> performSwipe(step.args, step.kwargs)
                "input" -> performInput(step.args, step.kwargs)
                "back" -> performBack()
                "return_true" -> true // 对应 JS 中的 return_true
                else -> {
                    service.addLog("❌ 未知动作类型: ${step.func}")
                    false
                }
            }
        } catch (e: Exception) {
            service.addLog("🚨 执行异常: ${e.message}")
            false
        }
    }

    /**
     * 1. 打开应用操作
     * args[0] 通常是包名
     */
    private fun performLaunch(args: List<Any>): Boolean {
        val packageName = args.getOrNull(0) as? String ?: return false
        service.addLog("执行启动应用: $packageName")
        return try {
            val intent = service.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                service.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 2. 点击操作 (占位，待后续实现具体无障碍逻辑)
     */
    private suspend fun performTouch(args: List<Any>, kwargs: Map<String, Any>): Boolean {
        service.addLog("模拟点击: $args")
        delay(300) // 模拟执行耗时
        return true
    }

    /**
     * 3. 滑动操作 (占位)
     */
    private suspend fun performSwipe(args: List<Any>, kwargs: Map<String, Any>): Boolean {
        service.addLog("模拟滑动: $args")
        delay(500)
        return true
    }

    /**
     * 4. 输入操作 (占位)
     */
    private suspend fun performInput(args: List<Any>, kwargs: Map<String, Any>): Boolean {
        val text = args.getOrNull(0) as? String ?: ""
        service.addLog("模拟输入: $text")
        return true
    }

    /**
     * 5. 系统返回操作
     */
    private fun performBack(): Boolean {
        service.addLog("模拟返回键")
        // 之后会调用 service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        return true
    }
}