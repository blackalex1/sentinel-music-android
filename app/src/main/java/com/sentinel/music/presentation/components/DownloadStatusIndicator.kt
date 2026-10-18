package com.sentinel.music.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DownloadStatusIndicator(
    isDownloaded: Boolean,
    modifier: Modifier = Modifier,
    isDownloading: Boolean = false,
    showText: Boolean = true
) {
    if (isDownloading) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = Color(0xFFBB86FC),
                modifier = Modifier.size(11.dp)
            )
            if (showText) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Загрузка...",
                    fontSize = 10.sp,
                    color = Color(0xFFBB86FC),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    } else if (isDownloaded) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0x221DB954),
            modifier = modifier
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Скачан в кеш",
                    tint = Color(0xFF1DB954),
                    modifier = Modifier.size(11.dp)
                )
                if (showText) {
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "В кеше",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1DB954)
                    )
                }
            }
        }
    }
}
