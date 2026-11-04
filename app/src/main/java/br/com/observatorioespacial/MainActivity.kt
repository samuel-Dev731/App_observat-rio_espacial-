package br.com.observatorioespacial

import android.app.Activity
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(UniverseView())
    }
}

private class UniverseView(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stars = List(260) {
        Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 2.2f + 0.4f)
    }
    private var zoom = 1f
    private var lastDistance = 0f
    private var rotation = 0f
    private var lastX = 0f

    init {
        setBackgroundColor(Color.rgb(3, 7, 24))
        isFocusable = true
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
        val radii = floatArrayOf(48f, 76f, 108f, 145f, 188f)
        radii.forEach { r ->
            canvas.drawOval(RectF(cx - r, cy - r * 0.48f, cx + r, cy + r * 0.48f), orbitPaint)
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
            val angle = i * 1.05f + 0.55f
            val x = cx + (cos(angle.toDouble()) * p.first).toFloat()
            val y = cy + (sin(angle.toDouble()) * p.first * 0.48f).toFloat()
            paint.color = p.second
            canvas.drawCircle(x, y, p.third, paint)
        }
        canvas.restore()

        paint.color = Color.WHITE
        paint.textSize = 23f
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        canvas.drawText("OBSERVATÓRIO ESPACIAL", 22f, 48f, paint)
        paint.color = Color.rgb(145, 190, 235)
        paint.textSize = 13f
        canvas.drawText("UNIVERSO 3D • PROTÓTIPO 1.0", 23f, 70f, paint)

        paint.color = Color.WHITE
        paint.textSize = 14f
        canvas.drawText("Sistema Solar — visão ilustrativa", 22f, h - 54f, paint)
        paint.color = Color.LTGRAY
        paint.textSize = 12f
        canvas.drawText("Pinça para zoom • arraste para girar", 22f, h - 32f, paint)
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
                    if (lastDistance > 0f) {
                        zoom = (zoom * (newDistance / lastDistance)).coerceIn(0.55f, 2.8f)
                    }
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
