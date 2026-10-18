package com.sentinel.music.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.sentinel.music.data.model.AuthRequest
import com.sentinel.music.data.api.NetworkClient
import com.sentinel.music.data.session.SessionManager
import com.sentinel.music.presentation.auth.components.AuthHeader
import com.sentinel.music.presentation.auth.components.AuthModeToggle
import com.sentinel.music.presentation.auth.components.ManualLoginForm
import com.sentinel.music.presentation.auth.components.QrCameraScannerDialog
import com.sentinel.music.presentation.auth.components.QrTokenDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun LoginScreen(
    sessionManager: SessionManager,
    onLoginSuccess: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var isRegisterMode by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var showCameraScanner by remember { mutableStateOf(false) }
    var showManualQrDialog by remember { mutableStateOf(false) }

    fun performAuth() {
        errorMessage = null
        infoMessage = null
        focusManager.clearFocus()

        val cleanUrl = serverUrl.trim().removeSuffix("/")
        val cleanUser = username.trim()
        val cleanPass = password.trim()

        if (cleanUrl.isBlank()) {
            errorMessage = "Укажите адрес сервера (например http://10.0.2.2:8000)"
            return
        }
        if (cleanUser.length < 3) {
            errorMessage = "Имя пользователя должно быть не короче 3 символов"
            return
        }
        if (cleanPass.length < 4) {
            errorMessage = "Пароль должен быть не короче 4 символов"
            return
        }

        isLoading = true
        coroutineScope.launch {
            try {
                val probeApi = NetworkClient.createProbeService(cleanUrl)
                val req = AuthRequest(username = cleanUser, password = cleanPass, deviceName = "Android App")

                val response = withContext(Dispatchers.IO) {
                    if (isRegisterMode) {
                        probeApi.register(req)
                    } else {
                        probeApi.login(req)
                    }
                }

                if (response.token.isNotBlank()) {
                    sessionManager.saveSession(
                        serverUrl = cleanUrl,
                        token = response.token,
                        username = response.username,
                        isAdmin = response.isAdmin
                    )
                    withContext(Dispatchers.Main) {
                        onLoginSuccess()
                    }
                } else {
                    errorMessage = "Сервер не вернул токен сессии"
                }
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Ошибка сети"
                errorMessage = when {
                    msg.contains("401", ignoreCase = true) -> "Неверный логин или пароль"
                    msg.contains("400", ignoreCase = true) -> "Пользователь уже существует или неверные данные"
                    msg.contains("Failed to connect", ignoreCase = true) || msg.contains("Connection refused", ignoreCase = true) ->
                        "Не удалось подключиться к серверу $cleanUrl. Проверьте IP и запущен ли Docker/сервер."
                    else -> "Ошибка подключения: $msg"
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun handleQrPayload(payload: String) {
        try {
            val trimmed = payload.trim()
            if (trimmed.startsWith("{") && trimmed.contains("token")) {
                val json = JSONObject(trimmed)
                val url = json.optString("url", "").ifEmpty { json.optString("server", "") }
                val token = json.optString("token", "")
                val user = json.optString("username", "SentinelUser")
                val isAdmin = json.optBoolean("is_admin", false)

                if (url.isNotBlank() && token.isNotBlank()) {
                    sessionManager.saveSession(url, token, user, isAdmin)
                    onLoginSuccess()
                    return
                }
            } else if (trimmed.length > 20 && !trimmed.contains(" ") && !trimmed.startsWith("{")) {
                // Прямой ввод скопированного токена сессии
                val fallbackUrl = serverUrl.trim().removeSuffix("/").ifEmpty { "http://10.0.2.2:8000" }
                sessionManager.saveSession(fallbackUrl, trimmed, "SentinelUser", true)
                onLoginSuccess()
                return
            }
            errorMessage = "Неверный формат данных QR-кода"
        } catch (e: Exception) {
            errorMessage = "Ошибка парсинга QR-кода: ${e.localizedMessage}"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1F1035),
                        Color(0xFF121212),
                        Color(0xFF0A0A0A)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AuthHeader()

            Spacer(Modifier.height(32.dp))

            AuthModeToggle(
                isRegisterMode = isRegisterMode,
                onModeChange = {
                    isRegisterMode = it
                    errorMessage = null
                    infoMessage = null
                }
            )

            Spacer(Modifier.height(24.dp))

            ManualLoginForm(
                serverUrl = serverUrl,
                onServerUrlChange = { serverUrl = it },
                username = username,
                onUsernameChange = { username = it },
                password = password,
                onPasswordChange = { password = it },
                isRegisterMode = isRegisterMode,
                isLoading = isLoading,
                errorMessage = errorMessage,
                infoMessage = infoMessage,
                onSubmit = { performAuth() },
                onOpenQrDialog = { showCameraScanner = true }
            )
        }
    }

    if (showCameraScanner) {
        QrCameraScannerDialog(
            onDismiss = { showCameraScanner = false },
            onQrScanned = { payload ->
                showCameraScanner = false
                handleQrPayload(payload)
            },
            onManualInputClick = {
                showCameraScanner = false
                showManualQrDialog = true
            }
        )
    }

    if (showManualQrDialog) {
        QrTokenDialog(
            onDismiss = { showManualQrDialog = false },
            onApplyPayload = { payload ->
                showManualQrDialog = false
                handleQrPayload(payload)
            }
        )
    }
}
