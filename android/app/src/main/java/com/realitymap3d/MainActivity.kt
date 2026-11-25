package com.realitymap3d

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.ArCoreNotInstalledException
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.opengl.Matrix

class MainActivity : Activity() {
    private lateinit var glView: GLSurfaceView
    private lateinit var status: TextView
    private lateinit var renderer: DepthRenderer
    private var session: Session? = null
    private var installRequested = false
    private var depthSupported = false
    private val requestCamera = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFF0D1117.toInt())
        }
        val title = TextView(this).apply {
            text = "REALITYMAP 3D · DEPTH SCAN"
            textSize = 20f
            setTextColor(0xFF39D353.toInt())
        }
        status = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFFE6EDF3.toInt())
            text = "ARCore 공간 추적을 준비합니다."
            setPadding(0, 8, 0, 8)
        }
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
        }
        renderer = DepthRenderer { count, message ->
            runOnUiThread {
                status.text = "누적 3D 점: $count\n$message"
            }
        }
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        val scan = Button(this).apply {
            text = "3D 스캔 시작"
            setOnClickListener {
                if (!depthSupported) {
                    status.text = "이 기기에서 ARCore Depth를 사용할 수 없어 3D 깊이 스캔을 시작할 수 없습니다."
                } else {
                    renderer.startScan()
                    status.text = "공간을 천천히 움직이며 촬영하세요. 표면의 특징이 보이도록 여러 방향에서 스캔하세요."
                }
            }
        }
        val stop = Button(this).apply {
            text = "스캔 중지"
            setOnClickListener {
                renderer.stopScan()
                status.text = "스캔 중지 · 누적 점: ${renderer.pointCount()}"
            }
        }
        val export = Button(this).apply {
            text = "실제 3D 점군 PLY 내보내기"
            setOnClickListener { exportPointCloud() }
        }
        root.addView(title)
        root.addView(status)
        root.addView(glView, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(scan)
        root.addView(stop)
        root.addView(export)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), requestCamera)
            return
        }
        resumeArCore()
    }

    private fun resumeArCore() {
        try {
            when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    return
                }
                ArCoreApk.InstallStatus.INSTALLED -> Unit
            }
            if (session == null) session = Session(this)
            val config = Config(session)
            depthSupported = session!!.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
            if (depthSupported) config.depthMode = Config.DepthMode.AUTOMATIC
            session!!.configure(config)
            renderer.setSession(session!!)
            glView.onResume()
            session!!.resume()
            status.text = if (depthSupported) {
                "ARCore 추적 준비 완료 · Depth 지원 확인됨. 스캔을 시작하세요."
            } else {
                "ARCore는 실행되지만 이 기기는 Depth API를 지원하지 않습니다. 실제 깊이 기반 3D 스캔은 불가능합니다."
            }
        } catch (e: Exception) {
            status.text = "ARCore 시작 실패: ${e.javaClass.simpleName} · ${e.message ?: "기기 호환성 또는 Google Play Services for AR 확인 필요"}"
        }
    }

    private fun exportPointCloud() {
        renderer.stopScan()
        val points = renderer.snapshotPoints()
        if (points.isEmpty()) {
            status.text = "내보낼 3D 점이 없습니다. Depth 지원과 추적 상태를 확인한 뒤 다시 스캔하세요."
            return
        }
        Thread {
            try {
                val folder = File(getExternalFilesDir(null), "RealityMap3D").apply { mkdirs() }
                val file = File(folder, "realitymap_${System.currentTimeMillis()}.ply")
                file.bufferedWriter().use { out ->
                    out.appendLine("ply")
                    out.appendLine("format ascii 1.0")
                    out.appendLine("element vertex ${points.size / 3}")
                    out.appendLine("property float x")
                    out.appendLine("property float y")
                    out.appendLine("property float z")
                    out.appendLine("end_header")
                    var i = 0
                    while (i < points.size) {
                        out.append(points[i].toString()).append(' ')
                        out.append(points[i + 1].toString()).append(' ')
                        out.appendLine(points[i + 2].toString())
                        i += 3
                    }
                }
                runOnUiThread {
                    status.text = "실제 깊이 기반 3D 점군 생성 완료\n점: ${points.size / 3}\n파일: ${file.absolutePath}"
                    Toast.makeText(this, "PLY 3D 파일 저장 완료", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "PLY 저장 실패: ${e.message}" }
            }
        }.start()
    }

    override fun onPause() {
        renderer.stopScan()
        if (session != null) {
            glView.onPause()
            session?.pause()
        }
        super.onPause()
    }

    override fun onDestroy() {
        session?.close()
        session = null
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCamera && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            resumeArCore()
        } else {
            status.text = "카메라 권한이 필요합니다."
        }
    }
}

