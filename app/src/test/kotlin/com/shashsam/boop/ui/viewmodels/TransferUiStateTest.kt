package com.shashsam.boop.ui.viewmodels

import com.shashsam.boop.transfer.ProfileData
import com.shashsam.boop.ui.models.LogEntry
import com.shashsam.boop.ui.models.RecentBoop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferUiStateTest {

    @Test
    fun `default TransferUiState is in receive mode`() {
        val state = TransferUiState()
        assertFalse(state.isTransferring)
        assertFalse(state.isSendMode)
        assertTrue(state.isReceiveMode)
        assertFalse(state.isNfcBroadcasting)
        assertTrue(state.isNfcReading)
        assertFalse(state.isWifiConnecting)
        assertFalse(state.isWifiConnected)
        assertFalse(state.transferComplete)
        assertEquals(0f, state.transferProgress, 0.001f)
        assertEquals(0L, state.transferredBytes)
        assertEquals(0L, state.totalBytes)
        assertNull(state.error)
        assertNull(state.savedFileUri)
        assertNull(state.receivedPayload)
        assertNull(state.currentFileName)
        assertTrue(state.statusLog.isEmpty())
        assertTrue(state.recentTransfers.isEmpty())
    }

    @Test
    fun `copy into send mode`() {
        val state = TransferUiState().copy(isSendMode = true, isReceiveMode = false)
        assertTrue(state.isSendMode)
        assertFalse(state.isReceiveMode)
    }

    @Test
    fun `copy into receive mode`() {
        val state = TransferUiState().copy(isReceiveMode = true, isNfcReading = true)
        assertTrue(state.isReceiveMode)
        assertTrue(state.isNfcReading)
    }

    @Test
    fun `transfer progress updates`() {
        val state = TransferUiState().copy(
            isTransferring = true,
            transferProgress = 0.5f,
            transferredBytes = 500L,
            totalBytes = 1000L
        )
        assertEquals(0.5f, state.transferProgress, 0.001f)
        assertEquals(500L, state.transferredBytes)
        assertEquals(1000L, state.totalBytes)
    }

    @Test
    fun `transfer complete state`() {
        val state = TransferUiState().copy(
            isTransferring = false,
            transferComplete = true,
            transferProgress = 1f
        )
        assertFalse(state.isTransferring)
        assertTrue(state.transferComplete)
        assertEquals(1f, state.transferProgress, 0.001f)
    }

    @Test
    fun `error state`() {
        val state = TransferUiState().copy(error = "Connection timed out")
        assertEquals("Connection timed out", state.error)
    }

    @Test
    fun `statusLog accumulates entries`() {
        val logs = listOf(
            LogEntry("Send mode activated"),
            LogEntry("Wi-Fi Direct connected"),
            LogEntry("Transfer error", isError = true)
        )
        val state = TransferUiState().copy(statusLog = logs)
        assertEquals(3, state.statusLog.size)
        assertTrue(state.statusLog[2].isError)
    }

    @Test
    fun `recentTransfers preserves list through copy`() {
        val boops = listOf(
            RecentBoop("a.jpg", 100L, "image/jpeg", 1L, true),
            RecentBoop("b.pdf", 200L, "application/pdf", 2L, false)
        )
        val state = TransferUiState(recentTransfers = boops)
        // Simulate reset preserving recent transfers
        val reset = TransferUiState(recentTransfers = state.recentTransfers)
        assertEquals(2, reset.recentTransfers.size)
        assertEquals("a.jpg", reset.recentTransfers[0].fileName)
        assertFalse(reset.isTransferring)
    }

    @Test
    fun `currentFileName is tracked`() {
        val state = TransferUiState().copy(currentFileName = "vacation.zip")
        assertEquals("vacation.zip", state.currentFileName)
    }

    @Test
    fun `isResetting defaults to false and can be set`() {
        val state = TransferUiState()
        assertFalse(state.isResetting)
        val resetting = state.copy(isResetting = true)
        assertTrue(resetting.isResetting)
    }

    @Test
    fun `pendingFriendRequest defaults to null`() {
        val state = TransferUiState()
        assertNull(state.pendingFriendRequest)
    }

    @Test
    fun `pendingFriendRequest can be set`() {
        val profile = ProfileData("01HABCTEST00000000TEST", "Test", "[]", null)
        val state = TransferUiState().copy(pendingFriendRequest = profile)
        assertEquals("Test", state.pendingFriendRequest?.displayName)
    }

    @Test
    fun `isProfileShareMode defaults to false`() {
        val state = TransferUiState()
        assertFalse(state.isProfileShareMode)
        val sharing = state.copy(isProfileShareMode = true)
        assertTrue(sharing.isProfileShareMode)
    }

    @Test
    fun `receivedProfile defaults to null`() {
        val state = TransferUiState()
        assertNull(state.receivedProfile)
        val profile = ProfileData("01HABCRECV00000000RECV", "Received", "[]", null)
        val withProfile = state.copy(receivedProfile = profile)
        assertEquals("Received", withProfile.receivedProfile?.displayName)
    }

    @Test
    fun `friendExchangeComplete defaults to false`() {
        val state = TransferUiState()
        assertFalse(state.friendExchangeComplete)
        val exchanged = state.copy(friendExchangeComplete = true)
        assertTrue(exchanged.friendExchangeComplete)
    }
}
