package com.mtdetc.picturewindow.dream

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.text.TextPaint
import android.util.AttributeSet
import android.widget.TextClock
import android.widget.TextView

/**
 * White text that reads on fog, snow and cloud without a box. The mockup's four shadows, drawn as four
 * passes from the softest glow to the tightest edge (Android paints one shadow per pass). The paint's
 * shadow is set directly so drawing doesn't invalidate the view again.
 */
private object TextShadows {
    private class Layer(val radiusDp: Float, val offsetYDp: Float, val alpha: Int)

    private val layers = listOf(
        Layer(radiusDp = 22f, offsetYDp = 0f, alpha = 89),
        Layer(radiusDp = 8f, offsetYDp = 0f, alpha = 128),
        Layer(radiusDp = 2f, offsetYDp = 1f, alpha = 166),
        Layer(radiusDp = 1f, offsetYDp = 0f, alpha = 191),
    )

    fun draw(paint: TextPaint, density: Float, drawText: () -> Unit) {
        for (layer in layers) {
            paint.setShadowLayer(layer.radiusDp * density, 0f, layer.offsetYDp * density, Color.argb(layer.alpha, 0, 0, 0))
            drawText()
        }
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
