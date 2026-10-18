package com.sentinel.music.presentation.auth.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AuthModeToggle(
    isRegisterMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    TabRow(
        selectedTabIndex = if (isRegisterMode) 1 else 0,
        containerColor = Color(0xFF1B1B26),
        contentColor = Color.White,
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                Modifier.tabIndicatorOffset(tabPositions[if (isRegisterMode) 1 else 0]),
                color = Color(0xFF8A2BE2)
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
    ) {
        Tab(
            selected = !isRegisterMode,
            onClick = { onModeChange(false) },
            text = {
                Text(
                    text = "Вход",
                    fontWeight = if (!isRegisterMode) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                )
            }
        )
        Tab(
            selected = isRegisterMode,
            onClick = { onModeChange(true) },
            text = {
                Text(
                    text = "Регистрация",
                    fontWeight = if (isRegisterMode) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                )
            }
        )
    }
}
