# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Boop is an Android P2P file-sharing app. Two devices share files by tapping together: NFC brokers the connection (HCE on Sender, foreground dispatch on Receiver), then Wi-Fi Direct + TCP sockets carry file bytes at full speed. No internet required.

**Package:** `com.shashsam.boop`

## Build Commands

```bash
./gradlew assembleDebug          # Debug APK → app/build/outputs/apk/debug/
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # Instrumented tests (device/emulator required)
./gradlew installDebug           # Install debug APK on connected device
```

`dl.google.com` must be reachable to download Android SDK components.

## Tech Stack

- **Kotlin 2.0** — no Java
- **Jetpack Compose + Material Design 3** — no XML layouts
- **Kotlin Coroutines & Flows** — no raw threads or callbacks
- **Gradle 8.6 / AGP 8.3.2** — version catalog in `gradle/libs.versions.toml`
- **minSdk 26 / compileSdk 34**

## Architecture

**MVVM with Compose.** `TransferViewModel` is the central orchestrator. It owns `WifiDirectManager`, observes Wi-Fi Direct and NFC state via `StateFlow`, and exposes a single `TransferUiState` to the UI layer.

### Transfer Flow

1. **Sender** picks a file via MediaStore → `BoopHceService` (HCE) emits an NDEF message containing Wi-Fi Direct MAC + TCP port as JSON
2. **Receiver** reads NDEF via `NfcReader` (foreground dispatch or reader mode) → extracts `ConnectionDetails`
3. Wi-Fi Direct group negotiation: Sender = Group Owner (GO IP: `192.168.49.1`), Receiver = peer
4. TCP socket stream: Sender runs `ServerSocket`, Receiver connects and writes to MediaStore
5. Wire format: `[nameLen][name][size][mimeLen][mime][bytes...]` in 16 KB chunks

### Key Components

| Component | Role |
|---|---|
| `MainActivity` | Entry point, NFC foreground dispatch, permission launcher, Compose host |
| `nfc/BoopHceService` | HCE service responding to SELECT AID APDU with NDEF payload (AID: `F0426F6F7001`) |
| `nfc/NfcReader` | NFC reader mode + foreground dispatch; parses NDEF → `ConnectionDetails` |
| `wifi/WifiDirectManager` | Coroutine-friendly wrapper around `WifiP2pManager`; exposes `StateFlow<WifiDirectState>` |
| `transfer/TransferManager` | Singleton with `sendFile()` / `receiveFile()` suspend functions; returns `Flow<TransferProgress>` |
| `ui/viewmodels/TransferViewModel` | Owns full transfer pipeline; produces `TransferUiState` |
| `ui/screens/HomeScreen` | Status banner, Send/Receive FABs, progress indicator, activity log |
| `utils/PermissionUtils` | Version-aware runtime permission helpers (API 26–34 differences) |
| `utils/FilePicker` | Compose wrapper around `OpenDocument` contract; resolves file metadata |

## Coding Conventions

- **Version catalog first** — every dependency goes in `gradle/libs.versions.toml` before `build.gradle.kts`
- **Scoped storage only** — all file I/O through MediaStore API; never use `Environment.getExternalStorageDirectory()`
- **Exhaustive logging** — every class has `private const val TAG` + `Log.d(TAG, ...)`. NFC and Wi-Fi Direct fail silently, so log every state transition
- **Sealed classes for states** — `NfcReaderState`, `WifiDirectState`, etc. with exhaustive `when` expressions
- **Bold UI** — `FontWeight.ExtraBold` for display, `FontWeight.Bold` for headlines; M3 dynamic color on API 31+, static Purple/Teal/Rose fallback
- **Compose state in ViewModels** — `MutableStateFlow` internally, exposed as `StateFlow`; no mutable Android objects in composables

## Common Pitfalls

- **NEARBY_WIFI_DEVICES** required on API 33+ instead of `ACCESS_FINE_LOCATION` for Wi-Fi Direct — both paths must be handled
- **HCE is one-way** — Sender can only respond to commands; Receiver always initiates the APDU exchange
- **Group Owner IP** is always `192.168.49.1` on Android; do not discover dynamically
- **MediaStore.Downloads** is API 29+ — use `Environment.getExternalStoragePublicDirectory()` with `WRITE_EXTERNAL_STORAGE` on API 26–28
- **`dl.google.com`** must be allowlisted in sandboxed CI for SDK component downloads

## Key Constants

| Constant | Value |
|---|---|
| HCE AID | `F0426F6F7001` |
| NDEF MIME type | `application/com.shashsam.boop` |
| Wi-Fi Direct GO IP | `192.168.49.1` |
| TCP transfer port | `8765` |


# Review

All the code changes you make will be reviewed by codex so maintain a thorough code quality.

# Update

Aggressively update the claude.md file after every session of edit 