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
- **Jetpack Navigation Compose 2.7.7** — multi-screen navigation with bottom nav
- **Kotlin Coroutines & Flows** — no raw threads or callbacks
- **Room 2.6.1 + KSP 2.0.0** — local SQLite persistence for transfer history and friends list
- **Gradle 8.6 / AGP 8.3.2** — version catalog in `gradle/libs.versions.toml`
- **minSdk 26 / compileSdk 34**

## Architecture

**MVVM with Compose + Navigation.** `TransferViewModel` is the central orchestrator. It owns `WifiDirectManager`, observes Wi-Fi Direct and NFC state via `StateFlow`, and exposes a single `TransferUiState` to the UI layer. `SettingsViewModel` manages user preferences via SharedPreferences.

### Navigation

`MainActivity` hosts `BoopTheme > Surface > BoopScaffold`. The scaffold contains:
- **Bottom nav bar** (`BoopBottomNavBar`): Home, History, Profile — visible on all screens except NFC Guide dialog
- **`BoopNavHost`**: Maps routes to screen composables via Jetpack Navigation Compose
- **Overlays**: NFC payload BottomSheet, error AlertDialog, NFC/Wi-Fi/Hotspot warning dialogs (transient, not navigation destinations)
- **Auto-navigation**: `LaunchedEffect` observes `isTransferring` → navigates to TransferProgressScreen; NFC guide auto-shows on first NFC activation via SharedPreferences

Routes are defined in `BoopRoute` sealed class: Home, History, Profile, TransferProgress, NfcGuide, Settings.

### Transfer Flow

