package com.shashsam.boop.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.shashsam.boop.data.BoopDatabase
import com.shashsam.boop.data.FriendEntity
import com.shashsam.boop.data.ProfileItemEntity
import com.shashsam.boop.data.TransferHistoryEntity
import androidx.room.withTransaction
import java.io.File
import java.time.Instant

private const val TAG = "BackupManager"
private const val PREFS_NAME = "boop_settings"
private const val KEY_DISPLAY_NAME = "display_name"
private const val KEY_ULID = "user_ulid"
private const val KEY_PROFILE_PIC = "profile_pic_path"
private const val KEY_BIO = "bio"

/**
 * Orchestrates full export/import of Boop data (profile, friends, history).
 * Reads Room tables + files, produces encrypted output via [BackupCrypto].
 */
class BackupManager(private val context: Context) {

    private val db = BoopDatabase.getInstance(context)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Exports all data to an encrypted backup file at [uri].
     */
    suspend fun export(uri: Uri, password: String) {
        Log.d(TAG, "Starting export")

        val profileItems = db.profileItemDao().getAllOnce()
        val friends = db.friendDao().getAllOnce()
        val history = db.transferHistoryDao().getAllOnce()

        val ulid = prefs.getString(KEY_ULID, "") ?: ""
        val displayName = prefs.getString(KEY_DISPLAY_NAME, "My Device") ?: "My Device"

        // Read profile pic
        val profilePicPath = prefs.getString(KEY_PROFILE_PIC, null)
        val profilePicBase64 = profilePicPath?.let { path ->
            val file = File(path)
            if (file.exists()) BackupSerializer.encodeToBase64(file.readBytes()) else null
        }

        val bio = prefs.getString(KEY_BIO, "") ?: ""

        // Build backup data
        val backupData = BackupSerializer.BackupData(
            version = 1,
            exportedAt = Instant.now().toString(),
            profile = BackupSerializer.ProfileBackup(
                ulid = ulid,
                displayName = displayName,
                profilePicBase64 = profilePicBase64,
                items = profileItems.map { item ->
                    BackupSerializer.ProfileItemBackup(
                        type = item.type,
                        label = item.label,
                        value = item.value,
                        size = item.size,
                        sortOrder = item.sortOrder
                    )
                },
                bio = bio
            ),
            friends = friends.map { friend ->
                val friendPicBase64 = friend.profilePicPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) BackupSerializer.encodeToBase64(file.readBytes()) else null
                }
                BackupSerializer.FriendBackup(
                    ulid = friend.ulid,
                    displayName = friend.displayName,
                    firstSeenTimestamp = friend.firstSeenTimestamp,
                    lastSeenTimestamp = friend.lastSeenTimestamp,
                    lastInteractionTimestamp = friend.lastInteractionTimestamp,
                    transferCount = friend.transferCount,
                    profileJson = friend.profileJson,
                    profilePicBase64 = friendPicBase64
                )
            },
            history = history.map { entry ->
                BackupSerializer.HistoryBackup(
                    fileName = entry.fileName,
                    fileSize = entry.fileSize,
                    mimeType = entry.mimeType,
                    timestamp = entry.timestamp,
                    wasSender = entry.wasSender,
                    peerUlid = entry.peerUlid
                )
            }
        )

        val plaintext = BackupSerializer.serialize(backupData)
        val encrypted = BackupCrypto.encrypt(plaintext, password)

        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(encrypted)
        } ?: throw Exception("Failed to open output stream")

        Log.d(TAG, "Export complete: ${friends.size} friends, ${history.size} history entries, ${profileItems.size} profile items")
    }

    /**
     * Imports data from an encrypted backup file at [uri].
     * Profile items: clear and replace. Friends: upsert by ULID.
     * History: append. SharedPreferences: overwrite.
     */
    suspend fun import(uri: Uri, password: String) {
        Log.d(TAG, "Starting import")

        val encrypted = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw Exception("Failed to open input stream")

        val plaintext = BackupCrypto.decrypt(encrypted, password)
        val data = BackupSerializer.deserialize(plaintext)

        Log.d(TAG, "Parsed backup: v${data.version}, ${data.friends.size} friends, ${data.history.size} history, ${data.profile.items.size} items")

        db.withTransaction {
            // Profile items: clear and replace
            db.profileItemDao().deleteAll()
            data.profile.items.forEach { item ->
                db.profileItemDao().insert(
                    ProfileItemEntity(
                        type = item.type,
                        label = item.label,
                        value = item.value,
                        size = item.size,
                        sortOrder = item.sortOrder
                    )
                )
            }

            // Friends: upsert by ULID
            data.friends.forEach { friend ->
                val picPath = friend.profilePicBase64?.let { base64 ->
                    saveFriendPic(friend.ulid, BackupSerializer.decodeFromBase64(base64))
                }
                db.friendDao().upsertByUlid(
                    FriendEntity(
                        ulid = friend.ulid,
                        ssid = "IMPORTED_${friend.ulid}",
                        displayName = friend.displayName,
                        firstSeenTimestamp = friend.firstSeenTimestamp,
                        lastSeenTimestamp = friend.lastSeenTimestamp,
                        lastInteractionTimestamp = friend.lastInteractionTimestamp,
                        transferCount = friend.transferCount,
                        profileJson = friend.profileJson,
                        profilePicPath = picPath
                    )
                )
            }

            // History: append
            data.history.forEach { entry ->
                db.transferHistoryDao().insert(
                    TransferHistoryEntity(
                        fileName = entry.fileName,
                        fileSize = entry.fileSize,
                        mimeType = entry.mimeType,
                        timestamp = entry.timestamp,
                        wasSender = entry.wasSender,
                        fileUriString = null,
                        peerUlid = entry.peerUlid
                    )
                )
            }
        }

        // SharedPreferences: overwrite
        if (data.profile.ulid.isNotBlank()) {
            prefs.edit().putString(KEY_ULID, data.profile.ulid).apply()
        }
        prefs.edit().putString(KEY_DISPLAY_NAME, data.profile.displayName).apply()

        // Bio: overwrite
        prefs.edit().putString(KEY_BIO, data.profile.bio).apply()

        // Profile pic: overwrite
        data.profile.profilePicBase64?.let { base64 ->
            val bytes = BackupSerializer.decodeFromBase64(base64)
            val picFile = File(context.filesDir, "profile_pic.jpg")
            picFile.writeBytes(bytes)
            prefs.edit().putString(KEY_PROFILE_PIC, picFile.absolutePath).apply()
        }

        Log.d(TAG, "Import complete")
    }

    private fun saveFriendPic(ulid: String, bytes: ByteArray): String {
        val dir = File(context.filesDir, "friend_pics")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "$ulid.jpg")
        file.writeBytes(bytes)
        return file.absolutePath
    }
}
