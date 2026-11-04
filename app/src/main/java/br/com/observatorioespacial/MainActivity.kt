package br.com.observatorioespacial

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.opengl.GLSurfaceView
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Bundle
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.TextView
import android.view.Gravity
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.Locale
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainActivity : Activity(), LocationListener {
    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: SpaceRenderer
    private lateinit var locationLabel: TextView
    private lateinit var locationManager: LocationManager

    companion object { private const val LOCATION_PERMISSION_REQUEST = 410 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            renderer = SpaceRenderer()
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            preserveEGLContextOnPause = true
        }
        root.addView(glView, FrameLayout.LayoutParams(-1, -1))
        val title = TextView(this).apply {
            text = "OBSERVATÓRIO ESPACIAL  •  3D"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(20, 24, 20, 12)
            setShadowLayer(8f, 0f, 1f, Color.BLACK)
        }
        root.addView(title, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        locationLabel = TextView(this).apply {
            text = "Solicitando localização…"
            setTextColor(Color.rgb(190, 215, 255))
            textSize = 12f
            setPadding(16, 12, 16, 22)
            setShadowLayer(6f, 0f, 1f, Color.BLACK)
        }
        root.addView(locationLabel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        checkLocationPermission()
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun checkLocationPermission() {
        if (hasLocationPermission()) startLocationUpdates()
        else requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), LOCATION_PERMISSION_REQUEST)
    }

    private fun startLocationUpdates() {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return
        try {
            var latest: Location? = null
            for (provider in locationManager.getProviders(true)) {
                val candidate = locationManager.getLastKnownLocation(provider)
                if (candidate != null && (latest == null || candidate.time > latest.time)) latest = candidate
            }
            latest?.let { onLocationChanged(it) }
            if (fine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000L, 10f, this)
            } else if (coarse && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000L, 100f, this)
            } else locationLabel.text = "Ative a localização • exploração 3D disponível"
        } catch (_: SecurityException) {
            locationLabel.text = "Permissão de localização não concedida"
        } catch (_: IllegalArgumentException) {
            locationLabel.text = "Provedor de localização indisponível"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) startLocationUpdates()
            else locationLabel.text = "Localização desativada • exploração 3D disponível"
        }
    }

    override fun onLocationChanged(location: Location) {
        val accuracy = if (location.hasAccuracy()) " • precisão ±${location.accuracy.toInt()} m" else ""
        val value = String.format(Locale.US, "Sua posição: %.4f°, %.4f°", location.latitude, location.longitude) + accuracy
        runOnUiThread { locationLabel.text = value }
    }
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) { runOnUiThread { locationLabel.text = "GPS indisponível — verifique a localização" } }
    @Deprecated("Compatibilidade com API 26")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onResume() {
        super.onResume()
        if (::glView.isInitialized) glView.onResume()
        if (::locationManager.isInitialized && hasLocationPermission()) startLocationUpdates()
    }
    override fun onPause() {
        if (::glView.isInitialized) glView.onPause()
        if (::locationManager.isInitialized) try { locationManager.removeUpdates(this) } catch (_: Exception) {}
        super.onPause()
    }
}

