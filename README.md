# Boop

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
│  User picks files       │            │  App in NFC reader mode │
│  (multi-file picker)    │            │                         │
│          │              │            │          │              │
│  BoopHceService (HCE)   │◄── NFC ───►│  NFC foreground dispatch│
│  sends NDEF payload:    │    tap     │  receives connection    │
│  • SSID + passphrase    │            │  details + sender ULID  │
│  • TCP port             │            │          │              │
│  • file count + ULID    │            │  Permission check:      │
│          │              │            │  auto-accept / prompt   │
│  WifiP2pManager (GO)    │◄─ Wi-Fi ──►│  WifiP2pManager (peer) │
│  TCP Server Socket      │  Direct    │  TCP Client Socket      │
│  streams file bytes     │            │  writes to MediaStore   │
│          │              │            │          │              │
│  Friend exchange        │◄── TCP ───►│  Friend exchange        │
│  (optional, post-xfer)  │            │  (optional, post-xfer)  │
└─────────────────────────┘            └─────────────────────────┘
```

### Flow Summary

1. **Sender** picks files (via multi-file picker or Android share sheet) and activates HCE. The NDEF payload carries the Wi-Fi Direct group SSID, passphrase, TCP port, file count, display name, and sender's persistent ULID as JSON.
2. **Receiver** reads the NFC payload via reader mode or foreground dispatch and checks receive permission: auto-accepts known friends (by ULID), otherwise shows a 3-button approval sheet (Accept / Accept + Become Friends / Reject).
3. **Receiver** joins the Sender's Wi-Fi Direct group via SSID + passphrase using `WifiP2pConfig.Builder` (API 29+). Sender is Group Owner.
4. A **TCP socket** stream carries raw file bytes from Sender to Receiver in 256 KB chunks with per-file progress. Multi-file transfers prepend a file count header.
5. The Receiver writes incoming bytes to **MediaStore** (scoped storage) in the Downloads folder.
6. **Friend exchange** (optional): after file transfer, the receiver can initiate a bidirectional profile exchange over the same TCP socket, sharing ULIDs, display names, profile items, and profile pictures.

---

## Features

- **NFC-brokered connection** — Sender broadcasts Wi-Fi Direct credentials via HCE (dual AID: proprietary + NDEF Type 4 Tag for cold-start); Receiver reads via NFC reader mode or foreground dispatch
- **Multi-file transfer** — pick and send multiple files at once with per-file progress tracking
- **Android share sheet** — share any file from any app directly to Boop via `ACTION_SEND` / `ACTION_SEND_MULTIPLE`
- **Friends system** — opt-in friend list with ULID-based identity. Auto-accept transfers from friends, auto-refresh profiles on each encounter
- **Profile & bento grid** — customizable profile with display name, profile picture, and a bento grid of links, emails, and phone numbers (up to 12 items). Share profiles via NFC tap
- **Transfer history** — persistent history (Room database) with direction and file-type filters, tap to open, share button to re-send
- **Encrypted backup** — export/import profile, friends, and history as a password-protected `.boop` file (AES-256-GCM with PBKDF2 key derivation)
- **Dark/light theme** — neo-brutalist design system. Dark: black + yellow accent. Light: purple-dominant + white accent. Aurora blob CTA stays yellow in both. Plus Jakarta Sans typography
- **NFC antenna guide** — Canvas visualization of the phone's NFC sweet spot using `getNfcAntennaInfo()` (API 34+) with fallback
- **Scoped storage** — all file I/O through MediaStore API; received files saved to Downloads

---

## Tech Stack

| Concern | Technology |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material Design 3 |
| Navigation | Jetpack Navigation Compose 2.7.7 |
| Async | Kotlin Coroutines & Flows |
| Persistence | Room 2.6.1 + KSP 2.0.0 |
| Image loading | Coil 2.6.0 |
| NFC (Sender) | Host-Based Card Emulation (HCE) — `HostApduService` |
| NFC (Receiver) | NFC reader mode + foreground dispatch |
| P2P Connection | Wi-Fi Direct — `WifiP2pManager` + `WifiP2pConfig.Builder` |
| File Transfer | TCP/IP sockets (256 KB chunks, 512 KB buffers) |
| Backup Crypto | AES-256-GCM, PBKDF2WithHmacSHA256 (javax.crypto) |
| Storage | Android MediaStore API (scoped storage) |
| Min SDK | 26 (Android 8.0 Oreo) |
| Target SDK | 34 (Android 14) |
| Build | Gradle 8.6, AGP 8.3.2, version catalog |

---

## Project Structure

```
app/src/main/
├── AndroidManifest.xml
├── kotlin/com/shashsam/boop/
│   ├── MainActivity.kt                # Entry point, NFC dispatch, share intent handler
│   ├── backup/
│   │   ├── BackupCrypto.kt            # AES-256-GCM encrypt/decrypt with PBKDF2
│   │   ├── BackupSerializer.kt        # JSON serialization for backup data
│   │   └── BackupManager.kt           # Orchestrates full export/import (Room + files)
│   ├── data/
│   │   ├── BoopDatabase.kt            # Room database singleton (v5, 4 migrations)
│   │   ├── TransferHistoryDao.kt      # DAO for transfer history
│   │   ├── TransferHistoryEntity.kt   # History entity (fileName, size, mime, peer ULID)
│   │   ├── FriendDao.kt               # DAO for friends (upsert by ULID)
│   │   ├── FriendEntity.kt            # Friend entity (ULID, profile, timestamps)
│   │   ├── ProfileItemDao.kt          # DAO for profile bento items
│   │   └── ProfileItemEntity.kt       # Profile item entity (type, label, value, size)
│   ├── nfc/
│   │   ├── BoopHceService.kt          # Dual-AID HCE (proprietary + NDEF Type 4 Tag)
│   │   └── NfcReader.kt               # NFC reader mode + foreground dispatch
│   ├── transfer/
│   │   ├── TransferManager.kt         # TCP send/receive with channelFlow progress
│   │   └── FriendExchange.kt          # Wire format for bidirectional friend exchange
│   ├── wifi/
│   │   └── WifiDirectManager.kt       # Wi-Fi Direct state machine
│   ├── ui/
│   │   ├── components/
│   │   │   ├── BentoGrid.kt           # 4-column profile grid (half/full items)
│   │   │   ├── ProfileItemDialog.kt   # Add/edit profile item dialog
│   │   │   └── NfcAntennaGuide.kt     # Canvas NFC antenna visualization
│   │   ├── models/
│   │   │   ├── LogEntry.kt            # Activity log entry
│   │   │   └── RecentBoop.kt          # Recent transfer record
│   │   ├── navigation/
│   │   │   ├── BoopScaffold.kt        # Top-level scaffold, overlays, auto-navigation
│   │   │   ├── BoopNavHost.kt         # Route → screen mapping
│   │   │   └── BoopNavigation.kt      # Route definitions (BoopRoute sealed class)
│   │   ├── screens/
│   │   │   ├── HomeScreen.kt          # Aurora blob CTA, recent boops
│   │   │   ├── HistoryScreen.kt       # Filtered transfer history
│   │   │   ├── ProfileScreen.kt       # Profile card, bento grid, friends list
│   │   │   ├── SettingsScreen.kt      # Toggles, receive permission, export/import
│   │   │   ├── TransferProgressScreen.kt # Transfer progress with rotating ring
│   │   │   ├── FriendProfileScreen.kt # Read-only friend profile view
│   │   │   └── NfcGuideScreen.kt      # NFC antenna guide dialog
│   │   ├── viewmodels/
│   │   │   ├── TransferViewModel.kt   # Central orchestrator
│   │   │   ├── ProfileViewModel.kt    # Profile items + profile pic
│   │   │   ├── SettingsViewModel.kt   # SharedPreferences-backed settings
│   │   │   └── BackupViewModel.kt     # Export/import state management
│   │   └── theme/
│   │       ├── BoopDesignSystem.kt    # Neo-brutalist components + design tokens
│   │       ├── Color.kt               # Brand colors, surface tones
│   │       ├── Theme.kt               # BoopTheme, dark/light schemes
│   │       └── Type.kt                # Plus Jakarta Sans + Space Grotesk
│   └── utils/
│       ├── BoopHaptics.kt             # Haptic feedback utility
│       ├── FilePicker.kt              # Compose file picker wrappers
│       ├── PermissionUtils.kt         # Version-aware permission helpers
│       ├── SocialIcons.kt             # Social platform icon resolver
│       └── Ulid.kt                    # ULID generator + persistent user ULID
└── res/
    └── xml/
        ├── apduservice.xml            # HCE AID filter
        └── nfc_tech_filter.xml        # IsoDep tech filter
