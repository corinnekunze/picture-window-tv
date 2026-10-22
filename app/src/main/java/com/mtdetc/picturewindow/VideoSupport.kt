package com.mtdetc.picturewindow

import android.media.MediaCodecList
import android.media.MediaFormat

/**
 * Whether this TV can decode the feed's 4K HEVC videos. Most TVs draw apps at 1080p but play video at
 * full 4K, so a TV that can gets each photo's video; one that can't keeps to the images.
 */
object VideoSupport {
    val canPlay4k: Boolean by lazy {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { codec -> !codec.isEncoder }
            .filter { codec -> codec.supportedTypes.any { type -> type.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true) } }
            .any { codec ->
                codec.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC).videoCapabilities?.isSizeSupported(3840, 2160) == true
            }
    }
}