private class SpaceRenderer : GLSurfaceView.Renderer {
    private lateinit var sphere: SphereMesh
    private lateinit var stars: FloatBuffer
    private var program = 0
    private var positionHandle = 0
    private var normalHandle = 0
    private var mvpHandle = 0
    private var modelHandle = 0
    private var colorHandle = 0
    private var lightHandle = 0
    private var lightingHandle = 0
    private var pointSizeHandle = 0
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val vp = FloatArray(16)
    @Volatile private var zoom = 1f
    @Volatile private var yaw = 0f
    @Volatile private var pitch = 0.18f
    private var surfaceWidth = 1
    private var surfaceHeight = 1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.008f, 0.012f, 0.035f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        val vertexShader = """
            uniform mat4 uMVP;
            uniform mat4 uModel;
            uniform float uPointSize;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            varying vec3 vPosition;
            void main() {
                vec4 world = uModel * vec4(aPosition, 1.0);
                vPosition = world.xyz;
                vNormal = mat3(uModel) * aNormal;
                gl_Position = uMVP * vec4(aPosition, 1.0);
                gl_PointSize = uPointSize;
            }
        """.trimIndent()
        val fragmentShader = """
            precision mediump float;
            uniform vec4 uColor;
            uniform vec3 uLight;
            uniform int uLighting;
            varying vec3 vNormal;
            varying vec3 vPosition;
            void main() {
                float shade = 1.0;
                if (uLighting == 1) {
                    vec3 n = normalize(vNormal);
                    vec3 l = normalize(uLight - vPosition);
                    float diffuse = max(dot(n, l), 0.0);
                    shade = 0.20 + diffuse * 0.90;
                }
                gl_FragColor = vec4(uColor.rgb * shade, uColor.a);
            }
        """.trimIndent()
        program = linkProgram(compileShader(GLES20.GL_VERTEX_SHADER, vertexShader), compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader))
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMVP")
        modelHandle = GLES20.glGetUniformLocation(program, "uModel")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        lightHandle = GLES20.glGetUniformLocation(program, "uLight")
        lightingHandle = GLES20.glGetUniformLocation(program, "uLighting")
        pointSizeHandle = GLES20.glGetUniformLocation(program, "uPointSize")
        sphere = SphereMesh(40, 56)
        val random = Random(2026)
        val starData = FloatArray(1800 * 3) { 0f }
        for (i in 0 until 1800) {
            val theta = random.nextFloat() * Math.PI.toFloat() * 2f
            val z = random.nextFloat() * 2f - 1f
            val radius = 38f + random.nextFloat() * 12f
            val ring = kotlin.math.sqrt(1f - z * z)
            starData[i * 3] = radius * ring * cos(theta)
            starData[i * 3 + 1] = radius * z
            starData[i * 3 + 2] = radius * ring * sin(theta)
        }
        stars = floatBuffer(starData)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        Matrix.perspectiveM(projection, 0, 48f, surfaceWidth.toFloat() / surfaceHeight, 0.1f, 120f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        val distance = 24f / zoom.coerceIn(0.65f, 2.6f)
        val eyeX = sin(yaw) * distance
        val eyeZ = cos(yaw) * distance
        val eyeY = 5f + pitch * 10f
        Matrix.setLookAtM(view, 0, eyeX, eyeY, eyeZ, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, projection, 0, view, 0)

        Matrix.setIdentityM(model, 0)
        drawPoints(stars, 1800, 1.6f, 0.78f, 0.86f, 1f)
        drawOrbit(2.2f, 0.20f, 0.25f, 0.38f)
        drawOrbit(3.3f, 0.20f, 0.25f, 0.38f)
        drawOrbit(4.6f, 0.20f, 0.25f, 0.38f)
        drawOrbit(6.2f, 0.20f, 0.25f, 0.38f)
        drawOrbit(8.2f, 0.20f, 0.25f, 0.38f)
        drawPlanet(0f, 0f, 0f, 0.72f, 1.0f, 0.66f, 0.20f, false)
        val bodies = arrayOf(
            floatArrayOf(2.2f, 0.12f, 0.0f, 0.15f, 0.15f, 0.17f, 0.92f),
            floatArrayOf(3.3f, -0.22f, 0.0f, 0.38f, 0.29f, 0.12f, 1.0f),
            floatArrayOf(4.6f, 0.12f, 0.0f, 0.18f, 0.43f, 0.95f, 1.0f),
            floatArrayOf(6.2f, -0.16f, 0.0f, 0.72f, 0.25f, 0.12f, 0.88f),
            floatArrayOf(8.2f, 0.12f, 0.0f, 0.75f, 0.59f, 0.34f, 1.25f)
        )
        for ((index, b) in bodies.withIndex()) {
            val angle = (index * 1.13f) + yaw * 0.25f
            drawPlanet(cos(angle) * b[0], b[1], sin(angle) * b[0], b[6], b[3], b[4], b[5], true)
        }
    }

    private fun drawPlanet(x: Float, y: Float, z: Float, radius: Float, red: Float, green: Float, blue: Float, lit: Boolean) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y, z)
        Matrix.scaleM(model, 0, radius, radius, radius)
        drawMesh(sphere, model, red, green, blue, if (lit) 1 else 0)
    }

    private fun drawMesh(mesh: SphereMesh, modelMatrix: FloatArray, r: Float, g: Float, b: Float, lighting: Int) {
        Matrix.multiplyMM(mvp, 0, vp, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(modelHandle, 1, false, modelMatrix, 0)
        GLES20.glUniform4f(colorHandle, r, g, b, 1f)
        GLES20.glUniform3f(lightHandle, -5f, 8f, 7f)
        GLES20.glUniform1i(lightingHandle, lighting)
        GLES20.glUniform1f(pointSizeHandle, 1f)
        mesh.draw(positionHandle, normalHandle)
    }

    private fun drawPoints(buffer: FloatBuffer, count: Int, size: Float, r: Float, g: Float, b: Float) {
        Matrix.setIdentityM(model, 0)
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(modelHandle, 1, false, model, 0)
        GLES20.glUniform4f(colorHandle, r, g, b, 1f)
        GLES20.glUniform1i(lightingHandle, 0)
        GLES20.glUniform1f(pointSizeHandle, size)
        buffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, buffer)
        GLES20.glDisableVertexAttribArray(normalHandle)
        GLES20.glVertexAttrib3f(normalHandle, 0f, 1f, 0f)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    private fun drawOrbit(radius: Float, r: Float, g: Float, b: Float) {
        val data = FloatArray(361 * 3)
        for (i in 0..360) {
            val a = i * Math.PI.toFloat() / 180f
            data[i * 3] = cos(a) * radius
            data[i * 3 + 1] = 0f
            data[i * 3 + 2] = sin(a) * radius
        }
        val buffer = floatBuffer(data)
        Matrix.setIdentityM(model, 0)
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(modelHandle, 1, false, model, 0)
        GLES20.glUniform4f(colorHandle, r, g, b, 1f)
        GLES20.glUniform1i(lightingHandle, 0)
        GLES20.glUniform1f(pointSizeHandle, 1f)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, buffer)
        GLES20.glDisableVertexAttribArray(normalHandle)
        GLES20.glVertexAttrib3f(normalHandle, 0f, 1f, 0f)
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, 361)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    fun changeView(scale: Float, dx: Float, dy: Float) {
        zoom = (zoom * scale).coerceIn(0.65f, 2.6f)
        yaw += dx * 0.008f
        pitch = (pitch + dy * 0.004f).coerceIn(-0.45f, 0.75f)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val message = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Falha no shader: $message")
        }
        return shader
    }

    private fun linkProgram(vertex: Int, fragment: Int): Int {
        val result = GLES20.glCreateProgram()
        GLES20.glAttachShader(result, vertex)
        GLES20.glAttachShader(result, fragment)
        GLES20.glLinkProgram(result)
        val status = IntArray(1)
        GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) throw RuntimeException("Falha ao vincular shaders: " + GLES20.glGetProgramInfoLog(result))
        return result
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(values).apply { position(0) }
}

