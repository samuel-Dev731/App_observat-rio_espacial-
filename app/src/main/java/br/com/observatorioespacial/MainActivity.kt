package br.com.observatorioespacial

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : Activity(), LocationListener {
    private lateinit var universeView: UniverseView
    private lateinit var locationManager: LocationManager

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 410
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        universeView = UniverseView(this)
        setContentView(universeView)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        checkLocationPermission()
    }

    private fun hasLocationPermission(): Boolean {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun checkLocationPermission() {
        if (hasLocationPermission()) {
            startLocationUpdates()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        }
    }

    private fun startLocationUpdates() {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            universeView.setLocationText("Localização não autorizada")
            return
        }

        try {
            var latest: Location? = null
            for (provider in locationManager.getProviders(true)) {
                val candidate = locationManager.getLastKnownLocation(provider)
                if (candidate != null && (latest == null || candidate.time > latest.time)) latest = candidate
            }
            latest?.let { onLocationChanged(it) }

            if (fine && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10_000L, 10f, this)
            } else if (coarse && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30_000L, 100f, this)
            } else {
                universeView.setLocationText("Ative a localização do aparelho")
            }
        } catch (_: SecurityException) {
            universeView.setLocationText("Permissão de localização necessária")
        } catch (_: IllegalArgumentException) {
            universeView.setLocationText("Provedor de localização indisponível")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) startLocationUpdates()
            else universeView.setLocationText("Localização não autorizada — exploração disponível")
        }
    }

    override fun onLocationChanged(location: Location) {
        val coords = String.format(Locale.US, "Lat %.4f°  Lon %.4f°", location.latitude, location.longitude)
        val accuracy = if (location.hasAccuracy()) " • precisão ±" + location.accuracy.toInt() + " m" else ""
        universeView.setLocationText(coords + accuracy)
    }

    override fun onProviderEnabled(provider: String) {
        universeView.setLocationText("Localização ativada — obtendo posição…")
    }

    override fun onProviderDisabled(provider: String) {
        universeView.setLocationText("GPS indisponível — verifique a localização")
    }

    @Deprecated("Deprecated by Android; retained for API 26 compatibility")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onPause() {
        super.onPause()
        if (::locationManager.isInitialized) {
            try { locationManager.removeUpdates(this) } catch (_: Exception) { }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::locationManager.isInitialized && hasLocationPermission()) startLocationUpdates()
    }
}

private class UniverseView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stars = List(420) {
        Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 2.2f + 0.4f)
    }
    private var zoom = 1f
    private var lastDistance = 0f
    private var rotation = 0f
    private var lastX = 0f
    private var locationText = "Solicitando localização…"

    init {
        setBackgroundColor(Color.rgb(3, 7, 24))
        isFocusable = true
    }

    fun setLocationText(value: String) {
        locationText = value
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w * 0.5f
        val cy = h * 0.48f

        stars.forEachIndexed { index, star ->
            paint.color = if (index % 13 == 0) Color.rgb(130, 190, 255) else Color.WHITE
            paint.alpha = (150 + star.third * 45).toInt().coerceIn(0, 255)
            canvas.drawCircle(star.first * w, star.second * h, star.third, paint)
        }

        canvas.save()
        canvas.scale(zoom, zoom, cx, cy)
        canvas.rotate(rotation, cx, cy)
        val orbitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.argb(100, 120, 170, 220)
        }
        floatArrayOf(48f, 76f, 108f, 145f, 188f).forEach { r ->
            canvas.drawOval(RectF(cx-r, cy-r*0.48f, cx+r, cy+r*0.48f), orbitPaint)
        }

        paint.color = Color.rgb(255, 190, 55)
        canvas.drawCircle(cx, cy, 22f, paint)
        paint.color = Color.argb(75, 255, 150, 20)
        canvas.drawCircle(cx, cy, 31f, paint)

        val planets = listOf(
            Triple(48f, Color.rgb(170, 160, 145), 4f),
            Triple(76f, Color.rgb(230, 185, 95), 6f),
            Triple(108f, Color.rgb(65, 145, 245), 7f),
            Triple(145f, Color.rgb(205, 105, 70), 5.5f),
            Triple(188f, Color.rgb(220, 180, 125), 12f)
        )
        planets.forEachIndexed { i, p ->
            val angle = i * 1.05f + 0.55f + rotation * 0.01f
            val x = cx + (cos(angle.toDouble()) * p.first).toFloat()
            val y = cy + (sin(angle.toDouble()) * p.first * 0.48f).toFloat()
            paint.color = p.second
            canvas.drawCircle(x, y, p.third, paint)
        }
        canvas.restore()

        paint.color = Color.WHITE
        paint.textSize = 21f
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        canvas.drawText("OBSERVATÓRIO ESPACIAL", 20f, 44f, paint)
        paint.color = Color.rgb(145, 190, 235)
        paint.textSize = 13f
        canvas.drawText("UNIVERSO • VERSÃO 1.0.0", 21f, 66f, paint)

        paint.color = Color.WHITE
        paint.textSize = 13f
        canvas.drawText("Sistema Solar — modelo ilustrativo", 20f, h-74f, paint)
        paint.color = Color.rgb(145, 190, 235)
        paint.textSize = 11f
        canvas.drawText(locationText, 20f, h-51f, paint)
        paint.color = Color.LTGRAY
        paint.textSize = 11f
        canvas.drawText("Pinça: zoom • arraste: girar", 20f, h-28f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastDistance = 0f
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) lastDistance = distance(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val newDistance = distance(event)
                    if (lastDistance > 0f) zoom = (zoom * (newDistance / lastDistance)).coerceIn(0.55f, 2.8f)
                    lastDistance = newDistance
                } else {
                    rotation += (event.x - lastX) * 0.35f
                    lastX = event.x
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                lastDistance = 0f
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun distance(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
