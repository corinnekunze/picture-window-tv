# Picture Window for TV

The TV side of [Picture Window](https://github.com/corinnekunze/picture-window): an Android TV
companion app that syncs the photo feed, and a photo screensaver. Ticket MTD-47.

- Feed: `https://corinnekunze.github.io/picture-window/index.json`
- Test TV: TCL Smart TV Pro (`G05_4K_US`), Android TV 11. Apps draw at 1920×1080 there.
- Toolchain: JDK 17, Android SDK platform 35 and adb (Homebrew `android-commandlinetools` and
  `android-platform-tools`).

**Back up the TV before changing anything, and know how to undo it:** see [UNDO.md](UNDO.md).

## Install on a TV

You need a computer with adb (macOS: `brew install android-platform-tools`) on the same network as
the TV, once. Google TV hides other apps' screensavers from its menu, so choosing this one takes an
adb command, and the setup script runs it for you.

1. On the TV: Settings → System → About → press OK on "Android TV OS build" seven times. Then
   Settings → System → Developer options → turn on USB debugging (on some TVs, Network debugging).
   Note the TV's IP address (Settings → Network & Internet → your Wi-Fi).
2. Download `picture-window-<version>.apk` from the [latest release](../../releases/latest).
3. Run, with the TV's IP address:

   ```sh
   TV_HOST=192.168.1.50 scripts/setup-tv.sh --dry-run --apk picture-window-0.1.0.apk   # preview
   TV_HOST=192.168.1.50 scripts/setup-tv.sh --apk picture-window-0.1.0.apk
   ```

   The first time, the TV asks to allow debugging from this computer: tick "Always allow" and accept.

The script backs the TV up first (`~/.picture-window-tv/backups/`), installs the app, lets it sync
in the background (on TCL TVs that needs TCL's `APP_AUTO_START` permission, which the script grants
for this app only) and makes Picture Window the screensaver. The photos, about 2 GB, download over
the next few minutes. **To undo everything:** `TV_HOST=192.168.1.50 scripts/restore-tv.sh` (see
[UNDO.md](UNDO.md)). Turn USB debugging off again afterwards if you like; Picture Window doesn't need it.

### With CleanYourTV

[CleanYourTV](https://github.com/corinnekunze/cleanyourtv)'s home screen can show the same photo, with
its credit, as its wallpaper: set `"photo_source": "picture_window"` in its theme (its
`themes/README.md` has the details). Replacing the home screen is the riskier step on some TCL models,
so read CleanYourTV's notes for your TV first.

## Developer setup

```sh
export ANDROID_SERIAL=192.168.1.50:5555             # the TV, with USB debugging on
TV_HOST=192.168.1.50 scripts/backup-tv.sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell appops set com.mtdetc.picturewindow APP_AUTO_START allow   # TCL only, see below
```

A debug build and a release build are signed with different keys, so switching between them means
uninstalling first (`scripts/setup-tv.sh --replace`), which deletes the downloaded photos.

**TCL TVs block background jobs** for any app without TCL's `APP_AUTO_START` app-op (even YouTube's
and the Play Store's), so the daily sync never starts on its own. The `appops` line above allows it
for this app only; uninstalling clears it. Without it, the screensaver and the settings screen start a
sync whenever the last successful one is more than a day old.

## Releasing

```sh
scripts/release.sh init-key   # once; back up ~/.picture-window-tv/keys and signing.properties
scripts/release.sh build      # tests, signed APK and its SHA-256 in dist/
scripts/release.sh publish    # GitHub Release v<version> with the APK
```

Bump `pictureWindowVersionCode` and `pictureWindowVersionName` in `gradle.properties` for each release.
Losing the key means TVs can't update to new releases, only uninstall and reinstall.
