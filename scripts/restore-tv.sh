#!/usr/bin/env bash
# Put the TV back the way scripts/backup-tv.sh recorded it: re-enable anything disabled since, give
# Home back to the recorded launcher, restore the screensaver and sleep settings, and uninstall the
# apps this project added. Uses this TV's first backup (by serial number) unless BACKUP_DIR names one.
#
# Usage: TV_HOST=192.168.1.50 scripts/restore-tv.sh [--dry-run]
set -euo pipefail
source "$(dirname "$0")/tv-common.sh"
# Apps this project may install. Each is removed only if the backup shows it wasn't there before.
PROJECT_PACKAGES=(com.mtdetc.picturewindow com.gathin.tvhome)
SECURE_KEYS=(screensaver_enabled screensaver_components screensaver_default_component
  screensaver_activate_on_sleep screensaver_activate_on_dock sleep_timeout
  enabled_accessibility_services accessibility_enabled)
SYSTEM_KEYS=(screen_off_timeout)
DRY_RUN=false
[ "${1:-}" = "--dry-run" ] && DRY_RUN=true

run_on_tv() {
  echo "+ adb shell $*"
  if [ "$DRY_RUN" = false ]; then adb shell "$@"; fi
}

recorded_setting() {
  local namespace="$1" key="$2"
  grep -m1 "^$key=" "$BACKUP_DIR/settings-$namespace.txt" | cut -d= -f2- || true
}

restore_setting() {
  local namespace="$1" key="$2" value
  value="$(recorded_setting "$namespace" "$key")"
  if [ -z "$value" ] || [ "$value" = "null" ]; then
    run_on_tv settings delete "$namespace" "$key"
    return
  fi
  run_on_tv settings put "$namespace" "$key" "$value"
}

check_backup() {
  BACKUP_DIR="${BACKUP_DIR:-$(first_backup_for_tv "$(tv_serial)")}"
  BACKUP_DIR="${BACKUP_DIR%/}"
  if [ -z "$BACKUP_DIR" ] || [ ! -f "$BACKUP_DIR/home.txt" ]; then
    echo "No backup of this TV under $BACKUP_ROOT. Nothing to restore from." >&2
    exit 1
  fi
  echo "Restoring from $BACKUP_DIR"
}

reenable_packages() {
  local package
  for package in $(comm -13 "$BACKUP_DIR/packages-disabled.txt" \
      <(adb shell pm list packages -d | tr -d '\r' | sort) | sed 's/^package://'); do
    run_on_tv pm enable --user 0 "$package"
  done
}

restore_home() {
  run_on_tv cmd package set-home-activity "$(cat "$BACKUP_DIR/home.txt")"
}

restore_screensaver() {
  local key
  for key in "${SECURE_KEYS[@]}"; do restore_setting secure "$key"; done
  for key in "${SYSTEM_KEYS[@]}"; do restore_setting system "$key"; done
}

uninstall_project_apps() {
  local package
  for package in "${PROJECT_PACKAGES[@]}"; do
    if grep -qx "package:$package" "$BACKUP_DIR/packages-all.txt"; then continue; fi
    if ! adb shell pm list packages | tr -d '\r' | grep -qx "package:$package"; then continue; fi
    run_on_tv pm uninstall "$package"
  done
}

report() {
  [ "$DRY_RUN" = true ] && { echo "Dry run: nothing changed."; return; }
  echo "Home now: $(adb shell cmd package resolve-activity --brief \
    -c android.intent.category.HOME -a android.intent.action.MAIN | tail -1 | tr -d '\r')"
  echo "Screensaver now: $(adb shell settings get secure screensaver_components | tr -d '\r')"
  adb shell input keyevent KEYCODE_HOME
}

main() {
  connect_tv
  check_backup
  reenable_packages
  restore_home
  restore_screensaver
  uninstall_project_apps
  report
}

main
