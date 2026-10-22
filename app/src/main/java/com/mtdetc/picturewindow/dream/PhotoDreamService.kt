package com.mtdetc.picturewindow.dream

import android.graphics.Bitmap
import android.graphics.Rect
import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.View
import android.widget.ImageView
import com.mtdetc.picturewindow.Config
import com.mtdetc.picturewindow.PictureWindowApp
import com.mtdetc.picturewindow.R
import com.mtdetc.picturewindow.rotation.CurrentPhoto
import com.mtdetc.picturewindow.settings.AppSettings
import com.mtdetc.picturewindow.sync.SyncScheduler
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * The photo screensaver: the current photo full screen, crossfading when the rotation moves on, with
 * the clock and date in the bottom-left corner and "Photo by …" in the bottom-right. Both move a little
 * every minute against burn-in, but only while faded out, so they never visibly slide. It checks the
 * rotation on the same tick, so a revoked photo goes within a minute.
 *
 * PROTOTYPE (branch prototype/4k-video): when files/videos/<id>.mp4 exists, the photo plays as a 4K
 * video on the TV's video layer, which reaches the panel at full resolution; the text stays on top.
 */
class PhotoDreamService : DreamService() {
    private val handler = Handler(Looper.getMainLooper())
    private var decoder: ExecutorService? = null
    private lateinit var frontImage: ImageView
    private lateinit var backImage: ImageView
    private lateinit var clock: View
    private lateinit var credit: ShadowTextView
    @Volatile private var shownId: String? = null
    private var shownBitmap: Bitmap? = null
    private lateinit var videoView: SurfaceView
    private var videoPlayer: MediaPlayer? = null
    private var videoSurfaceReady = false
    private var pendingVideo: File? = null

    private val tick = object : Runnable {
        override fun run() {
            relocateText()
            refreshPhoto()
            handler.postDelayed(this, Config.DRIFT_INTERVAL_MS)
        }
    }

    /** Wakes exactly when the rotation is due, so a change never waits for the next drift tick. */
    private val photoDue = Runnable { refreshPhoto() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        setContentView(R.layout.dream)
        frontImage = findViewById(R.id.photo_front)
        backImage = findViewById(R.id.photo_back)
        clock = findViewById(R.id.clock)
        credit = findViewById(R.id.credit)
        videoView = findViewById(R.id.photo_video)
        videoView.holder.addCallback(videoSurfaceCallback)
        clock.visibility = if (AppSettings(this).showClock) View.VISIBLE else View.GONE
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        decoder = Executors.newSingleThreadExecutor()
        handler.post(tick)
        SyncScheduler.syncIfStale(this, PictureWindowApp.from(this).store.readSyncState().lastSuccessAt)
    }

    override fun onDreamingStopped() {
        handler.removeCallbacksAndMessages(null)
        decoder?.shutdownNow()
        decoder = null
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        releaseVideo()
        frontImage.setImageDrawable(null)
        backImage.setImageDrawable(null)
        shownBitmap?.recycle()
        shownBitmap = null
        super.onDetachedFromWindow()
    }

    private fun refreshPhoto() {
        val executor = decoder ?: return
        executor.execute {
            val current = PictureWindowApp.from(this).library.current()
            if (current == null) {
                handler.post { showWaiting() }
                return@execute
            }
            scheduleNextPhoto(current)
            if (current.item.id == shownId) return@execute
            val video = File(filesDir, "videos/${current.item.id}.mp4")
            if (video.exists()) {
                val metrics = resources.displayMetrics
                val splitImage = if (AppSettings(this).splitCompare) {
                    PhotoDecoder.decode(current.file, metrics.widthPixels, metrics.heightPixels)
                } else {
                    null
                }
                handler.post { showVideo(current, video, splitImage) }
                return@execute
            }
            val metrics = resources.displayMetrics
            val bitmap = PhotoDecoder.decode(current.file, metrics.widthPixels, metrics.heightPixels) ?: return@execute
            handler.post { crossfadeTo(current, bitmap) }
        }
    }

