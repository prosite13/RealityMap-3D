package com.realitymap3d

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.Recording
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private lateinit var imageCapture: ImageCapture
    private lateinit var videoCapture: VideoCapture<Recorder>
    private var recording: Recording? = null
    private var useBackCamera = true
    private var cameraProvider: ProcessCameraProvider? = null
    private val requestCamera = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFF0D1117.toInt())
        }
        val title = TextView(this).apply {
            text = "REALITYMAP 3D"
            textSize = 21f; setTextColor(0xFF39D353.toInt())
        }
        status = TextView(this).apply {
            textSize = 12f; setTextColor(0xFFE6EDF3.toInt())
            setPadding(0, 8, 0, 8)
        }
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        val frame = FrameLayout(this)
        frame.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        val guide = TextView(this).apply {
            text = "┌                         ┐\n\n\n\n\n└                         ┘"
            textSize = 24f; gravity = Gravity.CENTER
            setTextColor(0xFF39D353.toInt())
            setBackgroundColor(0x22000000)
        }
        val guideParams = FrameLayout.LayoutParams(-1, -1).apply {
            setMargins(34, 45, 34, 45)
        }
        frame.addView(guide, guideParams)
        val photo = Button(this).apply {
            text = "사진 촬영"
            setOnClickListener { takePhoto() }
        }
        val video = Button(this).apply {
            text = "영상 촬영 시작"
            setOnClickListener { toggleVideo() }
        }
        val switch = Button(this).apply {
            text = "전면 / 후면 전환"
            setOnClickListener {
                useBackCamera = !useBackCamera
                startCamera()
            }
        }
        root.addView(title)
        root.addView(status)
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(photo)
        root.addView(video)
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
            cameraProvider = provider
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD)).build()
            videoCapture = VideoCapture.withOutput(recorder)
            val selector = if (useBackCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, imageCapture, videoCapture)
                status.text = deviceReport() + "\n카메라 준비 완료 · 초록 프레임 안에 공간을 맞추세요."
            } catch (e: Exception) {
                status.text = "카메라 시작 실패: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        if (!::imageCapture.isInitialized) return
        val name = "RMAP_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RealityMap3D")
        }
        val options = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ).build()
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Toast.makeText(this@MainActivity, "사진 저장 완료", Toast.LENGTH_SHORT).show()
                    status.text = "사진 저장 완료: Pictures/RealityMap3D\n여러 방향에서 겹치도록 촬영하세요."
                }
                override fun onError(exc: ImageCaptureException) {
                    status.text = "사진 저장 실패: ${exc.message}"
                }
            })
    }

    private fun toggleVideo() {
        if (!::videoCapture.isInitialized) return
        val active = recording
        if (active != null) {
            active.stop()
            recording = null
            return
        }
        val name = "RMAP_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/RealityMap3D")
        }
        val output = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values).build()
        try {
            recording = videoCapture.output.prepareRecording(this, output).start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is androidx.camera.video.VideoRecordEvent.Start -> status.text = "영상 촬영 중 · 현재 버전은 영상 저장 기능입니다. 실시간 3D 복원은 아직 연결되지 않았습니다."
                    is androidx.camera.video.VideoRecordEvent.Finalize -> {
                        recording = null
                        status.text = if (event.hasError()) "영상 저장 실패: ${event.error}" else "영상 저장 완료: Movies/RealityMap3D"
                    }
                }
            }
        } catch (e: Exception) {
            status.text = "영상 촬영을 시작할 수 없습니다: ${e.message}"
        }
    }

    private fun deviceReport(): String {
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        val gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val vulkan = packageManager.hasSystemFeature("android.hardware.vulkan.version")
        return "기기: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}" +
            "\n여유 RAM: ${info.availMem / (1024 * 1024)} MB" +
            "\n자이로: ${if (gyro) "지원" else "없음"} · 가속도계: ${if (accel) "지원" else "없음"}" +
            "\nVulkan feature: ${if (vulkan) "있음(버전 별도 확인)" else "미확인"} · ARCore Depth/3DGS: 미검증"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCamera && hasCameraPermission()) startCamera()
        else status.text = "카메라 권한이 필요합니다."
    }

    override fun onDestroy() {
        recording?.stop()
        cameraProvider?.unbindAll()
        super.onDestroy()
    }
}
