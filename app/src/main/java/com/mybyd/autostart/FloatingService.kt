package com.mybyd.autostart

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.*
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var cardView: View? = null
    private lateinit var floatingLayoutParams: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundService()
        initFloatingView()
    }

    private fun startForegroundService() {
        val channelId = "floating_service_channel"
        val channel = NotificationChannel(
            channelId, "Floating Service",
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Floating Button Active")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        startForeground(1, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initFloatingView() {
        floatingView = FrameLayout(this).apply {
            val outer = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#80000000"))
                setStroke(dpToPx(2), Color.parseColor("#40FFFFFF"))
            }
            background = outer

            val innerView = View(context).apply {
                val inner = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                }
                background = inner
                alpha = 0.6f
            }
            val innerSize = dpToPx(35)
            val params = FrameLayout.LayoutParams(innerSize, innerSize, Gravity.CENTER)
            addView(innerView, params)
            
            alpha = 0f
            scaleX = 0f
            scaleY = 0f
            animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(500)
                .setInterpolator(OvershootInterpolator()).start()
        }

        val size = dpToPx(60)
        floatingLayoutParams = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            x = 0
            y = 0
        }

        try {
            windowManager.addView(floatingView, floatingLayoutParams)
        } catch (e: Exception) {
            Log.e("FloatingService", "Error adding floating view", e)
        }

        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f
            private var isClick: Boolean = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = floatingLayoutParams.x
                        initialY = floatingLayoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val diffX = (event.rawX - initialTouchX).toInt()
                        val diffY = (event.rawY - initialTouchY).toInt()
                        if (abs(diffX) > 10 || abs(diffY) > 10) isClick = false
                        
                        floatingLayoutParams.x = initialX + diffX
                        floatingLayoutParams.y = initialY + diffY
                        try {
                            if (v.isAttachedToWindow) {
                                windowManager.updateViewLayout(floatingView, floatingLayoutParams)
                            }
                        } catch (e: Exception) {
                            Log.e("FloatingService", "Error updating layout", e)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(200)
                            .setInterpolator(OvershootInterpolator()).start()
                        if (isClick) {
                            toggleCard()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun toggleCard() {
        if (cardView != null) {
            hideCard()
        } else {
            showCard()
        }
    }

    private fun hideCard(actionAfter: (() -> Unit)? = null) {
        val viewToRemove = cardView ?: return
        cardView = null

        // Stop any current animation and disable interaction
        viewToRemove.animate().cancel()
        viewToRemove.isEnabled = false

        // Hide shadow immediately as it often causes flickers during alpha transitions on modified Android systems
        viewToRemove.elevation = 0f

        viewToRemove.animate()
            .alpha(0f)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(150)
            .setInterpolator(AccelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    viewToRemove.visibility = View.GONE
                    safeRemoveView(viewToRemove)

                    // Small delay before starting the activity to ensure WindowManager is ready
                    if (actionAfter != null) {
                        viewToRemove.postDelayed({
                            actionAfter.invoke()
                        }, 50)
                    }
                }
            }).start()
    }

    private fun showCard() {
        if (cardView != null) return

        cardView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val shape = GradientDrawable().apply {
                setColor(Color.parseColor("#1e293b"))
                cornerRadius = dpToPx(20).toFloat()
            }
            background = shape
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            elevation = dpToPx(10).toFloat()

            alpha = 0f
            scaleX = 0.9f
            scaleY = 0.9f
        }

        val title = TextView(this).apply {
            text = "Quick Actions"
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(0, 0, 0, dpToPx(12))
        }
        (cardView as LinearLayout).addView(title)

        val grid = GridLayout(this).apply {
            columnCount = 3
        }
        
        val actions = listOf("Back", "Home", "Music", "Map", "Clean", "Exit")
        for (actionName in actions) {
            val btn = createActionButton(actionName)
            grid.addView(btn)
        }
        
        (cardView as LinearLayout).addView(grid)

        val cardParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            windowManager.addView(cardView, cardParams)

            cardView?.animate()
                ?.alpha(1f)
                ?.scaleX(1f)
                ?.scaleY(1f)
                ?.setDuration(300)
                ?.setInterpolator(OvershootInterpolator())
                ?.start()

            cardView?.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    hideCard()
                    true
                } else false
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "Error adding card view", e)
            cardView = null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createActionButton(name: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val btnParams = GridLayout.LayoutParams().apply {
                width = dpToPx(80)
                height = dpToPx(80)
                setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            }
            this.layoutParams = btnParams

            background = GradientDrawable().apply {
                setColor(Color.parseColor("#334155"))
                cornerRadius = dpToPx(12).toFloat()
            }

            val icon = TextView(context).apply {
                text = when(name) {
                    "Back" -> "⬅️"
                    "Home" -> "🏠"
                    "Music" -> "🎵"
                    "Map" -> "🗺️"
                    "Clean" -> "🧹"
                    "Exit" -> "❌"
                    else -> "⚡"
                }
                textSize = 20f
                gravity = Gravity.CENTER
            }
            val label = TextView(context).apply {
                text = name
                setTextColor(Color.parseColor("#cbd5e1"))
                textSize = 12f
                gravity = Gravity.CENTER
            }
            addView(icon)
            addView(label)
            
            setOnTouchListener { v, event ->
                when(event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50).start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                    }
                }
                false
            }

            setOnClickListener {
                Log.d("FloatingService", "Action clicked: $name")
                // Cancel the button's own animation before hiding the whole card
                this.animate().cancel()
                this.scaleX = 1f
                this.scaleY = 1f

                hideCard {
                    performAction(name)
                }
            }
        }
    }

    private fun performAction(name: String) {
        when (name) {
            "Music" -> launchApp("com.tencent.qqmusiccar")
            "Map" -> launchApp("com.baidu.mapauto")
            "Home" -> {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
            "Exit" -> stopSelf()
            else -> Log.d("FloatingService", "Action $name not implemented")
        }
    }

    private fun launchApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                Log.e("FloatingService", "Package not found: $packageName")
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "Error launching $packageName", e)
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun safeRemoveView(view: View?) {
        try {
            if (view != null && view.isAttachedToWindow) {
                // Use removeView (async) instead of removeViewImmediate (sync)
                // for a smoother transition to other activities
                windowManager.removeView(view)
            }
        } catch (e: Exception) {
            Log.e("FloatingService", "Error removing view", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // During service destruction, we want immediate removal
        try {
            floatingView?.let { if (it.isAttachedToWindow) windowManager.removeViewImmediate(it) }
            cardView?.let { if (it.isAttachedToWindow) windowManager.removeViewImmediate(it) }
        } catch (e: Exception) {
            Log.e("FloatingService", "Error in onDestroy removal", e)
        }
        floatingView = null
        cardView = null
    }
}
