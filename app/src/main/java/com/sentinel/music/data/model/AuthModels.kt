package com.sentinel.music.data.model

import com.google.gson.annotations.SerializedName

data class AuthRequest(
    val username: String,
    val password: String,
    @SerializedName("device_name") val deviceName: String = "Android Mobile"
)

data class AuthResponse(
    val status: String,
    val token: String,
    val username: String,
    @SerializedName("is_admin") val isAdmin: Boolean
)

data class AuthStatusResponse(
    val initialized: Boolean
)

data class UserProfileDto(
    val id: Int,
    val username: String,
    @SerializedName("is_admin") val isAdmin: Boolean
)
