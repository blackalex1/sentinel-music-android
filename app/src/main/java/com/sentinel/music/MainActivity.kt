package com.sentinel.music

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.sentinel.music.data.api.NetworkClient
import com.sentinel.music.data.session.SessionManager
import com.sentinel.music.player.MusicService
import com.sentinel.music.presentation.auth.LoginScreen
import com.sentinel.music.presentation.main.MainScreen

class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController by mutableStateOf<MediaController?>(null)
    private val sessionManager by lazy { SessionManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        val serviceIntent = android.content.Intent(this, MusicService::class.java)
        startService(serviceIntent)

        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val controller = controllerFuture?.get()
            runOnUiThread {
                mediaController = controller
            }
        }, MoreExecutors.directExecutor())

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color(0xFF121212))) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
                    var isLoggedIn by remember { mutableStateOf(sessionManager.isLoggedIn) }

                    if (!isLoggedIn) {
                        LoginScreen(
                            sessionManager = sessionManager,
                            onLoginSuccess = {
                                isLoggedIn = true
                            }
                        )
                    } else {
                        val api = remember(sessionManager.serverUrl, sessionManager.token) {
                            NetworkClient.createApiService(sessionManager)
                        }
                        MainScreen(
                            api = api,
                            sessionManager = sessionManager,
                            controllerProvider = { mediaController },
                            onLogout = {
                                sessionManager.logout()
                                isLoggedIn = false
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}
