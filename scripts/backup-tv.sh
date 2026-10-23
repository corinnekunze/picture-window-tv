#!/usr/bin/env bash
# Record the TV's current state so scripts/restore-tv.sh can put it back: the Home activity, the
# screensaver and sleep settings, and which packages are installed and disabled. Read-only on the TV.
# Restore uses each TV's first backup (matched by serial number), so extra backups never hide it.
#
# Usage: TV_HOST=192.168.1.50 scripts/backup-tv.sh
set -euo pipefail
source "$(dirname "$0")/tv-common.sh"
connect_tv

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
