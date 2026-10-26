package com.sentinel.music.presentation.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.music.data.model.RemoteDeviceDto
import com.sentinel.music.presentation.navigation.MainTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopAppBar(
    selectedTab: MainTab,
    isSearchVisible: Boolean,
    isSearchActive: Boolean,
    isQueueAvailable: Boolean,
    activeDevice: RemoteDeviceDto?,
    isPcOnline: Boolean,
    onSearchClick: () -> Unit,
    onQueueClick: () -> Unit,
    onCastClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = selectedTab.title,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White
            )
        },
        actions = {
            // Кнопка поиска
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Поиск",
                    tint = if ((isSearchVisible || isSearchActive) && selectedTab == MainTab.TRACKS) {
                        Color(0xFFBB86FC)
                    } else {
                        Color.LightGray
                    }
                )
            }

            // Кнопка открытия текущей очереди треков
            if (isQueueAvailable) {
                IconButton(onClick = onQueueClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Очередь воспроизведения",
                        tint = Color(0xFFBB86FC)
                    )
                }
            }

            // Трансляция на ПК (Sentinel Connect)
            IconButton(onClick = onCastClick) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(
                        imageVector = Icons.Filled.Cast,
                        contentDescription = "Трансляция на ПК",
                        tint = if (activeDevice != null) {
                            Color(0xFF1DB954)
                        } else if (isPcOnline) {
                            Color(0xFFBB86FC)
                        } else {
                            Color.Gray
                        }
                    )
                    if (isPcOnline) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1DB954))
                        )
                    }
                }
            }

            // Настройки приложения
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Настройки",
                    tint = if (selectedTab == MainTab.SETTINGS) Color(0xFFBB86FC) else Color.LightGray
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF16141C))
    )
}
