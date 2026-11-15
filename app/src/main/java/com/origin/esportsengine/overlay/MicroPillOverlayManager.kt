package com.origin.esportsengine.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.origin.esportsengine.core.EngineState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** A user-granted, non-focusable pill for short engine status alerts. */
object MicroPillOverlayManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var appContext: Context? = null
    private var windowManager: WindowManager? = null
    private var pill: TextView? = null
    private var collectorStarted = false

    @Synchronized
    fun initializeOverlay(context: Context) {
        val applicationContext = context.applicationContext
        appContext = applicationContext
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !Settings.canDrawOverlays(applicationContext)) return

        windowManager = applicationContext.getSystemService(WindowManager::class.java)
        if (pill == null) pill = createPill(applicationContext)
        attachIfNeeded()

        if (!collectorStarted) {
            collectorStarted = true
            scope.launch {
                EngineState.latestPillAlert.collectLatest { message ->
                    val view = pill ?: return@collectLatest
                    if (message.isNullOrBlank()) {
                        view.visibility = View.GONE
                        return@collectLatest
                    }
                    view.text = message
                    view.visibility = View.VISIBLE
                    delay(3_000)
                    if (EngineState.latestPillAlert.value == message) view.visibility = View.GONE
                }
            }
        }
    }

    @Synchronized
    fun removeOverlay() {
        val view = pill ?: return
        runCatching { windowManager?.removeView(view) }
        pill = null
        windowManager = null
        appContext = null
    }

    private fun attachIfNeeded() {
        val view = pill ?: return
        val manager = windowManager ?: return
        if (view.parent != null) return

        val density = appContext?.resources?.displayMetrics?.density ?: 1f
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (32 * density).toInt()
        }
        runCatching { manager.addView(view, params) }
    }

    private fun createPill(context: Context): TextView = TextView(context).apply {
        text = ""
        textSize = 13f
        setTextColor(Color.WHITE)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setPadding(dp(context, 16), dp(context, 9), dp(context, 16), dp(context, 9))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 22).toFloat()
            setColor(Color.parseColor("#DD0A0D12"))
            setStroke(dp(context, 1), Color.parseColor("#00F0FF"))
        }
        visibility = View.GONE
        maxLines = 2
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
