package com.mtdetc.picturewindow

/** Tunables. Anything a viewer can change lives in settings/AppSettings instead. */
object Config {
    const val DEFAULT_FEED_URL = "https://corinnekunze.github.io/picture-window/index.json"
    const val USER_AGENT = "PictureWindowTV/${BuildConfig.VERSION_NAME} (+https://github.com/corinnekunze/picture-window)"
    /** The newest release's description; GitHub redirects "latest" to the newest release. */
    const val UPDATE_URL = "https://github.com/corinnekunze/picture-window-tv/releases/latest/download/update.json"
    const val PROVIDER_AUTHORITY = "com.mtdetc.picturewindow.photos"

    /**
     * Photos kept on the TV, images and 4K videos together (about 4.5 MB a photo). The feed is larger,
     * so older, already-shown photos make room.
     */
    const val CACHE_BUDGET_BYTES = 2_000_000_000L

    /** Free space left on the TV's storage no matter what the budget allows. */
    const val MIN_FREE_BYTES = 2_000_000_000L

    /** How long each photo stays up; Google TV offers 5 s to 10 min, and 3 min leaves time to read the caption. */
    const val ROTATION_INTERVAL_MS = 3 * 60 * 1000L
    /** How often the screensaver rechecks the rotation, so a revoked photo goes within a minute. */
    const val CHECK_INTERVAL_MS = 60 * 1000L
    const val CROSSFADE_MS = 2_000L
    const val SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L
}
