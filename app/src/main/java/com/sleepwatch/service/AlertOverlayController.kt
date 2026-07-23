package com.sleepwatch.service

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sleepwatch.ui.alert.AlertInfo
import com.sleepwatch.ui.alert.AlertScreen

class AlertOverlayController(private val context: Context) {
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null

    val isShowing: Boolean get() = overlayView != null

    fun show(
        info: AlertInfo,
        onDismiss: () -> Unit,
        onSkip: () -> Unit
    ): Boolean {
        if (isShowing || !Settings.canDrawOverlays(context)) return false
        return try {
            val owner = ServiceLifecycleOwner()
            val view = ComposeView(context).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    AlertScreen(
                        info = info,
                        onDismiss = onDismiss,
                        onSkip = onSkip
                    )
                }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }

            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(view, params)
            lifecycleOwner = owner
            overlayView = view
            owner.start()
            true
        } catch (_: Exception) {
            hide()
            false
        }
    }

    fun hide() {
        overlayView?.let { view ->
            runCatching {
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
            }
        }
        lifecycleOwner?.destroy()
        overlayView = null
        lifecycleOwner = null
    }
}
