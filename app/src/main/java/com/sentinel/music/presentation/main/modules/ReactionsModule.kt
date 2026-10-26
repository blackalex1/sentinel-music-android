package com.sentinel.music.presentation.main.modules

import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.sentinel.music.data.api.MusicApiService
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.player.MusicService
import com.sentinel.music.presentation.main.MainUiState
import com.sentinel.music.presentation.main.updateTrackInCollections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Модуль управления реакциями (лайки, дизлайки, обновление треков в UI и нотификации)
 */
class ReactionsModule(
    private val scope: CoroutineScope,
    private val api: MusicApiService,
    private val uiStateFlow: MutableStateFlow<MainUiState>,
    private val getMediaController: () -> MediaController?,
    private val onPlayNext: () -> Unit,
    private val onSyncRemoteState: () -> Unit
) {
    fun updateTrackReaction(trackId: Int, isLiked: Boolean, isDisliked: Boolean) {
        uiStateFlow.update { it.updateTrackInCollections(trackId) { t -> t.copy(isLiked = isLiked, isDisliked = isDisliked) } }
        val active = uiStateFlow.value.activeDevice
        if (active?.currentTrack?.id == trackId) {
            onSyncRemoteState()
        }
    }

    fun toggleLike(targetTrack: TrackDto? = null) {
        val tr = targetTrack ?: uiStateFlow.value.currentTrack ?: return
        if (tr.id <= 0 || tr.isPreview) return
        scope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    api.setReaction(tr.id, isLike = true)
                }
                updateTrackReaction(tr.id, res.isLiked, res.isDisliked)

                if (uiStateFlow.value.currentTrack?.id == tr.id) {
                    getMediaController()?.sendCustomCommand(
                        SessionCommand(MusicService.ACTION_UPDATE_REACTIONS, Bundle.EMPTY),
                        Bundle().apply {
                            putBoolean("IS_LIKED", res.isLiked)
                            putBoolean("IS_DISLIKED", res.isDisliked)
                        }
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleDislike(targetTrack: TrackDto? = null) {
        val tr = targetTrack ?: uiStateFlow.value.currentTrack ?: return
        if (tr.id <= 0 || tr.isPreview) return
        scope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    api.setReaction(tr.id, isLike = false)
                }
                updateTrackReaction(tr.id, res.isLiked, res.isDisliked)

                if (uiStateFlow.value.currentTrack?.id == tr.id) {
                    getMediaController()?.sendCustomCommand(
                        SessionCommand(MusicService.ACTION_UPDATE_REACTIONS, Bundle.EMPTY),
                        Bundle().apply {
                            putBoolean("IS_LIKED", res.isLiked)
                            putBoolean("IS_DISLIKED", res.isDisliked)
                        }
                    )
                }

                if (res.isDisliked && uiStateFlow.value.currentTrack?.id == tr.id) {
                    onPlayNext()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
