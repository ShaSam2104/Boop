package com.shashsam.boop.ui.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shashsam.boop.data.BoopDatabase
import com.shashsam.boop.data.ProfileItemDao
import com.shashsam.boop.data.ProfileItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "ProfileViewModel"
private const val PREFS_NAME = "boop_settings"
private const val KEY_PROFILE_PIC = "profile_pic_path"
private const val KEY_PROFILE_ANSWERS = "profile_answers"
private const val MAX_PROFILE_ITEMS = 12

/**
 * Parsed result from profile JSON — items plus optional "About Me" answers.
 */
data class ParsedProfile(
    val items: List<ProfileItemEntity>,
    val answers: Map<String, String>
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val profileItemDao: ProfileItemDao =
        BoopDatabase.getInstance(application).profileItemDao()

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val profileItems: StateFlow<List<ProfileItemEntity>> = profileItemDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _profilePicPath = MutableStateFlow(prefs.getString(KEY_PROFILE_PIC, null))
    val profilePicPath: StateFlow<String?> = _profilePicPath.asStateFlow()

    fun addProfileItem(type: String, label: String, value: String, size: String) {
        viewModelScope.launch {
            val count = profileItemDao.getCount()
            if (count >= MAX_PROFILE_ITEMS) {
                Log.w(TAG, "Cannot add more than $MAX_PROFILE_ITEMS profile items")
                return@launch
            }
            val item = ProfileItemEntity(
                type = type,
                label = label,
                value = value,
                size = size,
                sortOrder = count
            )
            profileItemDao.insert(item)
            Log.d(TAG, "Added profile item: $label")
        }
    }

    fun updateProfileItem(item: ProfileItemEntity) {
        viewModelScope.launch {
            profileItemDao.update(item)
            Log.d(TAG, "Updated profile item: ${item.label}")
        }
    }

    fun deleteProfileItem(id: Long) {
        viewModelScope.launch {
            profileItemDao.deleteById(id)
            Log.d(TAG, "Deleted profile item: $id")
        }
    }

    fun reorderProfileItems(reorderedList: List<ProfileItemEntity>) {
        viewModelScope.launch {
            reorderedList.forEachIndexed { index, item ->
                profileItemDao.updateSortOrder(item.id, index)
            }
            Log.d(TAG, "Reordered ${reorderedList.size} profile items")
        }
    }

    fun setProfilePic(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val destFile = File(context.filesDir, "profile_pic.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val path = destFile.absolutePath
                prefs.edit().putString(KEY_PROFILE_PIC, path).apply()
                _profilePicPath.value = path
                Log.d(TAG, "Profile pic saved: $path")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save profile pic", e)
            }
        }
    }

    fun buildProfileJson(): String {
        val items = profileItems.value
        val array = JSONArray()
        for (item in items) {
            array.put(JSONObject().apply {
                put("type", item.type)
                put("label", item.label)
                put("value", item.value)
                put("size", item.size)
                put("sortOrder", item.sortOrder)
            })
        }
        // Read profile answers from SharedPreferences
        val answersMap = mutableMapOf<String, String>()
        val answersJson = prefs.getString(KEY_PROFILE_ANSWERS, null)
        if (answersJson != null) {
            try {
                val obj = JSONObject(answersJson)
                for (key in obj.keys()) {
                    answersMap[key] = obj.getString(key)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse profile answers", e)
            }
        }
        // New envelope format: {items: [...], answers: {...}}
        val envelope = JSONObject().apply {
            put("items", array)
            put("answers", JSONObject(answersMap as Map<*, *>))
        }
        return envelope.toString()
    }

    fun getProfilePicFile(): File? {
        val path = _profilePicPath.value ?: return null
        val file = File(path)
        return if (file.exists()) file else null
    }

    companion object {
        /**
         * Parses profile JSON in either old format (`[...]` array) or new envelope
         * format (`{items: [...], answers: {...}}`). Returns [ParsedProfile].
         */
        fun parseProfileJson(json: String?): ParsedProfile {
            if (json.isNullOrBlank()) return ParsedProfile(emptyList(), emptyMap())
            return try {
                val trimmed = json.trim()
                if (trimmed.startsWith("[")) {
                    // Old format: bare JSON array of items
                    ParsedProfile(parseItemsArray(JSONArray(trimmed)), emptyMap())
                } else {
                    // New envelope format
                    val obj = JSONObject(trimmed)
                    val items = parseItemsArray(obj.optJSONArray("items"))
                    val answersObj = obj.optJSONObject("answers")
                    val answers = mutableMapOf<String, String>()
                    if (answersObj != null) {
                        for (key in answersObj.keys()) {
                            answers[key] = answersObj.getString(key)
                        }
                    }
                    ParsedProfile(items, answers)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse profile JSON", e)
                ParsedProfile(emptyList(), emptyMap())
            }
        }

        private fun parseItemsArray(array: JSONArray?): List<ProfileItemEntity> {
            if (array == null) return emptyList()
            return (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ProfileItemEntity(
                    type = obj.getString("type"),
                    label = obj.getString("label"),
                    value = obj.getString("value"),
                    size = obj.optString("size", "half"),
                    sortOrder = obj.optInt("sortOrder", i)
                )
            }
        }
    }
}
