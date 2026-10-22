package com.mtdetc.picturewindow.dream

import android.media.MediaPlayer
import android.os.Handler
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.io.File

/**
 * Plays a photo's one-frame 4K video on the TV's video layer, which reaches the panel at full
 * resolution while apps draw at 1080p. The video sits behind the window, so anything the window draws
 * (images, text, the fade veil) shows on top. When playback ends, the last frame stays on screen.
 */
class VideoLayer(private val surface: SurfaceView, private val handler: Handler) {
    private var player: MediaPlayer? = null
    private var surfaceReady = false
    private var pending: (() -> Unit)? = null

    init {
        surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                surfaceReady = true
                pending?.invoke()
                pending = null
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                surfaceReady = false
                release()
            }
        })
    }

    /**
     * Starts [video], then calls [onShowing] once its frame has had time to reach the screen, or
     * [onFailed] if it can't be played. Replaces whatever was playing.
     */
    fun play(video: File, onShowing: () -> Unit, onFailed: () -> Unit) {
        val start = { start(video, onShowing, onFailed) }
        if (surfaceReady) start() else pending = start
    }

    fun release() {
        pending = null
        player?.release()
        player = null
    }

    private fun start(video: File, onShowing: () -> Unit, onFailed: () -> Unit) {
        release()
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        try {
            mediaPlayer.setDataSource(video.path)
            mediaPlayer.setDisplay(surface.holder)
            mediaPlayer.setOnPreparedListener { prepared ->
                prepared.start()
                // Tunneled playback doesn't report its first frame, so allow it a moment.
                handler.postDelayed({ if (player === prepared) onShowing() }, FIRST_FRAME_MS)
            }
            mediaPlayer.setOnErrorListener { failed, _, _ ->
                if (player === failed) {
                    release()
                    onFailed()
                }
                true
            }
            mediaPlayer.prepareAsync()
        } catch (error: Exception) {
            release()
            onFailed()
        }
    }

    private companion object {
        const val FIRST_FRAME_MS = 300L
    }
}
