package com.sentinel.music.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.music.data.session.SessionManager
import com.sentinel.music.presentation.main.MainUiState
import com.sentinel.music.presentation.main.MainViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    sessionManager: SessionManager,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showClearOfflineDialog by remember { mutableStateOf(false) }
    var showClearStreamDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val currentAppVersion = "0.0.2"
    var isCheckingAppUpdate by remember { mutableStateOf(false) }
    var updateAvailableVersion by remember { mutableStateOf<String?>(null) }
    var updateDownloadUrl by remember { mutableStateOf<String?>(null) }
    var updateCheckMessage by remember { mutableStateOf<String?>("Проверка актуальности...") }

    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadStatusText by remember { mutableStateOf<String?>(null) }

    fun startInAppUpdate(apkUrl: String, version: String) {
        if (isDownloadingUpdate) return
        isDownloadingUpdate = true
        downloadProgress = 0f
        downloadStatusText = "Подключение к GitHub..."

        coroutineScope.launch {
            try {
                val apkFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val updateDir = java.io.File(context.cacheDir, "updates")
                    if (!updateDir.exists()) updateDir.mkdirs()
                    val targetFile = java.io.File(updateDir, "Sentinel-Music-v$version.apk")
                    if (targetFile.exists()) targetFile.delete()

                    var url = java.net.URL(apkUrl)
                    var conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 20000
                    conn.instanceFollowRedirects = true

                    // Обработка HTTP-редиректов (GitHub releases перенаправляет на AWS S3)
                    var responseCode = conn.responseCode
                    var redirectCount = 0
                    while ((responseCode in 301..308) && redirectCount < 5) {
                        val newUrl = conn.getHeaderField("Location") ?: break
                        conn.disconnect()
                        url = java.net.URL(newUrl)
                        conn = url.openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 15000
                        conn.readTimeout = 20000
                        responseCode = conn.responseCode
                        redirectCount++
                    }

                    val totalLength = conn.contentLengthLong
                    conn.inputStream.use { input ->
                        targetFile.outputStream().use { output ->
                            val buffer = ByteArray(32768)
                            var bytesRead: Int
                            var totalBytesRead = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (totalLength > 0) {
                                    val prog = totalBytesRead.toFloat() / totalLength.toFloat()
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        downloadProgress = prog
                                        val mbRead = totalBytesRead / (1024f * 1024f)
                                        val mbTotal = totalLength / (1024f * 1024f)
                                        downloadStatusText = String.format(Locale.US, "Загрузка: %.1f МБ / %.1f МБ (%.0f%%)", mbRead, mbTotal, prog * 100f)
                                    }
                                }
                            }
                        }
                    }
                    targetFile
                }

                downloadStatusText = "Запуск установщика..."
                kotlinx.coroutines.delay(200)

                val apkUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )

                // Проверка разрешения на установку пакетов (Android 8.0+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        val permIntent = android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            android.net.Uri.parse("package:${context.packageName}")
                        ).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(permIntent)
                        downloadStatusText = "Разрешите установку из источника и повторите"
                        return@launch
                    }
                }

                val installIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(installIntent)
                downloadStatusText = "Установщик запущен"
            } catch (e: Exception) {
                downloadStatusText = "Ошибка загрузки: ${e.message}"
            } finally {
                isDownloadingUpdate = false
            }
        }
    }

    fun checkForAppUpdates() {
        if (isCheckingAppUpdate) return
        isCheckingAppUpdate = true
        updateCheckMessage = "Проверка обновлений на GitHub..."
        coroutineScope.launch {
            try {
                val url = java.net.URL("https://api.github.com/repos/blackalex1/sentinel-music-android/releases/latest")
                val conn = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val c = url.openConnection() as java.net.HttpURLConnection
                    c.setRequestProperty("Accept", "application/vnd.github.v3+json")
                    c.connectTimeout = 8000
                    c.readTimeout = 8000
                    c
                }
                val code = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { conn.responseCode }
                if (code == 200) {
                    val body = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        conn.inputStream.bufferedReader().use { it.readText() }
                    }
                    val json = org.json.JSONObject(body)
                    val tagName = json.optString("tag_name", "").removePrefix("v").trim()
                    val htmlUrl = json.optString("html_url", "https://github.com/blackalex1/sentinel-music-android/releases")

                    var apkUrl: String? = null
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url", "")
                                break
                            }
                        }
                    }

                    if (isNewerAppVersion(tagName, currentAppVersion)) {
                        updateAvailableVersion = tagName
                        updateDownloadUrl = apkUrl ?: htmlUrl
                        updateCheckMessage = "Доступно обновление: v$tagName!"
                    } else {
                        updateAvailableVersion = null
                        updateDownloadUrl = null
                        updateCheckMessage = "У вас установлена актуальная версия (v$currentAppVersion)"
                    }
                } else if (code == 404) {
                    updateAvailableVersion = null
                    updateCheckMessage = "Релизов пока нет. У вас актуальная версия."
                } else {
                    updateCheckMessage = "Не удалось проверить обновления"
                }
            } catch (e: Exception) {
                updateCheckMessage = "Не удалось связаться с GitHub"
            } finally {
                isCheckingAppUpdate = false
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshStorageStats()
        checkForAppUpdates()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E0C12))
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ====================================================================
        // 1. ПРОФИЛЬ И СЕРВЕР
        // ====================================================================
        item {
            AccountCard(
                username = state.currentUsername.ifEmpty { sessionManager.username ?: "Пользователь" },
                serverUrl = sessionManager.serverUrl,
                isAdmin = state.isCurrentUserAdmin || sessionManager.isAdmin,
                onLogoutClick = { showLogoutDialog = true }
            )
        }

        // ====================================================================
        // 2. ПОЛНОЕ СКАЧИВАНИЕ ФОНОТЕКИ В КЕШ
        // ====================================================================
        item {
            FullDownloadCard(
                state = state,
                onStartFullDownload = { viewModel.startFullDownload(onlyLiked = false) },
                onStartLikedDownload = { viewModel.startFullDownload(onlyLiked = true) },
                onCancelDownload = { viewModel.cancelFullDownload() }
            )
        }

        // ====================================================================
        // 3. НАСТРОЙКИ КЕШИРОВАНИЯ И СЕТИ
        // ====================================================================
        item {
            Text(
                text = "Кеширование и сеть",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
            )
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1B1724),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Оффлайн-режим (автоматическое сохранение в кеш)
                    SettingsSwitchRow(
                        title = "Оффлайн-режим (сохранение в кеш)",
                        subtitle = "Воспроизводимые треки автоматически сохраняются на устройство для прослушивания без интернета",
                        icon = Icons.Filled.CloudDownload,
                        checked = state.isDownloadMode,
                        onCheckedChange = { viewModel.toggleDownloadMode() }
                    )

                    HorizontalDivider(color = Color(0xFF2B2638), modifier = Modifier.padding(vertical = 12.dp))

                    // Предзагрузка следующего трека
                    SettingsSwitchRow(
                        title = "Предзагрузка следующего трека",
                        subtitle = "Заранее кешировать следующий трек в очереди для моментального перехода",
                        icon = Icons.Filled.FastForward,
                        checked = state.preloadNextTrack,
                        onCheckedChange = { viewModel.setPreloadNextTrack(it) }
                    )

                    HorizontalDivider(color = Color(0xFF2B2638), modifier = Modifier.padding(vertical = 12.dp))

                    // Только по Wi-Fi
                    SettingsSwitchRow(
                        title = "Скачивать только по Wi-Fi",
                        subtitle = "Запрещает скачивание треков и фонотеки при использовании мобильного интернета",
                        icon = Icons.Filled.Wifi,
                        checked = state.downloadOnlyOnWifi,
                        onCheckedChange = { viewModel.setDownloadOnlyOnWifi(it) }
                    )

                    HorizontalDivider(color = Color(0xFF2B2638), modifier = Modifier.padding(vertical = 12.dp))

                    // Лимит кеша потока
                    Text(
                        text = "Лимит кеша потокового плеера (LRU)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Автоматическая очистка старых треков при превышении лимита",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                    )

                    val cacheSizeOptions = listOf(250, 500, 1000, 2000, 5000)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        cacheSizeOptions.forEach { sizeMb ->
                            val isSelected = state.maxStreamCacheSizeMb == sizeMb
                            val label = if (sizeMb >= 1000) "${sizeMb / 1000} ГБ" else "$sizeMb МБ"

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFF8A2BE2) else Color(0xFF262033),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.setStreamCacheSizeMb(sizeMb) }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color.LightGray,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // ====================================================================
        // 4. ХРАНИЛИЩЕ И ОЧИСТКА
        // ====================================================================
        item {
            Text(
                text = "Хранилище устройства",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
            )
        }

        item {
            StorageStatsCard(
                offlineBytes = state.offlineTracksSizeBytes,
                streamCacheBytes = state.streamCacheSizeBytes,
                downloadedCount = state.downloadedTrackIds.size,
                onClearOffline = { showClearOfflineDialog = true },
                onClearStream = { showClearStreamDialog = true }
            )
        }

        // ====================================================================
        // 5. ЗВУК И ВОСПРОИЗВЕДЕНИЕ
        // ====================================================================
        item {
            Text(
                text = "Звук и воспроизведение",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
            )
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1B1724),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingsSwitchRow(
                        title = "Нормализация громкости (EBU R128)",
                        subtitle = "Выравнивание громкости треков без перегрузок и искажений",
                        icon = Icons.Filled.GraphicEq,
                        checked = state.isLoudnessNormalizationEnabled,
                        onCheckedChange = { viewModel.setLoudnessNormalization(it) }
                    )

                    AnimatedVisibility(visible = state.isLoudnessNormalizationEnabled) {
                        Column(modifier = Modifier.padding(top = 14.dp)) {
                            Text(
                                text = "Целевой уровень: ${state.loudnessTargetLufs.toInt()} LUFS",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Slider(
                                value = state.loudnessTargetLufs,
                                onValueChange = { viewModel.setLoudnessTarget(it) },
                                valueRange = -23.0f..-11.0f,
                                steps = 11,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF8A2BE2),
                                    activeTrackColor = Color(0xFF8A2BE2),
                                    inactiveTrackColor = Color(0xFF332B45)
                                )
                            )
                        }
                    }
                }
            }
        }

        // ====================================================================
        // 6. SENTINEL CONNECT (УПРАВЛЕНИЕ ПК)
        // ====================================================================
        item {
            Text(
                text = "Sentinel Connect (Управление ПК)",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
            )
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1B1724),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingsSwitchRow(
                        title = "Бесшовный переход с ПК (Handoff)",
                        subtitle = "При выключении компьютера продолжать прослушивание на телефоне с сохранением трека, позиции и очереди",
                        icon = Icons.Filled.Computer,
                        checked = state.isPcPlaybackHandoffEnabled,
                        onCheckedChange = { viewModel.setPcPlaybackHandoffEnabled(it) }
                    )
                }
            }
        }

        // ====================================================================
        // 7. О ПРИЛОЖЕНИИ И ОБНОВЛЕНИЯ С GITHUB
        // ====================================================================
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (updateAvailableVersion != null) Color(0xFF1E293B) else Color(0xFF15131C),
                border = if (updateAvailableVersion != null) BorderStroke(1.dp, Color(0xFF38BDF8)) else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Sentinel Music",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Версия $currentAppVersion • Поддержка FLAC Lossless и Офлайн-кеша",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    if (updateAvailableVersion != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x3310B981),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "Доступно обновление: v$updateAvailableVersion!",
                                color = Color(0xFF34D399),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        if (isDownloadingUpdate) {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = Color(0xFF38BDF8),
                                    trackColor = Color(0xFF334155)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = downloadStatusText ?: "Загрузка обновления...",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            if (downloadStatusText != null) {
                                Text(
                                    text = downloadStatusText ?: "",
                                    color = Color(0xFFF87171),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        val apkUrl = updateDownloadUrl
                                        val version = updateAvailableVersion
                                        if (apkUrl != null && version != null) {
                                            if (apkUrl.endsWith(".apk", ignoreCase = true)) {
                                                startInAppUpdate(apkUrl, version)
                                            } else {
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(apkUrl))
                                                context.startActivity(intent)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Установить обновление",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        updateDownloadUrl?.let { url ->
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                            context.startActivity(intent)
                                        }
                                    }
                                ) {
                                    Text(
                                        text = "GitHub",
                                        color = Color(0xFFA78BFA),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    } else {
                        if (updateCheckMessage != null) {
                            Text(
                                text = updateCheckMessage ?: "",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(4.dp))
                        }

                        TextButton(
                            onClick = { checkForAppUpdates() },
                            enabled = !isCheckingAppUpdate
                        ) {
                            Text(
                                text = if (isCheckingAppUpdate) "Проверка..." else "Проверить обновления",
                                color = Color(0xFFA78BFA),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // ====================================================================
        // 8. ВЫХОД ИЗ АККАУНТА
        // ====================================================================
        item {
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFFF5252)
                ),
                border = BorderStroke(1.dp, Color(0x66FF5252)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFFFF5252)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Выйти из аккаунта",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFF5252)
                )
            }
        }
    }

    // Диалог подтверждения очистки офлайн-треков
    if (showClearOfflineDialog) {
        AlertDialog(
            onDismissRequest = { showClearOfflineDialog = false },
            title = { Text("Очистить скачанные треки?") },
            text = {
                Text(
                    "Будут удалены все ${state.downloadedTrackIds.size} сохраненных треков " +
                    "(${formatBytes(state.offlineTracksSizeBytes)}). " +
                    "Вы сможете скачать их снова в любое время."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearOfflineCache()
                        showClearOfflineDialog = false
                    }
                ) {
                    Text("Удалить все", color = Color(0xFFFF5252))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearOfflineDialog = false }) {
                    Text("Отмена", color = Color.White)
                }
            },
            containerColor = Color(0xFF221C2E)
        )
    }

    // Диалог подтверждения очистки потокового кеша
    if (showClearStreamDialog) {
        AlertDialog(
            onDismissRequest = { showClearStreamDialog = false },
            title = { Text("Очистить кеш потока?") },
            text = { Text("Будет очищен временный буфер потокового плеера (${formatBytes(state.streamCacheSizeBytes)}).") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearStreamCache()
                        showClearStreamDialog = false
                    }
                ) {
                    Text("Очистить", color = Color(0xFFBB86FC))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearStreamDialog = false }) {
                    Text("Отмена", color = Color.White)
                }
            },
            containerColor = Color(0xFF221C2E)
        )
    }

    // Диалог подтверждения выхода
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Выйти из аккаунта?") },
            text = { Text("Вы выйдете из профиля '${sessionManager.username}'. Локально сохраненные треки не будут удалены.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("Выйти", color = Color(0xFFFF5252))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Отмена", color = Color.White)
                }
            },
            containerColor = Color(0xFF221C2E)
        )
    }
}

