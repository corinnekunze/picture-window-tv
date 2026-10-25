# Prompt: install Picture Window with your AI agent

Copy everything in the box below into your AI coding agent (for example Claude Code), in a folder
where it can work. It walks you through the setup in [INSTALL.md](INSTALL.md) and asks before it
changes anything on your TV.

```text
Help me install Picture Window (https://github.com/corinnekunze/picture-window-tv) on my Google TV
or Android TV: a photo screensaver app. I'm not necessarily technical, so explain each step in
plain language, tell me exactly what to press on the TV remote, and wait for me when I need to do
something.

This is a family TV that people use every day. These rules are not negotiable:

1. Only change the TV through the repo's own scripts: scripts/setup-tv.sh to install and
   scripts/restore-tv.sh to undo. Read each script before running it. Don't write your own adb
   commands that change the TV. Read-only adb commands (adb devices, getprop, pm list packages,
   dumpsys) are fine for checking things.
2. Never disable, uninstall, root, flash or factory-reset anything, and never change TV settings
   yourself. The scripts change only what they print.
3. Before any step that changes the TV, run it with --dry-run first, show me what it will do in
   plain words, and wait for me to say yes.
4. If a command fails or the TV says something unexpected, stop and explain it to me. Don't retry
   in a loop or try workarounds. The repo's INSTALL.md has a table of common problems.
5. Never install an APK from anywhere except this repo's GitHub releases, and check its SHA-256
   against the .sha256 file on the release page before using it.

The steps (INSTALL.md has the details):

1. Check my computer: is adb installed (adb version)? If not, tell me how to install it for my
   system and ask before installing anything. On Windows, use Git Bash or WSL for the scripts.
2. Get the repo (git clone, or download the zip) and read INSTALL.md, README.md and UNDO.md.
3. Download the APK from https://github.com/corinnekunze/picture-window-tv/releases/latest and
   check its SHA-256.
4. Walk me through turning on debugging on the TV: Settings > System > About > press OK on
   "Android TV OS build" seven times, then Settings > System > Developer options > USB debugging on
   (and Network debugging, if the TV has it). Then help me find the TV's IP address under
   Settings > Network & Internet.
5. Connect: adb connect <ip>:5555. If the TV shows "Allow USB debugging?", tell me to tick
   "Always allow from this computer" and press Allow. Confirm with adb devices.
6. Run TV_HOST=<ip> scripts/setup-tv.sh --dry-run --apk <apk>, explain what it will do, and wait
   for my OK. Then run it without --dry-run.
   - If it says a copy signed with another key is installed, explain that --replace removes that
     copy and its downloaded photos (they download again), and ask before using it.
7. Tell me how to see the screensaver and what to expect: photos download over the next few
   minutes, a new photo every 3 minutes.
8. Remind me to turn USB debugging (and Network debugging) off again, and tell me how. Picture
   Window doesn't need it.
9. Tell me how to undo everything later (scripts/restore-tv.sh, or from the remote as in UNDO.md),
   and where the backup is (~/.picture-window-tv/backups/).

If I also want a cleaner home screen with CleanYourTV (https://github.com/corinnekunze/cleanyourtv):
that's a separate, bigger change. Do it only if I ask, after Picture Window works. Follow
CleanYourTV's own PROMPT.md and its safety rules exactly, read its notes for my TV model in its
examples/ folder first, and tell me plainly about any risk listed there. Its theme setting
"photo_source": "picture_window" makes its wallpaper show Picture Window's photo.
```
