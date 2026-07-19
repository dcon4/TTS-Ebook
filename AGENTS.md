# AGENTS.md — TTS Ebook

## Purpose

Android app that reads ebooks aloud using any installed TTS engine. Users
import books from local storage or SD card, navigate by chapter and paragraph,
and control playback via a persistent notification.

## Supported formats

- EPUB (primary, via epublib)
- PDF (via PdfBox-Android)
- TXT
- HTML / HTM / XHTML

MOBI/AZW3 is planned for a future version.

## Architecture

- Single Activity (`MainActivity`) + Jetpack Compose + Navigation Compose
- Hilt DI
- MVVM with Room database (books, positions, bookmarks)
- Foreground service (`TtsPlaybackService`, type `mediaPlayback`) for TTS playback
- `MediaSessionCompat` + notification with 6 actions (prev chapter, prev para,
  play/pause, next para, next chapter, add bookmark)

## Repository

`https://github.com/dcon4/TTS-Ebook`

## Build

```bash
./gradlew assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Required features

- In-app debug log share via BugReport icon in TopAppBar.
- Verbose logging toggle (planned: DataStore, default true).
- CI builds debug APK and uploads as artifact.

## Log naming convention

Filename: `ttsebook-debug.log.YYYY-MM-DD-HH-MM.txt`
Line format: `YYYY-MM-DD HH:MM:SS.mmm [Tag] Message`
First line: `[TTS-Ebook] [VERBOSE] Logger initialized (build X)`

## Git author identity

Use `dcon4 <dcon4@gmail.com>` for all commits.
