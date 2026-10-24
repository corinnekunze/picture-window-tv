#!/usr/bin/env bash
# Set up Picture Window on a Google TV / Android TV in one go: back the TV up, install the app, let it
# sync in the background, and make it the screensaver. Everything it does is undone by
# scripts/restore-tv.sh.
#
# Usage: TV_HOST=192.168.1.50 scripts/setup-tv.sh [--dry-run] [--apk <file>] [--replace]
#   --dry-run   show what would happen; changes nothing on the TV
#   --apk       the APK to install (default: the newest dist/picture-window-*.apk from scripts/release.sh)
#   --replace   uninstall a copy signed with another key first (e.g. a developer build). That deletes
#               its downloaded photos; they download again.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/tv-common.sh
MIN_SDK=26
DRY_RUN=false
REPLACE=false
APK=""
WAS_INSTALLED=false

while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) DRY_RUN=true ;;
    --replace) REPLACE=true ;;
    --apk) APK="${2:?--apk needs a file}"; shift ;;
    *) sed -n '2,12p' "$0" >&2; exit 2 ;;
  esac
  shift
done

run_on_tv() {
  echo "+ adb $*"
  if [ "$DRY_RUN" = false ]; then adb "$@"; fi
}

find_apk() {
  if [ -z "$APK" ]; then APK="$(ls dist/picture-window-*.apk 2>/dev/null | sort -V | tail -1)"; fi
  if [ -z "$APK" ] || [ ! -f "$APK" ]; then
    echo "No APK to install. Download picture-window-<version>.apk from the GitHub release and pass" >&2
    echo "--apk <file>, or build one with scripts/release.sh build." >&2
    exit 1
  fi
  echo "APK: $APK"
}

check_tv() {
  local sdk
  sdk="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  if [ "$sdk" -lt "$MIN_SDK" ]; then
    echo "This TV runs Android SDK $sdk; Picture Window needs $MIN_SDK (Android 8) or newer. Nothing changed." >&2
    exit 1
  fi
  if ! adb shell pm list features | grep -q "android.software.leanback"; then
    echo "This doesn't look like an Android TV (no leanback feature). Nothing changed." >&2
    exit 1
  fi
  echo "TV: $(adb shell getprop ro.product.model | tr -d '\r'), Android SDK $sdk"
}

# Restore goes back to this TV's first backup, so only take one if there isn't one yet.
back_up() {
  local existing
  existing="$(first_backup_for_tv "$(tv_serial)")"
  if [ -n "$existing" ]; then
    echo "Backup: $existing (this TV's first; kept as the state to restore to)"
    return
  fi
  if [ "$DRY_RUN" = true ]; then echo "+ scripts/backup-tv.sh"; return; fi
  scripts/backup-tv.sh
}

install_app() {
  if adb shell pm list packages | tr -d '\r' | grep -qx "package:$PACKAGE"; then WAS_INSTALLED=true; fi
  if [ "$REPLACE" = true ] && adb shell pm list packages | tr -d '\r' | grep -qx "package:$PACKAGE"; then
    run_on_tv shell pm uninstall "$PACKAGE"
  fi
  echo "+ adb install -r $APK"
  if [ "$DRY_RUN" = true ]; then return; fi
  local output
  output="$(adb install -r "$APK" 2>&1 || true)"
  echo "$output"
  case "$output" in
    *Success*) ;;
    *INSTALL_FAILED_UPDATE_INCOMPATIBLE*)
      echo "A copy signed with another key is installed (a developer build?). Run again with --replace;" >&2
      echo "that deletes its downloaded photos, which then download again." >&2
      exit 1 ;;
    *) echo "Install failed; nothing else was changed." >&2; exit 1 ;;
  esac
}

# TCL TVs refuse background work for apps without their APP_AUTO_START app-op; other TVs don't have
# it and say so, which is fine. A fresh install also sits in the "never used" standby bucket, where
# its first sync would wait; the first query below starts the app, which schedules that sync.
start_syncing() {
  echo "+ adb shell cmd appops set $PACKAGE APP_AUTO_START allow   (TCL only; other TVs ignore it)"
  if [ "$DRY_RUN" = false ]; then adb shell cmd appops set "$PACKAGE" APP_AUTO_START allow >/dev/null 2>&1 || true; fi
  run_on_tv shell am set-standby-bucket "$PACKAGE" active
  run_on_tv shell content query --uri "content://$PACKAGE.photos/current"
}

set_screensaver() {
  run_on_tv shell settings put secure screensaver_enabled 1
  run_on_tv shell settings put secure screensaver_components "$DREAM_COMPONENT"
}

report() {
  if [ "$DRY_RUN" = true ]; then echo "Dry run: nothing changed."; return; fi
  echo
  if [ "$WAS_INSTALLED" = true ] && [ "$REPLACE" = false ]; then
    echo "Done. Picture Window was updated; its photos and settings stay. To try it now:"
  else
    echo "Done. Picture Window is downloading its photos (about 2 GB, a few minutes on fast Wi-Fi); until"
    echo "then the screensaver shows the ones it has. To try it now:"
  fi
  echo "  adb -s $ANDROID_SERIAL shell am start -n com.android.systemui/.Somnambulator"
  echo "To undo everything: TV_HOST=$TV_HOST scripts/restore-tv.sh   (add --dry-run to preview)"
  echo
  echo "Now turn debugging off on the TV: Settings > System > Developer options > USB debugging (and"
  echo "Network debugging) off. Picture Window doesn't need it, and while it's on, computers the TV has"
  echo "allowed can control it. Turn it back on only to run restore-tv.sh or setup again."
}

main() {
  find_apk
  connect_tv
  check_tv
  back_up
  install_app
  start_syncing
  set_screensaver
  report
}

main
