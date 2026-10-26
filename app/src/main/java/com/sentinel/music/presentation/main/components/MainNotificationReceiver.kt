package com.sentinel.music.presentation.main.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.sentinel.music.player.MusicService
import com.sentinel.music.presentation.main.MainViewModel

@Composable
fun MainNotificationReceiver(viewModel: MainViewModel) {
    val context = LocalContext.current

    DisposableEffect(context, viewModel) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    MusicService.ACTION_NOTIFICATION_PLAY -> viewModel.play()
                    MusicService.ACTION_NOTIFICATION_PAUSE -> viewModel.pause()
                    MusicService.ACTION_NOTIFICATION_NEXT -> viewModel.playNext()
                    MusicService.ACTION_NOTIFICATION_PREV -> viewModel.playPrev()
                    MusicService.ACTION_NOTIFICATION_PLAY_PAUSE -> viewModel.playPause()
                    MusicService.ACTION_NOTIFICATION_SEEK -> {
                        val pos = intent.getLongExtra("POSITION_MS", 0L)
                        viewModel.seekTo(pos)
                    }
                    MusicService.ACTION_REACTIONS_CHANGED -> {
                        val trackId = intent.getIntExtra("TRACK_ID", -1)
                        if (trackId != -1) {
                            val isLiked = intent.getBooleanExtra("IS_LIKED", false)
                            val isDisliked = intent.getBooleanExtra("IS_DISLIKED", false)
                            viewModel.updateTrackReaction(trackId, isLiked, isDisliked)
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(MusicService.ACTION_NOTIFICATION_PLAY)
            addAction(MusicService.ACTION_NOTIFICATION_PAUSE)
            addAction(MusicService.ACTION_NOTIFICATION_NEXT)
            addAction(MusicService.ACTION_NOTIFICATION_PREV)
            addAction(MusicService.ACTION_NOTIFICATION_PLAY_PAUSE)
            addAction(MusicService.ACTION_NOTIFICATION_SEEK)
            addAction(MusicService.ACTION_REACTIONS_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
}