private class SphereMesh(latitudeBands: Int, longitudeBands: Int) {
    private val vertices: FloatBuffer
    private val normals: FloatBuffer
    private val indices: ShortBuffer
    private val indexCount: Int

    init {
        val vertexData = ArrayList<Float>()
        val normalData = ArrayList<Float>()
        val indexData = ArrayList<Short>()
        for (lat in 0..latitudeBands) {
            val theta = lat * Math.PI.toFloat() / latitudeBands
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)
            for (lon in 0..longitudeBands) {
                val phi = lon * 2f * Math.PI.toFloat() / longitudeBands
                val x = cos(phi) * sinTheta
                val y = cosTheta
                val z = sin(phi) * sinTheta
                vertexData.add(x); vertexData.add(y); vertexData.add(z)
                normalData.add(x); normalData.add(y); normalData.add(z)
            }
        }
        for (lat in 0 until latitudeBands) {
            for (lon in 0 until longitudeBands) {
                val first = (lat * (longitudeBands + 1) + lon).toShort()
                val second = (first.toInt() + longitudeBands + 1).toShort()
                indexData.add(first); indexData.add(second); indexData.add((first.toInt() + 1).toShort())
                indexData.add(second); indexData.add((second.toInt() + 1).toShort()); indexData.add((first.toInt() + 1).toShort())
            }
        }
        vertices = ByteBuffer.allocateDirect(vertexData.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexData.forEach { vertices.put(it) }; vertices.position(0)
        normals = ByteBuffer.allocateDirect(normalData.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        normalData.forEach { normals.put(it) }; normals.position(0)
        indices = ByteBuffer.allocateDirect(indexData.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        indexData.forEach { indices.put(it) }; indices.position(0)
        indexCount = indexData.size
    }

    fun draw(positionHandle: Int, normalHandle: Int) {
        vertices.position(0); normals.position(0); indices.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, normals)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indices)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
    }
}
