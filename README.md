# Boop 📲

> **"Just boop that file"** — AirDrop-style P2P file sharing for Android.

Boop lets two Android devices share files by simply tapping them together. An NFC tap brokers the connection; the actual transfer happens over a high-speed Wi-Fi Direct link using TCP sockets — no internet required.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Features](#features)
3. [Tech Stack](#tech-stack)
4. [Project Structure](#project-structure)
5. [Building the App](#building-the-app)
6. [Permissions](#permissions)

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

1. **Sender** picks a file (via file picker or Android share sheet) and activates HCE via `BoopHceService`. The NDEF message carries the Wi-Fi Direct group SSID, passphrase, MAC, and TCP port as JSON.
2. **Receiver** reads the NFC payload via reader mode or foreground dispatch and extracts the connection parameters.
3. **Receiver** joins the Sender's Wi-Fi Direct group using SSID + passphrase via `WifiP2pConfig.Builder` (API 29+). The Sender acts as Group Owner (GO).
4. A **TCP socket** stream carries the raw file bytes from Sender → Receiver in 16 KB chunks with progress reporting.
5. The Receiver writes the incoming bytes to **MediaStore** (scoped storage) in the Downloads folder.
6. If the Receiver does not have Boop installed, the **Android Application Record (AAR)** in the NDEF message redirects them to the Play Store.

---

## Tech Stack

| Concern | Technology |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material Design 3 |
| Async | Kotlin Coroutines & Flows |
| NFC (Sender) | Host-Based Card Emulation (HCE) — `HostApduService` |
| NFC (Receiver) | NFC reader mode + foreground dispatch |
| P2P Connection | Wi-Fi Direct — `WifiP2pManager` + `WifiP2pConfig.Builder` |
| File Transfer | TCP/IP sockets (byte streams) |
| Storage | Android MediaStore API (scoped storage) |
| Min SDK | 26 (Android 8.0 Oreo) |
| Target SDK | 34 (Android 14) |
| Build | Gradle 8.6, AGP 8.3.2 |

---

## Features

- **NFC-brokered connection** — Sender broadcasts Wi-Fi Direct credentials via HCE; Receiver reads via NFC reader mode or foreground dispatch
- **Wi-Fi Direct file transfer** — high-speed P2P transfer over TCP sockets with 16 KB chunking and real-time progress
- **Android share sheet integration** — share any file from any app (Contacts, Gallery, Files, etc.) directly to Boop via `ACTION_SEND`
- **NFC antenna location guide** — Canvas visualization of the phone's NFC sweet spot using `getNfcAntennaInfo()` (API 34+) with fallback; auto-shows on first share, manually toggleable after
- **Connection timeout & error handling** — 10s Wi-Fi Direct timeout with `CircularProgressIndicator`, green checkmark on connect, M3 `AlertDialog` for errors
- **Transfer progress** — `LinearProgressIndicator` with bytes transferred display
- **Scoped storage** — all file I/O through MediaStore API; received files saved to Downloads

## Project Structure

```
app/src/main/
├── AndroidManifest.xml
├── kotlin/com/shashsam/boop/
│   ├── MainActivity.kt              # Entry point, NFC dispatch, share intent handler
│   ├── nfc/
│   │   ├── BoopHceService.kt        # HCE service — NDEF payload with SSID/token/MAC/port
│   │   └── NfcReader.kt             # NFC reader mode + foreground dispatch → ConnectionDetails
│   ├── transfer/
│   │   └── TransferManager.kt       # TCP send/receive with channelFlow progress emission
│   ├── wifi/
│   │   └── WifiDirectManager.kt     # Wi-Fi Direct state machine, SSID+passphrase connect
│   ├── ui/
│   │   ├── components/
│   │   │   └── NfcAntennaGuide.kt   # Canvas NFC antenna visualization with pulsing ripple
│   │   ├── screens/
│   │   │   └── HomeScreen.kt        # Full home UI — status, FABs, progress, log, dialogs
│   │   ├── viewmodels/
│   │   │   └── TransferViewModel.kt # Central orchestrator — NFC → Wi-Fi Direct → TCP
│   │   └── theme/
│   │       ├── Color.kt             # M3 color palette (Purple/Teal/Rose + SuccessGreen)
│   │       ├── Theme.kt             # BoopTheme (dynamic color on API 31+)
│   │       └── Type.kt              # Bold typography scale
│   └── utils/
│       ├── FilePicker.kt            # Compose file picker + metadata resolver
│       └── PermissionUtils.kt       # Version-aware runtime permission helpers
└── res/
    └── xml/
        └── apduservice.xml          # HCE AID filter (F0426F6F7001)
```

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
