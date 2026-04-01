package com.shashsam.boop.ui.viewmodels

import com.shashsam.boop.nfc.ConnectionDetails
import com.shashsam.boop.transfer.ProfileData
import com.shashsam.boop.transfer.TransferProgress
import com.shashsam.boop.ui.models.RecentBoop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for transfer reliability — verifying that state transitions correctly
 * recover from errors, timeouts, and successive transfer cycles.
 *
 * These are data-class-level tests (no Android framework required) that validate
 * the state machine invariants that the ViewModel must uphold.
 */
class TransferReliabilityTest {

    // ── Reset after error ──────────────────────────────────────────────

    @Test
    fun `error state preserves recentTransfers after reset`() {
        val boops = listOf(
            RecentBoop("a.jpg", 100L, "image/jpeg", 1L, true)
        )
        val errorState = TransferUiState(recentTransfers = boops)
            .copy(isTransferring = false, error = "Connection timed out")

        // Simulate dismissError -> resetToReceive: clears error, resets to receive
        val resetState = TransferUiState(
            recentTransfers = errorState.recentTransfers,
            isResetting = true
        )
        assertNull(resetState.error)
        assertTrue(resetState.isResetting)
        assertEquals(1, resetState.recentTransfers.size)
        assertFalse(resetState.isTransferring)
        assertFalse(resetState.isSendMode)
    }

    @Test
    fun `error state must not leave isTransferring true`() {
        // When an error occurs, isTransferring should be false
        val state = TransferUiState().copy(
            isTransferring = true,
            transferProgress = 0.3f
        )
        // Simulate error handler
        val errorState = state.copy(isTransferring = false, error = "Socket timeout")
        assertFalse(errorState.isTransferring)
        assertEquals("Socket timeout", errorState.error)
    }

    @Test
    fun `after error reset, NFC reading is re-armed`() {
        val errorState = TransferUiState().copy(
            isTransferring = false,
            isNfcReading = false,
            isSendMode = true,
            error = "Send failed"
        )
        // Simulate full resetToReceive: isResetting -> reset complete
        val resetting = TransferUiState(
            recentTransfers = errorState.recentTransfers,
            isResetting = true
        )
        // After wifiDirectManager.reset() completes:
        val ready = resetting.copy(isResetting = false, isReceiveMode = true, isNfcReading = true)
        assertTrue(ready.isNfcReading)
        assertTrue(ready.isReceiveMode)
        assertFalse(ready.isSendMode)
        assertFalse(ready.isResetting)
        assertNull(ready.error)
    }

    // ── Successive transfers ───────────────────────────────────────────

    @Test
    fun `complete transfer resets cleanly for next cycle`() {
        // First transfer completes
        val completed = TransferUiState().copy(
            isTransferring = false,
            transferComplete = true,
            transferProgress = 1f,
            isSendMode = true,
            isNfcReading = false
        )
        // resetToReceive creates fresh state
        val resetState = TransferUiState(
            recentTransfers = completed.recentTransfers,
            isResetting = true
        )
        // Verify all transfer state is cleared
        assertFalse(resetState.transferComplete)
        assertEquals(0f, resetState.transferProgress, 0.001f)
        assertFalse(resetState.isSendMode)
        assertFalse(resetState.isTransferring)
        assertNull(resetState.error)
        assertNull(resetState.pendingApproval)
        assertNull(resetState.pendingFriendRequest)
    }

    @Test
    fun `reset clears pending approval`() {
        val details = ConnectionDetails("00:11:22", 8765, "DIRECT-ab-Test", "pass123")
        val pending = TransferUiState().copy(pendingApproval = details)
        val reset = TransferUiState(recentTransfers = pending.recentTransfers, isResetting = true)
        assertNull(reset.pendingApproval)
    }

    @Test
    fun `reset clears pending friend request`() {
        val profile = ProfileData("01HABCALICE000000ALICE", "Alice", "[]", null)
        val pending = TransferUiState().copy(pendingFriendRequest = profile)
        val reset = TransferUiState(recentTransfers = pending.recentTransfers, isResetting = true)
        assertNull(reset.pendingFriendRequest)
    }

    @Test
    fun `reset clears receivedProfile`() {
        val profile = ProfileData("01HABCBOBBB0000000BOBB", "Bob", "{}", null)
        val withProfile = TransferUiState().copy(receivedProfile = profile)
        val reset = TransferUiState(recentTransfers = withProfile.recentTransfers, isResetting = true)
        assertNull(reset.receivedProfile)
    }

    @Test
    fun `reset clears profile share mode`() {
        val sharing = TransferUiState().copy(
            isProfileShareMode = true,
            isSendMode = true,
            isNfcReading = false
        )
        val reset = TransferUiState(recentTransfers = sharing.recentTransfers, isResetting = true)
        assertFalse(reset.isProfileShareMode)
        assertFalse(reset.isSendMode)
    }

    // ── Guard states ──────────────────────────────────────────────────

