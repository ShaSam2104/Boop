# Boop — Copilot Coding Agent Instructions

## Project Overview

Boop is an **Android-only** P2P file-sharing app (AirDrop-like UX). Two Android devices share files by tapping them together: NFC brokers the connection handshake, then Wi-Fi Direct + TCP sockets carry the file bytes at full speed — no internet required.

**Package:** `com.shashsam.boop`
**Repo:** `ShaSam2104/Boop`

---

## Technology Stack

| Concern | Choice | Notes |
|---|---|---|
| Language | **Kotlin 2.0** | All source files in `app/src/main/kotlin/` |
| UI | **Jetpack Compose + Material Design 3** | No XML layouts; use `@Composable` functions only |
| Async | **Kotlin Coroutines & Flows** | All network/NFC work must be non-blocking via Coroutines/Flows — no callbacks |
| NFC (Sender) | **HCE** — `HostApduService` | `BoopHceService.kt`; AID `F0426F6F7001` |
| NFC (Receiver) | **Foreground Dispatch / NDEF_DISCOVERED** | handled in `MainActivity` |
| P2P | **Wi-Fi Direct** — `WifiP2pManager` | negotiates the IP/port after NFC tap |
| Transfer | **TCP/IP sockets** | raw byte streams; not HTTP |
| Storage | **MediaStore API** | scoped storage compliance; no `File` API for media |
| Min SDK | **26** (Android 8.0) | |
| Compile/Target SDK | **34** | |
| Build | Gradle 8.6, AGP 8.3.2 | |

---

## Repository Layout

```
Boop/
├── .github/
│   └── copilot-instructions.md   ← you are here
├── README.md
├── build.gradle.kts              # root build script
├── settings.gradle.kts
├── gradle/
│   ├── libs.versions.toml        # version catalog — always add deps here
│   └── wrapper/
└── app/
    ├── build.gradle.kts          # app module
    └── src/main/
        ├── AndroidManifest.xml
        ├── kotlin/com/shashsam/boop/
        │   ├── MainActivity.kt
        │   ├── nfc/
        │   │   └── BoopHceService.kt
        │   ├── ui/
        │   │   ├── screens/HomeScreen.kt
        │   │   └── theme/{Color,Theme,Type}.kt
        │   └── utils/PermissionUtils.kt
        └── res/xml/apduservice.xml
```

---

## Coding Conventions

1. **Kotlin-first** — never write Java. Use idiomatic Kotlin (data classes, extension functions, sealed classes, `when` expressions).
2. **Compose UI only** — do not create XML layouts or Views. Every UI element is a `@Composable`.
3. **Material Design 3 (M3)** — use `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`, and M3 components (`Card`, `ExtendedFloatingActionButton`, `Scaffold`, `TopAppBar`, etc.).
4. **Coroutines/Flows for async** — use `suspend` functions and `Flow` for all network, NFC, and file I/O. No `Thread`, no raw callbacks.
5. **Version catalog** — every new dependency must be declared in `gradle/libs.versions.toml` before referencing it in `build.gradle.kts`.
6. **Scoped storage** — all file reads/writes go through the MediaStore API. Never use `Environment.getExternalStorageDirectory()`.
7. **Logging** — every class has a private `const val TAG` and uses `Log.d(TAG, ...)` for debug messages. Be exhaustive — NFC and Wi-Fi Direct fail silently.
8. **No internet required** — the app must work fully offline; never add network calls to remote servers.
9. **Bold, high-contrast UI** — `FontWeight.ExtraBold` for display text, `FontWeight.Bold` for headlines. Sizes must be large and readable (at minimum `titleMedium` for status banners).
10. **KDoc** — public and internal APIs should have KDoc comments.

---

## Phase 1 — Done ✅

| Area | Status | Files |
|---|---|---|
| Gradle infrastructure (wrapper, version catalog, BOM) | ✅ | `build.gradle.kts`, `gradle/libs.versions.toml` |
| AndroidManifest permissions (NFC, Wi-Fi Direct, storage, API-level-aware) | ✅ | `AndroidManifest.xml` |
| NFC HCE service stub with proprietary AID | ✅ | `nfc/BoopHceService.kt`, `res/xml/apduservice.xml` |
| M3 theme (dynamic color API 31+, static Purple/Teal/Rose fallback) | ✅ | `ui/theme/` |
| Home screen: status banner, Send/Receive FABs, activity log | ✅ | `ui/screens/HomeScreen.kt` |
| Runtime permission helpers (version-aware, Compose launcher) | ✅ | `utils/PermissionUtils.kt` |
| MainActivity wiring (permission flow, log state) | ✅ | `MainActivity.kt` |

---

## Phase 2 — Pending 🔲

Implement each item as a separate, incremental PR:

