package com.mtdetc.picturewindow.dream

import android.graphics.Bitmap
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * The photo screensaver: the current photo full screen, crossfading when the rotation moves on, with
 * the clock and date in the bottom-left corner and "Photo by …" in the bottom-right. Both drift every minute
 * against burn-in. It checks the rotation on the same tick, so a revoked photo goes within a minute.
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

    private val tick = object : Runnable {
        override fun run() {
            drift()
            refreshPhoto()
            handler.postDelayed(this, Config.DRIFT_INTERVAL_MS)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        setContentView(R.layout.dream)
        frontImage = findViewById(R.id.photo_front)
        backImage = findViewById(R.id.photo_back)
        clock = findViewById(R.id.clock)
        credit = findViewById(R.id.credit)
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
            if (current.item.id == shownId) return@execute
            val metrics = resources.displayMetrics
            val bitmap = PhotoDecoder.decode(current.file, metrics.widthPixels, metrics.heightPixels) ?: return@execute
            handler.post { crossfadeTo(current, bitmap) }
        }
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
        credit.animate().alpha(0f).setDuration(Config.CROSSFADE_MS / 2).withEndAction {
            credit.text = getString(R.string.photo_by, photo.item.author)
            credit.animate().alpha(1f).setDuration(Config.CROSSFADE_MS / 2).start()
        }.start()
        frontImage = backImage
        backImage = outgoingImage
        frontImage.bringToFront()
        clock.bringToFront()
        credit.bringToFront()
    }

    /** Moves the clock and the credit to a new spot inside their corners. */
    private fun drift() {
        val range = DRIFT_RANGE_DP * resources.displayMetrics.density
        clock.animate().translationX(Random.nextFloat() * range).translationY(-Random.nextFloat() * range)
            .setDuration(DRIFT_ANIMATION_MS).start()
        credit.animate().translationX(-Random.nextFloat() * range).translationY(-Random.nextFloat() * range)
            .setDuration(DRIFT_ANIMATION_MS).start()
    }

    private companion object {
        const val DRIFT_RANGE_DP = 48f
        const val DRIFT_ANIMATION_MS = 3_000L
    }
}
