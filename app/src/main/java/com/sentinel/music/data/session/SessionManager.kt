package com.sentinel.music.data.session

import android.content.Context
import android.content.SharedPreferences

class SessionManager(val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sentinel_music_session", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) {
            val formatted = value.trim().removeSuffix("/")
            prefs.edit().putString(KEY_SERVER_URL, formatted).apply()
        }

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var isAdmin: Boolean
        get() = prefs.getBoolean(KEY_IS_ADMIN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_ADMIN, value).apply()

    val isLoggedIn: Boolean
        get() = !token.isNullOrBlank()

    fun saveSession(serverUrl: String, token: String, username: String, isAdmin: Boolean) {
        val formattedUrl = serverUrl.trim().removeSuffix("/")
        prefs.edit()
            .putString(KEY_SERVER_URL, formattedUrl)
            .putString(KEY_TOKEN, token)
            .putString(KEY_USERNAME, username)
            .putBoolean(KEY_IS_ADMIN, isAdmin)
            .apply()
    }

    fun logout() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USERNAME)
            .remove(KEY_IS_ADMIN)
            .remove(KEY_LAST_TRACK_JSON)
            .remove(KEY_LAST_POSITION_MS)
            .remove(KEY_LAST_QUEUE_JSON)
            .remove(KEY_LAST_SAVED_AT)
            .apply()
    }

    var isLoudnessNormalizationEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOUDNESS_NORMALIZATION, true)
        set(value) = prefs.edit().putBoolean(KEY_LOUDNESS_NORMALIZATION, value).apply()

    var loudnessTargetLufs: Float
        get() = prefs.getFloat(KEY_LOUDNESS_TARGET_LUFS, -14.0f)
        set(value) = prefs.edit().putFloat(KEY_LOUDNESS_TARGET_LUFS, value).apply()

    val deviceId: String
        get() {
            var id = prefs.getString(KEY_DEVICE_ID, null)
            if (id.isNullOrBlank()) {
                id = "android_" + java.util.UUID.randomUUID().toString().substring(0, 8)
                prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            }
            return id
        }

    var isPcPlaybackHandoffEnabled: Boolean
        get() = prefs.getBoolean(KEY_PC_PLAYBACK_HANDOFF, true)
        set(value) = prefs.edit().putBoolean(KEY_PC_PLAYBACK_HANDOFF, value).apply()

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USERNAME = "auth_username"
        private const val KEY_IS_ADMIN = "is_admin"
        private const val KEY_LOUDNESS_NORMALIZATION = "loudness_normalization"
        private const val KEY_LOUDNESS_TARGET_LUFS = "loudness_target_lufs"
        private const val KEY_DEVICE_ID = "remote_device_id"
        private const val KEY_PC_PLAYBACK_HANDOFF = "pc_playback_handoff"
        private const val KEY_LAST_TRACK_JSON = "last_track_json"
        private const val KEY_LAST_POSITION_MS = "last_position_ms"
        private const val KEY_LAST_QUEUE_JSON = "last_queue_json"
        private const val KEY_LAST_SAVED_AT = "last_saved_at"
    }

    fun saveLastPlaybackSession(trackJson: String?, positionMs: Long, queueJson: String?) {
        prefs.edit()
            .putString(KEY_LAST_TRACK_JSON, trackJson)
            .putLong(KEY_LAST_POSITION_MS, positionMs)
            .putString(KEY_LAST_QUEUE_JSON, queueJson)
            .putLong(KEY_LAST_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    val lastTrackJson: String?
        get() = prefs.getString(KEY_LAST_TRACK_JSON, null)

    val lastPositionMs: Long
        get() = prefs.getLong(KEY_LAST_POSITION_MS, 0L)

    val lastQueueJson: String?
        get() = prefs.getString(KEY_LAST_QUEUE_JSON, null)
}
