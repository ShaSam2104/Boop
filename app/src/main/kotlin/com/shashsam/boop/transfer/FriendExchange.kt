package com.shashsam.boop.transfer

import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream

private const val TAG = "FriendExchange"

data class ProfileData(
    val displayName: String,
    val profileItemsJson: String,
    val profilePicBytes: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProfileData) return false
        return displayName == other.displayName &&
                profileItemsJson == other.profileItemsJson &&
                profilePicBytes.contentEquals(other.profilePicBytes)
    }

    override fun hashCode(): Int {
        var result = displayName.hashCode()
        result = 31 * result + profileItemsJson.hashCode()
        result = 31 * result + (profilePicBytes?.contentHashCode() ?: 0)
        return result
    }
}

const val FRIEND_REQUEST_MAGIC = "BOOP_FRIEND\n"
const val FRIEND_ACK_MAGIC = "BOOP_FRIEND_ACK\n"
const val FRIEND_NAK_MAGIC = "BOOP_FRIEND_NAK\n"

fun sendFriendRequest(out: DataOutputStream, profile: ProfileData) {
    Log.d(TAG, "Sending friend request: name=${profile.displayName}")
    val magicBytes = FRIEND_REQUEST_MAGIC.toByteArray(Charsets.UTF_8)
    out.write(magicBytes)

    // Write display name
    val nameBytes = profile.displayName.toByteArray(Charsets.UTF_8)
    out.writeInt(nameBytes.size)
    out.write(nameBytes)

    // Write profile items JSON
    val jsonBytes = profile.profileItemsJson.toByteArray(Charsets.UTF_8)
    out.writeInt(jsonBytes.size)
    out.write(jsonBytes)

    // Write profile pic
    val picSize = profile.profilePicBytes?.size ?: 0
    out.writeInt(picSize)
    if (picSize > 0) {
        out.write(profile.profilePicBytes!!)
    }
    out.flush()
    Log.d(TAG, "Friend request sent (json=${jsonBytes.size}B pic=${picSize}B)")
}

fun readFriendRequest(inp: DataInputStream): ProfileData? {
    return try {
        // Read magic
        val magicBytes = FRIEND_REQUEST_MAGIC.toByteArray(Charsets.UTF_8)
        val magic = ByteArray(magicBytes.size)
        inp.readFully(magic)
        if (!magic.contentEquals(magicBytes)) {
            Log.w(TAG, "Invalid friend request magic")
            return null
        }

        readProfileData(inp)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to read friend request", e)
        null
    }
}

fun sendFriendResponse(out: DataOutputStream, accepted: Boolean, profile: ProfileData?) {
    if (accepted) {
        Log.d(TAG, "Sending friend ACK")
        out.write(FRIEND_ACK_MAGIC.toByteArray(Charsets.UTF_8))
        if (profile != null) {
            // Write display name
            val nameBytes = profile.displayName.toByteArray(Charsets.UTF_8)
            out.writeInt(nameBytes.size)
            out.write(nameBytes)

            // Write profile items JSON
            val jsonBytes = profile.profileItemsJson.toByteArray(Charsets.UTF_8)
            out.writeInt(jsonBytes.size)
            out.write(jsonBytes)

            // Write profile pic
            val picSize = profile.profilePicBytes?.size ?: 0
            out.writeInt(picSize)
            if (picSize > 0) {
                out.write(profile.profilePicBytes!!)
            }
        }
    } else {
        Log.d(TAG, "Sending friend NAK")
        out.write(FRIEND_NAK_MAGIC.toByteArray(Charsets.UTF_8))
    }
    out.flush()
}

fun readFriendResponse(inp: DataInputStream): Pair<Boolean, ProfileData?> {
    return try {
        val ackMagic = FRIEND_ACK_MAGIC.toByteArray(Charsets.UTF_8)
        val nakMagic = FRIEND_NAK_MAGIC.toByteArray(Charsets.UTF_8)
        // ACK and NAK are same length
        val magic = ByteArray(ackMagic.size)
        inp.readFully(magic)

        when {
            magic.contentEquals(ackMagic) -> {
                Log.d(TAG, "Friend response: ACK")
                val profile = readProfileData(inp)
                Pair(true, profile)
            }
            magic.contentEquals(nakMagic) -> {
                Log.d(TAG, "Friend response: NAK")
                Pair(false, null)
            }
            else -> {
                Log.w(TAG, "Unknown friend response magic")
                Pair(false, null)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to read friend response", e)
        Pair(false, null)
    }
}

private fun readProfileData(inp: DataInputStream): ProfileData {
    val nameLen = inp.readInt()
    val nameBytes = ByteArray(nameLen).also { inp.readFully(it) }

    val jsonLen = inp.readInt()
    val jsonBytes = ByteArray(jsonLen).also { inp.readFully(it) }

    val picSize = inp.readInt()
    val picBytes = if (picSize > 0) {
        ByteArray(picSize).also { inp.readFully(it) }
    } else null

    return ProfileData(
        displayName = String(nameBytes, Charsets.UTF_8),
        profileItemsJson = String(jsonBytes, Charsets.UTF_8),
        profilePicBytes = picBytes
    )
}
