#!/usr/bin/env bash
# Record the TV's current state so scripts/restore-tv.sh can put it back: the Home activity, the
# screensaver and sleep settings, and which packages are installed and disabled. Read-only on the TV.
#
# Usage: TV_HOST=192.168.1.50 scripts/backup-tv.sh
set -euo pipefail
export ANDROID_SERIAL="${TV_HOST:?set TV_HOST to the IP address of the TV}:5555"
BACKUP_ROOT="${BACKUP_ROOT:-$HOME/.picture-window-tv/backups}"

adb connect "$ANDROID_SERIAL" >/dev/null
if [ "$(adb get-state 2>/dev/null)" != "device" ]; then
  echo "Can't reach the TV at $ANDROID_SERIAL. Wake it (standby drops adb) and allow debugging." >&2
  exit 1
fi

backup_dir="$BACKUP_ROOT/$(date +%Y%m%dT%H%M%S)"
mkdir -p "$backup_dir"
adb shell getprop > "$backup_dir/getprop.txt"
adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN \
  | tail -1 | tr -d '\r' > "$backup_dir/home.txt"
adb shell pm list packages | tr -d '\r' | sort > "$backup_dir/packages-all.txt"
adb shell pm list packages -d | tr -d '\r' | sort > "$backup_dir/packages-disabled.txt"
adb shell pm list packages -3 | tr -d '\r' | sort > "$backup_dir/packages-third-party.txt"
for namespace in secure system global; do
  adb shell settings list "$namespace" | tr -d '\r' > "$backup_dir/settings-$namespace.txt"
done

echo "Saved $backup_dir"
echo "Home: $(cat "$backup_dir/home.txt")"
grep '^screensaver_components=' "$backup_dir/settings-secure.txt" || true
