# Shared by the scripts in this folder; source it, don't run it.
# Needs TV_HOST (the TV's IP address); adb talks to it on port 5555.

export ANDROID_SERIAL="${TV_HOST:?set TV_HOST to the IP address of the TV}:5555"
BACKUP_ROOT="${BACKUP_ROOT:-$HOME/.picture-window-tv/backups}"
PACKAGE=com.mtdetc.picturewindow
DREAM_COMPONENT=$PACKAGE/.dream.PhotoDreamService

connect_tv() {
  adb connect "$ANDROID_SERIAL" >/dev/null 2>&1 || true
  case "$(adb get-state 2>&1)" in
    device) return 0 ;;
    *unauthorized*)
      echo "The TV is asking to allow USB debugging: accept it on screen (tick \"Always allow\"), then run this again." >&2 ;;
    *)
      echo "Can't reach the TV at $ANDROID_SERIAL. Check the IP, wake the TV (standby drops adb) and turn on" >&2
      echo "Settings > System > Developer options > USB debugging (Network debugging on some TVs)." >&2 ;;
  esac
  exit 1
}

# The TV's serial number, so each TV's backups stay its own.
tv_serial() {
  adb shell getprop ro.serialno | tr -d '\r'
}

backup_serial() {
  sed -n 's/^\[ro.serialno\]: \[\(.*\)\]$/\1/p' "$1/getprop.txt" 2>/dev/null
}

# This TV's first backup: the state before anything from this project was put on it.
first_backup_for_tv() {
  local serial="$1" dir
  for dir in $(ls -d "$BACKUP_ROOT"/*/ 2>/dev/null | sort); do
    dir="${dir%/}"
    if [ -f "$dir/home.txt" ] && [ "$(backup_serial "$dir")" = "$serial" ]; then
      echo "$dir"
      return 0
    fi
  done
}
