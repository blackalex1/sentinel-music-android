package com.sentinel.music.player

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.sentinel.music.MainActivity
import com.sentinel.music.R
import com.sentinel.music.data.api.NetworkClient
import com.sentinel.music.data.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val okHttpClient = NetworkClient.sharedOkHttpClient

    companion object {
        const val ACTION_LIKE = "com.sentinel.music.ACTION_LIKE"
        const val ACTION_DISLIKE = "com.sentinel.music.ACTION_DISLIKE"
        const val ACTION_UPDATE_REACTIONS = "com.sentinel.music.ACTION_UPDATE_REACTIONS"
        const val ACTION_NOTIFICATION_NEXT = "com.sentinel.music.ACTION_NOTIFICATION_NEXT"
        const val ACTION_NOTIFICATION_PREV = "com.sentinel.music.ACTION_NOTIFICATION_PREV"
        const val ACTION_NOTIFICATION_PLAY = "com.sentinel.music.ACTION_NOTIFICATION_PLAY"
        const val ACTION_NOTIFICATION_PAUSE = "com.sentinel.music.ACTION_NOTIFICATION_PAUSE"
        const val ACTION_NOTIFICATION_PLAY_PAUSE = "com.sentinel.music.ACTION_NOTIFICATION_PLAY_PAUSE"
        const val ACTION_NOTIFICATION_SEEK = "com.sentinel.music.ACTION_NOTIFICATION_SEEK"
        const val ACTION_REACTIONS_CHANGED = "com.sentinel.music.ACTION_REACTIONS_CHANGED"
        const val ACTION_SYNC_REMOTE_STATE = "com.sentinel.music.ACTION_SYNC_REMOTE_STATE"
        const val ACTION_CLEAR_REMOTE_STATE = "com.sentinel.music.ACTION_CLEAR_REMOTE_STATE"
    }

    private var rawPlayer: ExoPlayer? = null
    private var isRemoteMode: Boolean = false
    private var remoteIsPlaying: Boolean = false
    private var isSyncingRemoteState: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var remoteTrackMediaItem: MediaItem? = null
    private var remotePositionMs: Long = 0L
    private var remoteDurationMs: Long = 0L
    private var forwardingPlayer: ForwardingPlayer? = null
    private val sessionListeners = mutableListOf<Player.Listener>()
    private var remoteTimeline: androidx.media3.common.Timeline = androidx.media3.common.Timeline.EMPTY

    @OptIn(UnstableApi::class)
    private fun createNotificationButtons(isLiked: Boolean, isDisliked: Boolean): List<CommandButton> {
        val dislikeButton = CommandButton.Builder(
            if (isDisliked) CommandButton.ICON_THUMB_DOWN_FILLED else CommandButton.ICON_THUMB_DOWN_UNFILLED
        )
            .setDisplayName(if (isDisliked) "Не нравится (активно)" else "Не нравится")
            .setIconResId(R.drawable.ic_thumb_down)
            .setSessionCommand(SessionCommand(ACTION_DISLIKE, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_BACK_SECONDARY)
            .build()

        val likeButton = CommandButton.Builder(
            if (isLiked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
        )
            .setDisplayName(if (isLiked) "Нравится (активно)" else "Нравится")
            .setIconResId(if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart)
            .setSessionCommand(SessionCommand(ACTION_LIKE, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_FORWARD_SECONDARY)
            .build()

        return listOf(dislikeButton, likeButton)
    }

    @OptIn(UnstableApi::class)
    private val bitmapLoader = object : BitmapLoader {
        override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

        override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
            val originalBitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            return if (originalBitmap != null) {
                val maxDim = 640
                val bitmap = if (originalBitmap.width > maxDim || originalBitmap.height > maxDim) {
                    val scale = maxDim.toFloat() / maxOf(originalBitmap.width, originalBitmap.height)
                    Bitmap.createScaledBitmap(
                        originalBitmap,
                        (originalBitmap.width * scale).toInt(),
                        (originalBitmap.height * scale).toInt(),
                        true
                    )
                } else originalBitmap
                Futures.immediateFuture(bitmap)
            } else {
                Futures.immediateFailedFuture(IllegalArgumentException("Failed to decode cover bitmap"))
            }
        }

        override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
            val future = SettableFuture.create<Bitmap>()

            if (uri.scheme == "file") {
                try {
                    val originalBitmap = BitmapFactory.decodeFile(uri.path)
                    if (originalBitmap != null) {
                        val maxDim = 640
                        val bitmap = if (originalBitmap.width > maxDim || originalBitmap.height > maxDim) {
                            val scale = maxDim.toFloat() / maxOf(originalBitmap.width, originalBitmap.height)
                            Bitmap.createScaledBitmap(
                                originalBitmap,
                                (originalBitmap.width * scale).toInt(),
                                (originalBitmap.height * scale).toInt(),
                                true
                            )
                        } else originalBitmap
                        future.set(bitmap)
                    } else {
                        future.setException(IllegalArgumentException("Failed to decode local file: $uri"))
                    }
                } catch (e: Exception) {
                    future.setException(e)
                }
                return future
            }

            val request = Request.Builder().url(uri.toString()).build()
            okHttpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    future.setException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val bytes = response.body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (originalBitmap != null) {
                                val maxDim = 640
                                val bitmap = if (originalBitmap.width > maxDim || originalBitmap.height > maxDim) {
                                    val scale = maxDim.toFloat() / maxOf(originalBitmap.width, originalBitmap.height)
                                    Bitmap.createScaledBitmap(
                                        originalBitmap,
                                        (originalBitmap.width * scale).toInt(),
                                        (originalBitmap.height * scale).toInt(),
                                        true
                                    )
                                } else originalBitmap
                                future.set(bitmap)
                            } else {
                                future.setException(IllegalArgumentException("Failed to decode HTTP cover bitmap"))
                            }
                        } else {
                            future.setException(IOException("Empty cover response from $uri"))
                        }
                    } catch (e: Exception) {
                        future.setException(e)
                    } finally {
                        response.close()
                    }
                }
            })
            return future
        }
    }

    @OptIn(UnstableApi::class)
    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_LIKE, Bundle.EMPTY))
                .add(SessionCommand(ACTION_DISLIKE, Bundle.EMPTY))
                .add(SessionCommand(ACTION_UPDATE_REACTIONS, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SYNC_REMOTE_STATE, Bundle.EMPTY))
                .add(SessionCommand(ACTION_CLEAR_REMOTE_STATE, Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(availableSessionCommands)
                .setCustomLayout(createNotificationButtons(isLiked = false, isDisliked = false))
                .build()
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val player = mediaSession.player
            if (player.mediaItemCount > 0) {
                val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
                val startIdx = player.currentMediaItemIndex.coerceAtLeast(0)
                val startPos = player.currentPosition.coerceAtLeast(0L)
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(items, startIdx, startPos)
                )
            }
            return Futures.immediateFailedFuture(UnsupportedOperationException("No media items available to resume"))
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_UPDATE_REACTIONS -> {
                    val isLiked = args.getBoolean("IS_LIKED", false)
                    val isDisliked = args.getBoolean("IS_DISLIKED", false)
                    session.setCustomLayout(createNotificationButtons(isLiked, isDisliked))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_SYNC_REMOTE_STATE -> {
                    val trackId = args.getInt("TRACK_ID", -1)
                    if (trackId != -1) {
                        val title = args.getString("TITLE", "")
                        val artist = args.getString("ARTIST", "")
                        val album = args.getString("ALBUM", "Sentinel Music")
                        val coverUrl = args.getString("COVER_URL", "")
                        val isPlaying = args.getBoolean("IS_PLAYING", false)
                        val isLiked = args.getBoolean("IS_LIKED", false)
                        val isDisliked = args.getBoolean("IS_DISLIKED", false)
                        val posMs = args.getLong("POSITION_MS", 0L)
                        val durMs = args.getLong("DURATION_MS", 0L)
                        val devName = args.getString("DEVICE_NAME", "")

                        setRemotePlaybackState(
                            trackId = trackId,
                            title = title,
                            artist = if (devName.isNotBlank()) "$artist • $devName" else artist,
                            album = album,
                            coverUrl = coverUrl,
                            isPlaying = isPlaying,
                            isLiked = isLiked,
                            isDisliked = isDisliked,
                            positionMs = posMs,
                            durationMs = durMs
                        )
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                ACTION_CLEAR_REMOTE_STATE -> {
                    clearRemotePlaybackState()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }

            val currentMediaId = session.player.currentMediaItem?.mediaId?.toIntOrNull()
            if (currentMediaId != null && currentMediaId > 0) {
                when (customCommand.customAction) {
                    ACTION_LIKE -> {
                        serviceScope.launch {
                            try {
                                val sessionManager = SessionManager(this@MusicService)
                                val api = NetworkClient.createApiService(sessionManager)
                                val res = api.setReaction(currentMediaId, isLike = true)
                                withContext(Dispatchers.Main) {
                                    session.setCustomLayout(createNotificationButtons(res.isLiked, res.isDisliked))
                                    val reactionArgs = Bundle().apply {
                                        putInt("TRACK_ID", currentMediaId)
                                        putBoolean("IS_LIKED", res.isLiked)
                                        putBoolean("IS_DISLIKED", res.isDisliked)
                                    }
                                    session.broadcastCustomCommand(SessionCommand(ACTION_REACTIONS_CHANGED, Bundle.EMPTY), reactionArgs)
                                    sendBroadcast(Intent(ACTION_REACTIONS_CHANGED).setPackage(packageName).putExtras(reactionArgs))
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    ACTION_DISLIKE -> {
                        serviceScope.launch {
                            try {
                                val sessionManager = SessionManager(this@MusicService)
                                val api = NetworkClient.createApiService(sessionManager)
                                val res = api.setReaction(currentMediaId, isLike = false)
                                withContext(Dispatchers.Main) {
                                    session.setCustomLayout(createNotificationButtons(res.isLiked, res.isDisliked))
                                    val reactionArgs = Bundle().apply {
                                        putInt("TRACK_ID", currentMediaId)
                                        putBoolean("IS_LIKED", res.isLiked)
                                        putBoolean("IS_DISLIKED", res.isDisliked)
                                    }
                                    session.broadcastCustomCommand(SessionCommand(ACTION_REACTIONS_CHANGED, Bundle.EMPTY), reactionArgs)
                                    sendBroadcast(Intent(ACTION_REACTIONS_CHANGED).setPackage(packageName).putExtras(reactionArgs))
                                    session.player.seekToNextMediaItem()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    @OptIn(UnstableApi::class)
    private fun setRemotePlaybackState(
        trackId: Int,
        title: String,
        artist: String,
        album: String,
        coverUrl: String,
        isPlaying: Boolean,
        isLiked: Boolean,
        isDisliked: Boolean,
        positionMs: Long,
        durationMs: Long
    ) {
        isSyncingRemoteState = true
        try {
            val extras = Bundle().apply {
                putBoolean("IS_LIKED", isLiked)
                putBoolean("IS_DISLIKED", isDisliked)
            }
            val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(if (coverUrl.isNotBlank()) Uri.parse(coverUrl) else null)
                .setDisplayTitle(title)
                .setExtras(extras)
                .build()

            val remoteItem = MediaItem.Builder()
                .setMediaId(trackId.toString())
                .setMediaMetadata(mediaMetadata)
                .build()

            val itemChanged = remoteTrackMediaItem?.mediaId != remoteItem.mediaId
            val playStateChanged = remoteIsPlaying != isPlaying || !isRemoteMode

            val durUs = if (durationMs > 0) durationMs * 1000L else androidx.media3.common.C.TIME_UNSET
            remoteTimeline = SinglePeriodTimeline(
                durUs,
                durUs,
                0L,
                0L,
                /* isSeekable = */ true,
                /* isDynamic = */ false,
                /* useLiveConfiguration = */ false,
                null,
                remoteItem
            )

            isRemoteMode = true
            remoteTrackMediaItem = remoteItem
            remoteIsPlaying = isPlaying
            remotePositionMs = positionMs
            remoteDurationMs = durationMs

            // Локальный плеер ставим на паузу, чтобы звук шел только с ПК
            try {
                rawPlayer?.pause()
            } catch (_: Exception) {}

            val listenersCopy = ArrayList(sessionListeners)
            val fp = forwardingPlayer ?: rawPlayer
            if (itemChanged) {
                for (listener in listenersCopy) {
                    try {
                        listener.onTimelineChanged(remoteTimeline, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
                        listener.onMediaItemTransition(remoteItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
                        listener.onMediaMetadataChanged(mediaMetadata)
                        listener.onPlaylistMetadataChanged(mediaMetadata)
                    } catch (e: Exception) {
                        android.util.Log.w("MusicService", "Ошибка слушателя при смене трека: ${e.message}")
                    }
                }
            }

            if (playStateChanged || itemChanged) {
                for (listener in listenersCopy) {
                    try {
                        listener.onPlaybackStateChanged(Player.STATE_READY)
                        listener.onPlayWhenReadyChanged(isPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
                        listener.onIsPlayingChanged(isPlaying)
                    } catch (e: Exception) {
                        android.util.Log.w("MusicService", "Ошибка слушателя при смене статуса: ${e.message}")
                    }
                }
            }

            if (fp != null) {
                val flagSet = androidx.media3.common.FlagSet.Builder()
                    .add(Player.EVENT_TIMELINE_CHANGED)
                    .add(Player.EVENT_MEDIA_ITEM_TRANSITION)
                    .add(Player.EVENT_MEDIA_METADATA_CHANGED)
                    .add(Player.EVENT_PLAYLIST_METADATA_CHANGED)
                    .add(Player.EVENT_PLAYBACK_STATE_CHANGED)
                    .add(Player.EVENT_PLAY_WHEN_READY_CHANGED)
                    .add(Player.EVENT_IS_PLAYING_CHANGED)
                    .build()
                val events = Player.Events(flagSet)
                for (listener in listenersCopy) {
                    try {
                        listener.onEvents(fp, events)
                    } catch (e: Exception) {
                        android.util.Log.w("MusicService", "Ошибка onEvents: ${e.message}")
                    }
                }
            }

            mediaSession?.setCustomLayout(createNotificationButtons(isLiked, isDisliked))
            mediaSession?.let { session ->
                mainHandler.post {
                    try {
                        val startInForeground = isPlaying || (isRemoteMode && remoteTrackMediaItem != null)
                        onUpdateNotification(session, startInForeground)
                    } catch (e: Exception) {
                        android.util.Log.w("MusicService", "Ошибка обновления уведомления: ${e.message}")
                    }
                }
            }
        } finally {
            mainHandler.postDelayed({ isSyncingRemoteState = false }, 500L)
        }
    }

    private fun clearRemotePlaybackState() {
        if (!isRemoteMode) return
        isRemoteMode = false
        remoteTrackMediaItem = null
        remoteTimeline = androidx.media3.common.Timeline.EMPTY
        remoteIsPlaying = false
        remotePositionMs = 0L
        remoteDurationMs = 0L

        val localItem = rawPlayer?.currentMediaItem
        val localMeta = localItem?.mediaMetadata ?: androidx.media3.common.MediaMetadata.EMPTY
        val localPlaying = rawPlayer?.isPlaying ?: false
        val localTimeline = rawPlayer?.currentTimeline ?: androidx.media3.common.Timeline.EMPTY

        val listenersCopy = ArrayList(sessionListeners)
        val fp = forwardingPlayer ?: rawPlayer
        for (listener in listenersCopy) {
            listener.onTimelineChanged(localTimeline, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
            listener.onMediaMetadataChanged(localMeta)
            listener.onPlaylistMetadataChanged(localMeta)
            listener.onIsPlayingChanged(localPlaying)
            listener.onPlaybackStateChanged(rawPlayer?.playbackState ?: Player.STATE_IDLE)
        }

        if (fp != null) {
            val flagSet = androidx.media3.common.FlagSet.Builder()
                .add(Player.EVENT_TIMELINE_CHANGED)
                .add(Player.EVENT_MEDIA_METADATA_CHANGED)
                .add(Player.EVENT_PLAYBACK_STATE_CHANGED)
                .add(Player.EVENT_IS_PLAYING_CHANGED)
                .build()
            val events = Player.Events(flagSet)
            for (listener in listenersCopy) {
                listener.onEvents(fp, events)
            }
        }

        val extras = localItem?.mediaMetadata?.extras
        val isLiked = extras?.getBoolean("IS_LIKED", false) ?: false
        val isDisliked = extras?.getBoolean("IS_DISLIKED", false) ?: false
        mediaSession?.setCustomLayout(createNotificationButtons(isLiked, isDisliked))
        mediaSession?.let { session ->
            mainHandler.post {
                try {
                    onUpdateNotification(session, localPlaying)
                } catch (_: Exception) {}
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            val channel = android.app.NotificationChannel(
                "default_channel_id",
                "Воспроизведение",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомления воспроизведения музыки"
                setShowBadge(false)
            }
            nm?.createNotificationChannel(channel)
        }

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val playerInstance = ExoPlayer.Builder(this)
            .setMediaSourceFactory(AudioCacheManager.createMediaSourceFactory(this))
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        rawPlayer = playerInstance

        EqualizerManager.getInstance(this).attachAudioSession(playerInstance.audioSessionId)
        playerInstance.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                EqualizerManager.getInstance(this@MusicService).attachAudioSession(audioSessionId)
            }
        })

        val player = object : ForwardingPlayer(playerInstance) {
            override fun addListener(listener: Player.Listener) {
                if (!sessionListeners.contains(listener)) {
                    sessionListeners.add(listener)
                }
                super.addListener(listener)
            }

            override fun removeListener(listener: Player.Listener) {
                sessionListeners.remove(listener)
                super.removeListener(listener)
            }

            override fun getCurrentTimeline(): androidx.media3.common.Timeline {
                if (isRemoteMode && remoteTrackMediaItem != null) {
                    return remoteTimeline
                }
                return super.getCurrentTimeline()
            }

            override fun getCurrentMediaItem(): MediaItem? {
                if (isRemoteMode && remoteTrackMediaItem != null) {
                    return remoteTrackMediaItem
                }
                return super.getCurrentMediaItem()
            }

            override fun getMediaMetadata(): androidx.media3.common.MediaMetadata {
                if (isRemoteMode && remoteTrackMediaItem != null) {
                    return remoteTrackMediaItem!!.mediaMetadata
                }
                return super.getMediaMetadata()
            }

            override fun getPlaylistMetadata(): androidx.media3.common.MediaMetadata {
                if (isRemoteMode && remoteTrackMediaItem != null) {
                    return remoteTrackMediaItem!!.mediaMetadata
                }
                return super.getPlaylistMetadata()
            }

            override fun getCurrentMediaItemIndex(): Int {
                if (isRemoteMode) return 0
                return super.getCurrentMediaItemIndex()
            }

            override fun getCurrentPeriodIndex(): Int {
                if (isRemoteMode) return 0
                return super.getCurrentPeriodIndex()
            }

            @Suppress("DEPRECATION")
            @Deprecated("Deprecated in Java")
            override fun getCurrentWindowIndex(): Int {
                if (isRemoteMode) return 0
                return super.getCurrentWindowIndex()
            }

            override fun getMediaItemCount(): Int {
                if (isRemoteMode) return if (remoteTrackMediaItem != null) 1 else 0
                return super.getMediaItemCount()
            }

            override fun getMediaItemAt(index: Int): MediaItem {
                if (isRemoteMode && remoteTrackMediaItem != null) {
                    if (index == 0) return remoteTrackMediaItem!!
                    throw IndexOutOfBoundsException("Remote mode only has 1 media item at index 0")
                }
                return super.getMediaItemAt(index)
            }

            override fun isPlaying(): Boolean {
                if (isRemoteMode) {
                    return remoteIsPlaying
                }
                return super.isPlaying()
            }

            override fun getPlayWhenReady(): Boolean {
                if (isRemoteMode) {
                    return remoteIsPlaying
                }
                return super.getPlayWhenReady()
            }

            override fun getPlaybackState(): Int {
                if (isRemoteMode) {
                    return Player.STATE_READY
                }
                return super.getPlaybackState()
            }

            override fun getPlaybackSuppressionReason(): Int {
                if (isRemoteMode) {
                    return Player.PLAYBACK_SUPPRESSION_REASON_NONE
                }
                return super.getPlaybackSuppressionReason()
            }

            override fun getDuration(): Long {
                if (isRemoteMode && remoteDurationMs > 0) {
                    return remoteDurationMs
                }
                return super.getDuration()
            }

            override fun getCurrentPosition(): Long {
                if (isRemoteMode) {
                    return remotePositionMs
                }
                return super.getCurrentPosition()
            }

            override fun getContentDuration(): Long {
                if (isRemoteMode && remoteDurationMs > 0) {
                    return remoteDurationMs
                }
                return super.getContentDuration()
            }

            override fun getContentPosition(): Long {
                if (isRemoteMode) {
                    return remotePositionMs
                }
                return super.getContentPosition()
            }

            override fun getBufferedPosition(): Long {
                if (isRemoteMode && remoteDurationMs > 0) {
                    return remoteDurationMs
                }
                return super.getBufferedPosition()
            }

            override fun getContentBufferedPosition(): Long {
                if (isRemoteMode && remoteDurationMs > 0) {
                    return remoteDurationMs
                }
                return super.getContentBufferedPosition()
            }

            override fun getTotalBufferedDuration(): Long {
                if (isRemoteMode) return 0L
                return super.getTotalBufferedDuration()
            }

            override fun getBufferedPercentage(): Int {
                if (isRemoteMode) return 100
                return super.getBufferedPercentage()
            }

            override fun isCurrentMediaItemSeekable(): Boolean {
                if (isRemoteMode) return true
                return super.isCurrentMediaItemSeekable()
            }

            override fun isCurrentMediaItemDynamic(): Boolean {
                if (isRemoteMode) return false
                return super.isCurrentMediaItemDynamic()
            }

            override fun isCurrentMediaItemLive(): Boolean {
                if (isRemoteMode) return false
                return super.isCurrentMediaItemLive()
            }

            override fun isPlayingAd(): Boolean {
                if (isRemoteMode) return false
                return super.isPlayingAd()
            }

            override fun getCurrentAdGroupIndex(): Int {
                if (isRemoteMode) return androidx.media3.common.C.INDEX_UNSET
                return super.getCurrentAdGroupIndex()
            }

            override fun getCurrentAdIndexInAdGroup(): Int {
                if (isRemoteMode) return androidx.media3.common.C.INDEX_UNSET
                return super.getCurrentAdIndexInAdGroup()
            }

            override fun getCurrentTracks(): androidx.media3.common.Tracks {
                if (isRemoteMode) return androidx.media3.common.Tracks.EMPTY
                return super.getCurrentTracks()
            }

            override fun seekTo(positionMs: Long) {
                if (isRemoteMode) {
                    seekTo(0, positionMs)
                    return
                }
                super.seekTo(positionMs)
            }

            override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
                if (isRemoteMode) {
                    remotePositionMs = positionMs
                    val listenersCopy = ArrayList(sessionListeners)
                    for (l in listenersCopy) {
                        l.onPositionDiscontinuity(
                            Player.PositionInfo(null, 0, null, null, 0, positionMs, positionMs, 0, 0),
                            Player.PositionInfo(null, 0, null, null, 0, positionMs, positionMs, 0, 0),
                            Player.DISCONTINUITY_REASON_SEEK
                        )
                    }
                    sendBroadcast(Intent(ACTION_NOTIFICATION_SEEK).setPackage(packageName).putExtra("POSITION_MS", positionMs))
                    return
                }
                super.seekTo(mediaItemIndex, positionMs)
            }

            override fun play() {
                if (isRemoteMode) {
                    remoteIsPlaying = true
                    val listenersCopy = ArrayList(sessionListeners)
                    for (l in listenersCopy) {
                        l.onIsPlayingChanged(true)
                        l.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                    }
                    sendBroadcast(Intent(ACTION_NOTIFICATION_PLAY).setPackage(packageName))
                    return
                }
                super.play()
            }

            override fun pause() {
                if (isRemoteMode) {
                    if (isSyncingRemoteState) {
                        // Игнорируем паразитные паузы от Media3 во время применения удаленного состояния
                        return
                    }
                    remoteIsPlaying = false
                    val listenersCopy = ArrayList(sessionListeners)
                    for (l in listenersCopy) {
                        l.onIsPlayingChanged(false)
                        l.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                    }
                    sendBroadcast(Intent(ACTION_NOTIFICATION_PAUSE).setPackage(packageName))
                    return
                }
                super.pause()
            }

            override fun setPlayWhenReady(playWhenReady: Boolean) {
                if (isRemoteMode) {
                    if (playWhenReady) play() else pause()
                    return
                }
                super.setPlayWhenReady(playWhenReady)
            }

            override fun isCommandAvailable(command: Int): Boolean {
                if (command == Player.COMMAND_PLAY_PAUSE ||
                    command == Player.COMMAND_SEEK_TO_NEXT || 
                    command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
                    command == Player.COMMAND_SEEK_TO_PREVIOUS ||
                    command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ||
                    command == Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM ||
                    command == Player.COMMAND_SEEK_TO_MEDIA_ITEM ||
                    command == Player.COMMAND_SET_REPEAT_MODE ||
                    command == Player.COMMAND_SET_SHUFFLE_MODE ||
                    command == Player.COMMAND_GET_CURRENT_MEDIA_ITEM ||
                    command == Player.COMMAND_GET_TIMELINE ||
                    command == Player.COMMAND_GET_METADATA) {
                    return true
                }
                return super.isCommandAvailable(command)
            }

            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                    .add(Player.COMMAND_SET_REPEAT_MODE)
                    .add(Player.COMMAND_SET_SHUFFLE_MODE)
                    .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                    .add(Player.COMMAND_GET_TIMELINE)
                    .add(Player.COMMAND_GET_METADATA)
                    .build()
            }

            override fun hasNextMediaItem(): Boolean = true
            override fun hasPreviousMediaItem(): Boolean = true

            override fun seekToNext() {
                seekToNextMediaItem()
            }

            override fun seekToNextMediaItem() {
                if (isRemoteMode) {
                    sendBroadcast(Intent(ACTION_NOTIFICATION_NEXT).setPackage(packageName))
                    return
                }
                if (rawPlayer?.hasNextMediaItem() == true) {
                    rawPlayer?.seekToNextMediaItem()
                } else {
                    mediaSession?.broadcastCustomCommand(SessionCommand(ACTION_NOTIFICATION_NEXT, Bundle.EMPTY), Bundle.EMPTY)
                    sendBroadcast(Intent(ACTION_NOTIFICATION_NEXT).setPackage(packageName))
                }
            }

            override fun seekToPrevious() {
                seekToPreviousMediaItem()
            }

            override fun seekToPreviousMediaItem() {
                if (isRemoteMode) {
                    sendBroadcast(Intent(ACTION_NOTIFICATION_PREV).setPackage(packageName))
                    return
                }
                if ((rawPlayer?.currentPosition ?: 0L) > 3000L) {
                    rawPlayer?.seekTo(0)
                } else if (rawPlayer?.hasPreviousMediaItem() == true) {
                    rawPlayer?.seekToPreviousMediaItem()
                } else {
                    mediaSession?.broadcastCustomCommand(SessionCommand(ACTION_NOTIFICATION_PREV, Bundle.EMPTY), Bundle.EMPTY)
                    sendBroadcast(Intent(ACTION_NOTIFICATION_PREV).setPackage(packageName))
                }
            }
        }

        forwardingPlayer = player

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val extras = mediaItem?.mediaMetadata?.extras
                val isLiked = extras?.getBoolean("IS_LIKED", false) ?: false
                val isDisliked = extras?.getBoolean("IS_DISLIKED", false) ?: false
                mediaSession?.setCustomLayout(createNotificationButtons(isLiked, isDisliked))
            }
        })

        val sessionIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(sessionCallback)
            .setBitmapLoader(bitmapLoader)
            .setCustomLayout(createNotificationButtons(isLiked = false, isDisliked = false))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        EqualizerManager.getInstance(this).release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        rawPlayer?.release()
        rawPlayer = null
        super.onDestroy()
    }
}
