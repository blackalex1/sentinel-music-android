package com.sentinel.music.presentation.tracks.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.music.data.model.TrackDto

@Composable
fun CandidateTracksContent(
    newCandidates: List<Map<String, Any?>>,
    filteredCandidates: List<Map<String, Any?>>,
    candidateFilterOptions: List<Pair<String, String>>,
    selectedCandidateFilter: String,
    onCandidateFilterSelected: (String) -> Unit,
    searchQuery: String,
    isSearchVisible: Boolean,
    isSearchingNewCandidates: Boolean,
    candidateListState: LazyListState,
    currentTrack: TrackDto?,
    isPlaying: Boolean,
    downloadingCandidateIds: Set<String>,
    downloadedCandidateIds: Set<String>,
    onResetSearch: () -> Unit,
    onPreviewCandidate: ((Map<String, Any?>) -> Unit)? = null,
    onDownloadCandidate: (Map<String, Any?>) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier.fillMaxSize()) {
        // Фильтры по источнику / качеству (динамически на основе найденных кандидатов)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            candidateFilterOptions.forEach { (key, label) ->
                val isSelected = selectedCandidateFilter == key
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCandidateFilterSelected(key)
                    },
                    label = {
                        Text(
                            text = label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF8A2BE2),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF23202A),
                        labelColor = Color.LightGray
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (isSelected) Color(0xFF8A2BE2) else Color(0xFF383444),
                        enabled = true,
                        selected = isSelected
                    )
                )
            }
        }

        if (searchQuery.isBlank()) {
            // Приглашение к глобальному поиску
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 90.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Surface(
                        color = Color(0xFF231F2E),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.TravelExplore,
                                contentDescription = null,
                                tint = Color(0xFFBB86FC),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Глобальный поиск треков",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Введите название трека или исполнителя в поле поиска выше для поиска и загрузки через подключенные плагины сервера.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else if (isSearchingNewCandidates && newCandidates.isEmpty()) {
            // Загрузка
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 90.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF8A2BE2), strokeWidth = 3.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Поиск по подключенным плагинам сервера...",
                        color = Color.LightGray,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else if (filteredCandidates.isEmpty()) {
            // Пусто
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 90.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.SearchOff,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Новых треков не найдено",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Попробуйте изменить запрос. Если поиск ничего не находит, возможно, на сервере не подключены плагины скачивания.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            // Список кандидатов
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = "Найдено во внешних источниках: ${filteredCandidates.size}",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    if (searchQuery.isNotBlank() && !isSearchVisible) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "• Сбросить",
                            fontSize = 12.sp,
                            color = Color(0xFFBB86FC),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                onResetSearch()
                            }
                        )
                    }
                }
            }

            LazyColumn(
                state = candidateListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredCandidates, key = { it["candidate_id"] as? String ?: it.hashCode().toString() }) { cand ->
                    val cid = cand["candidate_id"] as? String ?: ""
                    val isPlayingThis = currentTrack?.isPreview == true && currentTrack.candidateId == cid && isPlaying
                    val isDownloadingThis = downloadingCandidateIds.contains(cid)
                    val isDownloadedThis = downloadedCandidateIds.contains(cid)

                    CandidateTrackCard(
                        candidate = cand,
                        isPlayingThis = isPlayingThis,
                        isDownloadingThis = isDownloadingThis,
                        isDownloadedThis = isDownloadedThis,
                        onPreviewClick = { onPreviewCandidate?.invoke(cand) },
                        onDownloadClick = { onDownloadCandidate(cand) }
                    )
                }
            }
        }
    }
}
