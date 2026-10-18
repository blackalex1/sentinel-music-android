package com.sentinel.music.presentation.main

import com.sentinel.music.data.model.TrackDto

/**
 * Вспомогательное расширение для иммутабельного обновления трека во всех коллекциях MainUiState.
 * Устраняет дублирование кода между updateTrackReaction, updateReplacedTrack и загрузками.
 */
fun MainUiState.updateTrackInCollections(
    trackId: Int,
    transform: (TrackDto) -> TrackDto
): MainUiState {
    val updatedTracks = tracks.map { if (it.id == trackId) transform(it) else it }
    val updatedRecents = recentTracks.map { if (it.id == trackId) transform(it) else it }
    val updatedSearch = searchResults.map { if (it.id == trackId) transform(it) else it }
    val updatedQueue = queue.map { if (it.id == trackId) transform(it) else it }
    val updatedCur = if (currentTrack?.id == trackId) transform(currentTrack) else currentTrack

    val updatedActive = activeDevice?.let { dev ->
        if (dev.currentTrack?.id == trackId) {
            dev.copy(currentTrack = transform(dev.currentTrack))
        } else dev
    }

    val curLiked = if (currentTrack?.id == trackId) updatedCur?.isLiked ?: isLiked else isLiked
    val curDisliked = if (currentTrack?.id == trackId) updatedCur?.isDisliked ?: isDisliked else isDisliked

    return copy(
        tracks = updatedTracks,
        recentTracks = updatedRecents,
        searchResults = updatedSearch,
        queue = updatedQueue,
        currentTrack = updatedCur,
        activeDevice = updatedActive,
        isLiked = curLiked,
        isDisliked = curDisliked
    )
}
