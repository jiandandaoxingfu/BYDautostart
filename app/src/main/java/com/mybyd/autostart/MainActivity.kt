package com.mybyd.autostart

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 此 Activity 主要用于逻辑中转，通常不需要复杂布局
        Log.d("MainActivity", "App started, executing sequence...")

        checkOverlayPermission()
        startSequence()
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 1234)
        } else {
            startFloatingService()
        }
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun startSequence() {
        // 2. 打开qq音乐(com.tencent.qqmusiccar)
        Log.d("MainActivity", "Step 1: Opening QQ Music...")
        launchApp("com.tencent.qqmusiccar")

        // 3. 4s后, 打开百度地图(com.baidu.mapauto)
        handler.postDelayed({
            Log.d("MainActivity", "Step 2: Opening Baidu Map...")
            launchApp("com.baidu.mapauto")

            // 4. 退出App
            // 延迟一会再退出，确保启动指令已发送
            handler.postDelayed({
                Log.d("MainActivity", "Step 3: Exiting app...")
                finishAndRemoveTask()
            }, 3000)
        }, 4000)
    }

    private fun launchApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                Log.e("MainActivity", "Package not found: $packageName")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error launching $packageName", e)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1234) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingService()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
