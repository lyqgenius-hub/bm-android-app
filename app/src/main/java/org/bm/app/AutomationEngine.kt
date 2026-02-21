package org.bm.app

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AutomationEngine(
    private val service: FloatWindowService, // 用于发送日志和调用无障碍操作
    private val scope: CoroutineScope         // 协程作用域，管理生命周期
) {
    // 使用 StateFlow 维护运行时上下文，UI 可以实时订阅
    private val _context = MutableStateFlow(MobileContext())
    val context = _context.asStateFlow()

    private var engineJob: Job? = null

    // 步骤配置表 - 实际开发中可以从本地 JSON 或服务器获取
    private val stepConfig = mutableMapOf<Int, Step>()

    private val executor = StepExecutor(service)

    /**
     * 初始化步骤配置 (示例迁移自 main.js)
     */
    init {
        // 步骤 1: 打开微信
        stepConfig[1] = Step(1, "打开微信", "launch", args = listOf("com.tencent.mm"), next = 3, failNext = 0)
        // 步骤 3: 模拟点击登录 (假设坐标 500, 1000)
        stepConfig[3] = Step(3, "点击登录", "touch", args = listOf(500, 1000), next = 5, failNext = 1)
        // 更多步骤可以继续添加...
    }

    /**
     * 启动引擎
     */
    fun start() {
        if (_context.value.isRunning) return

        _context.update { it.copy(isRunning = true, step = 1) }
        service.addLog("🚀 自动化引擎启动")

        engineJob = scope.launch(Dispatchers.IO) {
            try {
                var currentId = _context.value.step

                while (isActive && _context.value.isRunning && currentId != 0) {
                    val step = stepConfig[currentId]
                    if (step == null) {
                        service.addLog("❌ 找不到步骤配置: $currentId")
                        break
                    }

                    service.addLog("执行步骤[$currentId]: ${step.name}")

                    // 执行具体动作 (下一步我们要实现的 StepExecutor)
                    val success = executeStepAction(step)

                    if (success) {
                        currentId = step.next
                    } else {
                        service.addLog("⚠️ 步骤[${step.name}]执行失败")
                        currentId = step.failNext
                    }

                    // 更新上下文中的当前步骤 ID
                    _context.update { it.copy(step = currentId) }
                    // 步骤间隔延时，对应 JS 的 setTimeout(500)
                    delay(500)
                }
            } catch (e: CancellationException) {
                service.addLog("⏹️ 任务被取消")
            } catch (e: Exception) {
                service.addLog("🚨 引擎崩溃: ${e.message}")
                _context.update { it.copy(lastError = e.message) }
            } finally {
                stop()
            }
        }
    }

    /**
     * 模拟执行动作 (先打个桩，下一步迁移 StepExecutor)
     */
    private suspend fun executeStepAction(step: Step): Boolean {
        val success = executor.execute(step)

        // 这里可以扩展 JS 中的 expected_check (预期画面检查) 逻辑
        if (success && step.kwargs["expected_check"] == true) {
            val expectedImage = step.kwargs["expected"] as? String
            if (expectedImage != null) {
                service.addLog("🔍 检查预期画面: $expectedImage")
                // 之后在这里调用 ImageUtils 搜图
                delay(1000)
            }
        }

        return success
    }

    /**
     * 停止引擎
     */
    fun stop() {
        _context.update { it.copy(isRunning = false) }
        engineJob?.cancel()
        service.addLog("🏁 自动化引擎已结束")
    }
}