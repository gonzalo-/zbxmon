# ZbxMon 📡

**English** · [Español](README.es.md) · [日本語](README.ja.md)

[![Build](https://github.com/gonzalo-/zbxmon/actions/workflows/build.yml/badge.svg)](https://github.com/gonzalo-/zbxmon/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

A small, privacy-friendly Android client for **Zabbix 7.x**.
Check host status, browse problems by severity, acknowledge and comment on them,
look at item values and graphs, and get notifications — **without Google Play Services**
(works on stock Android and on de-Googled systems such as GrapheneOS).

Built with Kotlin and Jetpack Compose (Material 3). No analytics, no trackers.

## Features

- 🖥️ **Hosts** with status emojis: 🟢 OK · 🔥🔴🟠🟡🔵⚪ by worst problem severity · 📵 interface down · 🔧 maintenance · ⏸️ disabled
- 🎛️ **Persistent filters** (saved automatically): show/hide hosts by state (🟢 🚨 📵 🔧 ⏸️ with counts), problem severity, unacknowledged only
- 🏷️ Filter by **host group or tag**, ↕️ **sort** (severity / name / most recent) and ⭐ **favorites** pinned on top
- 🚨 **Problems** with acknowledge, remove acknowledgement and **comment**, with history; 📤 **share** a problem as text
- 🕘 **History** of problems resolved in the last 7 days
- 📊 **Items** with human-readable last values and 📈 **graphs** (1 h, 6 h, 24 h, 7 d)
- 🔔 **Notifications** for new problems (and optionally resolved ones) with a configurable minimum severity,
  an optional single grouped notification, 🌙 **quiet hours**, and 😴 **snooze** / 🔕 **mute** per host or problem
- 🧩 **Home screen widget** with the latest summary
- 🗂️ **Multiple servers** (profiles), each with its own connection mode
- 🎨 Two styles (✨ Minimal / 🖥️ Terminal), 📏 compact or comfortable density, configurable refresh interval
- 🌐 UI language: English (default), Spanish, or follow the system

## Install

### From GitHub Releases (recommended)

1. Open the [Releases](https://github.com/gonzalo-/zbxmon/releases) page and download `zbxmon-<version>.apk`
   and its `zbxmon-<version>.apk.sha256` from the latest release.
2. Verify the download (put both files in the same folder):

   ```bash
   sha256sum -c zbxmon-1.0.0.apk.sha256      # should print: zbxmon-1.0.0.apk: OK
   ```

3. Install it on the phone (Android 8.0+, API 26). Either:
   - **On the phone:** open the APK from your file manager or browser downloads. Android asks you to allow
     installing apps from that source (*Install unknown apps*): allow it for the app you used to open the file, then tap *Install*.
   - **From a computer** with USB debugging enabled: `adb install -r zbxmon-1.0.0.apk`
4. Updates: install the newer APK from Releases over the old one. Your servers and settings are kept,
   as long as both APKs are signed with the same key (official releases always are).

The release APK is signed by the maintainer. An APK you build yourself is signed with a different key,
so it cannot be installed over the official one (uninstall first, which deletes the app data).

### Build it yourself

See [Build from source](#build-from-source) below.

## Build from source

Requirements: **JDK 17** (17–21 work; Gradle 8.9 does not run on anything newer than 22),
the **Android SDK** (platform 35, build-tools 34.0.0) and an internet connection for the first build
(it downloads Gradle 8.9 and the dependencies).

### Arch Linux (step by step)

```bash
# 1. Packages from the official repositories
#    (android-tools provides adb, only needed to install the APK from your computer)
sudo pacman -S --needed git jdk17-openjdk unzip curl android-tools

# 2. Make JDK 17 the active Java. Arch's default JDK is newer than what Gradle 8.9 supports.
sudo archlinux-java set java-17-openjdk
java -version            # should report 17.x

# 3. Android SDK command-line tools, from Google
export ANDROID_HOME="$HOME/Android/Sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"
cd /tmp
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux-11076708_latest.zip -d "$ANDROID_HOME/cmdline-tools"
mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
cd -

# 4. Environment (also add these two lines to ~/.bashrc or ~/.zshrc)
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"

# 5. Accept the licenses and install the SDK components (one time)
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;34.0.0"

# 6. Get the code and build a debug APK
git clone https://github.com/gonzalo-/zbxmon.git
cd zbxmon
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# 7. Install on a phone with USB debugging enabled (optional)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Notes:

- If the download in step 3 returns 404, Google published a newer version: copy the current
  "Command line tools only → Linux" link from <https://developer.android.com/studio#command-line-tools-only>.
- The AUR package `android-sdk-cmdline-tools-latest` is an alternative to step 3 if you prefer your AUR helper.
- The debug APK is signed automatically with the Android debug key, so it installs right away.
  No keystore is needed to build it.

### Other systems

- **Android Studio** (any OS): *File → Open* the project folder, let it sync, then *Run* or *Build → Build APK(s)*.
- **macOS / other Linux:** install JDK 17 and the SDK command-line tools, set `ANDROID_HOME`,
  run the `sdkmanager` commands from step 5 and then `./gradlew assembleDebug`.
- **Windows:** use Android Studio, or `gradlew.bat assembleDebug` after setting `JAVA_HOME` and `ANDROID_HOME`.

### Troubleshooting

| Problem | Fix |
|---------|-----|
| `SDK location not found` | `export ANDROID_HOME="$HOME/Android/Sdk"` or `echo "sdk.dir=$HOME/Android/Sdk" > local.properties` |
| `licences have not been accepted` | `yes \| sdkmanager --licenses` |
| `Unsupported class file major version` / Gradle fails on startup | Use JDK 17: `sudo archlinux-java set java-17-openjdk`, or `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk` |
| `./gradlew: Permission denied` | `chmod +x gradlew` |
| `Could not resolve ...` / timeouts | Check your connection or proxy; the first build needs to download dependencies |
| `OutOfMemoryError` | Raise `org.gradle.jvmargs=-Xmx2048m` in `gradle.properties` |

### Signed release build

```bash
cp keystore.properties.example keystore.properties     # then edit it with your keystore details
./release.sh --no-tag                                    # → dist/zbxmon-<version>.apk + .sha256
```

Without `--no-tag` the script requires a clean git tree and creates the local tag `v<version>`.
Create a keystore once with:

```bash
keytool -genkeypair -keystore release.jks -storetype PKCS12 -alias zbxmon \
        -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=ZbxMon"
```

⚠️ Keep your keystore safe and **never commit it**. An installed app can only be updated by an APK signed
with the same key, so APKs you sign yourself cannot update the official release (and vice versa).

The version lives in `gradle.properties` (`zbxmon.versionName`, `zbxmon.versionCode`).
Bump `versionCode` on every release.

## GitHub Actions

| Workflow | When | What it does |
|----------|------|--------------|
| `.github/workflows/build.yml` | every push to `main` and every pull request | validates the Gradle wrapper, checks that translations are in sync, builds a debug APK and uploads it as an artifact |
| `.github/workflows/release.yml` | when a tag like `v1.0.0` is pushed | builds a **signed** release APK and publishes it, with its `.sha256`, as a GitHub release |

The debug APK from `build.yml` is on the run's page under *Artifacts* (a GitHub login is required to download it).
It is meant for testing, not for everyday use: use the signed APK from Releases instead.

`release.yml` needs signing secrets that only the repository owner has (the list is in the comment at the top of the file),
so on a fork it does nothing useful. Forks can build and sign their own APK locally (see above).

## Setting up Zabbix

1. In Zabbix go to **Users → API tokens → Create API token** (use a dedicated, least-privileged user).
2. In ZbxMon add a server with the frontend URL (e.g. `https://zbx.example.com/zabbix`, without `api_jsonrpc.php`) and the token.
3. Tap **Test connection**.

Acknowledging and commenting requires write permission on the affected hosts for the token's user.
The app talks to `<URL>/api_jsonrpc.php` using `Authorization: Bearer <token>`.

### Connection modes (per server)

| Mode | How |
|------|-----|
| Valid HTTPS | Default. Normal system certificate validation. |
| Self-signed HTTPS | Enable *Self-signed certificate*, tap *Get server fingerprint*, verify it against your server, save. The SHA-256 fingerprint is pinned. |
| Plain HTTP | Use an `http://` URL. Only for LAN/VPN: the token travels unencrypted. |

## Notifications without Google services

There is no FCM/push dependency. Two mechanisms are available:

- **Periodic check (WorkManager):** every 15 / 30 / 60 minutes (Android's minimum is 15).
- **Foreground service (optional):** polls every 30 s – 5 min and keeps a persistent notification with a 🟢/🔴 summary.
  It is restarted after a reboot.

On GrapheneOS and other restrictive systems, allow notifications and set the app's battery usage to *Unrestricted*
if you use the foreground service (Settings has shortcuts for both).
The first poll of a server only records the current state, so existing problems do not flood you with notifications.

Snoozes and mutes are local to the phone: they only silence notifications and never change anything in Zabbix.
Problems that appear during quiet hours are held back and notified when quiet hours end
(Disaster problems can still notify if you keep that option on).
The widget shows the result of the latest background check, so it is refreshed by the periodic check or the foreground service.

## Languages

English is the default and fallback. Spanish is included and selectable in **Settings → Language**.
To add a language, see [CONTRIBUTING.md](CONTRIBUTING.md).

## Making your own version

Fork the repository, then:

- change `applicationId` / `namespace` in `app/build.gradle.kts` (and move the `dev/zbxmon` source folder and `package` lines to match)
  if you want to publish it as a separate app;
- create your own keystore (see above); to use `release.yml` in your fork, add the secrets listed in its header;
- replace the donation address in `app/src/main/java/dev/zbxmon/Donate.kt` and in this README with your own (or remove them).

## Security

Tokens are encrypted with a key in the Android Keystore and excluded from backups.
See [SECURITY.md](SECURITY.md) for details and how to report vulnerabilities.

## Project structure

```
app/src/main/java/dev/zbxmon/
  data/     models, storage, Keystore encryption, TLS pinning, Zabbix JSON-RPC client
  notify/   notifications, WorkManager worker, foreground service, boot receiver
  widget/   home screen widget
  ui/       Compose screens, theme, view model
scripts/    helper scripts (translation check)
.github/    CI workflows
```

## Support the project · Apoyá el proyecto

**English.** ZbxMon is free and open source. If it is useful to you, you can support its development with Bitcoin.
The same address is in the app under **Settings → Support the project**.

**Español.** ZbxMon es libre y de código abierto. Si te resulta útil, podés apoyar su desarrollo con Bitcoin.
La misma dirección está en la app, en **Ajustes → Apoyá el proyecto**.

₿ **Bitcoin (on-chain)**

```
bc1p9nq9peaf3qzvuptpavg7flacdh05pyzr2f8rnjuj6a623sk22aysspyssg
```

## License

[MIT](LICENSE)
