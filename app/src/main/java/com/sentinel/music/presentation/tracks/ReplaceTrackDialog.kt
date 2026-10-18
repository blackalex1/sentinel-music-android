package com.sentinel.music.presentation.tracks

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.gson.Gson
import com.sentinel.music.data.api.MusicApiService
import com.sentinel.music.data.model.TrackDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReplaceTrackDialog(
    track: TrackDto,
    api: MusicApiService,
    serverUrl: String,
    token: String,
    onDismiss: () -> Unit,
    onSuccess: (TrackDto) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var rawCandidates by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf("all") }

    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var previewingCandidateId by remember { mutableStateOf<String?>(null) }
    var replacingCandidateId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                previewPlayer?.stop()
                previewPlayer?.release()
            } catch (_: Exception) {}
            previewPlayer = null
        }
    }

    fun loadCandidates() {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    api.getTrackCandidates(track.id, limit = 10)
                }
                @Suppress("UNCHECKED_CAST")
                val list = res["candidates"] as? List<Map<String, Any?>> ?: emptyList()
                rawCandidates = list
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = e.localizedMessage ?: "Сбой загрузки кандидатов"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(track.id) {
        loadCandidates()
    }

    fun togglePreview(candId: String) {
        if (previewingCandidateId == candId) {
            try {
                previewPlayer?.stop()
                previewPlayer?.release()
            } catch (_: Exception) {}
            previewPlayer = null
            previewingCandidateId = null
            return
        }

        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (_: Exception) {}
        previewPlayer = null
        previewingCandidateId = null

        val cleanUrl = serverUrl.trim().removeSuffix("/")
        val streamUrl = "$cleanUrl/api/tracks/candidates/preview?candidate_id=$candId&token=$token"

        try {
            val mp = MediaPlayer().apply {
                setDataSource(streamUrl)
                setOnPreparedListener { it.start() }
                setOnCompletionListener {
                    previewingCandidateId = null
                    try { it.release() } catch (_: Exception) {}
                    previewPlayer = null
                }
                setOnErrorListener { _, _, _ ->
                    previewingCandidateId = null
                    try { previewPlayer?.release() } catch (_: Exception) {}
                    previewPlayer = null
                    true
                }
                prepareAsync()
            }
            previewPlayer = mp
            previewingCandidateId = candId
        } catch (e: Exception) {
            e.printStackTrace()
            previewingCandidateId = null
        }
    }

    fun replaceWith(cand: Map<String, Any?>) {
        val cid = cand["candidate_id"] as? String ?: return
        replacingCandidateId = cid
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (_: Exception) {}
        previewPlayer = null
        previewingCandidateId = null

        coroutineScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    api.replaceTrack(track.id, mapOf("candidate" to cand))
                }
                val gson = Gson()
                val trackJson = gson.toJson(res["track"])
                val updatedTrack = gson.fromJson(trackJson, TrackDto::class.java)
                if (updatedTrack != null) {
                    android.widget.Toast.makeText(context, "Версия трека успешно заменена", android.widget.Toast.LENGTH_SHORT).show()
                    onSuccess(updatedTrack)
                } else {
                    onDismiss()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Ошибка замены трека: ${e.localizedMessage}"
                replacingCandidateId = null
            }
        }
    }

    // Фильтрация кандидатов по чипам (как в веб-версии)
    val filteredCandidates = remember(rawCandidates, selectedFilter) {
        rawCandidates.filter { c ->
            if (selectedFilter == "all") return@filter true
            val vType = (c["version_type"] as? String ?: "").lowercase()
            val fmt = (c["format"] as? String ?: "").lowercase()
            val src = (c["source_name"] as? String ?: c["source"] as? String ?: "").lowercase()
            val isExplicit = (c["explicit"] as? Boolean == true) || vType.contains("explicit")

            when (selectedFilter) {
                "flac" -> fmt == "flac" || vType.contains("flac")
                "explicit" -> isExplicit
                "remix" -> vType.contains("remix") || vType.contains("slowed") || vType.contains("speed")
                else -> src.equals(selectedFilter, ignoreCase = true)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1A1724)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // 1. Верхняя панель заголовка
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Не та версия / Заменить",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Текущий: ${track.artist} — ${track.title}",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.LightGray)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 2. Чипы фильтрации (Все, FLAC, 18+, Динамические источники, Ремиксы)
                val filterChips = remember(rawCandidates) {
                    val list = mutableListOf(
                        "all" to "Все",
                        "flac" to "FLAC Lossless",
                        "explicit" to "Explicit (18+)"
                    )
                    val dynamicSources = rawCandidates.mapNotNull {
                        val src = (it["source_name"] as? String ?: it["source"] as? String)?.trim()
                        if (!src.isNullOrEmpty()) src else null
                    }.distinct()
                    for (src in dynamicSources) {
                        val label = src.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        list.add(src.lowercase() to label)
                    }
                    list.add("remix" to "Remix / Slowed")
                    list
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filterChips.forEach { (key, label) ->
                        val isSelected = selectedFilter == key
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedFilter = key
                            },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF8A2BE2),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF24202C),
                                labelColor = Color.LightGray
                            )
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF2B2638))

                Spacer(Modifier.height(8.dp))

                // 3. Содержимое: Загрузка / Ошибка / Список кандидатов
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF8A2BE2), strokeWidth = 3.dp)
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "Поиск альтернативных версий через плагины сервера...",
                                color = Color.LightGray,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(errorMessage!!, color = Color(0xFFFF5252), fontSize = 13.sp)
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { loadCandidates() }) {
                                Text("Повторить поиск")
                            }
                        }
                    }
                } else if (filteredCandidates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (rawCandidates.isEmpty()) {
                                "Альтернативных версий не найдено. Проверьте, подключены ли плагины скачивания на сервере."
                            } else {
                                "По выбранному фильтру альтернативных версий не найдено."
                            },
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredCandidates, key = { it["candidate_id"] as? String ?: it.hashCode().toString() }) { cand ->
                            val cid = cand["candidate_id"] as? String ?: ""
                            val cTitle = cand["title"] as? String ?: track.title
                            val cArtist = cand["artist"] as? String ?: track.artist
                            val cSource = cand["source_name"] as? String ?: (cand["source"] as? String ?: "Источник")
                            val cFormat = (cand["format"] as? String ?: "mp3").uppercase()
                            val cBitrate = (cand["bitrate"] as? Number)?.toInt()
                            val cBitrateStr = if (cBitrate != null && cBitrate > 0) "$cBitrate kbps" else ""

                            val vType = (cand["version_type"] as? String ?: "").lowercase()
                            val isExplicit = (cand["explicit"] as? Boolean == true) || vType.contains("explicit")
                            val vLabel = cand["version_label"] as? String ?: when {
                                isExplicit -> "Explicit (18+)"
                                cFormat == "FLAC" || vType.contains("flac") -> "FLAC Lossless"
                                vType.contains("slowed") -> "Slowed"
                                vType.contains("speed") -> "Speed Up"
                                vType.contains("remix") -> "Remix"
                                else -> "Оригинал"
                            }
                            val vBadgeColor = when {
                                isExplicit -> Color(0xFFFF5252)
                                cFormat == "FLAC" || vType.contains("flac") -> Color(0xFF1DB954)
                                vType.contains("remix") || vType.contains("slowed") -> Color(0xFFE040FB)
                                else -> Color(0xFF8A2BE2)
                            }

                            val canPreview = cand["can_preview"] as? Boolean ?: true
                            val isPlayingThis = previewingCandidateId == cid
                            val isReplacingThis = replacingCandidateId == cid

                            // Карточка версии-кандидата
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF221E2C),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    // 1. Бейджи
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            color = Color(0xFF332B40),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = cSource,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFD8B4FE),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Surface(
                                            color = vBadgeColor.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = vLabel,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = vBadgeColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Surface(
                                            color = Color(0xFF2E2A38),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "$cFormat $cBitrateStr".trim(),
                                                fontSize = 10.sp,
                                                color = Color.LightGray,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(6.dp))

                                    // 2. Название и исполнитель
                                    Text(
                                        text = cTitle,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = cArtist,
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(Modifier.height(10.dp))

                                    // 3. Кнопки действий: Прослушать / Заменить
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (canPreview) {
                                            OutlinedButton(
                                                onClick = { togglePreview(cid) },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                modifier = Modifier.height(34.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = if (isPlayingThis) Color(0xFFD8B4FE) else Color.White
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlayingThis) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(if (isPlayingThis) "Стоп" else "Слушать", fontSize = 12.sp)
                                            }

                                            Spacer(Modifier.width(8.dp))
                                        }

                                        Button(
                                            onClick = { replaceWith(cand) },
                                            enabled = replacingCandidateId == null,
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF8A2BE2)
                                            )
                                        ) {
                                            if (isReplacingThis) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    color = Color.White,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text("Загрузка...", fontSize = 12.sp)
                                            } else {
                                                Text("Заменить", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
