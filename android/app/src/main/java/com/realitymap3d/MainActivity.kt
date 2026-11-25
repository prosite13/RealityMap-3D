package com.realitymap3d

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Build
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainActivity : Activity() {
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private lateinit var executor: ExecutorService
    private var useBackCamera = true
    private val requestCamera = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        executor = Executors.newSingleThreadExecutor()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(0xFF0D1117.toInt())
        }
        val title = TextView(this).apply {
            text = "REALITYMAP 3D  ·  DEVICE CHECK"
            textSize = 19f; setTextColor(0xFF39D353.toInt())
        }
        status = TextView(this).apply {
            textSize = 13f; setTextColor(0xFFE6EDF3.toInt())
            setPadding(0, 12, 0, 12)
        }
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        val switch = Button(this).apply {
            text = "전면 / 후면 전환"
            setOnClickListener { useBackCamera = !useBackCamera; startCamera() }
        }
        root.addView(title)
        root.addView(status)
        root.addView(previewView, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(switch)
        setContentView(root)
        status.text = deviceReport()
        if (hasCameraPermission()) startCamera() else
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), requestCamera)
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val selector = if (useBackCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview)
                status.text = deviceReport() + "\n\n카메라 미리보기 준비 완료. 다음 단계에서 추적·깊이 엔진 호환성을 검증합니다."
            } catch (e: Exception) {
                status.text = deviceReport() + "\n카메라 시작 실패: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun deviceReport(): String {
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        val gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        val gl = IntArray(1)
        val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        val vulkan = if (Build.VERSION.SDK_INT >= 24) {
            packageManager.hasSystemFeature("android.hardware.vulkan.version")
        } else false
        return "기기: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" +
            "\nABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}" +
            "\nRAM 여유: ${info.availMem / (1024 * 1024)} MB" +
            "\n자이로: ${if (gyro) "지원" else "없음"} · 가속도계: ${if (accel) "지원" else "없음"}" +
            "\nVulkan feature flag: ${if (vulkan) "있음(실제 버전 별도 확인 필요)" else "미확인/없음"}" +
            "\nARCore Depth / 3DGS: 아직 검증 전"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCamera && hasCameraPermission()) startCamera()
        else status.text = "카메라 권한이 필요합니다."
    }

    override fun onDestroy() { super.onDestroy(); if (::executor.isInitialized) executor.shutdown() }
}
