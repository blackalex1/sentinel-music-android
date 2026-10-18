package com.sentinel.music.presentation.main.modules

import androidx.media3.session.MediaController
import com.sentinel.music.data.remote.RemoteController
import com.sentinel.music.data.session.SessionManager
import com.sentinel.music.player.EqualizerManager
import com.sentinel.music.presentation.main.MainUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.pow

/**
 * Модуль обработки звука (DSP, эквалайзер, EBU R128 нормализация громкости, лимитер)
 */
class DspModule(
    private val sessionManager: SessionManager,
    private val equalizerManager: EqualizerManager,
    private val remoteController: RemoteController,
    private val uiStateFlow: MutableStateFlow<MainUiState>,
    private val getMediaController: () -> MediaController?
) {
    fun setLoudnessNormalization(enabled: Boolean) {
        sessionManager.isLoudnessNormalizationEnabled = enabled
        uiStateFlow.update { it.copy(isLoudnessNormalizationEnabled = enabled) }
        applyVolumeGain(uiStateFlow.value.currentTrack?.gainDb)
        if (uiStateFlow.value.activeDevice != null) {
            remoteController.setLimiter(loudnessEnabled = enabled)
        }
    }

    fun setLoudnessTarget(targetLufs: Float) {
        sessionManager.loudnessTargetLufs = targetLufs
        uiStateFlow.update { it.copy(loudnessTargetLufs = targetLufs) }
        applyVolumeGain(uiStateFlow.value.currentTrack?.gainDb)
        if (uiStateFlow.value.activeDevice != null) {
            remoteController.setLimiter(targetLufs = targetLufs)
        }
    }

    fun setLimiterMode(mode: String) {
        uiStateFlow.update { it.copy(currentLimiterMode = mode) }
        if (uiStateFlow.value.activeDevice != null) {
            remoteController.setLimiter(mode = mode)
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        equalizerManager.setEnabled(enabled)
        if (uiStateFlow.value.activeDevice != null) {
            remoteController.setEqualizer(enabled = enabled)
        }
    }

    fun setEqualizerBandLevel(bandIndex: Int, levelDb: Float) {
        equalizerManager.setBandLevel(bandIndex, levelDb)
        if (uiStateFlow.value.activeDevice != null) {
            remoteController.setEqualizer(bandIndex = bandIndex, levelDb = levelDb)
        }
    }

    fun setEqualizerPreset(presetName: String) {
        equalizerManager.setPreset(presetName)
        if (uiStateFlow.value.activeDevice != null) {
            remoteController.setEqualizer(preset = presetName)
        }
    }

    fun resetEqualizer() {
        equalizerManager.resetToFlat()
        if (uiStateFlow.value.activeDevice != null) {
            remoteController.setEqualizer(reset = true)
        }
    }

    fun setBassBoost(strengthPercent: Int) {
        equalizerManager.setBassBoost(strengthPercent)
        if (uiStateFlow.value.activeDevice != null) {
            remoteController.setEqualizer(bassBoost = strengthPercent)
        }
    }

    fun setVirtualizer(strengthPercent: Int) {
        equalizerManager.setVirtualizer(strengthPercent)
    }

    fun applyVolumeGain(gainDb: Double?) {
        try {
            val state = uiStateFlow.value
            val volume = calculateNormalizedVolume(
                gainDb = gainDb,
                isEnabled = state.isLoudnessNormalizationEnabled,
                targetLufs = state.loudnessTargetLufs
            )
            getMediaController()?.volume = volume
        } catch (_: Exception) {
            // Безопасный фоллбэк: избегаем крэшей если MediaController еще не подключен
        }
    }

    companion object {
        fun calculateNormalizedVolume(
            gainDb: Double?,
            isEnabled: Boolean = true,
            targetLufs: Float = -14.0f
        ): Float {
            if (!isEnabled || gainDb == null) return 1.0f
            val offsetDb = (targetLufs - (-14.0f)).toDouble()
            val effectiveGain = gainDb + offsetDb
            return 10.0.pow(effectiveGain / 20.0).toFloat().coerceIn(0.05f, 1.0f)
        }
    }
}
