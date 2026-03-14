package com.mybyd.autostart

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 此 Activity 主要用于逻辑中转，通常不需要复杂布局
        Log.d("MainActivity", "App started, executing sequence...")

        startSequence()
    }

    private fun startSequence() {
        // 2. 打开qq音乐(com.tencent.qqmusiccar)
        Log.d("MainActivity", "Step 1: Opening QQ Music...")
        launchApp("com.tencent.qqmusiccar")

        // 3. 5s后, 打开百度地图(com.baidu.mapauto)
        handler.postDelayed({
            Log.d("MainActivity", "Step 2: Opening Baidu Map...")
            launchApp("com.baidu.mapauto")

            // 4. 退出App
            // 延迟一会再退出，确保启动指令已发送
            handler.postDelayed({
                Log.d("MainActivity", "Step 3: Exiting app...")
                finishAndRemoveTask()
            }, 5000)
        }, 5000)
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
