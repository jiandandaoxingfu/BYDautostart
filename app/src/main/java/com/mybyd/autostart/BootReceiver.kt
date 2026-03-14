package com.mybyd.autostart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "Action received: ${intent.action}")

        // 1. 车机开机3s后, 软件启动
        // 使用 Handler 延迟 3 秒启动 MainActivity
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    Log.d("BootReceiver", "Starting MainActivity...")
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to start MainActivity", e)
            }
        }, 1000)
    }
}
