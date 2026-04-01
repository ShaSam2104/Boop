package com.shashsam.boop.backup

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * Serializes/deserializes Boop backup data to/from JSON.
 *
 * Uses [java.util.Base64] (not android.util.Base64) for JVM test compatibility.
 */
object BackupSerializer {

    private const val CURRENT_VERSION = 1

    data class BackupData(
        val version: Int,
        val exportedAt: String,
        val profile: ProfileBackup,
        val friends: List<FriendBackup>,
        val history: List<HistoryBackup>
    )

    data class ProfileBackup(
        val ulid: String,
        val displayName: String,
        val profilePicBase64: String?,
        val items: List<ProfileItemBackup>,
        val bio: String = ""
    )

    data class ProfileItemBackup(
        val type: String,
        val label: String,
        val value: String,
        val size: String,
        val sortOrder: Int
    )

    data class FriendBackup(
        val ulid: String,
        val displayName: String,
        val firstSeenTimestamp: Long,
        val lastSeenTimestamp: Long,
        val lastInteractionTimestamp: Long,
        val transferCount: Int,
        val profileJson: String?,
        val profilePicBase64: String?
    )

    data class HistoryBackup(
        val fileName: String,
        val fileSize: Long,
        val mimeType: String,
        val timestamp: Long,
        val wasSender: Boolean,
        val peerUlid: String?
    )

    fun serialize(data: BackupData): ByteArray {
        val json = JSONObject().apply {
            put("version", data.version)
            put("exportedAt", data.exportedAt)

            put("profile", JSONObject().apply {
                put("ulid", data.profile.ulid)
                put("displayName", data.profile.displayName)
                put("profilePicBase64", data.profile.profilePicBase64 ?: JSONObject.NULL)
                put("items", JSONArray().apply {
                    data.profile.items.forEach { item ->
                        put(JSONObject().apply {
                            put("type", item.type)
                            put("label", item.label)
                            put("value", item.value)
                            put("size", item.size)
                            put("sortOrder", item.sortOrder)
                        })
                    }
                })
                put("bio", data.profile.bio)
            })

            put("friends", JSONArray().apply {
                data.friends.forEach { friend ->
                    put(JSONObject().apply {
                        put("ulid", friend.ulid)
                        put("displayName", friend.displayName)
                        put("firstSeenTimestamp", friend.firstSeenTimestamp)
                        put("lastSeenTimestamp", friend.lastSeenTimestamp)
                        put("lastInteractionTimestamp", friend.lastInteractionTimestamp)
                        put("transferCount", friend.transferCount)
                        put("profileJson", friend.profileJson ?: JSONObject.NULL)
                        put("profilePicBase64", friend.profilePicBase64 ?: JSONObject.NULL)
                    })
                }
            })

            put("history", JSONArray().apply {
                data.history.forEach { entry ->
                    put(JSONObject().apply {
                        put("fileName", entry.fileName)
                        put("fileSize", entry.fileSize)
                        put("mimeType", entry.mimeType)
                        put("timestamp", entry.timestamp)
                        put("wasSender", entry.wasSender)
                        put("peerUlid", entry.peerUlid ?: JSONObject.NULL)
                    })
                }
            })
        }

        return json.toString().toByteArray(Charsets.UTF_8)
    }

    fun deserialize(bytes: ByteArray): BackupData {
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        val version = json.getInt("version")
        if (version > CURRENT_VERSION) {
            throw UnsupportedVersionException(
                "Backup was created by a newer version of Boop (data v$version)"
            )
        }

        val profileJson = json.getJSONObject("profile")
        val profile = ProfileBackup(
            ulid = profileJson.optString("ulid", ""),
            displayName = profileJson.optString("displayName", "My Device"),
            profilePicBase64 = profileJson.optString("profilePicBase64", null)
                .takeIf { it != "null" && !it.isNullOrBlank() },
            items = parseProfileItems(profileJson.optJSONArray("items")),
            bio = profileJson.optString("bio", "")
        )

        val friendsArray = json.optJSONArray("friends")
        val friends = if (friendsArray != null) {
            (0 until friendsArray.length()).map { i ->
                val f = friendsArray.getJSONObject(i)
                FriendBackup(
                    ulid = f.optString("ulid", ""),
                    displayName = f.optString("displayName", ""),
                    firstSeenTimestamp = f.optLong("firstSeenTimestamp", 0),
                    lastSeenTimestamp = f.optLong("lastSeenTimestamp", 0),
                    lastInteractionTimestamp = f.optLong("lastInteractionTimestamp", 0),
                    transferCount = f.optInt("transferCount", 0),
                    profileJson = f.optString("profileJson", null)
                        .takeIf { it != "null" && !it.isNullOrBlank() },
                    profilePicBase64 = f.optString("profilePicBase64", null)
                        .takeIf { it != "null" && !it.isNullOrBlank() }
                )
            }
        } else emptyList()

        val historyArray = json.optJSONArray("history")
        val history = if (historyArray != null) {
            (0 until historyArray.length()).map { i ->
                val h = historyArray.getJSONObject(i)
                HistoryBackup(
                    fileName = h.optString("fileName", ""),
                    fileSize = h.optLong("fileSize", 0),
                    mimeType = h.optString("mimeType", ""),
                    timestamp = h.optLong("timestamp", 0),
                    wasSender = h.optBoolean("wasSender", false),
                    peerUlid = h.optString("peerUlid", null)
                        .takeIf { it != "null" && !it.isNullOrBlank() }
                )
            }
        } else emptyList()

        return BackupData(
            version = version,
            exportedAt = json.optString("exportedAt", ""),
            profile = profile,
            friends = friends,
            history = history
        )
    }

    fun encodeToBase64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    fun decodeFromBase64(str: String): ByteArray =
        Base64.getDecoder().decode(str)

    private fun parseProfileItems(array: JSONArray?): List<ProfileItemBackup> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            ProfileItemBackup(
                type = item.optString("type", "link"),
                label = item.optString("label", ""),
                value = item.optString("value", ""),
                size = item.optString("size", "half"),
                sortOrder = item.optInt("sortOrder", i)
            )
        }
    }
}
