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
- **Backup:** a copy is kept in the maintainer's password manager, as a document item named
  "Picture Window – Android release key" holding the `.jks` file, its password and the alias
  (`picture-window`). The key password is the same as the keystore password.
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
- **If the key leaks**, someone could sign apps that install as Picture Window updates. Treat it
  like a lost key: make a new one, publish with it, and tell people to reinstall.

Debug builds (`./gradlew assembleDebug`) are signed with the Android SDK's own debug key, so a TV
switching between a debug build and a release needs `scripts/setup-tv.sh --replace`.