```

---

## Color System

Boop uses a dual-theme "Solid Geometric" design system. Dark mode is black with yellow accents; light mode is purple-dominant with white accents. No dynamic color — brand palette is always used.

### M3 Color Scheme

| Role | Dark | Light | Usage |
|---|---|---|---|
| `primary` | `#736DEE` Purple | `#FFFFFF` White | NeoBrutalistButton bg |
| `onPrimary` | `#FFFFFF` | `#4B45B8` PurpleDark | Button text |
| `primaryContainer` | `#4B45B8` | `#EEEEFF` | — |
| `secondary` | `#9B97F5` PurpleLight | `#F8FFA3` Yellow | — |
| `background` | `#000000` Black | `#736DEE` Purple | Screen bg, status bar |
| `onBackground` | `#FFFFFF` | `#FFFFFF` | Headings, primary text |
| `surface` | `#111111` | `#6862E0` | — |
| `onSurface` | `#FFFFFF` | `#FFFFFF` | — |
| `surfaceVariant` | `#1A1A1A` | `#5D57D0` | Disabled button bg |
| `onSurfaceVariant` | `#B0B0B0` | `#D0CDFF` | Cancel text, dialog body |
| `outline` | `#777777` | `#ADA9E0` | Disabled borders |
| `error` | `#CF6679` | `#CF6679` | Remove friend, error dialogs |

