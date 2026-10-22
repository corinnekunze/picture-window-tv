package com.mtdetc.picturewindow.dream

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Decodes a 3840×2160 photo no larger than the screen needs. On a TV whose apps draw at 1080p this
 * halves each side, so a bitmap costs about 8 MB instead of 33 MB.
 */
object PhotoDecoder {

    fun decode(file: File, screenWidth: Int, screenHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, screenWidth, screenHeight)
        }
        return BitmapFactory.decodeFile(file.path, options)
    }

    /** The largest power of two that still leaves the image at least as big as the screen. */
    fun sampleSize(imageWidth: Int, imageHeight: Int, screenWidth: Int, screenHeight: Int): Int {
        var sample = 1
        while (imageWidth / (sample * 2) >= screenWidth && imageHeight / (sample * 2) >= screenHeight) {
            sample *= 2
        }
        return sample
    }
}
