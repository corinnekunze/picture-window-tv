# Prompt: install Picture Window with your AI agent

Copy everything in the box below into your AI coding agent (for example Claude Code), in a folder
where it can work. It walks you through the setup in [INSTALL.md](INSTALL.md) and asks before it
changes anything on your TV.

By default it also sets up [CleanYourTV](https://github.com/corinnekunze/cleanyourtv)'s simple home
screen, which shows the same photos. It asks first, and you can skip it.

```text
Help me install Picture Window (https://github.com/corinnekunze/picture-window-tv) on my Google TV
or Android TV: a photo screensaver app. Then, unless I skip it, set up CleanYourTV's simple home
screen (https://github.com/corinnekunze/cleanyourtv) so Home shows the same photos. I'm not
necessarily technical, so explain each step in plain language, tell me exactly what to press on the
TV remote, and wait for me when I need to do something.

This is a family TV that people use every day. These rules are not negotiable:

1. Only change the TV through the projects' own scripts. For Picture Window: scripts/setup-tv.sh to
   install and scripts/restore-tv.sh to undo. For CleanYourTV: the tools its PROMPT.md and README
   name (tools/disable.sh, launcher/build-and-install.sh, tools/theme.sh, tools/restore.sh). Read
   each script before running it. Don't write your own adb commands that change the TV. Read-only
   adb commands (adb devices, getprop, pm list packages, dumpsys) are fine for checking things.
2. Never uninstall, root, flash or factory-reset anything, and never change TV settings yourself.
   The scripts change only what they print. The only things that get switched off are the apps on
   CleanYourTV's list that I approve, and its scripts can switch every one back on.
3. Before any step that changes the TV, run it with --dry-run (or show me the exact list) first,
   explain it in plain words, and wait for me to say yes.
4. If a command fails or the TV says something unexpected, stop and explain it to me. Don't retry
   in a loop or try workarounds. Picture Window's INSTALL.md and CleanYourTV's
   docs/TROUBLESHOOTING.md cover common problems.
5. Never install an APK from anywhere except Picture Window's GitHub releases (check its SHA-256
   against the .sha256 file on the release page first) or a launcher you built from the CleanYourTV
   repo below.

First, ask me two things:
- My TV's brand and model (it's on a sticker on the back, or under Settings > System > About).
- Whether to skip the new home screen. Tell me plainly what it is: it replaces the TV's cluttered,
  ad-filled home screen with a simple row of my apps over the same photos as the screensaver, and
  switches off (never uninstalls) the manufacturer's ads and preloaded extras, asking me about any I
  might use. It's a bigger change than the screensaver, takes longer, and needs more installed on
  my computer (Java 17, the Android SDK and Python) to build the home screen. All of it can be
  undone. If I'm unsure, the default is to do it.

Part 1: Picture Window (INSTALL.md has the details). Always do this first: its setup backs up the TV
as it is now, so its restore script can later undo both parts.

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
7. Tell me how to see the screensaver and what to expect: the photos (about 2 GB) download over the
   next 10 to 30 minutes, and a new photo shows every 3 minutes.

Part 2: CleanYourTV's home screen (skip this part if I said so).

8. Get the MTD etc. version of CleanYourTV: git clone https://github.com/corinnekunze/cleanyourtv.
   Use this one, not the original: it's the version whose home screen can show Picture Window's
   photos. Read its README.md, PROMPT.md and themes/README.md, and its notes for my TV model in
   examples/ (the closest match by brand and model). Tell me plainly about any risk listed there
   before going on.
9. Follow CleanYourTV's PROMPT.md for my TV model, with its safety rules, using the repo's own tools
   (it says I cloned the repo). Its backup comes first; Picture Window's backup from Part 1 stays the
   state to restore to. Go through its checklist with me: which apps I use, the dock's apps and
   order, and whether to keep the voice assistant, Chromecast and updates. Show me the list of apps
   it will switch off and wait for my yes.
10. Check what my computer needs to build the home screen (launcher/build-and-install.sh lists it)
    and ask before installing anything. Then build and install it with that script, which makes it
    Home and rolls back if Home doesn't land on it.
11. Push the Picture Window theme: TV_HOST=<ip> tools/theme.sh themes/picture-window. Home restarts
    and shows the same photo as the screensaver, with its credit. If Picture Window has no photo
    yet, Home shows a plain background until one arrives.

Finally:

12. Remind me to turn USB debugging (and Network debugging) off again, and tell me how. Neither app
    needs it.
13. Tell me how to undo everything later: Picture Window's scripts/restore-tv.sh puts the TV back
    the way Part 1's backup found it (apps switched back on, the original home screen and
    screensaver, both apps removed), or from the remote as in UNDO.md. Tell me where the backups are
    (~/.picture-window-tv/backups/, plus CleanYourTV's own).
```
