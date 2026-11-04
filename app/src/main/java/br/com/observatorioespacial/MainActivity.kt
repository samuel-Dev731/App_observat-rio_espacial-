package br.com.observatorioespacial

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sqrt

private class NativeBridge {
    external fun nativeInit(memoryClassMb: Int)
    external fun nativeResize(width: Int, height: Int)
    external fun nativeRender(seconds: Float, zoom: Float, yaw: Float, pitch: Float)
    external fun nativeSetEphemeris(values: FloatArray)
    external fun getGpuRenderer(): String
    external fun getCpuCoreCount(): Int

    companion object { init { System.loadLibrary("observatorio_native") } }
}

class MainActivity : Activity(), LocationListener {
    private lateinit var glView: GLSurfaceView
    private lateinit var nativeBridge: NativeBridge
    private lateinit var spaceRenderer: NativeSpaceRenderer
    private lateinit var locationLabel: TextView
    private lateinit var graphicsLabel: TextView
    private lateinit var dataLabel: TextView
    private lateinit var locationManager: LocationManager
    private var previousTouchX = 0f
    private var previousTouchY = 0f
    private var previousPinchDistance = 0f

    companion object { private const val LOCATION_PERMISSION_REQUEST = 410 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nativeBridge = NativeBridge()
        val root = FrameLayout(this)
        glView = GLSurfaceView(this).apply {
            // ES 2.0 é o perfil mínimo garantido pelo manifesto.
            // Em GPUs ES 3.x, o driver mantém compatibilidade ES 2.0.
            setEGLContextClientVersion(2)
            spaceRenderer = NativeSpaceRenderer(nativeBridge, applicationContext) { info ->
                runOnUiThread { graphicsLabel.text = info }
            }
            setRenderer(spaceRenderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            preserveEGLContextOnPause = true
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        previousTouchX = event.x
                        previousTouchY = event.y
                        previousPinchDistance = 0f
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount >= 2) previousPinchDistance = pointerDistance(event)
                    MotionEvent.ACTION_MOVE -> {
                        if (event.pointerCount >= 2) {
                            val current = pointerDistance(event)
                            if (previousPinchDistance > 0f && current > 0f) spaceRenderer.changeView(current / previousPinchDistance, 0f, 0f)
                            previousPinchDistance = current
                        } else {
                            spaceRenderer.changeView(1f, event.x - previousTouchX, event.y - previousTouchY)
                            previousTouchX = event.x
                            previousTouchY = event.y
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> previousPinchDistance = 0f
                }
                true
            }
        }
        root.addView(glView, FrameLayout.LayoutParams(-1, -1))

        val title = TextView(this).apply {
            text = "OBSERVATÓRIO ESPACIAL • MOTOR 3D NATIVO"
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(18, 24, 18, 8)
            setShadowLayer(8f, 0f, 1f, Color.BLACK)
        }
        root.addView(title, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        graphicsLabel = TextView(this).apply {
            text = "Detectando GPU e perfil gráfico…"
            setTextColor(Color.rgb(170, 210, 255))
            textSize = 10f
            setPadding(18, 4, 18, 4)
            setShadowLayer(6f, 0f, 1f, Color.BLACK)
        }
        val graphicsParams = FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { topMargin = 62 }
        root.addView(graphicsLabel, graphicsParams)

        dataLabel = TextView(this).apply {
            text = "Carregando posições planetárias oficiais do NASA/JPL…"
            setTextColor(Color.rgb(170, 230, 210))
            textSize = 10f
            setPadding(18, 4, 18, 4)
            setShadowLayer(6f, 0f, 1f, Color.BLACK)
        }
        root.addView(dataLabel, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { topMargin = 88 })

        locationLabel = TextView(this).apply {
            text = "Solicitando localização…"
            setTextColor(Color.rgb(190, 215, 255))
            textSize = 12f
            setPadding(16, 12, 16, 22)
            setShadowLayer(6f, 0f, 1f, Color.BLACK)
        }
        root.addView(locationLabel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)
        addNasaImageGallery(root)
        AstronomyDataService.fetchPlanetPositions { positions, message ->
            runOnUiThread {
                dataLabel.text = message
                if (positions != null) {
                    glView.queueEvent { nativeBridge.nativeSetEphemeris(positions) }
                    dataLabel.text = "$message • movimento orbital acelerado • 1 dia por segundo"
                }
            }
        }
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        checkLocationPermission()
    }

    private fun addNasaImageGallery(root: FrameLayout) {
        val planets = listOf("Mercúrio", "Vênus", "Terra", "Marte", "Júpiter", "Saturno", "Urano", "Netuno")
        val queries = listOf("Mercury planet NASA spacecraft", "Venus planet NASA spacecraft", "Earth planet NASA", "Mars planet NASA Hubble", "Jupiter planet NASA Hubble", "Saturn planet NASA Cassini", "Uranus planet NASA", "Neptune planet NASA Voyager")
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.argb(145, 4, 10, 25))
            addView(strip)
        }
        root.addView(scroll, FrameLayout.LayoutParams(-1, dp(104), Gravity.BOTTOM).apply { bottomMargin = dp(58) })
        planets.forEachIndexed { index, name ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(5), dp(4), dp(5), dp(3))
            }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.rgb(22, 32, 52))
            }
            val label = TextView(this).apply {
                text = name
                setTextColor(Color.WHITE)
                textSize = 10f
                gravity = Gravity.CENTER
                maxLines = 1
            }
            card.addView(image, LinearLayout.LayoutParams(dp(96), dp(68)))
            card.addView(label, LinearLayout.LayoutParams(dp(100), dp(22)))
            strip.addView(card, LinearLayout.LayoutParams(dp(108), dp(100)))
            AstronomyDataService.fetchNasaThumbnail(queries[index]) { url ->
                if (url != null) AstronomyDataService.downloadImage(url) { bytes ->
                    if (bytes != null) {
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        runOnUiThread { image.setImageBitmap(bitmap) }
                    }
                }
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun pointerDistance(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return sqrt(dx * dx + dy * dy)
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

private class NativeSpaceRenderer(
    private val bridge: NativeBridge,
    private val appContext: Context,
    private val onProfile: (String) -> Unit
) : GLSurfaceView.Renderer {
    @Volatile private var zoom = 1f
    @Volatile private var yaw = 0f
    @Volatile private var pitch = 0.18f
    private var startNanos = 0L
    private var memoryClassMb = 128

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        memoryClassMb = (appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
        bridge.nativeInit(memoryClassMb)
        startNanos = System.nanoTime()
        val profile = bridge.getGpuRenderer() + " • CPU: " + bridge.getCpuCoreCount() + " núcleos lógicos • RAM app: " + memoryClassMb + " MB"
        onProfile(profile)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        bridge.nativeResize(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val seconds = ((System.nanoTime() - startNanos) / 1_000_000_000.0).toFloat()
        bridge.nativeRender(seconds, zoom, yaw, pitch)
    }

    fun changeView(scale: Float, dx: Float, dy: Float) {
        zoom = (zoom * scale).coerceIn(0.65f, 2.6f)
        yaw += dx * 0.008f
        pitch = (pitch + dy * 0.004f).coerceIn(-0.45f, 0.75f)
    }

}
