# Picture Window for TV

The TV side of [Picture Window](https://github.com/corinnekunze/picture-window): an Android TV
companion app that syncs the photo feed, and a photo screensaver. Ticket MTD-47.

- Feed: `https://corinnekunze.github.io/picture-window/index.json`
- Test TV: TCL Smart TV Pro (`G05_4K_US`), Android TV 11. Apps draw at 1920×1080 there.
- Toolchain: JDK 17, Android SDK platform 35 and adb (Homebrew `android-commandlinetools` and
  `android-platform-tools`).

**Back up the TV before changing anything, and know how to undo it:** see [UNDO.md](UNDO.md).

## Install on a TV (developer setup)

```sh
export ANDROID_SERIAL=192.168.1.50:5555             # the TV, with USB debugging on
TV_HOST=192.168.1.50 scripts/backup-tv.sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell appops set com.mtdetc.picturewindow APP_AUTO_START allow   # TCL only, see below
```

The app starts syncing on install: about 1.2 GB of photos over a few minutes. The screensaver is a
separate, later step; see UNDO.md before switching it.

**TCL TVs block background jobs** for any app without TCL's `APP_AUTO_START` app-op (even YouTube's
and the Play Store's), so the daily sync never starts on its own. The `appops` line above allows it
for this app only; uninstalling clears it. Without it, the screensaver and the settings screen start a
sync whenever the last successful one is more than a day old.
