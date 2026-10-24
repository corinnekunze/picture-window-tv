# Releasing

```sh
scripts/release.sh build      # tests, signed APK and its SHA-256 in dist/
scripts/release.sh publish    # GitHub Release v<version> with the APK
```

Before each release, bump `pictureWindowVersionCode` (by one) and `pictureWindowVersionName` in
`gradle.properties`. `build` refuses uncommitted changes, and `publish` refuses a version that's
already released.

## The release key

Android only installs an update over an app when both are signed with the same key. Every release
of Picture Window is signed with one release key, so TVs can update from one version to the next,
and nobody without the key can make an "update" that replaces it.

- **Never commit it.** It lives outside the repo, on the maintainer's computer:
  - `~/.picture-window-tv/keys/picture-window-release.jks`, the key itself.
  - `~/.picture-window-tv/signing.properties`, which tells the build where the key is and its
    password. Gradle reads it (or the file named by `PICTURE_WINDOW_SIGNING`). Without it, release
    builds come out unsigned and `release.sh` refuses them.
- **Backup:** a copy is kept in the maintainer's password manager that holds the .jks file and that password.
- **New computer:** put the `.jks` back in `~/.picture-window-tv/keys/`, then recreate
  `~/.picture-window-tv/signing.properties` with `chmod 600` and these lines:

  ```properties
  storeFile=/Users/<you>/.picture-window-tv/keys/picture-window-release.jks
  storePassword=<password from the backup>
  keyAlias=picture-window
  keyPassword=<same password>
  ```

  Check it with `scripts/release.sh build`.

- **If the key is lost**, TVs can't take updates any more: everyone has to uninstall Picture Window
  (which deletes its downloaded photos) and install a release signed with a new key. Make a new one
  with `scripts/release.sh init-key` only then, and say so in the release notes.
- **If the key leaks**, the risk is small: Picture Window has no update channel to hijack, and it
  only asks for internet access. Someone with the key could sign a fake "update", but they'd still
  have to talk a person into installing it by hand. Still, switch to a new key with Android's key
  rotation: the old key vouches for the new one, and installed TVs (Android 9 and newer) take the
  next release as a normal update, keeping their photos. Keep the old key; rotation needs it.

  ```sh
  BT="$(ls -d /opt/homebrew/share/android-commandlinetools/build-tools/* | sort -V | tail -1)"
  KEYS=~/.picture-window-tv/keys
  keytool -genkeypair -keystore $KEYS/picture-window-release-2.jks -alias picture-window-2 \
    -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Picture Window, O=MTD etc."
  # The proof that the old key hands over to the new one:
  $BT/apksigner rotate --out $KEYS/rotation.lineage \
    --old-signer --ks $KEYS/picture-window-release.jks --ks-key-alias picture-window \
    --new-signer --ks $KEYS/picture-window-release-2.jks --ks-key-alias picture-window-2
  # Every release from then on, after scripts/release.sh build:
  $BT/apksigner sign --lineage $KEYS/rotation.lineage --rotation-min-sdk-version 28 \
    --ks $KEYS/picture-window-release.jks --ks-key-alias picture-window \
    --next-signer --ks $KEYS/picture-window-release-2.jks --ks-key-alias picture-window-2 \
    dist/picture-window-<version>.apk
  $BT/apksigner verify -v dist/picture-window-<version>.apk   # shows the v3 rotation
  ```

  `--rotation-min-sdk-version 28` makes Android 9 to 12 use the new key too; without it, only
  Android 13 and newer do. Android 8 TVs keep trusting the old key. Back up the new key and
  `rotation.lineage` the same way as the first key, and teach `release.sh` the sign step (its
  `check_apk` looks for the first key's name).

Debug builds (`./gradlew assembleDebug`) are signed with the Android SDK's own debug key, so a TV
switching between a debug build and a release needs `scripts/setup-tv.sh --replace`.
