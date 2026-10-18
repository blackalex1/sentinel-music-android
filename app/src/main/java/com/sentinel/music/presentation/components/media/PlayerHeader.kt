package com.sentinel.music.presentation.components.media

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlayerHeader(
    audioFormat: String,
    showLyrics: Boolean = false,
    onDismiss: () -> Unit,
    onToggleLyrics: (() -> Unit)? = null,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    onToggleDownload: (() -> Unit)? = null,
    onOpenQueue: (() -> Unit)? = null,
    activeDeviceName: String? = null,
    onOpenDevices: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Свернуть",
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = if (activeDeviceName != null) "ИГРАЕТ НА: ${activeDeviceName.uppercase()}" else "СЕЙЧАС ИГРАЕТ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (activeDeviceName != null) Color(0xFF1DB954) else Color.LightGray,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Surface(
                    color = if (audioFormat.equals("flac", ignoreCase = true)) Color(0xFF1DB954) else Color(0xFF444444),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = audioFormat.uppercase(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(Modifier.width(6.dp))

                Surface(
                    color = if (isDownloaded) Color(0x331DB954) else Color(0x33BB86FC),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                strokeWidth = 1.5.dp,
                                color = Color(0xFFBB86FC),
                                modifier = Modifier.size(9.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "ЗАГРУЗКА",
                                color = Color(0xFFBB86FC),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (isDownloaded) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF1DB954),
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "В КЕШЕ",
                                color = Color(0xFF1DB954),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Wifi,
                                contentDescription = null,
                                tint = Color(0xFFBB86FC),
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "ОНЛАЙН",
                                color = Color(0xFFBB86FC),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            // Кнопка сохранения в кеш / удаления из кеша
            if (onToggleDownload != null) {
                IconButton(
                    onClick = onToggleDownload,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (isDownloaded) Icons.Filled.CloudDone else Icons.Filled.CloudDownload,
                        contentDescription = if (isDownloaded) "В кеше" else "Скачать в кеш",
                        tint = if (isDownloaded) Color(0xFF1DB954) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Кнопка выбора устройства воспроизведения (Sentinel Connect)
            if (onOpenDevices != null) {
                IconButton(
                    onClick = onOpenDevices,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cast,
                        contentDescription = "Устройства воспроизведения (Sentinel Connect)",
                        tint = if (activeDeviceName != null) Color(0xFF1DB954) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Кнопка очереди воспроизведения
            if (onOpenQueue != null) {
                IconButton(
                    onClick = onOpenQueue,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Очередь воспроизведения",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
