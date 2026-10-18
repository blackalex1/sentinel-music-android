package com.sentinel.music.player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

data class EqBand(
    val index: Int,
    val centerFreqHz: Int,
    val minLevelDb: Float = -12.0f,
    val maxLevelDb: Float = 12.0f,
    val currentLevelDb: Float = 0.0f
)

data class EqualizerState(
    val isEnabled: Boolean = false,
    val bands: List<EqBand> = emptyList(),
    val currentPreset: String = "Обычный (Flat)",
    val bassBoostStrength: Int = 0, // 0..1000 (0%..100%)
    val virtualizerStrength: Int = 0 // 0..1000 (0%..100%)
)

class EqualizerManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("sentinel_music_equalizer", Context.MODE_PRIVATE)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var currentAudioSessionId: Int = 0

    private val defaultBands = listOf(
        EqBand(0, 60, -12f, 12f, 0f),
        EqBand(1, 230, -12f, 12f, 0f),
        EqBand(2, 910, -12f, 12f, 0f),
        EqBand(3, 3600, -12f, 12f, 0f),
        EqBand(4, 14000, -12f, 12f, 0f)
    )

    private val _state = MutableStateFlow(
        EqualizerState(
            isEnabled = prefs.getBoolean(KEY_ENABLED, false),
            bands = defaultBands,
            currentPreset = prefs.getString(KEY_PRESET, "Обычный (Flat)") ?: "Обычный (Flat)",
            bassBoostStrength = prefs.getInt(KEY_BASS_BOOST, 0),
            virtualizerStrength = prefs.getInt(KEY_VIRTUALIZER, 0)
        )
    )
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    companion object {
        private const val TAG = "EqualizerManager"
        private const val KEY_ENABLED = "eq_enabled"
        private const val KEY_PRESET = "eq_preset"
        private const val KEY_BASS_BOOST = "eq_bass_boost"
        private const val KEY_VIRTUALIZER = "eq_virtualizer"
        private const val KEY_BAND_PREFIX = "eq_band_"

        val PRESET_VALUES = mapOf(
            "Обычный (Flat)" to listOf(0f, 0f, 0f, 0f, 0f),
            "Рок" to listOf(4.5f, 2.0f, -1.0f, 2.5f, 4.5f),
            "Поп" to listOf(-1.0f, 2.0f, 4.0f, 2.0f, -1.0f),
            "Джаз" to listOf(3.5f, 2.0f, 0.0f, 2.0f, 3.5f),
            "Хип-Хоп" to listOf(5.5f, 3.0f, 0.0f, 1.5f, 3.5f),
            "Электроника" to listOf(4.5f, 2.0f, 0.0f, 3.0f, 4.5f),
            "Классика" to listOf(3.5f, 2.5f, 0.0f, 2.0f, 3.5f),
            "Усиление вокала" to listOf(-2.0f, 1.0f, 4.0f, 2.0f, -1.0f),
            "Глубокий бас" to listOf(6.0f, 4.0f, 1.5f, 0.0f, 0.0f)
        )

        @Volatile
        private var instance: EqualizerManager? = null

        fun getInstance(context: Context): EqualizerManager {
            return instance ?: synchronized(this) {
                instance ?: EqualizerManager(context).also { instance = it }
            }
        }
    }

    init {
        loadSavedBands()
    }

    @Synchronized
    fun attachAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == currentAudioSessionId) return
        currentAudioSessionId = audioSessionId

        releaseEffects()

        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = _state.value.isEnabled
            }

            // Инициализация полос из реального аппаратного эквалайзера если доступно
            updateBandsFromHardware(equalizer!!)

            bassBoost = BassBoost(0, audioSessionId).apply {
                if (strengthSupported) {
                    setStrength(_state.value.bassBoostStrength.toShort())
                    enabled = _state.value.isEnabled && _state.value.bassBoostStrength > 0
                }
            }

            virtualizer = Virtualizer(0, audioSessionId).apply {
                if (strengthSupported) {
                    setStrength(_state.value.virtualizerStrength.toShort())
                    enabled = _state.value.isEnabled && _state.value.virtualizerStrength > 0
                }
            }

            applyAllToHardware()
            Log.d(TAG, "AudioFX attached to session $audioSessionId successfully")
        } catch (e: Exception) {
            Log.w(TAG, "AudioFX hardware init error: ${e.message}")
        }
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _state.update { it.copy(isEnabled = enabled) }

        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled && _state.value.bassBoostStrength > 0
            virtualizer?.enabled = enabled && _state.value.virtualizerStrength > 0
        } catch (e: Exception) {
            Log.w(TAG, "Failed to toggle AudioFX: ${e.message}")
        }
    }

    fun setBandLevel(bandIndex: Int, levelDb: Float) {
        val currentBands = _state.value.bands.toMutableList()
        val band = currentBands.find { it.index == bandIndex } ?: return
        val clamped = levelDb.coerceIn(band.minLevelDb, band.maxLevelDb)

        currentBands[bandIndex] = band.copy(currentLevelDb = clamped)
        prefs.edit().putFloat("$KEY_BAND_PREFIX$bandIndex", clamped).putString(KEY_PRESET, "Пользовательский").apply()

        _state.update {
            it.copy(
                bands = currentBands,
                currentPreset = "Пользовательский"
            )
        }

        try {
            // В Android Equalizer уровень задаётся в миллибелах (1 dB = 100 mB)
            val millibels = (clamped * 100).roundToInt().toShort()
            equalizer?.setBandLevel(bandIndex.toShort(), millibels)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set band level: ${e.message}")
        }
    }

    fun setPreset(presetName: String) {
        val presetValues = PRESET_VALUES[presetName] ?: return
        val currentBands = _state.value.bands.toMutableList()

        for (i in currentBands.indices) {
            val level = presetValues.getOrElse(i) { 0f }
            val band = currentBands[i]
            val clamped = level.coerceIn(band.minLevelDb, band.maxLevelDb)
            currentBands[i] = band.copy(currentLevelDb = clamped)
            prefs.edit().putFloat("$KEY_BAND_PREFIX$i", clamped)
        }

        prefs.edit().putString(KEY_PRESET, presetName).apply()
        _state.update {
            it.copy(
                bands = currentBands,
                currentPreset = presetName
            )
        }

        applyBandsToHardware(currentBands)
    }

    fun resetToFlat() {
        setPreset("Обычный (Flat)")
        setBassBoost(0)
        setVirtualizer(0)
    }

    fun setBassBoost(strengthPercent: Int) {
        // strengthPercent: 0..100 -> millibels strength 0..1000
        val clamped = strengthPercent.coerceIn(0, 100)
        val strength = (clamped * 10).coerceIn(0, 1000)

        prefs.edit().putInt(KEY_BASS_BOOST, strength).apply()
        _state.update { it.copy(bassBoostStrength = strength) }

        try {
            bassBoost?.let {
                if (it.strengthSupported) {
                    it.setStrength(strength.toShort())
                    it.enabled = _state.value.isEnabled && strength > 0
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set BassBoost: ${e.message}")
        }
    }

    fun setVirtualizer(strengthPercent: Int) {
        val clamped = strengthPercent.coerceIn(0, 100)
        val strength = (clamped * 10).coerceIn(0, 1000)

        prefs.edit().putInt(KEY_VIRTUALIZER, strength).apply()
        _state.update { it.copy(virtualizerStrength = strength) }

        try {
            virtualizer?.let {
                if (it.strengthSupported) {
                    it.setStrength(strength.toShort())
                    it.enabled = _state.value.isEnabled && strength > 0
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set Virtualizer: ${e.message}")
        }
    }

    fun applyRemoteEqualizer(remoteEq: com.sentinel.music.data.model.RemoteEqualizerDto) {
        val currentBands = _state.value.bands.toMutableList()
        val levels = remoteEq.safeBandLevels
        if (levels.isNotEmpty()) {
            for (i in currentBands.indices) {
                val lvl = levels.getOrElse(i) { 0f }
                val b = currentBands[i]
                currentBands[i] = b.copy(currentLevelDb = lvl.coerceIn(b.minLevelDb, b.maxLevelDb))
            }
        }
        val bbStrength = (remoteEq.bassBoostPercent * 10).coerceIn(0, 1000)
        _state.update {
            it.copy(
                isEnabled = remoteEq.isEnabled,
                bands = currentBands,
                currentPreset = remoteEq.safePreset,
                bassBoostStrength = bbStrength
            )
        }
        applyAllToHardware()
    }

    private fun loadSavedBands() {
        val savedPreset = prefs.getString(KEY_PRESET, "Обычный (Flat)") ?: "Обычный (Flat)"
        val bands = defaultBands.map { band ->
            val saved = prefs.getFloat("$KEY_BAND_PREFIX${band.index}", band.currentLevelDb)
            band.copy(currentLevelDb = saved)
        }
        _state.update {
            it.copy(
                bands = bands,
                currentPreset = savedPreset
            )
        }
    }

    private fun updateBandsFromHardware(eq: Equalizer) {
        try {
            val numBands = eq.numberOfBands.toInt().coerceAtLeast(1)
            val range = eq.bandLevelRange // e.g. [-1500, +1500]
            val minDb = range[0].toFloat() / 100f
            val maxDb = range[1].toFloat() / 100f

            val hardwareBands = mutableListOf<EqBand>()
            for (i in 0 until numBands) {
                val centerHz = eq.getCenterFreq(i.toShort()) / 1000
                val savedLevel = prefs.getFloat("$KEY_BAND_PREFIX$i", 0f)
                hardwareBands.add(
                    EqBand(
                        index = i,
                        centerFreqHz = centerHz,
                        minLevelDb = minDb,
                        maxLevelDb = maxDb,
                        currentLevelDb = savedLevel.coerceIn(minDb, maxDb)
                    )
                )
            }
            if (hardwareBands.isNotEmpty()) {
                _state.update { it.copy(bands = hardwareBands) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying hardware bands: ${e.message}")
        }
    }

    private fun applyAllToHardware() {
        try {
            equalizer?.enabled = _state.value.isEnabled
            applyBandsToHardware(_state.value.bands)

            val bb = _state.value.bassBoostStrength
            bassBoost?.let {
                if (it.strengthSupported) {
                    it.setStrength(bb.toShort())
                    it.enabled = _state.value.isEnabled && bb > 0
                }
            }

            val virt = _state.value.virtualizerStrength
            virtualizer?.let {
                if (it.strengthSupported) {
                    it.setStrength(virt.toShort())
                    it.enabled = _state.value.isEnabled && virt > 0
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed applying settings to hardware: ${e.message}")
        }
    }

    private fun applyBandsToHardware(bands: List<EqBand>) {
        try {
            val eq = equalizer ?: return
            for (band in bands) {
                val millibels = (band.currentLevelDb * 100).roundToInt().toShort()
                eq.setBandLevel(band.index.toShort(), millibels)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed applying bands: ${e.message}")
        }
    }

    fun release() {
        releaseEffects()
        currentAudioSessionId = 0
    }

    private fun releaseEffects() {
        try {
            equalizer?.release()
        } catch (_: Exception) {}
        equalizer = null

        try {
            bassBoost?.release()
        } catch (_: Exception) {}
        bassBoost = null

        try {
            virtualizer?.release()
        } catch (_: Exception) {}
        virtualizer = null
    }
}
