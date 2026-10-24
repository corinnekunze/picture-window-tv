#!/usr/bin/env bash
# Build, check and publish the release APK. The signing key lives outside the repo, in
# ~/.picture-window-tv/ (override with PICTURE_WINDOW_SIGNING=<properties file>).
#
# Usage: scripts/release.sh init-key   # once: make the release key (back it up: losing it means
#                                      # installed TVs can't take updates)
#        scripts/release.sh build      # tests, signed APK in dist/, checks it (see RELEASING.md)
#        scripts/release.sh publish    # GitHub Release v<version> with the APK from build
set -euo pipefail
cd "$(dirname "$0")/.."
SIGNING="${PICTURE_WINDOW_SIGNING:-$HOME/.picture-window-tv/signing.properties}"
KEY_DIR="$HOME/.picture-window-tv/keys"
ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
BUILD_TOOLS="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
VERSION="$(sed -n 's/^pictureWindowVersionName=//p' gradle.properties)"
APK="dist/picture-window-$VERSION.apk"

init_key() {
  if [ -e "$SIGNING" ]; then
    echo "$SIGNING already exists; refusing to replace the release key." >&2
    exit 1
  fi
  mkdir -p "$KEY_DIR"
  chmod 700 "$KEY_DIR"
  local store="$KEY_DIR/picture-window-release.jks"
  local password
  password="$(openssl rand -base64 24 | tr -d '/+=')"
  keytool -genkeypair -v -keystore "$store" -alias picture-window -keyalg RSA -keysize 4096 \
    -validity 10000 -storepass "$password" -keypass "$password" \
    -dname "CN=Picture Window, O=MTD etc." >/dev/null
  umask 077
  printf 'storeFile=%s\nstorePassword=%s\nkeyAlias=picture-window\nkeyPassword=%s\n' \
    "$store" "$password" "$password" > "$SIGNING"
  echo "Made $store and $SIGNING."
  echo "Back up both (a password manager is fine). Without them, TVs can't update to a new release."
}

require_clean_tree() {
  if [ -n "$(git status --porcelain)" ]; then
    echo "Commit or stash your changes first: a release is built from a commit." >&2
    exit 1
  fi
}

check_apk() {
  "$BUILD_TOOLS/apksigner" verify --print-certs "$1" | grep -q "CN=Picture Window" || {
    echo "$1 isn't signed with the release key." >&2; exit 1; }
  local manifest
  manifest="$("$BUILD_TOOLS/aapt2" dump xmltree --file AndroidManifest.xml "$1")"
  # The debug receiver and the debug-only cleartext config must never ship.
  if grep -qE "DebugReceiver|networkSecurityConfig|debuggable" <<<"$manifest"; then
    echo "$1 contains debug-only parts; refusing it." >&2; exit 1
  fi
}

build() {
  [ -f "$SIGNING" ] || { echo "No release key; run: scripts/release.sh init-key" >&2; exit 1; }
  require_clean_tree
  ./gradlew -q testDebugUnitTest assembleRelease
  mkdir -p dist
  cp app/build/outputs/apk/release/app-release.apk "$APK"
  check_apk "$APK"
  shasum -a 256 "$APK" | tee "$APK.sha256"
  echo "Built $APK ($(du -h "$APK" | cut -f1)) from $(git rev-parse --short HEAD)."
}

publish() {
  [ -f "$APK" ] || { echo "No $APK; run: scripts/release.sh build" >&2; exit 1; }
  require_clean_tree
  check_apk "$APK"
  if gh release view "v$VERSION" >/dev/null 2>&1; then
    echo "v$VERSION is already released; bump pictureWindowVersionName and pictureWindowVersionCode." >&2
    exit 1
  fi
  gh release create "v$VERSION" "$APK" "$APK.sha256" --target "$(git rev-parse HEAD)" \
    --title "Picture Window $VERSION" \
    --notes "Install with scripts/setup-tv.sh (see README). SHA-256 in picture-window-$VERSION.apk.sha256."
}

case "${1:-}" in
  init-key) init_key ;;
  build) build ;;
  publish) publish ;;
  *) sed -n '2,9p' "$0" >&2; exit 2 ;;
esac
