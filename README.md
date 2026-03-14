# Boop 📲

> **"Just boop that file"** — AirDrop-style P2P file sharing for Android.

Boop lets two Android devices share files by simply tapping them together. An NFC tap brokers the connection; the actual transfer happens over a high-speed Wi-Fi Direct link using TCP sockets — no internet required.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Tech Stack](#tech-stack)
3. [Project Structure](#project-structure)
4. [Phase 1 — Completed](#phase-1--completed)
5. [Phase 2 — Roadmap](#phase-2--roadmap)
6. [Building the App](#building-the-app)
7. [Permissions](#permissions)

---

## Architecture Overview

```
Sender Device                          Receiver Device
┌─────────────────────────┐            ┌─────────────────────────┐
│  User picks a file      │            │  App in NFC reader mode │
│  (MediaStore picker)    │            │                         │
│          │              │            │          │              │
│  BoopHceService (HCE)   │◄── NFC ───►│  NFC foreground dispatch│
│  sends NDEF payload:    │    tap     │  receives Wi-Fi Direct  │
│  • Wi-Fi Direct MAC     │            │  MAC + port             │
│  • TCP port             │            │          │              │
│  • AAR (app install)    │            │  WifiP2pManager.connect │
│          │              │            │          │              │
│  WifiP2pManager (GO)    │◄─ Wi-Fi ──►│  WifiP2pManager (peer) │
│  TCP Server Socket      │  Direct    │  TCP Client Socket      │
│  streams file bytes     │            │  writes to MediaStore   │
└─────────────────────────┘            └─────────────────────────┘
```

### Flow Summary

1. **Sender** activates HCE via `BoopHceService`. The NDEF message carries the Sender's Wi-Fi Direct device address and a listening TCP port.
2. **Receiver** reads the NDEF tag via foreground dispatch and extracts the connection parameters.
3. Both devices negotiate a **Wi-Fi Direct** group. The Sender acts as Group Owner (GO).
4. A **TCP socket** stream carries the raw file bytes from Sender → Receiver.
5. The Receiver writes the incoming bytes to **MediaStore** (scoped storage).
6. If the Receiver does not have Boop installed, the **Android Application Record (AAR)** in the NDEF message redirects them to the Play Store.

---

## Tech Stack

| Concern | Technology |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material Design 3 |
| Async | Kotlin Coroutines & Flows |
| NFC (Sender) | Host-Based Card Emulation (HCE) — `HostApduService` |
| NFC (Receiver) | NFC foreground dispatch / NDEF_DISCOVERED |
| P2P Discovery | Wi-Fi Direct — `WifiP2pManager` |
| File Transfer | TCP/IP sockets (byte streams) |
| Storage | Android MediaStore API (scoped storage) |
| Min SDK | 26 (Android 8.0 Oreo) |
| Target SDK | 34 (Android 14) |
| Build | Gradle 8.6, AGP 8.3.2 |

---

## Project Structure

```
app/src/main/
├── AndroidManifest.xml              # Permissions, HCE service declaration
├── kotlin/com/shashsam/boop/
│   ├── MainActivity.kt              # Entry point; hosts Compose content tree
│   ├── nfc/
│   │   └── BoopHceService.kt        # HCE stub (Phase 2: NDEF payload)
│   ├── ui/
│   │   ├── screens/
│   │   │   └── HomeScreen.kt        # Primary Compose UI screen
│   │   └── theme/
│   │       ├── Color.kt             # M3 color palette (Purple/Teal/Rose)
│   │       ├── Theme.kt             # BoopTheme (dynamic color on API 31+)
│   │       └── Type.kt              # Bold typography scale
│   └── utils/
│       └── PermissionUtils.kt       # Runtime permission helpers
└── res/
    └── xml/
        └── apduservice.xml          # HCE AID filter (F0426F6F7001)
```

---

## Phase 1 — Completed

Phase 1 establishes the project foundation:

### Infrastructure
- Gradle 8.6 wrapper, AGP 8.3.2, Kotlin 2.0, Compose BOM 2024.06.00
- Version catalog (`gradle/libs.versions.toml`)
- `minSdk=26`, `compileSdk=34`

### AndroidManifest Permissions
Version-aware permission set:
- NFC (`android.permission.NFC`)
- Wi-Fi Direct (`ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `CHANGE_NETWORK_STATE`)
- Location for Wi-Fi Direct discovery:
  - API ≤ 32: `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`
  - API 33+: `NEARBY_WIFI_DEVICES` (with `neverForLocation` flag)
- Storage:
  - API ≤ 28: `READ_EXTERNAL_STORAGE` + `WRITE_EXTERNAL_STORAGE`
  - API 29–32: `READ_EXTERNAL_STORAGE` only
  - API 33+: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`

### NFC HCE Stub
`BoopHceService` extends `HostApduService` with proprietary AID `F0426F6F7001`. Currently returns `SELECT_OK_SW (0x9000)` — full NDEF payload wired in Phase 2.

### Material Design 3 Theme (`BoopTheme`)
- Dynamic color on Android 12+ (API 31); static Purple/Teal/Rose palette on older devices
- Bold typography throughout (ExtraBold display, Bold headlines)
- Transparent status bar via `WindowCompat`

### Home Screen UI (`HomeScreen.kt`)
- **System Status Banner**: animated `Card` showing "Systems Ready: Permissions Granted" (green) or "Awaiting Permissions…" (amber)
- **Action Buttons**: two `ExtendedFloatingActionButton`s — "Send File" and "Receive File" — 64 dp tall, colors shift with permission state
- **Activity Log**: scrollable `LazyColumn` inside a `Card`; auto-scrolls to the latest entry

### Runtime Permissions (`PermissionUtils.kt`)
```kotlin
fun requiredPermissions(): Array<String>          // version-aware permission list
fun allPermissionsGranted(context: Context): Boolean
@Composable fun rememberPermissionLauncher(...)    // Compose-friendly launcher wrapper
```
`MainActivity` calls `rememberPermissionLauncher` and fires `LaunchedEffect(Unit)` to request permissions on first launch.

---

## Phase 2 — Roadmap

| # | Feature | Key Class / File |
|---|---|---|
| 2a | NFC NDEF payload construction in HCE | `BoopHceService.kt` |
| 2b | NFC foreground dispatch & NDEF parsing on Receiver | `MainActivity.kt` + new `NfcManager` |
| 2c | Wi-Fi Direct P2P group negotiation | New `WifiDirectManager.kt` |
| 2d | TCP socket file transfer (Sender server / Receiver client) | New `TransferManager.kt` |
| 2e | MediaStore file picker (Sender) | New `FilePicker.kt` |
| 2f | MediaStore write (Receiver) | Inside `TransferManager.kt` |
| 2g | Progress UI & transfer state | Extend `HomeScreen.kt` + `TransferViewModel` |

All async networking must use **Kotlin Coroutines and Flows**. No callbacks.

---

## Building the App

```bash
# Debug build (requires Android SDK; dl.google.com must be reachable)
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device or emulator)
./gradlew connectedAndroidTest
```

> **Note:** `dl.google.com` is required to download Android SDK components. In a sandboxed CI environment this domain must be allowlisted.

---

## Permissions

All permissions are declared in `AndroidManifest.xml` and requested at runtime by `PermissionUtils.kt`. The app gracefully degrades — UI buttons are disabled and log entries explain what is missing until all permissions are granted.