### 2a — NFC NDEF Payload (Sender / HCE)
**File:** `nfc/BoopHceService.kt`
- Parse the incoming SELECT AID APDU.
- Build an NDEF message containing:
  - A custom MIME record (`application/com.shashsam.boop`) with the Sender's Wi-Fi Direct MAC address and TCP port as a JSON payload (prefer JSON for readability and ease of debugging over binary).
  - An Android Application Record (AAR) for `com.shashsam.boop`.
- Return the serialised NDEF bytes as the APDU response.

### 2b — NFC NDEF Parsing (Receiver)
**Files:** `MainActivity.kt` + new `nfc/NfcReader.kt`
- Enable NFC foreground dispatch in `onResume` / `onPause`.
- Handle `ACTION_NDEF_DISCOVERED` intent; extract MAC address and port from the NDEF record.
- Pass the result to the Wi-Fi Direct manager via a `StateFlow`.

### 2c — Wi-Fi Direct P2P Manager
**File:** new `wifi/WifiDirectManager.kt`
- Wrap `WifiP2pManager` in a `CoroutineScope`; expose a `StateFlow<WifiDirectState>`.
- `connect(deviceAddress: String)` — initiates a P2P connection.
- Sender acts as Group Owner (GO); once connected, the GO's IP is `192.168.49.1` (Android convention).
- Emit connection events (CONNECTED, DISCONNECTED, ERROR) via Flow.

### 2d — TCP File Transfer
**File:** new `transfer/TransferManager.kt`
- **Sender side:** open a `ServerSocket` on a chosen port; accept one connection; stream file bytes.
- **Receiver side:** open a `Socket` to the Group Owner IP + port; read byte stream.
- Use `withContext(Dispatchers.IO)` for all socket operations.
- Report transfer progress (bytes sent/received) via a `Flow<TransferProgress>`.

### 2e — MediaStore File Picker (Sender)
**File:** new `utils/FilePicker.kt`
- Launch `ActivityResultContracts.OpenDocument` with MIME type `*/*`.
- Return a `Uri`; resolve file metadata (name, size, MIME type) via `ContentResolver`.

### 2f — MediaStore Write (Receiver)
- Inside `TransferManager.kt` on the Receiver path.
- Use `MediaStore.Downloads` (API 29+) or `Environment.DIRECTORY_DOWNLOADS` on API 26–28.
- Write bytes to a `ContentResolver` output stream obtained from `MediaStore.insert(...)`.

### 2g — Transfer Progress UI
**Files:** new `ui/viewmodels/TransferViewModel.kt`, extend `HomeScreen.kt`
- `TransferViewModel` collects `Flow<TransferProgress>` and exposes `StateFlow<UiState>`.
- Show a progress indicator (M3 `LinearProgressIndicator`) inside the activity log card.
- On completion, log the saved file path and file size.

---

## Key Constants & Values

| Constant | Value | Location |
|---|---|---|
| HCE AID | `F0426F6F7001` | `BoopHceService.kt`, `apduservice.xml` |
| NDEF MIME type | `application/com.shashsam.boop` | `AndroidManifest.xml` |
| Wi-Fi Direct GO IP | `192.168.49.1` | hardcoded Android convention |
| TCP transfer port | TBD in Phase 2 (suggest `8765`) | `TransferManager.kt` |

---

## Build & Test

```bash
# Debug APK (requires dl.google.com to be reachable for SDK components)
./gradlew assembleDebug

# Unit tests
./gradlew test

# Instrumented tests (connected device/emulator required)
./gradlew connectedAndroidTest
```

> ⚠️ `dl.google.com` is blocked in the default sandbox. The build must run on GitHub Actions or a machine with full internet access. Add `dl.google.com` to the Copilot coding agent allowlist in repository settings if a build is needed.

---

## Common Pitfalls

- **Wi-Fi Direct + NFC fail silently** — always log every state transition with `Log.d`.
- **NEARBY_WIFI_DEVICES** is required on API 33+ instead of `ACCESS_FINE_LOCATION` for Wi-Fi Direct peer discovery. Both code paths must be handled.
- **HCE is one-way** — the `HostApduService` can only respond to commands from the reader; it cannot initiate. Design the APDU exchange so the Receiver always asks and the Sender responds.
- **Group Owner IP** — after `WifiP2pManager.requestConnectionInfo`, the GO's IP address is always `192.168.49.1` on Android. Do not try to discover it dynamically.
- **MediaStore on API 26–28** — `MediaStore.Downloads` is API 29+. Use `Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)` with `WRITE_EXTERNAL_STORAGE` permission as a fallback.
- **Compose state** — keep UI state in `ViewModel`s backed by `StateFlow`; do not pass mutable Android objects into composables.
