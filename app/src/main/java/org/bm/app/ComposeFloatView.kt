package org.bm.app

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class ComposeFloatView : ViewGroup, LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private lateinit var composeView: ComposeView
    
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
    
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    constructor(context: Context) : super(context) {
        init(null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(null)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(null)
    }

    constructor(context: Context, content: @Composable () -> Unit) : super(context) {
        init(content)
    }

    private fun init(content: @Composable (() -> Unit)?) {
        // 初始化生命周期
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        
        composeView = ComposeView(context)
        
        // 设置ViewTree所有者
        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)
        
        content?.let {
            composeView.setContent(it)
        }
        addView(composeView)
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 确保生命周期状态正确
        if (lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED) {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
        if (lifecycleRegistry.currentState < Lifecycle.State.RESUMED) {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // 更新生命周期状态
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        composeView.layout(0, 0, r - l, b - t)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        composeView.measure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(composeView.measuredWidth, composeView.measuredHeight)
    }
}