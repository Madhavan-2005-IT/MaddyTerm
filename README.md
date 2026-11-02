# MaddyTerm — a Termux front-end for Android

A native Kotlin / Jetpack Compose terminal UI. It does not embed a shell itself:
every command is executed by **Termux** through Termux's official `RUN_COMMAND`
intent API, and the output comes back to MaddyTerm.

## Get the APK (no Android Studio needed)
1. Create a new GitHub repo and push this folder to it.
2. Open the repo's **Actions** tab, run **Build APK** (it also runs on every push).
3. Download the `MaddyTerm-debug-apk` artifact, unzip, install `app-debug.apk`.

Or locally: install JDK 17, Android SDK (platform 36) and Gradle 8.11+, then `gradle assembleDebug`.
The APK lands in `app/build/outputs/apk/debug/`.

## One-time setup on the phone
1. Install Termux from F-Droid or GitHub releases (the Play Store build is outdated and won't work).
2. Open MaddyTerm -> Setup -> **Grant** the permission.
3. In Termux run:
   `mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties`
4. Force-stop Termux and reopen it once, then press **Test** in MaddyTerm.

## Notes
- `cd` is tracked: the working directory is carried from one command to the next.
- Background mode has no TTY. For vim, nano, htop, ssh prompts etc. use the **Termux ↗** button,
  which opens the command in a real Termux session.
- Long-running commands (ping, tail -f) never finish in background mode; use **Clear** to reset the counter.
- Built against API 36 (compileSdk/targetSdk). Bump to 37 once the Android 17 SDK is on your build machine.