// ============================================================================
// ВСПОМОГАТЕЛЬНЫЕ КОМПОНЕНТЫ
// ============================================================================

@Composable
private fun AccountCard(
    username: String,
    serverUrl: String,
    isAdmin: Boolean,
    onLogoutClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1B1724),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF8A2BE2)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = username.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = username,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isAdmin) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0x33FFB300)
                        ) {
                            Text(
                                text = "ADMIN",
                                color = Color(0xFFFFB300),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = serverUrl,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onLogoutClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Выйти",
                    tint = Color(0xFFFF5252)
                )
            }
        }
    }
}

@Composable
private fun FullDownloadCard(
    state: MainUiState,
    onStartFullDownload: () -> Unit,
    onStartLikedDownload: () -> Unit,
    onCancelDownload: () -> Unit
) {
    val totalTracks = maxOf(state.totalServerTracksCount, state.tracks.size)
    val downloadedCount = state.downloadedTrackIds.size

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF231B33),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x338A2BE2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudDownload,
                        contentDescription = null,
                        tint = Color(0xFFBB86FC),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Полное скачивание в кеш",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "В кеше: $downloadedCount из $totalTracks треков",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (state.isFullDownloading) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Скачивание: ${state.fullDownloadCurrentIndex} из ${state.fullDownloadTotal}",
                            color = Color(0xFFBB86FC),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${(state.fullDownloadProgress * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { state.fullDownloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF1DB954),
                        trackColor = Color(0xFF332747)
                    )

                    if (state.fullDownloadCurrentTrackName.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.fullDownloadCurrentTrackName,
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Успешно: ${state.fullDownloadCompletedCount} • Ошибок: ${state.fullDownloadFailedCount}",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252))
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Остановить скачивание")
                    }
                }
            } else {
                Text(
                    text = "Загрузите всю вашу музыкальную библиотеку на телефон, чтобы слушать музыку в самолете, поездках и без доступа к сети.",
                    color = Color(0xFFD1C8E0),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = onStartFullDownload,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A2BE2))
                ) {
                    Icon(Icons.Filled.DownloadForOffline, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Скачать все треки медиатеки", fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onStartLikedDownload,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFBB86FC)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x668A2BE2))
                ) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFFF3366), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Скачать только 'Любимые'")
                }
            }
        }
    }
}