### Extended Tokens (`LocalBoopTokens`)

| Token | Dark | Light | Usage |
|---|---|---|---|
| `accent` | `#F8FFA3` Yellow | `#FFFFFF` White | Icons, "View All", add button, BentoGrid icons |
| `glowColor` | `#F8FFA3` 15% | `#FFFFFF` 15% | `boopGlow` modifier |
| `glassBg` | `#FFFFFF` 12% | `#FFFFFF` 15% | GlassCard background |
| `glassBorder` | `#FFFFFF` 20% | `#FFFFFF` 20% | GlassCard border, dividers |
| `cardBackground` | `#1E1E1E` | `#FFFFFF` 10% | — |
| `textSecondary` | `#B0B0B0` | `#FFFFFF` 80% | Subtitles, stats, bio |
| `textTertiary` | `#777777` | `#FFFFFF` 50% | Placeholder text, chevrons |
| `pillContainer` | `#1A1A1A` | `#5D57D0` | Mode pill bg |
| `pillActive` | `#2A2A2A` | `#8A85F5` | Active pill segment |
| `concentricDashed` | `#333333` | `#9590F0` | HomeScreen dashed ring |
| `concentricOuter` | `#222222` | `#6560D8` | HomeScreen outer circle |
| `concentricInner` | `#1A1A1A` | `#5D57D0` | HomeScreen inner circle |
| `navBarContainer` | `#000000` | `#5D57D0` | Bottom nav bg |
| `navIndicator` | `#2A2A2A` | `#5550C0` | Bottom nav active indicator |
| `dialogSurface` | `#111111` | `#1A1A2E` Deep navy | AlertDialog bg |

### Standalone Colors

| Color | Hex | Usage |
|---|---|---|
| `BoopBrandPurple` | `#736DEE` | Dialog icons, text field focus, ProfileItemDialog selected state |
| `BoopAccentYellow` | `#F8FFA3` | HomeScreen aurora blob CTA (hardcoded, not theme-dependent) |
| `WarningAmber` | `#FFB74D` | HomeScreen warning icon |
| `SocialInstagram` | `#E1306C` | Social icon brand color |
| `SocialLinkedIn` | `#0A66C2` | Social icon brand color |
| `SocialYouTube` | `#FF0000` | Social icon brand color |
| `SocialFacebook` | `#1877F2` | Social icon brand color |

> **Note:** The "Boop it" aurora blob always uses `BoopAccentYellow` directly — it stays yellow in both themes. The `tokens.accent` (white in light mode) applies to UI chrome: icons, labels, and interactive elements.

---

## Building the App

```bash
# Debug build (requires Android SDK; dl.google.com must be reachable)
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device or emulator)
./gradlew connectedAndroidTest

# Install on connected device
./gradlew installDebug
```

> **Note:** `dl.google.com` is required to download Android SDK components. In a sandboxed CI environment this domain must be allowlisted.

---

## Permissions

All permissions are declared in `AndroidManifest.xml` and requested at runtime by `PermissionUtils.kt`:

- **NFC** — required for connection brokering
- **NEARBY_WIFI_DEVICES** (API 33+) / **ACCESS_FINE_LOCATION** (API < 33) — required for Wi-Fi Direct
- **READ/WRITE_EXTERNAL_STORAGE** (API < 29) — scoped storage handles this on newer versions

The app checks NFC, Wi-Fi, and hotspot state on startup and resume, showing warning dialogs with "Open Settings" actions when needed.
