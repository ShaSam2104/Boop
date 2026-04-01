package com.shashsam.boop.utils

import android.content.Context
import com.github.f4b6a3.ulid.UlidCreator

private const val PREFS_NAME = "boop_settings"
private const val KEY_ULID = "user_ulid"

/**
 * Generates a new monotonic ULID string (26 Crockford Base32 characters).
 */
fun generateUlid(): String = UlidCreator.getMonotonicUlid().toString()

/**
 * Returns this user's persistent ULID, generating one on first call.
 */
fun getOrCreateUlid(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_ULID, null) ?: generateUlid().also {
        prefs.edit().putString(KEY_ULID, it).apply()
    }
}
