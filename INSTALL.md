# Install Picture Window on your TV

Picture Window turns your TV's screensaver into a slow show of 4K photos from Wikimedia Commons,
with a quiet credit for each photographer. No ads, ever.

**You need:**

- A Google TV or Android TV (Android 8 or newer), such as a TCL, Sony, Hisense or Philips Google TV,
  or a Chromecast with Google TV. Roku, Fire TV, Samsung and LG TVs can't run it.
- A computer (Mac, Linux, or Windows with [Git Bash](https://git-scm.com/downloads) or WSL) on the
  same Wi-Fi as the TV, for about 15 minutes, once.
- Ideally 4 GB free on the TV: the app keeps up to 2 GB of photos and always leaves 2 GB free, so
  with less space it simply keeps fewer photos.

Tested on a TCL Google TV (Android TV 11). Other Google TVs and Android TVs should work the same way;
if yours doesn't, please [open an issue](https://github.com/corinnekunze/picture-window-tv/issues).

Google TV hides other apps' screensavers from its menu, so choosing Picture Window takes one command
from a computer. After that, the computer isn't needed, not even for updates.

There are two ways to do it. Both use the same setup script, which backs the TV up first, and both
can be undone completely.

## Option 1: ask your AI agent

If you use an AI coding agent such as Claude Code, it can walk you through everything and run the
commands for you, asking before it changes anything on the TV.

1. Open your agent in a folder where it can work, for example a new folder called `picture-window`.
2. Paste in the prompt from [PROMPT.md](PROMPT.md) (copy everything in the grey box).
3. Follow along. It'll tell you what to press on the TV, show you what it's about to do, and wait
   for your OK.

## Option 2: do it yourself

### 1. Turn on debugging on the TV

1. **Settings → System → About**. Scroll to **Android TV OS build** and press OK on it seven times,
   until the TV says you're a developer. (On older Android TVs: **Settings → Device Preferences →
   About → Build**.)
2. Go back to **Settings → System → Developer options** and turn on **USB debugging**. Some TVs also
   have **Network debugging** or **Wireless debugging**; turn that on too.
3. Find the TV's IP address: **Settings → Network & Internet →** your Wi-Fi network. It looks like
   `192.168.1.50`.

### 2. Get the tools and the app on your computer

1. **adb**, Android's command-line tool:
   - Mac: install [Homebrew](https://brew.sh), then `brew install android-platform-tools`
   - Windows or Linux: download [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools),
     unzip it and add the folder to your PATH (on Linux, your package manager's `adb` works too).
2. **This repo**: `git clone https://github.com/corinnekunze/picture-window-tv`, or download it as a
   zip from the green **Code** button.
3. **The app**: download `picture-window-<version>.apk` from the
   [latest release](https://github.com/corinnekunze/picture-window-tv/releases/latest) into the repo
   folder. To check it arrived intact, compare `shasum -a 256 picture-window-<version>.apk` (or
   `sha256sum` on Windows and Linux) with the `.sha256` file on the release page.

### 3. Run the setup

In a terminal (on Windows, Git Bash or WSL), in the repo folder, with your TV's IP address and the
APK you downloaded:

```sh
TV_HOST=192.168.1.50 scripts/setup-tv.sh --dry-run --apk picture-window-0.1.0.apk   # shows what it will do
TV_HOST=192.168.1.50 scripts/setup-tv.sh --apk picture-window-0.1.0.apk             # does it
```

The first time, the TV asks **"Allow USB debugging?"**. Tick **Always allow from this computer** and
choose **Allow**, then run the command again.

The script backs the TV up to `~/.picture-window-tv/backups/`, installs the app, lets it download in
the background and makes it the screensaver. The photos, about 2 GB, arrive over the next few
minutes; until then the screensaver shows the ones it has.

### 4. Turn debugging off again

**Settings → System → Developer options →** turn **USB debugging** (and Network debugging) off.
Picture Window doesn't need it, and while it's on, any computer the TV has allowed can control it.

## Using it

- The screensaver starts on its own when the TV sits idle. How long it waits is a TV setting, under
  **Settings → System → Ambient mode** (or **Energy**) on most Google TVs.
- To see it now, leave the TV alone, or open **Picture Window** from your apps, which also shows how
  many photos it has and lets you turn the clock off.
- A new photo every 3 minutes, and new photos every month. [How the photos change](README.md#how-the-photos-change).

## Updating

When there's a new version, Picture Window's own screen (open it from your apps) shows **Update**,
and the TV shows one notification. Press Update, then confirm Android's prompt. The first time,
Android asks you to allow Picture Window to install apps. Your photos and settings stay. No computer
needed.

## Undo

Everything the setup did can be undone. Turn debugging back on, then:

```sh
TV_HOST=192.168.1.50 scripts/restore-tv.sh --dry-run   # shows what it will put back
TV_HOST=192.168.1.50 scripts/restore-tv.sh
```

That puts back the TV's own screensaver and settings and uninstalls Picture Window. Without a
computer, you can also pick Google's screensaver again under **Settings → System → Ambient mode**,
and uninstall Picture Window under **Settings → Apps**. [UNDO.md](UNDO.md) has the details.

## A cleaner home screen too (optional)

[CleanYourTV](https://github.com/corinnekunze/cleanyourtv) replaces Google TV's home screen with a
fast, simple one, and can show Picture Window's photo as its wallpaper with the same credit:
`"photo_source": "picture_window"` in its theme. Replacing the home screen is a bigger change than
the screensaver and is risky on some TV models, so follow CleanYourTV's own guide and read its notes
for your TV first.

## If something goes wrong

| What you see | What to do |
|---|---|
| "Can't reach the TV" | Check the IP address, wake the TV (it drops off the network in standby) and check debugging is on. |
| "The TV is asking to allow USB debugging" | Accept the prompt on the TV, ticking "Always allow", and run the command again. |
| "A copy signed with another key is installed" | An older or test copy is on the TV. Run again with `--replace`; its photos download again. |
| "This TV runs Android SDK …" | The TV's Android is too old for Picture Window. Nothing was changed. |
| The screensaver says it's downloading photos | Give it a few minutes on its first day. |
| Picture Window isn't in the TV's screensaver menu | That's expected on Google TV; the setup sets it for you. Choosing another screensaver there switches it off. |

Something else? [Open an issue](https://github.com/corinnekunze/picture-window-tv/issues) with your
TV's model and what the script printed.
