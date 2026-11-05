package br.com.observatorioespacial

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.EditText
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

class MainActivity : Activity(), LocationListener, SensorEventListener {
    private lateinit var glView: GLSurfaceView
    private lateinit var nativeBridge: NativeBridge
    private lateinit var spaceRenderer: NativeSpaceRenderer
    private lateinit var locationLabel: TextView
    private lateinit var graphicsLabel: TextView
    private lateinit var dataLabel: TextView
    private lateinit var locationManager: LocationManager
    private var currentLocation: Location? = null
    private lateinit var sensorManager: SensorManager
    private lateinit var connectivityManager: ConnectivityManager
    private var satelliteNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var d2dLabel: TextView? = null
    private var compassView: SkyCompassView? = null
    private var universeLiveMode = true
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

        val liveUniverseButton = TextView(this).apply {
            text = "◉  MODO LIVE • UNIVERSO INTEIRO"
            setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundColor(Color.argb(210, 25, 65, 105))
            setOnClickListener {
                universeLiveMode = !universeLiveMode
                spaceRenderer.setUniverseMode(universeLiveMode)
                text = if (universeLiveMode) "◉  MODO LIVE • UNIVERSO INTEIRO" else "◎  EXPLORAÇÃO LIVRE 3D"
                dataLabel.text = if (universeLiveMode) "UNIVERSO LIVE • cena 3D animada e efemérides NASA/JPL quando disponíveis" else "Exploração livre • arraste para girar e pinça para aproximar"
            }
        }
        root.addView(liveUniverseButton, FrameLayout.LayoutParams(-1, dp(42), Gravity.TOP).apply {
            topMargin = dp(116); leftMargin = dp(14); rightMargin = dp(14)
        })
        val searchObjectsButton = TextView(this).apply {
            text = "⌕  PESQUISAR SATÉLITES, COMETAS E OUTROS"
            setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setBackgroundColor(Color.argb(205, 18, 48, 78))
            setOnClickListener { showObjectSearch() }
        }
        root.addView(searchObjectsButton, FrameLayout.LayoutParams(-1, dp(44), Gravity.TOP).apply {
            topMargin = dp(164); leftMargin = dp(14); rightMargin = dp(14)
        })
        d2dLabel = TextView(this).apply {
            text = if (Build.VERSION.SDK_INT >= 35) "D2D/NTN: verificando rede satelital…" else "D2D/NTN: Android 15+ e modem/operadora compatíveis necessários"
            setTextColor(Color.LTGRAY); textSize = 9f; setPadding(dp(16), dp(2), dp(16), dp(2))
            setShadowLayer(5f, 0f, 1f, Color.BLACK)
        }
        root.addView(d2dLabel, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { topMargin = dp(210) })

        locationLabel = TextView(this).apply {
            text = "Solicitando localização…"
            setTextColor(Color.rgb(190, 215, 255))
            textSize = 12f
            setPadding(16, 12, 16, 22)
            setShadowLayer(6f, 0f, 1f, Color.BLACK)
        }
        root.addView(locationLabel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        compassView = SkyCompassView(this)
        root.addView(compassView, FrameLayout.LayoutParams(dp(116), dp(116), Gravity.RIGHT or Gravity.BOTTOM).apply { rightMargin = dp(12); bottomMargin = dp(172) })
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
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        checkLocationPermission()
    }

