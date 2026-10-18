package com.sentinel.music.presentation.components.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.player.EqualizerManager
import com.sentinel.music.player.EqualizerState

data class LoudnessPreset(
    val lufs: Float,
    val title: String,
    val subtitle: String
)

val LOUDNESS_PRESETS = listOf(
    LoudnessPreset(
        lufs = -14.0f,
        title = "-14 LUFS (Стриминг)",
        subtitle = "Общепринятый стриминговый стандарт (-14 LUFS). Оптимально для наушников."
    ),
    LoudnessPreset(
        lufs = -18.0f,
        title = "-18 LUFS (Hi-Fi)",
        subtitle = "Расширенный динамический диапазон для акустических систем."
    ),
    LoudnessPreset(
        lufs = -23.0f,
        title = "-23 LUFS (EBU R128)",
        subtitle = "Классический вещательный стандарт студийной звукорежиссуры."
    )
)

data class LimiterModeItem(
    val id: String,
    val title: String,
    val badge: String,
    val description: String
)

val LIMITER_MODES = listOf(
    LimiterModeItem("soft", "Мягкий (Soft)", "Рекомендуется", "Прозрачный лимитер без пампинга и заиканий звука"),
    LimiterModeItem("balanced", "Студийный (Studio)", "Balanced", "Сбалансированная динамика и плотный контроль пиков"),
    LimiterModeItem("hard", "Защитный (Brickwall)", "Brickwall", "Строгий лимитер для защиты динамиков от клиппинга"),
    LimiterModeItem("night", "Ночной режим (Night)", "Night", "Выравнивание тихих и громких звуков для комфорта ночью"),
    LimiterModeItem("off", "Выключен (Bypass)", "Bypass", "Чистый оригинальный звук без вмешательства в динамику")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoudnessDialog(
    // 1. Выравнивание громкости
    isEnabled: Boolean,
    currentTargetLufs: Float,
    currentTrack: TrackDto?,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectTargetLufs: (Float) -> Unit,
    // 2. Лимитер
    currentLimiterMode: String = "soft",
    onSelectLimiterMode: (String) -> Unit = {},
    // 3. Эквалайзер
    equalizerState: EqualizerState,
    onToggleEqualizer: (Boolean) -> Unit,
    onSetBandLevel: (Int, Float) -> Unit,
    onSelectPreset: (String) -> Unit,
    onResetEqualizer: () -> Unit,
    onSetBassBoost: (Int) -> Unit,
    onSetVirtualizer: (Int) -> Unit,
    // 4. Управление окном
    initialTab: Int = 0,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(initialTab) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1A26),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Gray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
        ) {
            // Верхняя шапка
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (selectedTab == 0) Icons.Filled.Tune else Icons.Filled.Equalizer,
                        contentDescription = null,
                        tint = Color(0xFF8A2BE2),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (selectedTab == 0) "Единый уровень громкости" else "Эквалайзер звука",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (selectedTab == 0) "EBU R128 / ReplayGain" else "Аппаратная калибровка частот",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.Gray)
                }
            }

            // Переключатель вкладок: Громкость (LUFS) и Эквалайзер (EQ)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedTab = 0 },
                    color = if (selectedTab == 0) Color(0xFF8A2BE2) else Color(0xFF262132),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Tune,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Громкость (LUFS)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedTab = 1 },
                    color = if (selectedTab == 1) Color(0xFF8A2BE2) else Color(0xFF262132),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Equalizer,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Эквалайзер (EQ)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // =====================================================================
            // ВКЛАДКА 0: НОРМАЛИЗАЦИЯ ГРОМКОСТИ
            // =====================================================================
            if (selectedTab == 0) {
                // Мастер-тумблер нормализации
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onToggleEnabled(!isEnabled) },
                    color = Color(0xFF262132),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Выравнивание громкости",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Устраняет резкие скачки громкости между тихими и громкими записями",
                                fontSize = 12.sp,
                                color = Color(0xFFB0A8C0),
                                modifier = Modifier.padding(top = 4.dp, end = 12.dp)
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = onToggleEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF8A2BE2),
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color(0xFF383244)
                            )
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Информация о текущем треке
                if (currentTrack != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF16131D)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Текущий трек: ${currentTrack.artist} — ${currentTrack.title}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Исходная громкость", fontSize = 11.sp, color = Color.Gray)
                                    val lufs = currentTrack.loudnessLufs
                                    Text(
                                        text = if (lufs != null) "%.1f LUFS".format(lufs) else "Не измерена",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00B4DB)
                                    )
                                }
                                Column {
                                    Text("Коррекция (Gain)", fontSize = 11.sp, color = Color.Gray)
                                    val gain = currentTrack.gainDb
                                    Text(
                                        text = if (gain != null) {
                                            (if (gain >= 0) "+%.1f dB" else "%.1f dB").format(gain)
                                        } else "0.0 dB",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (gain != null && gain > 0) Color(0xFFFFB300) else Color(0xFF1DB954)
                                    )
                                }
                                Column {
                                    Text("Статус", fontSize = 11.sp, color = Color.Gray)
                                    Text(
                                        text = if (isEnabled) "Применено" else "Отключено",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isEnabled) Color(0xFF8A2BE2) else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Целевые стандарты
                AnimatedVisibility(visible = isEnabled) {
                    Column {
                        Text(
                            text = "Целевой стандарт громкости",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LOUDNESS_PRESETS.forEach { preset ->
                                val isSelected = kotlin.math.abs(currentTargetLufs - preset.lufs) < 0.1f
                                val shape = RoundedCornerShape(12.dp)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(shape)
                                        .clickable { onSelectTargetLufs(preset.lufs) }
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) Color(0xFF8A2BE2) else Color(0xFF2E293A),
                                            shape = shape
                                        ),
                                    color = if (isSelected) Color(0xFF2C1E40) else Color(0xFF211D2B),
                                    shape = shape
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = preset.title,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 14.sp,
                                                color = if (isSelected) Color.White else Color.LightGray
                                            )
                                            Text(
                                                text = preset.subtitle,
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = Color(0xFF8A2BE2),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Динамический Лимитер (DSP)
                Text(
                    text = "Динамический лимитер (DSP)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Предотвращает перегрузку и клиппинг при усилении эквалайзера",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF16131D)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LIMITER_MODES.forEach { mode ->
                            val isSelected = currentLimiterMode == mode.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectLimiterMode(mode.id) },
                                color = if (isSelected) Color(0xFF2B223D) else Color(0xFF1E1A26),
                                shape = RoundedCornerShape(12.dp),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8A2BE2)) else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = mode.title,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 14.sp,
                                                color = if (isSelected) Color.White else Color.LightGray
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Surface(
                                                color = if (mode.id == "soft") Color(0x331DB954) else Color(0x33BB86FC),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = mode.badge,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (mode.id == "soft") Color(0xFF1DB954) else Color(0xFFBB86FC),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = mode.description,
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF8A2BE2),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =====================================================================
            // ВКЛАДКА 1: ЭКВАЛАЙЗЕР
            // =====================================================================
            if (selectedTab == 1) {
                // Мастер-тумблер эквалайзера
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onToggleEqualizer(!equalizerState.isEnabled) },
                    color = Color(0xFF262132),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Включить эквалайзер",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Кастомизация тембра и амплитуды частотных диапазонов",
                                fontSize = 12.sp,
                                color = Color(0xFFB0A8C0),
                                modifier = Modifier.padding(top = 4.dp, end = 12.dp)
                            )
                        }
                        Switch(
                            checked = equalizerState.isEnabled,
                            onCheckedChange = onToggleEqualizer,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF8A2BE2),
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color(0xFF383244)
                            )
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Горизонтальный список пресетов
                Text(
                    text = "Пресеты звучания",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                val allPresets = EqualizerManager.PRESET_VALUES.keys.toList() + "Пользовательский"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allPresets.forEach { preset ->
                        val isSelected = equalizerState.currentPreset == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectPreset(preset) },
                            enabled = equalizerState.isEnabled,
                            label = {
                                Text(
                                    preset,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF8A2BE2),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF262132),
                                labelColor = Color.LightGray,
                                disabledContainerColor = Color(0xFF1E1A26),
                                disabledLabelColor = Color.DarkGray
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = if (isSelected) Color(0xFF8A2BE2) else Color(0xFF383244),
                                enabled = equalizerState.isEnabled,
                                selected = isSelected
                            )
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 5 Частотных полос со слайдерами
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF231E2E)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Частотные диапазоны",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            TextButton(
                                onClick = onResetEqualizer,
                                enabled = equalizerState.isEnabled,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Сброс", fontSize = 12.sp)
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        equalizerState.bands.forEach { band ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.width(76.dp)) {
                                    val freqLabel = if (band.centerFreqHz < 1000) {
                                        "${band.centerFreqHz} Гц"
                                    } else {
                                        "${"%.1f".format(band.centerFreqHz / 1000f)} кГц"
                                    }
                                    Text(
                                        text = freqLabel,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (equalizerState.isEnabled) Color.White else Color.Gray
                                    )
                                    val bandDesc = when (band.index) {
                                        0 -> "Суббас"
                                        1 -> "Бас"
                                        2 -> "Средние"
                                        3 -> "ВЧ-мид"
                                        else -> "Воздух"
                                    }
                                    Text(
                                        text = bandDesc,
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }

                                Slider(
                                    value = band.currentLevelDb,
                                    onValueChange = { onSetBandLevel(band.index, it) },
                                    valueRange = band.minLevelDb..band.maxLevelDb,
                                    enabled = equalizerState.isEnabled,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color(0xFF8A2BE2),
                                        inactiveTrackColor = Color(0xFF383244),
                                        disabledThumbColor = Color.Gray,
                                        disabledActiveTrackColor = Color(0xFF484055)
                                    )
                                )

                                val levelFormatted = if (band.currentLevelDb > 0.05f) {
                                    "+%.1f dB".format(band.currentLevelDb)
                                } else {
                                    "%.1f dB".format(band.currentLevelDb)
                                }
                                Text(
                                    text = levelFormatted,
                                    color = if (equalizerState.isEnabled && kotlin.math.abs(band.currentLevelDb) > 0.1f) {
                                        Color(0xFF8A2BE2)
                                    } else Color.LightGray,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.width(62.dp),
                                    textAlign = TextAlign.End,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Звуковые эффекты: Bass Boost & Virtualizer
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF231E2E)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Звуковые эффекты",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // 1. Усиление баса (Bass Boost)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.width(100.dp)) {
                                Text("Bass Boost", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("Суббас", fontSize = 10.sp, color = Color.Gray)
                            }
                            val bbPercent = (equalizerState.bassBoostStrength / 10).toFloat()
                            Slider(
                                value = bbPercent,
                                onValueChange = { onSetBassBoost(it.toInt()) },
                                valueRange = 0f..100f,
                                enabled = equalizerState.isEnabled,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color(0xFFFF512F),
                                    inactiveTrackColor = Color(0xFF383244)
                                )
                            )
                            Text(
                                text = "${bbPercent.toInt()}%",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = if (bbPercent > 0 && equalizerState.isEnabled) Color(0xFFFF512F) else Color.LightGray,
                                modifier = Modifier.width(45.dp),
                                textAlign = TextAlign.End
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        // 2. Объёмный звук (Virtualizer)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.width(100.dp)) {
                                Text("Объём 3D", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("Виртуализатор", fontSize = 10.sp, color = Color.Gray)
                            }
                            val virtPercent = (equalizerState.virtualizerStrength / 10).toFloat()
                            Slider(
                                value = virtPercent,
                                onValueChange = { onSetVirtualizer(it.toInt()) },
                                valueRange = 0f..100f,
                                enabled = equalizerState.isEnabled,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color(0xFF00B4DB),
                                    inactiveTrackColor = Color(0xFF383244)
                                )
                            )
                            Text(
                                text = "${virtPercent.toInt()}%",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = if (virtPercent > 0 && equalizerState.isEnabled) Color(0xFF00B4DB) else Color.LightGray,
                                modifier = Modifier.width(45.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }
    }
}
