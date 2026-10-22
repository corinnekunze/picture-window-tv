package com.mtdetc.picturewindow

/** Tunables. Anything a viewer can change lives in settings/AppSettings instead. */
object Config {
    const val DEFAULT_FEED_URL = "https://corinnekunze.github.io/picture-window/index.json"
    const val USER_AGENT = "PictureWindowTV/0.1 (+https://github.com/corinnekunze/picture-window)"
    const val PROVIDER_AUTHORITY = "com.mtdetc.picturewindow.photos"

    /**
     * Photos kept on the TV, images and 4K videos together (about 4.5 MB a photo). The feed is larger,
     * so older, already-shown photos make room.
     */
    const val CACHE_BUDGET_BYTES = 2_000_000_000L

    /** Free space left on the TV's storage no matter what the budget allows. */
    const val MIN_FREE_BYTES = 2_000_000_000L

    const val ROTATION_INTERVAL_MS = 5 * 60 * 1000L
    const val DRIFT_INTERVAL_MS = 60 * 1000L
    const val CROSSFADE_MS = 2_000L
    const val SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L
}
