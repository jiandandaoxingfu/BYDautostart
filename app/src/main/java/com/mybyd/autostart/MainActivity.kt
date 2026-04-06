package com.mybyd.autostart

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import org.json.JSONArray
import org.json.JSONObject

data class LaunchStep(
    val appName: String,
    val packageName: String,
    var intervalSeconds: Long
)

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var layoutStepContainer: LinearLayout
    private lateinit var btnAddApp: ExtendedFloatingActionButton
    private lateinit var btnSave: Button
    private lateinit var btnExit: Button

    private val launchSteps = mutableListOf<LaunchStep>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        layoutStepContainer = findViewById(R.id.layoutStepContainer)
        btnAddApp = findViewById(R.id.btnAddApp)
        btnSave = findViewById(R.id.btnSave)
        btnExit = findViewById(R.id.btnExit)

        loadSettings()
        refreshStepViews()

        btnAddApp.setOnClickListener {
            showAppPickerDialog()
        }

        btnSave.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
            startLaunchSequence()
        }

        btnExit.setOnClickListener {
            finishAndRemoveTask()
        }

        if (intent.getBooleanExtra("auto_start", false)) {
            intent.removeExtra("auto_start")
            startLaunchSequence()
        }
    }

    private fun showAppPickerDialog() {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val launchableApps = packages.filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { pm.getApplicationLabel(it).toString() }

        val appNames = launchableApps.map { pm.getApplicationLabel(it).toString() }.toTypedArray()
        val appPackages = launchableApps.map { it.packageName }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择要添加的软件")
            .setItems(appNames) { _, which ->
                val name = appNames[which]
                val pkg = appPackages[which]
                launchSteps.add(LaunchStep(name, pkg, 3L)) // 默认间隔3秒
                refreshStepViews()
            }
            .show()
    }

    private fun refreshStepViews() {
        layoutStepContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        launchSteps.forEachIndexed { index, step ->
            val view = inflater.inflate(R.layout.item_app_step, layoutStepContainer, false)
            
            val tvIndex = view.findViewById<TextView>(R.id.tvIndex)
            val tvAppName = view.findViewById<TextView>(R.id.tvAppName)
            val tvPackageName = view.findViewById<TextView>(R.id.tvPackageName)
            val sbStepInterval = view.findViewById<SeekBar>(R.id.sbStepInterval)
            val tvStepInterval = view.findViewById<TextView>(R.id.tvStepInterval)
            val btnRemove = view.findViewById<ImageButton>(R.id.btnRemove)
            val layoutConnector = view.findViewById<View>(R.id.layoutConnector)

            tvIndex.text = (index + 1).toString()
            tvAppName.text = step.appName
            tvPackageName.text = step.packageName
            
            sbStepInterval.progress = step.intervalSeconds.toInt()
            tvStepInterval.text = step.intervalSeconds.toString()

            layoutConnector.visibility = if (index == launchSteps.size - 1) View.GONE else View.VISIBLE

            sbStepInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    step.intervalSeconds = progress.toLong()
                    tvStepInterval.text = progress.toString()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            btnRemove.setOnClickListener {
                launchSteps.removeAt(index)
                refreshStepViews()
            }

            layoutStepContainer.addView(view)
        }
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("autostart_prefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        launchSteps.forEach {
            val obj = JSONObject().apply {
                put("name", it.appName)
                put("pkg", it.packageName)
                put("interval", it.intervalSeconds)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("steps_json", jsonArray.toString()).apply()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("autostart_prefs", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("steps_json", "[]")
        try {
            val jsonArray = JSONArray(jsonString)
            launchSteps.clear()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                var interval = obj.getLong("interval")
                if (interval > 30) {
                    interval /= 1000
                }
                launchSteps.add(LaunchStep(
                    obj.getString("name"),
                    obj.getString("pkg"),
                    interval
                ))
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Load settings failed", e)
        }
    }

    private fun isPackageRunning(packageName: String): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        // 在比亚迪等系统上，com.byd.avc 运行状态可以通过 runningAppProcesses 获知
        val processes = am.runningAppProcesses
        if (processes != null) {
            for (process in processes) {
                if (process.processName == packageName) {
                    return true
                }
            }
        }
        return false
    }

    private fun startLaunchSequence() {
        if (launchSteps.isEmpty()) {
            finishAndRemoveTask()
            return
        }
        Log.d("MainActivity", "Starting flowchart sequence...")
        executeStep(0)
    }

    private fun executeStep(index: Int) {
        if (index >= launchSteps.size) {
            Log.d("MainActivity", "Sequence completed. Removing task and exiting...")
            // 稍等 3 秒再退出，确保最后一个应用稳定
            handler.postDelayed({
                finishAndRemoveTask()
            }, 3000L)
            return
        }

        // 判断 360 (com.byd.avc) 是否正在运行
        if (isPackageRunning("com.byd.avc")) {
            Log.d("MainActivity", "360 (com.byd.avc) is running, waiting 3s before proceeding to step $index...")
            handler.postDelayed({
                executeStep(index)
            }, 3000L)
        } else {
            val step = launchSteps[index]
            Log.d("MainActivity", "Executing step ${index + 1}: ${step.packageName}")
            launchApp(step.packageName)
            
            // 延迟执行下一个步骤
            handler.postDelayed({
                executeStep(index + 1)
            }, step.intervalSeconds * 1000L)
        }
    }

    private fun launchApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                Log.d("MainActivity", "Launched: $packageName")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error launching $packageName", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 这里不调用 handler.removeCallbacksAndMessages(null)
        // 确保当 MainActivity 切换到后台后，队列仍能继续执行
    }
}
