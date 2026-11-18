package com.colourswift.avarionxlite.themes

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.colourswift.avarionxlite.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private class Blob(val x: Float, val y: Float, val color: Int, val radius: Float, val opacity: Float)

class MeshBackgroundView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val blobs = if (isDark()) DARK_BLOBS else LIGHT_BLOBS
    private val base = context.getColor(R.color.surface)
    private val paints = blobs.map { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val radii = FloatArray(blobs.size)
    private var progress = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 22000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    private fun isDark() =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val dim = max(w, h).toFloat()
        blobs.forEachIndexed { i, blob ->
            val radius = blob.radius * dim
            radii[i] = radius
            paints[i].shader = RadialGradient(
                0f,
                0f,
                radius,
                intArrayOf(
                    tint(blob.color, blob.opacity * 1.35f),
                    tint(blob.color, blob.opacity * 0.92f),
                    tint(blob.color, blob.opacity * 0.44f),
                    tint(blob.color, 0f)
                ),
                floatArrayOf(0f, 0.34f, 0.68f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(base)
        val w = width.toFloat()
        val h = height.toFloat()
        val t = progress * PI.toFloat() * 2f
        blobs.forEachIndexed { i, blob ->
            val phase = i * 1.91f
            val cx = blob.x * w + sin(t + phase) * w * 0.08f + sin(t * 2f + phase) * w * 0.024f
            val cy = blob.y * h + cos(t + phase) * h * 0.06f + cos(t * 2f + phase) * h * 0.018f
            canvas.save()
            canvas.translate(cx, cy)
            canvas.drawCircle(0f, 0f, radii[i], paints[i])
            canvas.restore()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (ValueAnimator.areAnimatorsEnabled()) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (!animator.isStarted) return
        if (visibility == VISIBLE) animator.resume() else animator.pause()
    }

    private fun tint(color: Int, alpha: Float): Int =
        Color.argb((alpha.coerceIn(0f, 1f) * 255f).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        private val LIGHT_BLOBS = listOf(
            Blob(0.10f, 0.08f, 0xFFFFFFFF.toInt(), 0.42f, 0.52f),
            Blob(0.86f, 0.05f, 0xFFE4E8F0.toInt(), 0.48f, 0.48f),
            Blob(0.18f, 0.82f, 0xFFDDE5F2.toInt(), 0.40f, 0.34f),
            Blob(0.72f, 0.62f, 0xFFF8FAFC.toInt(), 0.36f, 0.28f)
        )

        private val DARK_BLOBS = listOf(
            Blob(0.10f, 0.08f, 0xFF28282C.toInt(), 0.16f, 0.42f),
            Blob(0.36f, 0.16f, 0xFF242428.toInt(), 0.14f, 0.34f),
            Blob(0.78f, 0.10f, 0xFF2C2C31.toInt(), 0.18f, 0.38f),
            Blob(0.16f, 0.42f, 0xFF202024.toInt(), 0.13f, 0.32f),
            Blob(0.82f, 0.44f, 0xFF303035.toInt(), 0.15f, 0.30f),
            Blob(0.30f, 0.72f, 0xFF26262A.toInt(), 0.17f, 0.36f),
            Blob(0.70f, 0.78f, 0xFF34343A.toInt(), 0.14f, 0.28f)
        )
    }
}