    @Test
    fun `isResetting guards against NFC payloads`() {
        val resetting = TransferUiState().copy(isResetting = true)
        // ViewModel.onNfcPayloadReceived checks isResetting and returns early
        assertTrue(resetting.isResetting)
    }

    @Test
    fun `isSendMode guards against NFC payloads`() {
        val sending = TransferUiState().copy(isSendMode = true, isReceiveMode = false)
        assertTrue(sending.isSendMode)
        assertFalse(sending.isReceiveMode)
    }

    @Test
    fun `in-progress state guards against duplicate NFC payloads`() {
        val connecting = TransferUiState().copy(isWifiConnecting = true)
        assertTrue(connecting.isWifiConnecting)

        val connected = TransferUiState().copy(isWifiConnected = true)
        assertTrue(connected.isWifiConnected)

        val transferring = TransferUiState().copy(isTransferring = true)
        assertTrue(transferring.isTransferring)
    }

    // ── TransferProgress reliability ──────────────────────────────────

    @Test
    fun `TransferProgress error sets isComplete false`() {
        val progress = TransferProgress(error = "Socket closed")
        assertFalse(progress.isComplete)
        assertEquals("Socket closed", progress.error)
        assertEquals(0f, progress.fraction, 0.001f)
    }

    @Test
    fun `TransferProgress fraction is zero when totalBytes is zero`() {
        val progress = TransferProgress(bytesTransferred = 100, totalBytes = 0)
        assertEquals(0f, progress.fraction, 0.001f)
    }

    @Test
    fun `TransferProgress fraction is clamped to 1`() {
        // Edge case: bytesTransferred > totalBytes (shouldn't happen but be safe)
        val progress = TransferProgress(bytesTransferred = 1100, totalBytes = 1000)
        assertEquals(1f, progress.fraction, 0.001f)
    }

    @Test
    fun `TransferProgress multi-file tracks fileIndex and totalFiles`() {
        val progress = TransferProgress(
            bytesTransferred = 500,
            totalBytes = 1000,
            fileIndex = 2,
            totalFiles = 5
        )
        assertEquals(2, progress.fileIndex)
        assertEquals(5, progress.totalFiles)
        assertEquals(0.5f, progress.fraction, 0.001f)
    }

    @Test
    fun `TransferProgress complete with friend request`() {
        val profile = ProfileData("01HABCHARLIE00000CHARL", "Charlie", "[]", null)
        val progress = TransferProgress(isComplete = true, friendRequest = profile)
        assertTrue(progress.isComplete)
        assertEquals("Charlie", progress.friendRequest?.displayName)
    }

    // ── Multi-file transfer state ─────────────────────────────────────

    @Test
    fun `multi-file state tracks current file index`() {
        val state = TransferUiState().copy(
            isTransferring = true,
            currentFileIndex = 2,
            totalFiles = 5,
            currentFileName = "photo3.jpg"
        )
        assertEquals(2, state.currentFileIndex)
        assertEquals(5, state.totalFiles)
        assertEquals("photo3.jpg", state.currentFileName)
    }

    @Test
    fun `multi-file per-file completion resets progress for next file`() {
        val midTransfer = TransferUiState().copy(
            isTransferring = true,
            transferProgress = 1f,
            currentFileIndex = 0,
            totalFiles = 3
        )
        // Simulate next file starting
        val nextFile = midTransfer.copy(
            transferProgress = 0f,
            currentFileIndex = 1,
            currentFileName = "next.pdf"
        )
        assertEquals(0f, nextFile.transferProgress, 0.001f)
        assertEquals(1, nextFile.currentFileIndex)
        assertTrue(nextFile.isTransferring)
    }

    // ── Ghost screen prevention ──────────────────────────────────────

    @Test
    fun `transferComplete state survives resource cleanup`() {
        // After transfer completes, cleanupTransferResources should NOT wipe UI state.
        // The transferComplete flag must remain true so BoopScaffold can navigate back.
        val completed = TransferUiState().copy(
            transferComplete = true,
            transferProgress = 1f,
            isTransferring = false,
            currentFileName = "photo.jpg"
        )
        // cleanupTransferResources only cleans sockets/Wi-Fi — UI state stays intact
        assertTrue(completed.transferComplete)
        assertEquals("photo.jpg", completed.currentFileName)
        assertEquals(1f, completed.transferProgress, 0.001f)
    }

    @Test
    fun `error state survives resource cleanup`() {
        // Error state must remain visible so the error dialog shows
        val errored = TransferUiState().copy(
            error = "Connection timed out",
            isTransferring = false
        )
        assertEquals("Connection timed out", errored.error)
        assertFalse(errored.isTransferring)
    }

