package com.mfp.filemanager.ui.viewmodels

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.mfp.filemanager.service.AudioPlayerService
import com.mfp.filemanager.utils.toMediaItem
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.io.File

class AudioViewModel(application: Application) : AndroidViewModel(application) {
    
    private var mediaController: MediaController? = null
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()
    
    private val _currentTrack = MutableStateFlow<MediaMetadata?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private var positionUpdateJob: kotlinx.coroutines.Job? = null

    private val repository = com.mfp.filemanager.data.FileRepository(application)
    private val _musicFiles = MutableStateFlow<List<com.mfp.filemanager.data.FileModel>>(emptyList())
    val musicFiles = _musicFiles.asStateFlow()

    init {
        loadMusicFiles()
    }

    fun loadMusicFiles() {
        viewModelScope.launch {
            _musicFiles.value = repository.getFilesByCategory(com.mfp.filemanager.data.FileType.AUDIO)
        }
    }

    fun initializeController(context: Context) {
        if (mediaController != null) return
        
        val sessionToken = SessionToken(context, ComponentName(context, AudioPlayerService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        
        controllerFuture.addListener({
            try {
                mediaController = controllerFuture.get()
                mediaController?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                        if (isPlaying) startPositionUpdates() else stopPositionUpdates()
                    }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        _currentTrack.value = mediaItem?.mediaMetadata
                        _duration.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                    }
                    
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _duration.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                    }
                    
                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                         _currentTrack.value = mediaMetadata
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        _shuffleEnabled.value = shuffleModeEnabled
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        _repeatMode.value = repeatMode
                    }
                })
                // Sync initial state
                _isPlaying.value = mediaController?.isPlaying == true
                _currentTrack.value = mediaController?.currentMediaItem?.mediaMetadata
                _shuffleEnabled.value = mediaController?.shuffleModeEnabled == true
                _repeatMode.value = mediaController?.repeatMode ?: Player.REPEAT_MODE_OFF
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (true) {
                _position.value = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
    }

    fun playNext() {
        mediaController?.seekToNext()
    }

    fun playPrevious() {
        mediaController?.seekToPrevious()
    }

    fun toggleShuffle() {
        val nextEnabled = !(_shuffleEnabled.value)
        mediaController?.shuffleModeEnabled = nextEnabled
    }

    fun toggleRepeatMode() {
        val nextMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        mediaController?.repeatMode = nextMode
    }
    
    fun playFile(file: File) {
        // If we have music files loaded, replace the whole playlist with them
        val allMusic = _musicFiles.value
        if (allMusic.isNotEmpty()) {
            val mediaItems = allMusic.map { it.toMediaItem() }
            val startIndex = allMusic.indexOfFirst { it.path == file.absolutePath }.coerceAtLeast(0)
            mediaController?.setMediaItems(mediaItems, startIndex, 0L)
        } else {
            mediaController?.setMediaItem(file.toMediaItem())
        }
        mediaController?.prepare()
        mediaController?.play()
    }

    fun togglePlayPause() {
        if (mediaController?.isPlaying == true) {
            mediaController?.pause()
        } else {
            mediaController?.play()
        }
    }

    fun stopPlayer() {
        mediaController?.stop()
        mediaController?.clearMediaItems()
    }
    
    override fun onCleared() {
        super.onCleared()
        mediaController?.release()
        mediaController = null
    }
}