    private fun showObjectSearch() {
        AlertDialog.Builder(this)
            .setTitle("Pesquisar objetos celestes")
            .setItems(arrayOf("Cometas, asteroides, planetas-anões e luas", "Satélites artificiais (ISS, Hubble etc.)", "Estrelas, constelações e objetos Messier")) { _, choice ->
                val satellite = choice == 1
                val skyCatalog = choice == 2
                val field = EditText(this).apply {
                    hint = when { satellite -> "Nome ou número NORAD (ex.: ISS)"; skyCatalog -> "Nome ou catálogo (ex.: Sírius, M31, M42)"; else -> "Nome ou designação (ex.: 1P/Halley)" }
                    setSingleLine(true)
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                }
                AlertDialog.Builder(this)
                    .setTitle(when { satellite -> "Buscar satélite"; skyCatalog -> "Buscar no catálogo do céu"; else -> "Buscar corpo celeste" })
                    .setView(field)
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Pesquisar") { _, _ ->
                        val query = field.text.toString().trim()
                        if (query.isNotEmpty()) {
                            dataLabel.text = "Consultando catálogo astronômico…"
                            val done: (String) -> Unit = { result ->
                                runOnUiThread {
                                    dataLabel.text = "Pesquisa concluída"
                                    val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(8)) }
                                    val blue = TextView(this).apply {
                                        text = result.lineSequence().firstOrNull { it.startsWith("VISÍVEL ATÉ:") || it.startsWith("VISIBILIDADE:") } ?: "VISIBILIDADE: previsão JPL"
                                        setTextColor(Color.rgb(65, 165, 255)); textSize = 11f
                                    }
                                    val gray = TextView(this).apply {
                                        text = result.lineSequence().firstOrNull { it.startsWith("PRÓXIMA VISIBILIDADE:") || it.startsWith("OBJETO:") } ?: "OBJETO: $query"
                                        setTextColor(Color.LTGRAY); textSize = 10f
                                    }
                                    val details = TextView(this).apply {
                                        text = result.lineSequence().filterNot { it.startsWith("VISÍVEL ATÉ:") || it.startsWith("VISIBILIDADE:") || it.startsWith("PRÓXIMA VISIBILIDADE:") }.joinToString("\n")
                                        setTextColor(Color.WHITE); textSize = 13f
                                    }
                                    panel.addView(blue); panel.addView(gray)
                                    if (satellite) {
                                        val az = Regex("Azimute: ([0-9.]+)").find(result)?.groupValues?.get(1)?.toFloatOrNull()
                                        val el = Regex("Elevação: (-?[0-9.]+)").find(result)?.groupValues?.get(1)?.toFloatOrNull()
                                        if (az != null && el != null) {
                                            panel.addView(SatelliteRadarView(this, az, el),
                                                LinearLayout.LayoutParams(dp(190), dp(190)).apply { gravity = Gravity.CENTER })
                                        }
                                    }
                                    panel.addView(details)
                                    AlertDialog.Builder(this).setTitle("Resultado da pesquisa").setView(panel).setPositiveButton("Fechar", null).show()
                                }
                            }
                            if (satellite) {
                                val observer = currentLocation
                                if (observer == null) {
                                    AlertDialog.Builder(this)
                                        .setMessage("Ative a localização para calcular a passagem do satélite sobre sua região.")
                                        .setPositiveButton("OK", null).show()
                                } else {
                                    ObjectCatalogService.searchArtificialSatellite(
                                        query, observer.latitude, observer.longitude,
                                        if (observer.hasAltitude()) observer.altitude else 0.0, done
                                    )
                                }
                            } else if (skyCatalog) {
                                val result = SkyCatalogService.search(query, currentLocation)
                                done(result)
                            } else {
                                val observer = currentLocation
                                if (observer == null) {
                                    AlertDialog.Builder(this)
                                        .setMessage("Ative a localização do aparelho para calcular azimute e elevação do objeto no seu céu.")
                                        .setPositiveButton("OK", null)
                                        .show()
                                } else {
                                    ObjectCatalogService.searchNaturalObject(
                                        query, observer.latitude, observer.longitude,
                                        if (observer.hasAltitude()) observer.altitude else 0.0, done
                                    )
                                }
                            }
                        }
                    }
                    .show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
        currentLocation = location
        val accuracy = if (location.hasAccuracy()) " • precisão ±${location.accuracy.toInt()} m" else ""
        val value = String.format(Locale.US, "Sua posição: %.4f°, %.4f°", location.latitude, location.longitude) + accuracy
        runOnUiThread { locationLabel.text = value }
    }
    private fun registerSatelliteNetworkMonitor() {
        if (Build.VERSION.SDK_INT < 35 || satelliteNetworkCallback != null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val satellite = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_SATELLITE)
                runOnUiThread {
                    d2dLabel?.text = if (satellite)
                        "REDE SATELITAL/D2D: conexão reportada pelo Android"
                    else
                        "D2D/NTN: sem conexão satelital ativa • SGP4 funciona sem D2D"
                }
            }
            override fun onLost(network: Network) {
                runOnUiThread { d2dLabel?.text = "D2D/NTN: conexão satelital não ativa no momento" }
            }
        }
        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
            satelliteNetworkCallback = callback
            val active = connectivityManager.activeNetwork
            val caps = active?.let { connectivityManager.getNetworkCapabilities(it) }
            d2dLabel?.text = if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_SATELLITE) == true)
                "REDE SATELITAL/D2D: conexão reportada pelo Android"
            else
                "D2D/NTN: aguardando rede satelital compatível"
        } catch (_: Exception) {
            d2dLabel?.text = "D2D/NTN: status indisponível neste aparelho"
        }
    }

    private fun unregisterSatelliteNetworkMonitor() {
        val callback = satelliteNetworkCallback ?: return
        try { connectivityManager.unregisterNetworkCallback(callback) } catch (_: Exception) {}
        satelliteNetworkCallback = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val matrix = FloatArray(9); val orientation = FloatArray(3)
            SensorManager.getRotationMatrixFromVector(matrix, event.values)
            SensorManager.getOrientation(matrix, orientation)
            compassView?.setAzimuth(Math.toDegrees(orientation[0].toDouble()).toFloat())
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) { runOnUiThread { locationLabel.text = "GPS indisponível — verifique a localização" } }
    @Deprecated("Compatibilidade com API 26")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onResume() {
        super.onResume()
        if (::glView.isInitialized) glView.onResume()
        if (::sensorManager.isInitialized) sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        registerSatelliteNetworkMonitor()
        if (::locationManager.isInitialized && hasLocationPermission()) startLocationUpdates()
    }
    override fun onPause() {
        if (::sensorManager.isInitialized) sensorManager.unregisterListener(this)
        unregisterSatelliteNetworkMonitor()
        if (::glView.isInitialized) glView.onPause()
        if (::locationManager.isInitialized) try { locationManager.removeUpdates(this) } catch (_: Exception) {}
        super.onPause()
    }
}

