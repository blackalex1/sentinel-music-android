package com.sentinel.music.presentation.auth.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QrTokenDialog(
    onDismiss: () -> Unit,
    onApplyPayload: (String) -> Unit
) {
    var qrInputText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сопряжение через QR / Токен", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Вставьте JSON-строку из QR-кода веб-интерфейса или скопированный токен сессии:",
                    fontSize = 13.sp,
                    color = Color.LightGray
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = qrInputText,
                    onValueChange = { qrInputText = it },
                    placeholder = { Text("{\"url\":\"http://...\",\"token\":\"...\"}") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onApplyPayload(qrInputText)
                }
            ) {
                Text("Применить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
