# Picture Window for TV

The TV side of [Picture Window](https://github.com/corinnekunze/picture-window): an Android TV
companion app that syncs the photo feed, and a photo screensaver. A project by
[MTD etc.](https://www.youtube.com/@mtdetc)

- Feed: `https://corinnekunze.github.io/picture-window/index.json`
- Test TV: TCL Smart TV Pro (`G05_4K_US`), Android TV 11. Apps draw at 1920×1080 there.
- Toolchain: JDK 17, Android SDK platform 35 and adb (Homebrew `android-commandlinetools` and
  `android-platform-tools`).

**No ads, ever.** Picture Window will never show ads, sponsored photos or promotions, and will never
be used to advertise to you: not on the screensaver, not on the home screen, not in a notification,
not in any future version. Its only notification is the one telling you a new version is out.

**Back up the TV before changing anything, and know how to undo it:** see [UNDO.md](UNDO.md).

## Install on a TV

See **[INSTALL.md](INSTALL.md)**: ask your AI agent with [PROMPT.md](PROMPT.md), or do it yourself in a
few steps. You need a computer once; after that, not even for updates.

### How the photos change

- The screensaver shows a new photo every 3 minutes, and never the same one twice in a row.
- The TV keeps about 2 GB of photos (roughly 350 with their 4K videos). The feed has more: each
  month's batch, for six months. Every day the TV swaps the photos it has already shown for ones it
  hasn't, so you work through the whole feed, and photos you haven't seen always come first.
- A new month's photos arrive within a day of being published and are shown before any repeats.
- If you've seen everything in the feed, the TV stops downloading and reshows what it has, in a
  different order each time, until the next month adds new photos.
- If the TV is offline, it keeps showing the photos it has. A photo taken out of the feed leaves the
  TV at its next daily check.
- **To keep the photos you have,** open Picture Window from your apps and untick **Get new photos each
  month**. The TV stops downloading and keeps reshowing what it has, even photos older than the feed's
  six months. The daily check still runs, so a photo taken out of the feed still leaves, and you still
  hear about app updates. Tick it again to get new photos the same day.

### Updating

When a new version is out, the TV finds out at its daily check: Picture Window's settings screen
(open Picture Window from your apps) shows "Version … is available" with an **Update** button, and
the TV shows one notification (on Google TV, under Settings → Notifications). The screensaver never
shows it. Press Update and confirm Android's prompt; the first time, Android asks you to allow
Picture Window to install updates. Photos and settings stay, and no computer or debugging is needed.
Android only accepts an update signed with the same key as the installed app.

### With CleanYourTV

[CleanYourTV](https://github.com/corinnekunze/cleanyourtv)'s home screen can show the same photo, with
its credit, as its wallpaper: set `"photo_source": "picture_window"` in its theme (its
`themes/README.md` has the details). Replacing the home screen is the riskier step on some TCL models,
so read CleanYourTV's notes for your TV first.

## Privacy and security

- **No ads or tracking:** see the promise at the top. There's no advertising code, no analytics and
  nothing that profiles what you watch, and there never will be.
- **What the app can do:** it asks only for internet access (to download photos) and to be told about
  a reboot (so its daily check stays scheduled). No account, microphone or location, and it
  can't see your other apps.
- **What it sends:** once a day it asks GitHub whether the photo list changed. Like any web request,
  that shows GitHub your home's public IP address and the app's name and version. Nothing else about
  your TV or household leaves it.
- **What it downloads:** photos and their 4K videos from GitHub, over HTTPS only. Each file is checked
  against the fingerprint (SHA-256) in the photo list before it's used; a damaged or swapped file is
  thrown away.
- **What other apps on the TV can read:** the photo on screen now, its credit and when it changes,
  read-only. That's how [CleanYourTV](#with-cleanyourtv)'s home screen shows the same photo. It's only
  the public photos and credits the feed already publishes.
- **Which photos appear:** photos come from Wikimedia Commons under free licences, and every month's
  batch is reviewed by hand before it's published. The feed's rules keep it family-friendly: distant
  crowds are fine, close-up portraits and anything grim are not. If a photo that shouldn't be there
  gets through anyway, removing it from the feed takes it off every TV at its next daily check, as
  long as the TV is on and online.
- **Trusting the APK:** it's built from this repo and signed with the same release key every time
  (see [RELEASING.md](RELEASING.md)), and each release lists the APK's SHA-256 so you can check your
  download (`shasum -a 256 picture-window-<version>.apk`).
- **Updates:** the daily check also reads `update.json` from this repo's latest release. The app only
  downloads an update when you press Update, checks it against that file's SHA-256, and Android
  installs it only after you confirm and only if it carries the same signature. For that, Android
  asks once to let Picture Window install apps; it can't install anything else on its own.
- **On TCL TVs** the setup grants TCL's `APP_AUTO_START` permission to this app only, so its daily
  check can run in the background. Uninstalling removes it.

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

## Usage stats

A daily GitHub Action ([`daily-stats.yml`](.github/workflows/daily-stats.yml)) records how many times the
APK has been downloaded and how many TVs checked for updates, from the download counters GitHub keeps on
release files. The app sends nothing extra: every installed TV already fetches `update.json` once a day,
so the daily rise in those downloads is roughly the number of TVs in use. The numbers are in
`stats/daily.csv` on the `stats` branch; [`scripts/record-stats.sh`](scripts/record-stats.sh) explains
each column.

## Releasing

See [RELEASING.md](RELEASING.md): building and publishing a release, and where the signing key lives.

## License

MIT, see [LICENSE](LICENSE). The bundled Outfit font is under the SIL Open Font License
([FONT-LICENSE-OFL.txt](FONT-LICENSE-OFL.txt)).
