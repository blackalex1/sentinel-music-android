package com.sentinel.music.presentation.tracks.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.music.presentation.main.SearchTab

@Composable
fun TracksSearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchTab: SearchTab,
    onSearchTabChange: (SearchTab) -> Unit,
    isSearching: Boolean,
    isSearchingNewCandidates: Boolean,
    focusRequester: FocusRequester,
    onCloseSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = modifier) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = if (searchTab == SearchTab.LIBRARY) {
                        "Поиск в медиатеке (название, артист, слова)..."
                    } else {
                        "Поиск новых треков через плагины..."
                    },
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                IconButton(onClick = {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Фокус поиска",
                        tint = if (searchQuery.isNotEmpty()) Color(0xFFD8B4FE) else Color.Gray
                    )
                }
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            onSearchQueryChange("")
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Очистить", tint = Color.LightGray)
                        }
                    }
                    IconButton(onClick = onCloseSearch) {
                        Icon(Icons.Filled.ExpandLess, contentDescription = "Скрыть поиск", tint = Color.Gray)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .padding(top = 8.dp, bottom = 4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF8A2BE2),
                unfocusedBorderColor = Color(0xFF333333),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        // 2 вкладки в поиске: «В медиатеке» и «Новые треки»
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(Color(0xFF1E1A26), RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (searchTab == SearchTab.LIBRARY) Color(0xFF8A2BE2) else Color.Transparent)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSearchTabChange(SearchTab.LIBRARY)
                    }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LibraryMusic,
                        contentDescription = null,
                        tint = if (searchTab == SearchTab.LIBRARY) Color.White else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "В медиатеке",
                        color = if (searchTab == SearchTab.LIBRARY) Color.White else Color.LightGray,
                        fontWeight = if (searchTab == SearchTab.LIBRARY) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (searchTab == SearchTab.NEW_TRACKS) Color(0xFF8A2BE2) else Color.Transparent)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSearchTabChange(SearchTab.NEW_TRACKS)
                    }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.TravelExplore,
                        contentDescription = null,
                        tint = if (searchTab == SearchTab.NEW_TRACKS) Color.White else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Новые треки",
                        color = if (searchTab == SearchTab.NEW_TRACKS) Color.White else Color.LightGray,
                        fontWeight = if (searchTab == SearchTab.NEW_TRACKS) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Индикатор выполнения серверного поиска
        val currentSearching = if (searchTab == SearchTab.LIBRARY) isSearching else isSearchingNewCandidates
        if (currentSearching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .padding(vertical = 1.dp),
                color = Color(0xFF8A2BE2),
                trackColor = Color(0x338A2BE2)
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }
    }
}
