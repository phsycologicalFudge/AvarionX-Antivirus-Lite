package com.colourswift.avarionxlite.home_ui.themes

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.colourswift.avarionxlite.R
import kotlin.math.min

class ProtectionRingView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val stroke = 3f * resources.displayMetrics.density
    private val arcColor = context.getColor(R.color.ring_arc)
    private val trackColor = context.getColor(R.color.ring_track)
    private val oval = RectF()
    private var fraction = 0f
    private var protectedState = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = stroke
    }

    private val sweep = ValueAnimator().apply {
        duration = 900L
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            fraction = it.animatedValue as Float
            invalidate()
        }
    }

    fun setProtected(enabled: Boolean, animate: Boolean) {
        if (enabled == protectedState) return
        protectedState = enabled
        val target = if (enabled) 1f else 0f
        sweep.cancel()
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            sweep.setFloatValues(fraction, target)
            sweep.start()
        } else {
            fraction = target
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - stroke
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)
        paint.color = trackColor
        canvas.drawArc(oval, START, SPAN, false, paint)
        if (fraction > 0f) {
            paint.color = arcColor
            canvas.drawArc(oval, START, SPAN * fraction, false, paint)
        }
    }

    override fun onDetachedFromWindow() {
        sweep.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val START = 117f
        const val SPAN = 306f
    }
}