@Composable
private fun StorageStatsCard(
    offlineBytes: Long,
    streamCacheBytes: Long,
    downloadedCount: Int,
    onClearOffline: () -> Unit,
    onClearStream: () -> Unit
) {
    val totalBytes = offlineBytes + streamCacheBytes

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1B1724),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Всего занято кешем:", color = Color.LightGray, fontSize = 14.sp)
                Text(
                    text = formatBytes(totalBytes),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Офлайн-треки ($downloadedCount)", color = Color.Gray, fontSize = 12.sp)
                    Text(formatBytes(offlineBytes), color = Color(0xFF1DB954), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Кеш потока", color = Color.Gray, fontSize = 12.sp)
                    Text(formatBytes(streamCacheBytes), color = Color(0xFFBB86FC), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            HorizontalDivider(color = Color(0xFF2B2638), modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onClearStream,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B344B))
                ) {
                    Text("Сброс потока", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onClearOffline,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FF5252))
                ) {
                    Text("Удалить треки", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) Color(0xFF1DB954) else Color.Gray,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color.Gray,
                lineHeight = 16.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF1DB954),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF282236)
            )
        )
    }
}

private fun isNewerAppVersion(latest: String, current: String): Boolean {
    if (latest.isBlank() || current.isBlank()) return false
    val lParts = latest.split(".").mapNotNull { it.toIntOrNull() }
    val cParts = current.split(".").mapNotNull { it.toIntOrNull() }
    val maxLen = maxOf(lParts.size, cParts.size)
    for (i in 0 until maxLen) {
        val l = lParts.getOrElse(i) { 0 }
        val c = cParts.getOrElse(i) { 0 }
        if (l > c) return true
        if (l < c) return false
    }
    return false
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 Б"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ")
    var b = bytes.toDouble()
    var u = 0
    while (b >= 1024.0 && u < units.size - 1) {
        b /= 1024.0
        u++
    }
    return String.format(Locale.US, "%.1f %s", b, units[u])
}
