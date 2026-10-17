# MediaGrab

Android app that downloads video or audio from links, built on [yt-dlp](https://github.com/yt-dlp/yt-dlp) and ffmpeg (via the `youtubedl-android` library).

## Features
- Paste a link, or share one to MediaGrab from a browser or any app
- Video (best / 720p / 480p, MP4) or audio only (MP3 / M4A)
- Progress and cancel
- Files are saved to `Downloads/MediaGrab`
- "Update download engine" button: sites change often, so update yt-dlp when a site stops working

## Get an APK

**Option A: Android Studio**
1. Install Android Studio, choose *Open*, and select this folder.
2. Let Gradle sync, then *Build > Build APK(s)*, or press Run with a phone or emulator connected.

**Option B: GitHub (no local setup)**
1. Create a new GitHub repository and upload this whole folder, including `.github`.
2. Open the *Actions* tab, run *Build APK*, and download `MediaGrab-debug-apk` from the finished run.
3. Copy the `.apk` to your phone and install it (allow installs from your file manager or browser when asked).

## Notes
- Requires Android 10 (API 29) or newer.
- The first launch unpacks the engine and takes a few seconds.
- The `youtubedl-android` version is set in `app/build.gradle.kts` (`ytdlVersion`). If Gradle cannot resolve it, check the library's releases for the latest version and change that one value.
- Not supported: DRM-protected streams (Netflix, Spotify and similar). Only download content you own, that is licensed for download, or that the site and your local law allow you to save.

## Ideas for next versions
Playlist support, a download history screen, background downloads with a notification (foreground service), and a queue.
