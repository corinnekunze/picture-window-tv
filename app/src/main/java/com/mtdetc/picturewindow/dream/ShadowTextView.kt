package com.mtdetc.picturewindow.dream

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.text.TextPaint
import android.util.AttributeSet
import android.widget.TextClock
import android.widget.TextView

/**
 * White text that reads on fog, snow and cloud without a box: a soft dark glow drawn first, then the
 * text again with a tight dark edge. The paint's shadow is set directly so drawing doesn't
 * invalidate the view again.
 */
private object TextShadows {
    private const val GLOW_RADIUS_DP = 12f
    private const val EDGE_RADIUS_DP = 2f
    private const val EDGE_OFFSET_DP = 1f
    private val glowColor = Color.argb(150, 0, 0, 0)
    private val edgeColor = Color.argb(220, 0, 0, 0)

    fun draw(paint: TextPaint, density: Float, drawText: () -> Unit) {
        paint.setShadowLayer(GLOW_RADIUS_DP * density, 0f, 0f, glowColor)
        drawText()
        paint.setShadowLayer(EDGE_RADIUS_DP * density, 0f, EDGE_OFFSET_DP * density, edgeColor)
        drawText()
    }
}

class ShadowTextView @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
) : TextView(context, attributes) {
    override fun onDraw(canvas: Canvas) =
        TextShadows.draw(paint, resources.displayMetrics.density) { super.onDraw(canvas) }
}

class ShadowTextClock @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
) : TextClock(context, attributes) {
    override fun onDraw(canvas: Canvas) =
        TextShadows.draw(paint, resources.displayMetrics.density) { super.onDraw(canvas) }
}
