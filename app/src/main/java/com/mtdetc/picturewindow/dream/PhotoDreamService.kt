package com.mtdetc.picturewindow.dream

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.mtdetc.picturewindow.Config
import com.mtdetc.picturewindow.PictureWindowApp
import com.mtdetc.picturewindow.R
import com.mtdetc.picturewindow.VideoSupport
import com.mtdetc.picturewindow.rotation.CurrentPhoto
import com.mtdetc.picturewindow.settings.AppSettings
import com.mtdetc.picturewindow.sync.SyncScheduler
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * The photo screensaver: the current photo full screen, with the clock and date in the bottom-left
 * corner and "Photo by …" in the bottom-right. The text moves a little every minute against burn-in,
 * but only while faded out, so it never visibly slides. It checks the rotation on the same tick, so a
 * revoked photo goes within a minute.
 *
 * Photos show as their 4K video when the TV has it (apps draw at 1080p; video reaches the panel at
 * full resolution), fading through black, and otherwise as the 1080p image, crossfading. A video that
 * won't play falls back to its image and isn't tried again this session.
 */
class PhotoDreamService : DreamService() {
    private val handler = Handler(Looper.getMainLooper())
    private var decoder: ExecutorService? = null
    private lateinit var frontImage: ImageView
    private lateinit var backImage: ImageView
    private lateinit var veil: View
    private lateinit var clock: View
    private lateinit var caption: View
    private lateinit var about: ShadowTextView
    private lateinit var credit: ShadowTextView
    private lateinit var place: ShadowTextView
    private lateinit var videoLayer: VideoLayer
    @Volatile private var shownId: String? = null
    private var shownBitmap: Bitmap? = null
    private var showingVideo = false
    private val failedVideoIds = mutableSetOf<String>()

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
        veil = findViewById(R.id.veil)
        clock = findViewById(R.id.clock)
        caption = findViewById(R.id.caption)
        about = findViewById(R.id.about)
        credit = findViewById(R.id.credit)
        place = findViewById(R.id.place)
        videoLayer = VideoLayer(findViewById(R.id.photo_video), handler)
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
        videoLayer.release()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        videoLayer.release()
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
            if (canPlayVideo(current)) {
                handler.post { fadeToVideo(current) }
                return@execute
            }
            decodeAndShowImage(current)
        }
    }

    private fun canPlayVideo(photo: CurrentPhoto): Boolean =
        photo.video != null && VideoSupport.canPlay4k && photo.item.id !in failedVideoIds

    /** Runs on the decoder thread. */
    private fun decodeAndShowImage(photo: CurrentPhoto) {
        val metrics = resources.displayMetrics
        val bitmap = PhotoDecoder.decode(photo.file, metrics.widthPixels, metrics.heightPixels) ?: return
        handler.post { crossfadeTo(photo, bitmap) }
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

    /** Fades to black, swaps in the video underneath, and fades back once its frame is up. */
    private fun fadeToVideo(photo: CurrentPhoto) {
        if (decoder == null) return
        shownId = photo.item.id
        relocateText(newCaption = photo)
        veil.animate().alpha(1f).setDuration(Config.CROSSFADE_MS / 2).withEndAction {
            clearImages()
            showingVideo = true
            videoLayer.play(
                photo.video!!,
                onShowing = { veil.animate().alpha(0f).setDuration(Config.CROSSFADE_MS / 2).start() },
                onFailed = { videoFailed(photo) },
            )
        }.start()
    }

    private fun videoFailed(photo: CurrentPhoto) {
        failedVideoIds += photo.item.id
        showingVideo = false
        decoder?.execute { decodeAndShowImage(photo) }
    }

    /**
     * Crossfades to [bitmap]. The two image views keep a fixed stacking order (so the veil and the text
     * always stay above them), so whichever view is on top does the fading.
     */
    private fun crossfadeTo(photo: CurrentPhoto, bitmap: Bitmap) {
        if (decoder == null) {
            bitmap.recycle()
            return
        }
        val outgoingBitmap = shownBitmap
        val outgoingImage = frontImage
        val incomingImage = backImage
        shownId = photo.item.id
        shownBitmap = bitmap
        incomingImage.setImageBitmap(bitmap)
        val afterFade = Runnable {
            outgoingImage.setImageDrawable(null)
            outgoingImage.alpha = 0f
            outgoingBitmap?.recycle()
            leaveVideo()
        }
        // Fading the outgoing view only works if it's showing something (not after a video).
        if (isAbove(incomingImage, outgoingImage) || outgoingImage.alpha == 0f) {
            incomingImage.alpha = 0f
            incomingImage.animate().alpha(1f).setDuration(Config.CROSSFADE_MS).withEndAction(afterFade).start()
        } else {
            incomingImage.alpha = 1f
            outgoingImage.animate().alpha(0f).setDuration(Config.CROSSFADE_MS).withEndAction(afterFade).start()
        }
        veil.animate().alpha(0f).setDuration(Config.CROSSFADE_MS / 2).start()
        relocateText(newCaption = photo)
        frontImage = incomingImage
        backImage = outgoingImage
    }

    private fun isAbove(view: View, other: View): Boolean {
        val parent = view.parent as ViewGroup
        return parent.indexOfChild(view) > parent.indexOfChild(other)
    }

    /** Once an image covers the screen, the video underneath can go. */
    private fun leaveVideo() {
        if (!showingVideo) return
        showingVideo = false
        videoLayer.release()
    }

    private fun clearImages() {
        listOf(frontImage, backImage).forEach { image ->
            image.animate().cancel()
            image.alpha = 0f
            image.setImageDrawable(null)
        }
        shownBitmap?.recycle()
        shownBitmap = null
    }

    /**
     * Fades the clock and caption out, moves them to a new spot inside their corners (and swaps in the
     * new photo's credit and place if given), then fades them back in.
     */
    private fun relocateText(newCaption: CurrentPhoto? = null) {
        caption.animate().alpha(0f).setDuration(TEXT_FADE_MS).start()
        clock.animate().alpha(0f).setDuration(TEXT_FADE_MS).withEndAction {
            moveText()
            if (newCaption != null) showCaption(newCaption)
            clock.animate().alpha(1f).setDuration(TEXT_FADE_MS).start()
            caption.animate().alpha(1f).setDuration(TEXT_FADE_MS).start()
        }.start()
    }

    /**
     * Only the feed's cleaned credit is shown, never the raw author: when the feed withholds a credit
     * (too long, or flagged), the raw text is exactly what it kept off screen.
     */
    private fun showCaption(photo: CurrentPhoto) {
        val photoCredit = photo.item.credit
        credit.text = if (photoCredit != null) getString(R.string.photo_by, photoCredit) else ""
        credit.visibility = if (photoCredit != null) View.VISIBLE else View.GONE
        about.text = photo.item.about.orEmpty()
        about.visibility = if (photo.item.about != null) View.VISIBLE else View.GONE
        val shownPlace = PlaceLine.toShow(photo.item.place, photo.item.about)
        place.text = shownPlace.orEmpty()
        place.visibility = if (shownPlace != null) View.VISIBLE else View.GONE
    }

    private fun moveText() {
        val range = DRIFT_RANGE_DP * resources.displayMetrics.density
        clock.translationX = Random.nextFloat() * range
        clock.translationY = -Random.nextFloat() * range
        caption.translationX = -Random.nextFloat() * range
        caption.translationY = -Random.nextFloat() * range
    }

    private companion object {
        const val DRIFT_RANGE_DP = 48f
        const val TEXT_FADE_MS = 600L
        const val DUE_SLACK_MS = 250L
    }
}