private class SatelliteRadarView(context: Context, private val azimuthDeg: Float, private val elevationDeg: Float) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f; val radius = minOf(cx, cy) * 0.82f
        paint.style = Paint.Style.FILL; paint.color = Color.argb(190, 3, 12, 28); canvas.drawCircle(cx, cy, radius, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f; paint.color = Color.rgb(85, 145, 195)
        canvas.drawCircle(cx, cy, radius, paint); canvas.drawCircle(cx, cy, radius * .66f, paint); canvas.drawCircle(cx, cy, radius * .33f, paint)
        canvas.drawLine(cx-radius, cy, cx+radius, cy, paint); canvas.drawLine(cx, cy-radius, cx, cy+radius, paint)
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER; paint.textSize = 10f; paint.color = Color.WHITE
        canvas.drawText("N", cx, cy-radius+12f, paint); canvas.drawText("S", cx, cy+radius-3f, paint)
        canvas.drawText("L", cx+radius-7f, cy+4f, paint); canvas.drawText("O", cx-radius+7f, cy+4f, paint)
        val distance = radius * (1f - elevationDeg.coerceIn(0f, 90f) / 90f)
        val angle = Math.toRadians(azimuthDeg.toDouble() - 90.0)
        val x = cx + (distance * kotlin.math.cos(angle)).toFloat()
        val y = cy + (distance * kotlin.math.sin(angle)).toFloat()
        paint.color = Color.rgb(65, 165, 255); canvas.drawCircle(x, y, 7f, paint)
        paint.color = Color.WHITE; paint.textSize = 9f
        canvas.drawText("AZ ${azimuthDeg.toInt()}° • EL ${elevationDeg.toInt()}°", cx, height-2f, paint)
    }
}

private class SkyCompassView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    @Volatile private var azimuth = 0f
    fun setAzimuth(value: Float) { azimuth = (value + 360f) % 360f; postInvalidateOnAnimation() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f; val r = minOf(cx, cy) * 0.82f
        paint.style = Paint.Style.FILL; paint.color = Color.argb(175, 3, 12, 28); canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f; paint.color = Color.argb(210, 110, 175, 235); canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER; paint.textSize = 10f; paint.color = Color.WHITE
        canvas.drawText("N", cx, cy-r+13f, paint); canvas.drawText("S", cx, cy+r-5f, paint)
        canvas.drawText("L", cx+r-8f, cy+4f, paint); canvas.drawText("O", cx-r+8f, cy+4f, paint)
        canvas.save(); canvas.rotate(-azimuth, cx, cy); paint.color = Color.rgb(65,165,255)
        val p = android.graphics.Path().apply { moveTo(cx,cy-r*0.68f); lineTo(cx-7f,cy+7f); lineTo(cx,cy+2f); lineTo(cx+7f,cy+7f); close() }
        canvas.drawPath(p, paint); canvas.restore()
        paint.textSize = 8f; paint.color = Color.LTGRAY; canvas.drawText("BÚSSOLA DO CÉU", cx, height-3f, paint)
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

    fun setUniverseMode(enabled: Boolean) { if (enabled) { zoom = 0.72f; yaw = 0f; pitch = 0.18f } else { zoom = 1f } }

    fun changeView(scale: Float, dx: Float, dy: Float) {
        zoom = (zoom * scale).coerceIn(0.65f, 2.6f)
        yaw += dx * 0.008f
        pitch = (pitch + dy * 0.004f).coerceIn(-0.45f, 0.75f)
    }

}
