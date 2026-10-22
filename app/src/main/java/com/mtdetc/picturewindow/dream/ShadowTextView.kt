package com.mtdetc.picturewindow.dream

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.text.TextPaint
import android.util.AttributeSet
import android.widget.TextClock
import android.widget.TextView

/**
 * White text that reads on light photos without a box: a few tight dark shadows that hug the letters.
 * Android paints one shadow per pass, so each layer is its own pass. The paint's shadow is set directly
 * so drawing doesn't invalidate the view again. Shadows are clipped at the view's edges, so each view
 * gets [PADDING_DP] of padding, more than the widest shadow reaches; the layout margins allow for it.
 */
private object TextShadows {
    const val PADDING_DP = 8f

    private class Layer(val radiusDp: Float, val offsetYDp: Float, val alpha: Int)

    private val layers = listOf(
        Layer(radiusDp = 4f, offsetYDp = 0f, alpha = 110),
        Layer(radiusDp = 2f, offsetYDp = 1f, alpha = 166),
        Layer(radiusDp = 1f, offsetYDp = 0f, alpha = 191),
    )

    fun draw(paint: TextPaint, density: Float, drawText: () -> Unit) {
        for (layer in layers) {
            paint.setShadowLayer(layer.radiusDp * density, 0f, layer.offsetYDp * density, Color.argb(layer.alpha, 0, 0, 0))
            drawText()
        }
    }

    fun padding(view: TextView) {
        val pixels = (PADDING_DP * view.resources.displayMetrics.density).toInt()
        view.setPadding(pixels, pixels, pixels, pixels)
    }
}

class ShadowTextView @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
) : TextView(context, attributes) {
    init {
        TextShadows.padding(this)
    }

    override fun onDraw(canvas: Canvas) =
        TextShadows.draw(paint, resources.displayMetrics.density) { super.onDraw(canvas) }
}

class ShadowTextClock @JvmOverloads constructor(
    context: Context,
    attributes: AttributeSet? = null,
) : TextClock(context, attributes) {
    init {
        TextShadows.padding(this)
    }

    override fun onDraw(canvas: Canvas) =
        TextShadows.draw(paint, resources.displayMetrics.density) { super.onDraw(canvas) }
}
