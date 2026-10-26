package com.sentinel.music.presentation.tracks.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
fun CandidateTrackCard(
    candidate: Map<String, Any?>,
    isPlayingThis: Boolean,
    isDownloadingThis: Boolean,
    isDownloadedThis: Boolean,
    onPreviewClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cid = candidate["candidate_id"] as? String ?: ""
    val cTitle = candidate["title"] as? String ?: "Без названия"
    val cArtist = candidate["artist"] as? String ?: "Неизвестный исполнитель"
    val cSource = candidate["source_name"] as? String ?: (candidate["source"] as? String ?: "Источник")
    val cFormat = (candidate["format"] as? String ?: "mp3").uppercase()
    val cBitrate = (candidate["bitrate"] as? Number)?.toInt()
    val cBitrateStr = if (cBitrate != null && cBitrate > 0) "$cBitrate kbps" else ""

    val vType = (candidate["version_type"] as? String ?: "").lowercase()
    val isExplicit = (candidate["explicit"] as? Boolean == true) || vType.contains("explicit")
    val vLabel = candidate["version_label"] as? String ?: when {
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

    val canPreview = candidate["can_preview"] as? Boolean ?: true

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF201C28),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Бейджи источника, типа и формата
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

            Spacer(Modifier.height(8.dp))

            // Название и артист
            Text(
                text = cTitle,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = cArtist,
                color = Color.Gray,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(10.dp))

            // Кнопки действий: Прослушать превью + Скачать в фонотеку
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canPreview) {
                    OutlinedButton(
                        onClick = onPreviewClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isPlayingThis) Color(0xFF1DB954) else Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlayingThis) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isPlayingThis) Color(0xFF1DB954) else Color.White
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isPlayingThis) "Стоп" else "Слушать",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = onDownloadClick,
                    enabled = !isDownloadingThis && !isDownloadedThis,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDownloadedThis) Color(0xFF1DB954) else Color(0xFF8A2BE2),
                        disabledContainerColor = if (isDownloadedThis) Color(0xFF1DB954) else Color(0xFF4A3B66),
                        contentColor = Color.White
                    )
                ) {
                    if (isDownloadingThis) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Загрузка...", fontSize = 12.sp)
                    } else if (isDownloadedThis) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("В медиатеке", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Скачать", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
