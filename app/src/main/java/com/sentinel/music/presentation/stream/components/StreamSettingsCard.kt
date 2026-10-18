package com.sentinel.music.presentation.stream.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.music.presentation.stream.models.STREAM_DISCOVERY_OPTIONS
import com.sentinel.music.presentation.stream.models.STREAM_MOODS
import kotlin.math.abs

@Composable
fun StreamSettingsCard(
    selectedMood: String,
    selectedDiscoveryRatio: Float,
    onMoodSelected: (String) -> Unit,
    onDiscoveryRatioSelected: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF131722),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF22293A)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Заголовок «НАСТРОЕНИЕ»
            Text(
                text = "НАСТРОЕНИЕ",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8892B0),
                letterSpacing = 1.3.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Горизонтальный ряд чипов настроения
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                STREAM_MOODS.forEach { mood ->
                    val isSelected = mood.id == selectedMood
                    Surface(
                        onClick = { onMoodSelected(mood.id) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) Color.Transparent else Color(0xFF1B2030),
                        border = if (isSelected) {
                            BorderStroke(1.dp, Color(0xFF9D4EDD))
                        } else {
                            BorderStroke(1.dp, Color(0xFF2E384D))
                        },
                        modifier = Modifier.then(
                            if (isSelected) {
                                Modifier.background(
                                    Brush.horizontalGradient(mood.gradientColors),
                                    shape = RoundedCornerShape(18.dp)
                                )
                            } else Modifier
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = mood.icon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = mood.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Заголовок «НОВАЯ МУЗЫКА»
            Text(
                text = "НОВАЯ МУЗЫКА",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8892B0),
                letterSpacing = 1.3.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Горизонтальный ряд чипов частоты новых треков
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                STREAM_DISCOVERY_OPTIONS.forEach { disc ->
                    val isSelected = abs(disc.ratio - selectedDiscoveryRatio) < 0.05f
                    Surface(
                        onClick = { onDiscoveryRatioSelected(disc.ratio) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) Color(0xFF0A2E3D) else Color(0xFF1B2030),
                        border = if (isSelected) {
                            BorderStroke(1.5.dp, Color(0xFF00B4D8))
                        } else {
                            BorderStroke(1.dp, Color(0xFF2E384D))
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = disc.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                            )
                            Spacer(Modifier.width(5.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFF0284C7) else Color(0x33475569)
                            ) {
                                Text(
                                    text = disc.badge,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color(0xFF64748B),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