1. **Sender** taps "Boop it" → multi-file picker opens → files picked → `prepareSend()` creates Wi-Fi Direct group → `BoopHceService` (HCE) emits NDEF with MAC, TCP port, SSID, token, and `fileCount` as JSON. Also triggered via Android share sheet (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`). No explicit mode toggle — cancelling picker stays in receive mode
2. **Receiver** reads NDEF via `NfcReader` (reader mode or foreground dispatch) → extracts `ConnectionDetails` → checks receive permission: "friends" auto-accepts known SSIDs, "no_one" shows Accept/Reject BottomSheet → on accept, proceeds with Wi-Fi Direct connection
3. Wi-Fi Direct group join: Sender = Group Owner (GO IP: `192.168.49.1`), Receiver joins via SSID + passphrase using `WifiP2pConfig.Builder` (API 29+)
4. TCP socket stream: Sender runs `ServerSocket`, Receiver connects and writes to MediaStore
5. **Single-file wire format**: `[nameLen][name][size][mimeLen][mime][bytes...]` in 16 KB chunks
6. **Multi-file wire format**: `[fileCount: Int32]` header, then per-file: `[nameLen][name][size][mimeLen][mime][bytes...]`

### Design System — "Solid Geometric"

- **Theme**: Dark mode (pure black #000000), light mode (purple-dominant #736DEE). Brand purple #736DEE, accent yellow #F8FFA3
- **Dark mode**: Black background, white text, yellow accent, purple primary buttons
- **Light mode**: Purple-dominant background, white text + yellow accent. White primary buttons with purple text. Both themes have light content on dark-ish backgrounds
- **Typography**: Plus Jakarta Sans (primary), Space Grotesk (numbers/monospace)
- **Components**: `NeoBrutalistButton` (box-shadow offset), `GlassCard` (semi-transparent + border), `boopGlow` modifier
- **Extended tokens**: `LocalBoopTokens` CompositionLocal for non-M3 design tokens (accent, glow, glass, card, pill, concentric-circle, nav-bar colors). No `isDark` boolean — each token carries the correct color for the active theme
- **Haptic feedback**: `BoopHaptics` utility (`utils/BoopHaptics.kt`) with tick/click/heavy levels, gated by `LocalHapticsEnabled` CompositionLocal (respects in-app vibration toggle). Use `rememberBoopHaptics()` in composables
- **No dynamic color**: Brand-specific palette always used; `dynamicColor` parameter removed from `BoopTheme`
- **Theme toggle**: Dark/light mode controlled via `SettingsViewModel.setDarkMode()`, wired through `BoopTheme(darkTheme = settingsState.darkModeEnabled)`

### Key Components

| Component | Role |
|---|---|
| `MainActivity` | Entry point, NFC foreground dispatch + cold-start intent parsing, reader mode state observer, permission launcher, share intent handler (`ACTION_SEND`/`ACTION_SEND_MULTIPLE`), hotspot detection, Compose host |
| `nfc/BoopHceService` | HCE service implementing two protocols: (1) NDEF Type 4 Tag (AID: `D2760000850101`) for cold-start NFC discovery — Android reads NDEF via CC+NDEF file SELECT/READ BINARY sequence; (2) Proprietary Boop AID (`F0426F6F7001`) for foreground reader mode — returns NDEF bytes directly. State machine tracks `SelectedApp` (NONE/BOOP/NDEF_TAG) and `SelectedFile` (NONE/CC/NDEF) |
| `nfc/NfcReader` | NFC reader mode + foreground dispatch; parses NDEF → `ConnectionDetails` |
| `wifi/WifiDirectManager` | Coroutine-friendly wrapper around `WifiP2pManager`; exposes `StateFlow<WifiDirectState>` |
| `transfer/TransferManager` | Singleton with `sendFile()` / `receiveFile()` + `sendFiles()` / `receiveFiles()` (multi-file); returns `Flow<TransferProgress>` (includes `fileName`, `mimeType`, `fileIndex`, `totalFiles` on completion) |
| `data/BoopDatabase` | Room database singleton (`boop_database`), holds `TransferHistoryEntity` |
| `data/TransferHistoryDao` | Room DAO: `insert()`, `getAll()` (as Flow), `deleteOlderThan()` |
| `data/TransferHistoryEntity` | Room entity for persisted transfer history records |
| `data/FriendEntity` | Room entity for friends list — SSID, displayName, timestamps, transferCount |
| `data/FriendDao` | Room DAO for friends: insert, getBySsid, updateLastSeen, deleteById |
| `ui/viewmodels/TransferViewModel` | Owns full transfer pipeline; produces `TransferUiState`; defaults to receive mode; `resetToReceive()` re-arms NFC after transfer; observes Room for history; `startSendingMultiple()` for multi-file; hotspot warning management; sender file URI persisted in history; friends auto-save on receive; approval gate (`pendingApproval`, `approveIncomingTransfer()`, `rejectIncomingTransfer()`) |
| `ui/viewmodels/SettingsViewModel` | SharedPreferences-backed settings (notifications, vibration, sound, display name, dark mode, receive permission) |
| `ui/navigation/BoopScaffold` | Top-level scaffold with bottom nav, overlays (payload sheet with optional Accept/Reject buttons, error dialog, NFC/Wi-Fi/Hotspot warning dialogs), auto-navigation with 1s post-transfer delay + reset to receive |
| `ui/navigation/BoopNavHost` | NavHost mapping routes to screen composables |
| `ui/navigation/BoopNavigation` | Route definitions (`BoopRoute` sealed class) |
| `ui/screens/HomeScreen` | Header with NFC icon + chai button, "Ready to Boop?" display, morphing aurora blob CTA (8-point Bezier + sweep gradient), recent boops with "View All" navigation. "Boop it" always opens multi-file picker — no explicit mode toggle |
| `ui/screens/TransferProgressScreen` | Full-screen transfer progress with percentage, progress bar, file card, "File X of Y" counter (multi-file), cancel button |
| `ui/screens/NfcGuideScreen` | Full-screen NFC antenna guide dialog with phone visualization and "Got it" dismiss |
| `ui/screens/SettingsScreen` | Functional settings with toggles for notifications, vibration, sound, dark mode + editable display name + receive permission picker + About section with "Buy me a Chai" UPI button |
| `ui/screens/HistoryScreen` | Transfer history (last 30 days) with direction + file-type filter chips, tap to open file, share button to re-send via Boop |
| `ui/screens/ProfileScreen` | Stub screen for Profile tab |
| `ui/components/NfcAntennaGuide` | Canvas visualization of NFC antenna location using `getNfcAntennaInfo()` (API 34+) with fallback |
| `ui/theme/BoopDesignSystem` | Neo-brutalist components: `NeoBrutalistButton`, `GlassCard`, `boopGlow`, `BoopBottomNavBar`, design tokens |
| `ui/theme/Color` | Brand colors (purple, yellow), surface tones (dark/light), glass/glow colors |
| `ui/theme/Type` | Plus Jakarta Sans + Space Grotesk font families, `BoopTypography` |
| `ui/theme/Theme` | `BoopTheme` composable, dark/light color schemes, `LocalBoopTokens` CompositionLocal |
| `ui/models/LogEntry` | Data class for activity log entries |
| `ui/models/RecentBoop` | Data class for recent transfer records (persisted via Room) |
| `utils/PermissionUtils` | Version-aware runtime permission helpers (API 26–34 differences) |
| `utils/FilePicker` | Compose wrappers: `rememberFilePicker` (`OpenDocument`), `rememberMultiFilePicker` (`OpenMultipleDocuments`); resolves file metadata |
| `utils/BoopHaptics` | Haptic feedback utility: `BoopHaptics` class (tick/click/heavy), `LocalHapticsEnabled` CompositionLocal |

## Coding Conventions

- **Version catalog first** — every dependency goes in `gradle/libs.versions.toml` before `build.gradle.kts`
- **Scoped storage only** — all file I/O through MediaStore API; never use `Environment.getExternalStorageDirectory()`
- **Exhaustive logging** — every class has `private const val TAG` + `Log.d(TAG, ...)`. NFC and Wi-Fi Direct fail silently, so log every state transition
- **Sealed classes for states** — `NfcReaderState`, `WifiDirectState`, `BoopRoute`, etc. with exhaustive `when` expressions
- **Bold UI** — `FontWeight.ExtraBold` for display, `FontWeight.Bold` for headlines; Plus Jakarta Sans primary font
- **Compose state in ViewModels** — `MutableStateFlow` internally, exposed as `StateFlow`; no mutable Android objects in composables
- **Design tokens via CompositionLocal** — non-M3 tokens (accent, glow, glass) via `LocalBoopTokens`
- **Neo-brutalist components** — use `NeoBrutalistButton`, `GlassCard`, `boopGlow` from `BoopDesignSystem.kt`

## Common Pitfalls

- **NEARBY_WIFI_DEVICES** required on API 33+ instead of `ACCESS_FINE_LOCATION` for Wi-Fi Direct — both paths must be handled
- **HCE is one-way** — Sender can only respond to commands; Receiver always initiates the APDU exchange
- **Group Owner IP** is always `192.168.49.1` on Android; do not discover dynamically
- **Wi-Fi Direct MAC is anonymized** on Android 10+ — `group.owner.deviceAddress` returns `02:00:00:00:00:00` (a placeholder). Receiver must connect via SSID + passphrase using `WifiP2pConfig.Builder`, NOT by MAC address
- **`flow {}` cannot emit from `withContext(Dispatchers.IO)`** — use `channelFlow {}` with `send()` for cross-context emission (TransferManager)
- **`requestGroupInfo` may return null** right after `createGroup` onSuccess — retry with delay (150ms × 5 attempts) to wait for the group to be fully provisioned
- **Stale Wi-Fi Direct groups** survive app crashes — `WifiDirectManager.reset()` now calls `removeGroup()` before setting Idle. `connect()` parallelizes `cancelConnect()` + `removeGroup()` for faster cleanup
- **`joinExistingGroup` flag** — On Samsung firmware, `WifiP2pConfig.Builder` defaults `joinExistingGroup=false`, causing `connect()` to be dropped. Set it to `true` via reflection when joining a Sender's group (Receiver side)
- **Duplicate NFC callbacks** — reader mode and foreground dispatch both fire for the same tap. Guard `onNfcPayloadReceived` with state checks; ignore payloads in Send mode
- **MediaStore.Downloads** is API 29+ — use `Environment.getExternalStoragePublicDirectory()` with `WRITE_EXTERNAL_STORAGE` on API 26–28
- **`dl.google.com`** must be allowlisted in sandboxed CI for SDK component downloads
- **Android NFC classes are stubs in JVM unit tests** — use `NfcReader.parsePayloadJson()` for direct JSON parsing tests; `NdefMessage`/`NdefRecord` require Robolectric or instrumented tests
- **`LinearProgressIndicator` API** — M3 1.2+ requires `progress` as a lambda `() -> Float`, not a bare Float
- **`Icons.AutoMirrored`** — some icons moved to `Icons.AutoMirrored.Filled` in M3; use explicit imports from `automirrored.filled` package
- **Default receive mode** — `TransferUiState` defaults to `isReceiveMode = true, isNfcReading = true`. The app starts ready to receive. NFC reader mode is auto-enabled via `LaunchedEffect(uiState.isNfcReading, permissionsGranted)` in `MainActivity`
- **Successive transfers** — after transfer completion, `BoopScaffold` calls `onResetToReceive()` (after 1s delay) which invokes `TransferViewModel.resetToReceive()`. `resetToReceive()` calls `wifiDirectManager.reset()` (which calls `removeGroup()`) then re-arms NFC reader mode for the next tap
- **Bottom nav saveState/restoreState** — always `true` for tab-to-tab navigation; conditional logic caused tabs to stop responding
- **NFC/Wi-Fi/Hotspot startup check** — `MainActivity.onCreate` and `onResume` check NFC/Wi-Fi enabled and hotspot state, setting `nfcDisabledWarning`/`wifiDisabledWarning`/`hotspotWarning` on `TransferUiState`. `BoopScaffold` shows AlertDialogs with "Open Settings" actions. Hotspot detection uses `WifiManager.isWifiApEnabled` via reflection
- **NFC cold start** — `MainActivity.onCreate` parses the launch intent for NFC data via `nfcReader.parseIntent(intent)` so NFC taps that open the app from scratch are handled
- **Sender file URI in history** — `startSending()` takes a persistable URI permission and stores `senderFileUri` in `TransferUiState`. On completion, history records use this URI for sender entries, enabling the re-send button
- **Multi-file transfer** — `ConnectionDetails.fileCount` and `BoopHceService.connectionFileCount` coordinate multi-file awareness. Wire format prepends a `fileCount` Int32 header. `TransferManager.sendFiles()`/`receiveFiles()` handle sequential files. `TransferProgress` includes `fileIndex`/`totalFiles`
- **Share sheet integration** — `AndroidManifest.xml` declares `ACTION_SEND` + `ACTION_SEND_MULTIPLE` intent filters. `MainActivity.handleShareIntent()` processes incoming URIs in both `onCreate` and `onNewIntent`
- **Room schema export** — `exportSchema = false` on `BoopDatabase`; no schema JSON files generated
- **TCP stream consistency** — `streamBytes()` must use the SAME wrapped stream (`DataOutputStream`/`DataInputStream`) as `writeHeader()`/`readHeader()`. Using a fresh `socket.getOutputStream()`/`socket.getInputStream()` bypasses the buffer, causing data corruption. `streamBytes()` also reads exactly `totalSize` bytes (not until EOF) so multi-file transfers don't read past file boundaries
- **NFC TECH_DISCOVERED** — manifest declares both `NDEF_DISCOVERED` and `TECH_DISCOVERED` intent filters with `nfc_tech_filter.xml` (IsoDep) so the app launches on HCE tap even when not running
- **NFC cold-start via Type 4 Tag** — `BoopHceService` implements NDEF Type 4 Tag (AID `D2760000850101`) so Android's NFC stack reads the NDEF message during discovery. This fires `NDEF_DISCOVERED` with payload in intent extras, even when the Receiver app is closed. The proprietary Boop AID path still works for foreground reader mode
- **Multi-file history** — `handleMultiFileProgress()` detects per-file completion (`fileName != null && bytesTransferred == totalBytes`) and inserts a history entry for each file. For sender, uses `pendingFileUris[fileIndex]` for the correct per-file URI instead of `senderFileUri` (which only holds the first file's URI)
- **History filter labels** — "Direction" and "File Type" labels precede their respective filter chip rows for clarity
- **Auto-infer send/receive mode** — no explicit mode toggle. "Boop it" always opens the file picker. Picking files calls `prepareSend()` then `startSending()`/`startSendingMultiple()`. Cancelling the picker stays in default receive mode. App always listens for NFC in receive mode
- **Friends list** — `FriendEntity` tracks sender SSIDs. Auto-saved on receiver side at transfer completion via `saveFriendIfNeeded()`. Display name extracted from SSID via regex `DIRECT-XX-(.+)`. `currentConnectionSsid` cleared in `reset()` and `resetToReceive()`
- **Receive permission** — `SettingsViewModel.receivePermission` ("friends" or "no_one"). `TransferViewModel.onNfcPayloadReceived()` checks permission + friend status. "friends" auto-accepts known SSIDs, otherwise sets `pendingApproval` on `TransferUiState`. `BoopScaffold` shows Accept/Reject buttons in BottomSheet when `pendingApproval != null`
- **Room migration 1→2** — `MIGRATION_1_2` creates the `friends` table. Applied via `.addMigrations()` in `BoopDatabase.getInstance()`
- **UPI chai button** — "Buy me a Chai" launches `upi://pay?pa=03.shubhamshah-1@oksbi&pn=Boop&tn=Buy%20me%20a%20Chai&cu=INR` via `ACTION_VIEW` intent. Present in Home header (coffee icon) and Settings About section

## Tests

JVM unit tests live in `app/src/test/kotlin/com/shashsam/boop/`. Run with `./gradlew test`.

| Test File | Coverage |
|---|---|
| `nfc/NfcReaderTest` | JSON payload parsing, APDU builder, edge cases (12 tests) |
| `ui/viewmodels/TransferUiStateTest` | Default receive mode, mode transitions, progress, completion, error, logs, recent transfers (9 tests) |
| `utils/FormattedSizeTest` | B/KB/MB/GB boundaries and values (8 tests) |
| `ui/navigation/BoopRouteTest` | Route strings, uniqueness across all 6 routes (7 tests) |
| `ui/viewmodels/SettingsUiStateTest` | Defaults, toggle copies, display name, dark mode, equality (7 tests) |
| `ui/models/RecentBoopTest` | Constructor fields, copy, equality, wasSender flag (5 tests) |
| `ui/models/LogEntryTest` | Default isError, error flag, equality (3 tests) |
| `ExampleUnitTest` | Sanity check, permission list non-empty (2 tests) |

**Note:** `SettingsViewModel` requires `Application` context — tested indirectly via `SettingsUiState` data class tests. `TransferViewModel` similarly requires Android framework; business logic tested via `TransferUiState` state transitions.

## Key Constants

| Constant | Value |
|---|---|
| HCE AID (Boop) | `F0426F6F7001` |
| HCE AID (NDEF Type 4) | `D2760000850101` |
| NDEF MIME type | `application/com.shashsam.boop` |
| NDEF JSON fields | `mac`, `port`, `ssid`, `token`, `fileCount` |
| Wi-Fi Direct GO IP | `192.168.49.1` |
| TCP transfer port | `8765` |
| Settings SharedPrefs | `boop_settings` (keys: notifications_enabled, vibration_enabled, sound_enabled, display_name, dark_mode_enabled, receive_permission) |
| UPI Payee | `03.shubhamshah-1@oksbi` |
| Room DB version | 2 (migration 1→2 adds `friends` table) |
| NFC Guide SharedPrefs | `boop_prefs` / `nfc_antenna_guide_seen` |


# Review

All the code changes you make will be reviewed by codex so maintain a thorough code quality.

# Update

Aggressively update the claude.md file after every session of edit
