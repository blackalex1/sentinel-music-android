package com.sentinel.music.presentation.tracks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.presentation.components.media.TrackCover

/**
 * Диалоговое окно подтверждения удаления трека с сервера.
 * Позволяет выбрать, удалять ли также файл с диска сервера.
 */
@Composable
fun DeleteTrackDialog(
    track: TrackDto,
    serverUrl: String,
    token: String,
    onDismiss: () -> Unit,
    onConfirmDelete: (deleteFile: Boolean) -> Unit
) {
    var deleteFileOnDisk by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E1A26),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Заголовок
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0x33FF5252), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteForever,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = "Удалить с сервера",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Вы уверены, что хотите удалить этот трек из фонотеки сервера?",
                    fontSize = 14.sp,
                    color = Color.LightGray,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(14.dp))

                // Карточка трека
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF282334),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackCover(
                            trackId = track.id,
                            serverUrl = serverUrl,
                            token = token,
                            size = 46.dp,
                            shapeRadius = 8.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = track.artist,
                                color = Color.Gray,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Чекбокс удаления файла с диска
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = deleteFileOnDisk,
                        onCheckedChange = { deleteFileOnDisk = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFFFF5252),
                            uncheckedColor = Color.Gray,
                            checkmarkColor = Color.White
                        )
                    )
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Удалить аудиофайл с диска",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            text = if (deleteFileOnDisk) "Файл трека и текст (.lrc) будут стёрты с сервера"
                            else "Запись будет удалена только из базы данных",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                // Кнопки действий
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Отмена", color = Color.LightGray, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { onConfirmDelete(deleteFileOnDisk) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5252),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Удалить", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
