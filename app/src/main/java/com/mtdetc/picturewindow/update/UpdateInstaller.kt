package com.mtdetc.picturewindow.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.mtdetc.picturewindow.feed.DownloadOutcome
import com.mtdetc.picturewindow.feed.FeedClient
import java.io.File

/**
 * Downloads [AppUpdate]'s APK, checks its size and SHA-256, and hands it to Android's installer,
 * which asks the viewer to confirm. Android itself refuses it unless it's signed with the same key.
 */
class UpdateInstaller(private val context: Context, private val client: FeedClient) {
    private val folder = File(context.cacheDir, "update")

    /** Runs off the main thread. False when the download fails or doesn't match. */
    fun downloadAndInstall(update: AppUpdate): Boolean {
        folder.mkdirs()
        folder.listFiles()?.filter { file -> !file.name.startsWith("${update.versionCode}.") }?.forEach(File::delete)
        val apk = File(folder, "${update.versionCode}.apk")
        val partial = File(folder, "${update.versionCode}.apk.partial")
        if (!apk.exists() && client.download(update.apk, partial, apk) != DownloadOutcome.SAVED) return false
        install(apk)
        return true
    }

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName) }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("picture-window.apk", 0, apk.length()).use { output ->
                apk.inputStream().use { input -> input.copyTo(output) }
                session.fsync(output)
            }
            // The installer adds the result to this intent, so it has to be mutable from Android 12 on.
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val result = PendingIntent.getBroadcast(
                context, sessionId, Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            )
            session.commit(result.intentSender)
        }
    }
}

/** Shows Android's "update this app?" prompt when the installer asks for it, and logs the outcome. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Log.i(TAG, "Update install finished: $status ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
            return
        }
        @Suppress("DEPRECATION")
        val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
        context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private companion object {
        const val TAG = "PictureWindowUpdate"
    }
}
