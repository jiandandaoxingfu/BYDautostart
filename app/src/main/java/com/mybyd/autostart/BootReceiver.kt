package com.mybyd.autostart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    companion object {
        // 防止短时间内多次触发 (30秒内只允许触发一次)
        private var lastTriggerTime: Long = 0
        private const val DEBOUNCE_INTERVAL = 3000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Action received: $action")

        // 过滤掉不必要的动作
        if (action == Intent.ACTION_SHUTDOWN) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTriggerTime < DEBOUNCE_INTERVAL) {
            Log.d("BootReceiver", "Skipping duplicate trigger within debounce interval")
            return
        }
        lastTriggerTime = currentTime

        // 1. 车机开机1s后, 软件启动
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    Log.d("BootReceiver", "Starting MainActivity after 3s delay")
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to start MainActivity", e)
            }
        }, 3000) // 延迟3秒
    }
}
