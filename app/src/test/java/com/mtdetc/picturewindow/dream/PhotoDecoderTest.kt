package com.mtdetc.picturewindow.dream

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoDecoderTest {
    @Test
    fun `halves a 4K photo for a 1080p screen`() =
        assertEquals(2, PhotoDecoder.sampleSize(3840, 2160, 1920, 1080))

    @Test
    fun `keeps full size for a 4K screen`() =
        assertEquals(1, PhotoDecoder.sampleSize(3840, 2160, 3840, 2160))

    @Test
    fun `never drops below the screen size`() =
        assertEquals(1, PhotoDecoder.sampleSize(3840, 2160, 1280, 1100))
}
