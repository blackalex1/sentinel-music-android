package com.sentinel.music.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

@Composable
fun AppBottomNavigation(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        containerColor = Color(0xFF16141C),
        contentColor = Color.White,
        modifier = modifier
    ) {
        NavigationBarItem(
            selected = selectedTab == MainTab.HOME,
            onClick = { onTabSelected(MainTab.HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("Главная", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF8A2BE2),
                selectedTextColor = Color(0xFF8A2BE2),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color(0x338A2BE2)
            )
        )
        NavigationBarItem(
            selected = selectedTab == MainTab.STREAM,
            onClick = { onTabSelected(MainTab.STREAM) },
            icon = { Icon(Icons.Filled.ElectricBolt, contentDescription = null) },
            label = { Text("Поток", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF8A2BE2),
                selectedTextColor = Color(0xFF8A2BE2),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color(0x338A2BE2)
            )
        )
        NavigationBarItem(
            selected = selectedTab == MainTab.TRACKS,
            onClick = { onTabSelected(MainTab.TRACKS) },
            icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
            label = { Text("Треки", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF8A2BE2),
                selectedTextColor = Color(0xFF8A2BE2),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color(0x338A2BE2)
            )
        )
        NavigationBarItem(
            selected = selectedTab == MainTab.PLAYLISTS,
            onClick = { onTabSelected(MainTab.PLAYLISTS) },
            icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
            label = { Text("Плейлисты", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF8A2BE2),
                selectedTextColor = Color(0xFF8A2BE2),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color(0x338A2BE2)
            )
        )
        NavigationBarItem(
            selected = selectedTab == MainTab.SETTINGS,
            onClick = { onTabSelected(MainTab.SETTINGS) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text("Настройки", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF8A2BE2),
                selectedTextColor = Color(0xFF8A2BE2),
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color(0x338A2BE2)
            )
        )
    }
}
