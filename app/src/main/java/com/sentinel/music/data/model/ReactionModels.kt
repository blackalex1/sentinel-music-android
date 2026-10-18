package com.sentinel.music.data.model

import com.google.gson.annotations.SerializedName

data class ReactionResponse(
    val status: String,
    @SerializedName("is_liked") val isLiked: Boolean,
    @SerializedName("is_disliked") val isDisliked: Boolean,
    @SerializedName("saved_permanent") val savedPermanent: Boolean? = null
)
