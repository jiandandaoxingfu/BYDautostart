package com.mybyd.autostart

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
            // 清除标志，防止 Activity 重建时再次触发
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

            // 最后一个步骤不显示连接线
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
                // 兼容旧版本，如果存的是毫秒（>30），则转为秒
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

    private fun startLaunchSequence() {
        if (launchSteps.isEmpty()) return
        
        Log.d("MainActivity", "Starting flowchart sequence...")
        
        var cumulativeDelay = 0L
        launchSteps.forEachIndexed { index, step ->
            handler.postDelayed({
                launchApp(step.packageName)
            }, cumulativeDelay)
            
            // 累加当前步骤设定的“等待时间”（转为毫秒）
            cumulativeDelay += (step.intervalSeconds * 1000L)
        }

        // 所有应用启动完成后，彻底退出应用并从最近任务列表中移除
        handler.postDelayed({
            Log.d("MainActivity", "Sequence completed. Removing task and exiting...")
            finishAndRemoveTask()
        }, cumulativeDelay + 3000L)
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
        handler.removeCallbacksAndMessages(null)
    }
}
