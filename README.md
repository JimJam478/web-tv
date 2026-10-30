# Web TV

Turn any website into an app on your Android TV, with ads blocked and a remote-friendly cursor.

You pick a website, a name and (optionally) a picture. The build makes a small app (~2 MB) that opens that site full
screen and sits on your TV's app shelf like any other app. Build as many as you like: each site gets its own app.

## Features

- **Full screen**, with no browser bars. Links stay inside the app, logins stay signed in, and videos can go full screen.
- **Remote control cursor.** The arrows move a pointer and OK clicks under it, so sites made for a mouse work too.
  Push the pointer past the top or bottom edge to scroll.
- **YouTube, made for the TV** (when the app's `url` is YouTube): videos open full screen, Back leaves them, and
  playback stays at 720p in the format the TV decodes in hardware, so the picture is smooth and the pointer quick.
- **Desktop layout.** Pages are laid out 1920 px wide instead of as a phone or tablet, so sites show their full
  desktop page at the TV's real resolution.
- **Ad blocking:**
  - Requests to about 275,000 known ad and tracker domains are refused.
  - Pop-up windows and new tabs never open.
  - A page that tries to redirect you to an ad site stays where it is.
  - Leftover empty ad boxes are hidden.
  - YouTube video ads are stripped before the player sees them, and any ad that still starts is skipped.
  - The lists update themselves weekly.
- **A way out:** hold Back for a second to jump to the home page and clear history, so a misbehaving page can't trap
  you.

Tested on a Xiaomi Mi TV (Android TV 11). It should work on any Android TV or Google TV running Android 8 or newer.

## What you need

- A computer running **macOS or Linux**. On Windows, use [WSL](https://learn.microsoft.com/windows/wsl/install)
  (untested).
- **Java (JDK 17 or newer)**, the **Android SDK command-line tools** and **adb**. You don't need Android Studio.
- The TV and the computer on the **same Wi-Fi network**.

### Install the tools

**macOS** (with [Homebrew](https://brew.sh)):

```sh
brew install openjdk@17
brew install --cask android-commandlinetools
sdkmanager "platform-tools" "build-tools;35.0.0" "platforms;android-35"
```

**Linux** (Debian/Ubuntu):

```sh
sudo apt install openjdk-17-jdk zip unzip
# Get "Command line tools only" from https://developer.android.com/studio#command-tools, then:
mkdir -p ~/Android/Sdk/cmdline-tools
unzip commandlinetools-linux-*.zip -d ~/Android/Sdk/cmdline-tools
mv ~/Android/Sdk/cmdline-tools/cmdline-tools ~/Android/Sdk/cmdline-tools/latest
export ANDROID_HOME=~/Android/Sdk PATH="$PATH:$HOME/Android/Sdk/cmdline-tools/latest/bin:$HOME/Android/Sdk/platform-tools"
sdkmanager "platform-tools" "build-tools;35.0.0" "platforms;android-35"
```

Check that it all works: `java -version`, `adb version`.

`build.sh` looks for the SDK in `$ANDROID_HOME`, `~/Library/Android/sdk`, `~/Android/Sdk` and Homebrew's location. If
yours is somewhere else, set `ANDROID_HOME`.

## Step 1: let your computer install apps on the TV

Menu names vary by brand. These are the usual ones.

1. **Turn on developer mode.** Go to *Settings → System → About* (on some TVs, *Settings → Device Preferences →
   About*). Select **Android TV OS build** (or **Build**) seven times, until it says you're a developer.
2. **Turn on debugging.** Go to *Settings → System → Developer options* (or *Device Preferences → Developer options*)
   and turn on **USB debugging**. Also turn on **Network debugging** or **Wireless debugging** if your TV has it.
3. **Find the TV's IP address** under *Settings → Network & Internet →* your Wi-Fi network, e.g. `192.168.1.36`.
4. **Connect** from the computer:

   ```sh
   adb connect 192.168.1.36:5555
   ```

   The TV asks *"Allow USB debugging?"*. Tick **Always allow from this computer** and choose **OK**. Then
   `adb devices` should list the TV as `device`.

   If your TV only has **Wireless debugging** (Android 11+ Google TV), open it, choose *Pair device with pairing
   code*, then run `adb pair <ip>:<pairing port>` with the code shown, and `adb connect <ip>:<port>`.

## Step 2: choose your site

```sh
git clone https://github.com/JimJam478/web-tv.git
cd web-tv
cp app.properties.example app.properties
```

Edit `app.properties`:

```properties
name = Wikipedia                  # name on the TV's app shelf
url = https://en.wikipedia.org/   # page the app opens
id = com.yourname.wikipedia       # unique per app: lowercase letters, digits, dots
# icon = local/logo.png           # optional picture (PNG/JPEG) for the icon and banner
# icon_crop = 0 0 400             # optional square of the picture: x y size, in pixels
# page_width = 1920               # optional: 1280 makes sites 1.5x bigger, easier to read from the couch
# desktop_ua = true               # optional: claim to be desktop Chrome (see Troubleshooting)
```

Without an `icon`, the app's name is drawn as the banner and its initials as the icon. With one, the picture becomes
the icon, and the banner shows the picture next to the name. Put your pictures in `local/`, which git ignores.

## Step 3: build and install

```sh
./build.sh
adb install -r com.yourname.wikipedia.apk
```

The first build downloads the ad lists (about 12 MB) and creates `release.keystore`, the key that signs your app.
**Keep `release.keystore`.** Updates to an installed app must be signed with the same key. If you lose it, uninstall
the app on the TV before installing the new build, which also clears its saved logins.

The app now appears in the TV's app list. Pin it to the home row from there.

## Using it

| Remote button | What it does |
| --- | --- |
| Arrows | Move the pointer. Hold to speed up. At the top or bottom edge, scroll the page. |
| OK | Click under the pointer |
| Back | Go back a page, or leave full-screen video |
| Hold Back (1 s) | Go to the home page and forget history |
| Play/pause and other media keys | Passed to the page |

The pointer hides after 4 seconds without movement, so it never sits on top of a video.

## More apps, updates and changes

- **Another site:** make another config file and build it. Each `id` installs as its own app:

  ```sh
  cp app.properties.example news.properties   # edit name, url, id
  ./build.sh news.properties
  adb install -r com.yourname.news.apk
  ```

- **Change the name, icon or URL:** edit the config, then `./build.sh` and `adb install -r` again. Logins are kept.
  Changing the `id` installs a separate, new app instead.
- **Ad lists:** the installed app re-downloads them in the background once a week, and the new lists apply from the
  next launch. `./update-blocklist.sh` refreshes the copy built into new APKs. You only need it for fresh installs.
- **Get the latest code:** `git pull`, then rebuild and reinstall.

## How the ad blocking works, and its limits

The app checks every request the page makes against the lists below, including requests made through a site's
service worker. If the domain is listed, the request gets an empty response.

| List | What it adds | License |
| --- | --- | --- |
| [StevenBlack/hosts](https://github.com/StevenBlack/hosts) | ad and tracker domains | MIT |
| [HaGeZi Multi PRO](https://github.com/hagezi/dns-blocklists) | ad, tracker, scam and pop-up domains | GPL-3.0 |
| [EasyList](https://easylist.to) | rules that hide leftover empty ad boxes on any site | GPL-3.0 / CC BY-SA 3.0 |

The lists aren't stored in this repository. `build.sh` downloads them, and the app keeps them up to date.

Limits:

- **Ads served from the site's own domain** can't be blocked by domain without breaking the site itself.
- **Cosmetic hiding** uses only EasyList's site-independent rules. Site-specific rules aren't supported, so some empty
  boxes stay. Ads can flash for a moment before they're hidden.
- **No new windows at all:** sites that sign you in through a pop-up window (some "Sign in with…" buttons) won't work.
- **New ad domains** appear daily. The weekly update keeps up with most of them, not all.
- **YouTube** (www.youtube.com): the app removes the ads from the player's data, the way uBlock Origin does. An
  ad that slips through, usually on a video opened straight from a link, is muted and skipped in about a second.
  YouTube changes this often. When ads come back, `git pull` and rebuild. YouTube videos embedded in other sites,
  youtube.com/tv and sponsor segments inside videos aren't covered.
- **YouTube can hold a video back** for about as long as the ad it would have shown (often 15 to 30 seconds of
  black screen). Its server simply doesn't send the video yet, so the app can't skip that wait.
- **A YouTube video opened straight from a link** (rather than from inside YouTube) can stutter: the app's
  720p/H.264 settings arrive after that first player has started.
- **Video ads in other sites' players** aren't blocked.

## Troubleshooting

**The app still shows its old name or icon.** Some launchers cache app tiles. Restart the launcher (or the TV). For a
custom launcher: `adb shell am force-stop <launcher package>`, then press Home.

**The site looks like a big phone or tablet layout.** Rebuild from the latest code, since desktop layout is on by
default. If the text is too small from the couch, set `page_width = 1280`. If the site still sends its phone page, or
Google sign-in says the browser isn't secure, set `desktop_ua = true`: the app then claims to be desktop Chrome.

**Stuck on "Just a moment..." or "Performing security verification".** That's Cloudflare's bot check, and it never
passes with `desktop_ua = true`: the web view still reports Android underneath, and the check sees the mismatch.
Remove that line (or set it to `false`) and rebuild.

**`adb connect` fails, or the device shows as `unauthorized`.** Check the TV for the *Allow USB debugging?* prompt. If
it doesn't appear, turn USB debugging off and on again. Some TVs forget the authorization after switching input or
picture mode.

**`INSTALL_FAILED_UPDATE_INCOMPATIBLE`.** The app on the TV was signed with a different key (another computer or a
lost `release.keystore`). Uninstall it first: `adb uninstall <id>`.

**`./update-blocklist.sh` or the first build times out.** It already forces IPv4, which fixes Java hanging on networks
with broken IPv6. Retry. A failed download never replaces lists you already have.

**A site doesn't load or play video.** It may block in-app browsers, need a pop-up for sign-in, or use DRM that
Android's web view can't play. These apps work best with sites that play in a regular desktop Chrome.

## Be fair

Use this with sites you're allowed to use. Many sites pay their creators with ads, so if you value one, support it
another way. You're responsible for what you build and what you point it at.

## Development

- No Gradle: `build.sh` calls the SDK tools directly. Source is in `app/src/app/webtv/`:
  - `MainActivity.java`: the web view, cursor and blocking
  - `Blocklist.java`: domain matching
  - `ListUpdater.java`: list download, used by both the app and `update-blocklist.sh`
  - `app/assets/youtube.js`: YouTube ad removal and TV playback settings, injected into every page (does nothing on
    other sites)
- `tools/Art.java` draws the icon and banner. `tools/BlocklistCheck.java` is a quick check of domain matching:

  ```sh
  javac -d out/check app/src/app/webtv/Blocklist.java tools/BlocklistCheck.java && java -cp out/check app.webtv.BlocklistCheck
  ```

- See logs from the app: `adb logcat -s WebTV`.

## License

[MIT](LICENSE). The ad lists that builds download have their own licences (see above).
