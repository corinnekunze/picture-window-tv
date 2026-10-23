# Undo

Everything this project does to a TV can be undone. Only the steps you've run need undoing; the
order below goes from smallest to biggest change.

## From a computer (preferred)

The backup is taken before the first change and kept in `~/.picture-window-tv/backups/`.
`scripts/setup-tv.sh` takes it for you. Restore uses each TV's first backup (matched by serial
number), so later backups never replace the state it goes back to.

```sh
TV_HOST=192.168.1.50 scripts/backup-tv.sh            # once, before changing anything
TV_HOST=192.168.1.50 scripts/restore-tv.sh --dry-run # show what restore would do
TV_HOST=192.168.1.50 scripts/restore-tv.sh           # put the TV back
```

Restore re-enables anything disabled since the backup, gives Home back to the recorded launcher,
puts back the screensaver and sleep settings, and uninstalls Picture Window and the CleanYourTV
launcher if they weren't there before. Uninstalling also deletes the downloaded photos and the TCL
`APP_AUTO_START` permission. Wake the TV first: adb can't reach it in standby.

## From the remote (no computer)

Menu names vary a little between TV models.

1. **Screensaver back to Google's:** Settings → System → Ambient mode (or Screensaver) → choose
   Google's ambient mode. Uninstalling Picture Window (next step) has the same effect.
2. **Remove Picture Window:** Settings → Apps → See all apps → Picture Window → Uninstall.
3. **Home screen back to Google TV**, only if the CleanYourTV launcher was installed:
   1. Settings → Apps → See all apps → Show system apps. Open each app marked "Disabled" and choose
      Enable. Start with Google TV Home.
   2. Settings → Apps → See all apps → CleanYourTV Home → Uninstall.
   3. Press Home and pick Google TV if asked.

According to its README, CleanYourTV's scripts never leave the TV without a home app; they only
disable a stock launcher they recognise.
