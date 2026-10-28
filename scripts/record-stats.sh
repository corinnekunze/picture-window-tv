#!/usr/bin/env bash
# Adds today's row to the install and usage stats, from the download counters GitHub keeps on this
# repo's release files. Nothing is collected from TVs: every installed TV already fetches update.json
# from the latest release once a day, so the day-to-day rise in update.json downloads is roughly the
# number of TVs that were on and online.
#
# Usage: scripts/record-stats.sh <csv file>
#   Needs the GitHub CLI (gh) signed in, or GH_TOKEN set, as in the daily-stats workflow.
#   RELEASES_JSON=<file> reads release data from a file instead of the API (for testing).
#
# Columns:
#   date                      UTC day of the snapshot
#   latest_release            tag of the newest release
#   apk_downloads             all-time APK downloads, every release (installs, updates and a few
#                             downloads that were never installed)
#   update_checks             all-time update.json downloads, every release (each TV, once a day)
#   apk_downloads_since_last  rise since the previous row
#   update_checks_since_last  rise since the previous row
#   active_tvs_estimate       update checks per day since the previous row: roughly the TVs in use
set -euo pipefail

CSV="${1:?usage: scripts/record-stats.sh <csv file>}"
REPO="${GITHUB_REPOSITORY:-corinnekunze/picture-window-tv}"
TODAY="$(date -u +%Y-%m-%d)"
HEADER="date,latest_release,apk_downloads,update_checks,apk_downloads_since_last,update_checks_since_last,active_tvs_estimate"

if [ -n "${RELEASES_JSON:-}" ]; then
  releases="$(cat "$RELEASES_JSON")"
else
  # --paginate returns one JSON array per page; --slurp joins them.
  releases="$(gh api --paginate "repos/$REPO/releases?per_page=100" | jq -s 'add // []')"
fi

# Drafts have no public downloads, and their counters aren't real installs.
read -r latest apk checks < <(jq -r '
  [ .[] | select(.draft | not) ] as $r
  | [
      ($r | sort_by(.published_at) | last | .tag_name // "none"),
      ([ $r[].assets[] | select(.name | endswith(".apk")) | .download_count ] | add // 0),
      ([ $r[].assets[] | select(.name == "update.json") | .download_count ] | add // 0)
    ]
  | @tsv' <<<"$releases")

if [ ! -s "$CSV" ]; then
  echo "$HEADER" > "$CSV"
fi

if grep -q "^$TODAY," "$CSV"; then
  echo "Already recorded $TODAY; nothing to do."
  exit 0
fi

previous="$(tail -n 1 "$CSV")"
if [ "$previous" = "$HEADER" ]; then
  apk_since=""
  checks_since=""
  active=""
else
  IFS=, read -r prev_date _ prev_apk prev_checks _ <<<"$previous"
  apk_since=$(( apk - prev_apk ))
  checks_since=$(( checks - prev_checks ))
  # Spread a gap (a skipped day) evenly; GNU date on the runner, BSD date on a Mac.
  to_epoch() { date -u -d "$1" +%s 2>/dev/null || date -u -j -f %Y-%m-%d "$1" +%s; }
  days=$(( ($(to_epoch "$TODAY") - $(to_epoch "$prev_date")) / 86400 ))
  [ "$days" -lt 1 ] && days=1
  active=$(( (checks_since + days / 2) / days ))
fi

echo "$TODAY,$latest,$apk,$checks,$apk_since,$checks_since,$active" >> "$CSV"
echo "Recorded $TODAY: $apk APK downloads, $checks update checks, about ${active:-?} TVs active."