    private val videoSurfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            videoSurfaceReady = true
            pendingVideo?.let(::playVideo)
            pendingVideo = null
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            videoSurfaceReady = false
            releaseVideo()
        }
    }

    private fun showVideo(photo: CurrentPhoto, video: File, splitImage: Bitmap?) {
        shownId = photo.item.id
        backImage.alpha = 0f
        showSplit(splitImage)
        relocateText(newCredit = getString(R.string.photo_by, photo.item.author))
        if (videoSurfaceReady) playVideo(video) else pendingVideo = video
    }

    /** With a split image, the left half shows the 1080p image over the right half's 4K video. */
    private fun showSplit(splitImage: Bitmap?) {
        val labels = listOf<View>(findViewById(R.id.label_1080p), findViewById(R.id.label_4k), findViewById(R.id.split_divider))
        if (splitImage == null) {
            frontImage.animate().alpha(0f).setDuration(Config.CROSSFADE_MS).start()
            labels.forEach { view -> view.visibility = View.GONE }
            return
        }
        frontImage.animate().cancel()
        frontImage.setImageBitmap(splitImage)
        frontImage.alpha = 1f
        frontImage.clipBounds = Rect(0, 0, frontImage.width / 2, frontImage.height)
        labels.forEach { view -> view.visibility = View.VISIBLE }
    }

    private fun playVideo(video: File) {
        releaseVideo()
        videoPlayer = MediaPlayer().apply {
            setDataSource(video.path)
            setDisplay(videoView.holder)
            setOnPreparedListener { player -> player.start() }
            prepareAsync()
        }
    }

    private fun releaseVideo() {
        videoPlayer?.release()
        videoPlayer = null
    }

    private fun scheduleNextPhoto(current: CurrentPhoto) {
        val delayMs = (current.nextChangeAt - System.currentTimeMillis()).coerceAtLeast(0) + DUE_SLACK_MS
        handler.removeCallbacks(photoDue)
        handler.postDelayed(photoDue, delayMs)
    }

    private fun showWaiting() {
        if (shownId != null) return
        credit.setText(R.string.dream_waiting)
    }

    private fun crossfadeTo(photo: CurrentPhoto, bitmap: Bitmap) {
        if (decoder == null) {
            bitmap.recycle()
            return
        }
        val outgoingBitmap = shownBitmap
        val outgoingImage = frontImage
        shownId = photo.item.id
        shownBitmap = bitmap
        backImage.setImageBitmap(bitmap)
        backImage.alpha = 0f
        backImage.animate().alpha(1f).setDuration(Config.CROSSFADE_MS).withEndAction {
            outgoingImage.setImageDrawable(null)
            outgoingBitmap?.recycle()
        }.start()
        outgoingImage.animate().alpha(0f).setDuration(Config.CROSSFADE_MS).start()
        relocateText(newCredit = getString(R.string.photo_by, photo.item.author))
        frontImage = backImage
        backImage = outgoingImage
        frontImage.bringToFront()
        clock.bringToFront()
        credit.bringToFront()
    }

    /**
     * Fades the clock and credit out, moves them to a new spot inside their corners (and swaps in a new
     * credit if given), then fades them back in.
     */
    private fun relocateText(newCredit: String? = null) {
        credit.animate().alpha(0f).setDuration(TEXT_FADE_MS).start()
        clock.animate().alpha(0f).setDuration(TEXT_FADE_MS).withEndAction {
            moveText()
            if (newCredit != null) credit.text = newCredit
            clock.animate().alpha(1f).setDuration(TEXT_FADE_MS).start()
            credit.animate().alpha(1f).setDuration(TEXT_FADE_MS).start()
        }.start()
    }

    private fun moveText() {
        val range = DRIFT_RANGE_DP * resources.displayMetrics.density
        clock.translationX = Random.nextFloat() * range
        clock.translationY = -Random.nextFloat() * range
        credit.translationX = -Random.nextFloat() * range
        credit.translationY = -Random.nextFloat() * range
    }

    private companion object {
        const val DRIFT_RANGE_DP = 48f
        const val TEXT_FADE_MS = 600L
        const val DUE_SLACK_MS = 250L
    }
}
