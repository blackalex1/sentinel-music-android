package com.sentinel.music.data.api

import com.sentinel.music.data.model.AddTrackToPlaylistRequest
import com.sentinel.music.data.model.AuthRequest
import com.sentinel.music.data.model.AuthResponse
import com.sentinel.music.data.model.AuthStatusResponse
import com.sentinel.music.data.model.CreatePlaylistRequest
import com.sentinel.music.data.model.LyricItemDto
import com.sentinel.music.data.model.LyricsResponse
import com.sentinel.music.data.model.PlaylistDetailsDto
import com.sentinel.music.data.model.PlaylistDto
import com.sentinel.music.data.model.ReactionResponse
import com.sentinel.music.data.model.TrackDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

typealias TrackDto = com.sentinel.music.data.model.TrackDto
typealias LyricItemDto = com.sentinel.music.data.model.LyricItemDto
typealias LyricsResponse = com.sentinel.music.data.model.LyricsResponse
typealias ReactionResponse = com.sentinel.music.data.model.ReactionResponse
typealias AuthRequest = com.sentinel.music.data.model.AuthRequest
typealias AuthResponse = com.sentinel.music.data.model.AuthResponse
typealias AuthStatusResponse = com.sentinel.music.data.model.AuthStatusResponse
typealias PlaylistDto = com.sentinel.music.data.model.PlaylistDto
typealias PlaylistDetailsDto = com.sentinel.music.data.model.PlaylistDetailsDto
typealias CreatePlaylistRequest = com.sentinel.music.data.model.CreatePlaylistRequest
typealias AddTrackToPlaylistRequest = com.sentinel.music.data.model.AddTrackToPlaylistRequest

interface MusicApiService {
    @GET("/api/auth/status")
    suspend fun getAuthStatus(): AuthStatusResponse

    @GET("/api/auth/me")
    suspend fun getMe(): com.sentinel.music.data.model.UserProfileDto

    @POST("/api/auth/login")
    suspend fun login(@Body req: AuthRequest): AuthResponse

    @POST("/api/auth/register")
    suspend fun register(@Body req: AuthRequest): AuthResponse

    @GET("/api/tracks/count")
    suspend fun getTracksCount(
        @Query("mood") mood: String? = null
    ): Map<String, Int>

    @GET("/api/tracks")
    suspend fun getTracks(
        @Query("limit") limit: Int = 5000,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String = "recent"
    ): List<TrackDto>

    @GET("/api/tracks/recent")
    suspend fun getRecentTracks(
        @Query("limit") limit: Int = 30
    ): List<TrackDto>

    @GET("/api/tracks/search")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): List<TrackDto>

    @GET("/api/tracks/{id}")
    suspend fun getTrack(@Path("id") id: Int): TrackDto

    @GET("/api/lyrics/{id}")
    suspend fun getLyrics(@Path("id") id: Int): LyricsResponse

    @POST("/api/reaction/{id}")
    suspend fun setReaction(
        @Path("id") id: Int,
        @Query("is_like") isLike: Boolean
    ): ReactionResponse

    @GET("/api/stream/next")
    suspend fun getNextStreamTrack(
        @Query("current_track_id") currentTrackId: Int? = null,
        @Query("mood") mood: String? = "default",
        @Query("discovery_ratio") discoveryRatio: Float? = 0.2f,
        @Query("mood_energy") moodEnergy: Float? = null,
        @Query("recent_ids") recentIds: String? = null
    ): TrackDto

    @GET("/api/playlists")
    suspend fun getPlaylists(): List<PlaylistDto>

    @POST("/api/playlists")
    suspend fun createPlaylist(@Body req: CreatePlaylistRequest): Map<String, Any>

    @GET("/api/playlists/{id}")
    suspend fun getPlaylistDetails(@Path("id") id: Int): PlaylistDetailsDto

    @POST("/api/playlists/{id}/tracks")
    suspend fun addTrackToPlaylist(
        @Path("id") playlistId: Int,
        @Body req: AddTrackToPlaylistRequest
    ): Map<String, Any>

    @DELETE("/api/playlists/{id}/tracks/{track_id}")
    suspend fun removeTrackFromPlaylist(
        @Path("id") playlistId: Int,
        @Path("track_id") trackId: Int
    ): Map<String, Any>

    @DELETE("/api/playlists/{id}")
    suspend fun deletePlaylist(@Path("id") id: Int): Map<String, Any>

    @GET("/api/tracks/{track_id}/candidates")
    suspend fun getTrackCandidates(
        @Path("track_id") trackId: Int,
        @Query("limit") limit: Int = 10
    ): Map<String, Any?>

    @POST("/api/tracks/{track_id}/replace")
    suspend fun replaceTrack(
        @Path("track_id") trackId: Int,
        @Body req: Map<String, Any?>
    ): Map<String, Any?>

    @GET("/api/tracks/candidates/search")
    suspend fun searchNewCandidates(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10
    ): Map<String, Any?>

    @POST("/api/tracks/candidates/download")
    suspend fun downloadCandidate(
        @Body req: Map<String, Any?>
    ): Map<String, Any?>

    @GET("/api/remote/devices")
    suspend fun getRemoteDevices(): com.sentinel.music.data.model.RemoteDevicesResponse

    @GET("/api/remote/status")
    suspend fun getRemoteStatus(): com.sentinel.music.data.model.RemoteStatusResponse

    @POST("/api/remote/command")
    suspend fun sendRemoteCommand(
        @Body req: com.sentinel.music.data.model.RemoteCommandRequest
    ): Map<String, Any?>
}