private class DepthRenderer(
    private val onStatus: (Int, String) -> Unit
) : GLSurfaceView.Renderer {
    @Volatile private var session: Session? = null
    private val scanning = AtomicBoolean(false)
    private val points = ArrayList<Float>(900_000)
    private var textureId = -1
    private var program = 0
    private var positionHandle = 0
    private var uvHandle = 0
    private var textureHandle = 0
    private var lastSampleNs = 0L
    private var frameCounter = 0
    private var depthAvailable = false
    private val vertices = floatArrayOf(-1f,-1f, 1f,-1f, -1f,1f, 1f,1f)
    private val uv = floatArrayOf(0f,1f, 1f,1f, 0f,0f, 1f,0f)
    private val vertexBuffer = directBuffer(vertices)
    private val uvBuffer = directBuffer(uv)
    private val transformedUv = directBuffer(FloatArray(8))

    fun setSession(value: Session) { session = value }
    fun startScan() { synchronized(points) { points.clear() }; scanning.set(true) }
    fun stopScan() { scanning.set(false) }
    fun pointCount(): Int = synchronized(points) { points.size / 3 }
    fun snapshotPoints(): FloatArray = synchronized(points) { points.toFloatArray() }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        textureId = createExternalTexture()
        program = createProgram()
        positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        uvHandle = GLES20.glGetAttribLocation(program, "a_TexCoord")
        textureHandle = GLES20.glGetUniformLocation(program, "sTexture")
        session?.setCameraTextureName(textureId)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        session?.setDisplayGeometry(0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val activeSession = session ?: return
        try {
            val frame = activeSession.update()
            drawCamera(frame)
            val camera = frame.camera
            if (camera.trackingState != TrackingState.TRACKING) {
                if (frameCounter++ % 60 == 0) onStatus(pointCount(), "카메라 위치 추적 중… 공간을 천천히 움직이세요.")
                return
            }
            if (!scanning.get()) return
            val now = System.nanoTime()
            if (now - lastSampleNs < 250_000_000L) return
            lastSampleNs = now
            try {
                frame.acquireDepthImage16Bits().use { depth ->
                    val plane = depth.planes[0]
                    val buffer = plane.buffer.order(ByteOrder.LITTLE_ENDIAN)
                    val width = depth.width
                    val height = depth.height
                    val intrinsics = camera.imageIntrinsics
                    val focal = intrinsics.focalLength
                    val principal = intrinsics.principalPoint
                    val dimensions = intrinsics.imageDimensions
                    val sx = dimensions[0].toFloat() / width
                    val sy = dimensions[1].toFloat() / height
                    val fx = focal[0] / sx
                    val fy = focal[1] / sy
                    val cx = principal[0] / sx
                    val cy = principal[1] / sy
                    val pose = camera.pose
                    val step = if (width > 300) 8 else 4
                    synchronized(points) {
                        if (points.size / 3 >= 220_000) {
                            scanning.set(false)
                        } else {
                            var y = 0
                            while (y < height) {
                                var x = 0
                                while (x < width) {
                                    val offset = y * plane.rowStride + x * plane.pixelStride
                                    if (offset + 1 < buffer.limit()) {
                                        val mm = buffer.getShort(offset).toInt() and 0xFFFF
                                        if (mm in 250..8000) {
                                            val z = mm / 1000f
                                            val px = (x - cx) * z / fx
                                            val py = (cy - y) * z / fy
                                            val world = pose.transformPoint(floatArrayOf(px, py, -z))
                                            points.add(world[0]); points.add(world[1]); points.add(world[2])
                                        }
                                    }
                                    x += step
                                }
                                y += step
                            }
                        }
                    }
                    depthAvailable = true
                    if (frameCounter++ % 8 == 0) onStatus(pointCount(), "Depth 프레임 누적 중 · 초록색 공간을 천천히 훑으세요.")
                }
            } catch (_: Exception) {
                if (frameCounter++ % 90 == 0) onStatus(pointCount(), "Depth 데이터 대기 중 · 천천히 움직여 추적 특징을 확보하세요.")
            }
        } catch (e: CameraNotAvailableException) {
            onStatus(pointCount(), "카메라를 사용할 수 없습니다.")
        } catch (e: Exception) {
            if (frameCounter++ % 90 == 0) onStatus(pointCount(), "추적 오류: ${e.javaClass.simpleName}")
        }
    }

    private fun drawCamera(frame: Frame) {
        if (program == 0 || textureId < 0) return
        val coords = FloatArray(8)
        frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, vertices, Coordinates2d.TEXTURE_NORMALIZED, coords)
        val b = directBuffer(coords)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(uvHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glVertexAttribPointer(uvHandle, 2, GLES20.GL_FLOAT, false, 0, b)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(textureHandle, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(uvHandle)
    }

    private fun createExternalTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        session?.setCameraTextureName(ids[0])
        return ids[0]
    }

    private fun createProgram(): Int {
        val vertex = "attribute vec4 a_Position; attribute vec2 a_TexCoord; varying vec2 v_TexCoord; void main(){ gl_Position=a_Position; v_TexCoord=a_TexCoord; }"
        val fragment = "#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES sTexture; varying vec2 v_TexCoord; void main(){ gl_FragColor=texture2D(sTexture,v_TexCoord); }"
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragment)
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vs); GLES20.glAttachShader(it, fs); GLES20.glLinkProgram(it)
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun directBuffer(values: FloatArray): FloatBuffer {
        val b = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        b.put(values).position(0)
        return b
    }
}
