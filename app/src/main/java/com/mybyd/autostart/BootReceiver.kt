package com.mybyd.autostart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    companion object {
        private var lastTriggerTime: Long = 0
        private const val DEBOUNCE_INTERVAL = 10000L // 10秒内不重复触发
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Action received: $action")

        if (action != Intent.ACTION_BOOT_COMPLETED && action != "android.intent.action.LOCKED_BOOT_COMPLETED") {
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTriggerTime < DEBOUNCE_INTERVAL) {
            Log.d("BootReceiver", "Skipping duplicate trigger")
            return
        }
        lastTriggerTime = currentTime

        // 启动主界面并告知其开始自启动序列
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    launchIntent.putExtra("auto_start", true)
                    context.startActivity(launchIntent)
                    Log.d("BootReceiver", "Starting MainActivity for auto-start sequence")
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to start MainActivity", e)
            }
        }, 2000) // 延迟2秒，确保系统服务稳定
    }
}