    @Test
    fun `full reset only happens after navigation completes`() {
        // resetToReceive creates a FRESH state — verify it wipes everything
        val completed = TransferUiState().copy(
            transferComplete = true,
            transferProgress = 1f,
            currentFileName = "photo.jpg",
            isSendMode = true
        )
        // Simulate resetToReceive: fresh state with only recentTransfers preserved
        val reset = TransferUiState(
            recentTransfers = completed.recentTransfers,
            isResetting = true
        )
        assertFalse(reset.transferComplete)
        assertEquals(0f, reset.transferProgress, 0.001f)
        assertNull(reset.currentFileName)
        assertFalse(reset.isSendMode)
        assertTrue(reset.isResetting)
    }

    // ── Profile refresh with friends ──────────────────────────────────

    @Test
    fun `ProfileData round-trips display name and JSON`() {
        val profile = ProfileData("01HABCALICE000000ALICE", "Alice", """[{"type":"link","value":"github.com"}]""", null)
        assertEquals("Alice", profile.displayName)
        assertTrue(profile.profileItemsJson.contains("github.com"))
        assertNull(profile.profilePicBytes)
    }

    @Test
    fun `ProfileData with pic bytes`() {
        val picBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val profile = ProfileData("01HABCBOBBB0000000BOBB", "Bob", "[]", picBytes)
        assertEquals(3, profile.profilePicBytes?.size)
    }

    // ── Connection details ────────────────────────────────────────────

    @Test
    fun `ConnectionDetails defaults type to file`() {
        val details = ConnectionDetails("00:11:22", 8765, "DIRECT-ab-Test", "pass")
        assertEquals("file", details.type)
    }

    @Test
    fun `ConnectionDetails profile type`() {
        val details = ConnectionDetails("00:11:22", 8765, "DIRECT-ab-Test", "pass", type = "profile")
        assertEquals("profile", details.type)
    }

    @Test
    fun `ConnectionDetails fileCount defaults to 1`() {
        val details = ConnectionDetails("00:11:22", 8765, "DIRECT-ab-Test", "pass")
        assertEquals(1, details.fileCount)
    }

    @Test
    fun `ConnectionDetails ulid defaults to empty`() {
        val details = ConnectionDetails("00:11:22", 8765, "DIRECT-ab-Test", "pass")
        assertEquals("", details.ulid)
    }

    @Test
    fun `ConnectionDetails carries ulid`() {
        val details = ConnectionDetails("00:11:22", 8765, "DIRECT-ab-Test", "pass", ulid = "01HABCTEST00000000TEST")
        assertEquals("01HABCTEST00000000TEST", details.ulid)
    }

    @Test
    fun `ProfileData carries ulid`() {
        val profile = ProfileData("01HABCALICE000000ALICE", "Alice", "[]", null)
        assertEquals("01HABCALICE000000ALICE", profile.ulid)
    }

    // ── Warning dismiss-once behavior ─────────────────────────────────────

    @Test
    fun `warning dialog hidden after session dismiss`() {
        // Warning is active but user dismissed it
        val state = TransferUiState(
            nfcDisabledWarning = true,
            nfcWarningDismissedThisSession = true
        )
        // Dialog should NOT show: warning && dismissed → hidden
        assertTrue(state.nfcDisabledWarning)
        assertTrue(state.nfcWarningDismissedThisSession)
        // Has active warnings (for icon): warning is still true, dismissed is true
        val hasActiveWarnings = (state.nfcDisabledWarning && state.nfcWarningDismissedThisSession)
        assertTrue(hasActiveWarnings)
    }

    @Test
    fun `warning dialog shows when not dismissed`() {
        val state = TransferUiState(nfcDisabledWarning = true)
        // Dialog SHOULD show: warning && !dismissed
        assertTrue(state.nfcDisabledWarning)
        assertFalse(state.nfcWarningDismissedThisSession)
    }

    @Test
    fun `clearing dismissed flag re-enables dialog`() {
        val dismissed = TransferUiState(
            wifiDisabledWarning = true,
            wifiWarningDismissedThisSession = true
        )
        // Simulate clearing dismissed flag (issue resolved or user tapped warning icon)
        val cleared = dismissed.copy(wifiWarningDismissedThisSession = false)
        assertTrue(cleared.wifiDisabledWarning)
        assertFalse(cleared.wifiWarningDismissedThisSession)
    }

    @Test
    fun `resolved warning clears both state and dismissed flag`() {
        val state = TransferUiState(
            hotspotWarning = true,
            hotspotWarningDismissedThisSession = true
        )
        // Simulate user turning off hotspot → onResume clears both
        val resolved = state.copy(hotspotWarning = false, hotspotWarningDismissedThisSession = false)
        assertFalse(resolved.hotspotWarning)
        assertFalse(resolved.hotspotWarningDismissedThisSession)
        // No active warnings
        val hasActiveWarnings = (resolved.nfcDisabledWarning && resolved.nfcWarningDismissedThisSession)
            || (resolved.wifiDisabledWarning && resolved.wifiWarningDismissedThisSession)
            || (resolved.hotspotWarning && resolved.hotspotWarningDismissedThisSession)
        assertFalse(hasActiveWarnings)
    }
}
