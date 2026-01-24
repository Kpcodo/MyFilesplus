package com.mfp.filemanager.utils

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.buffer
import okio.source
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Custom Coil Fetcher for extracting album art from audio files.
 * Enables automatic caching of audio thumbnails for smooth scrolling.
 */
class AudioAlbumArtFetcher(
    private val file: File,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        return try {
            if (!file.exists() || !file.canRead()) {
                Log.w("AudioAlbumArtFetcher", "File doesn't exist or can't be read: ${file.path}")
                return null
            }

            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.path)
            val art = retriever.embeddedPicture
            retriever.release()

            if (art != null && art.isNotEmpty()) {
                val source = ByteArrayInputStream(art).source().buffer()
                SourceResult(
                    source = ImageSource(source, options.context),
                    mimeType = "image/jpeg",
                    dataSource = DataSource.DISK
                )
            } else {
                Log.d("AudioAlbumArtFetcher", "No embedded picture found in: ${file.path}")
                null
            }
        } catch (e: Exception) {
            Log.e("AudioAlbumArtFetcher", "Error extracting album art from: ${file.path}", e)
            null
        }
    }

    class Factory : Fetcher.Factory<Any> {
        override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
            // Convert data to File
            val file = when (data) {
                is File -> data
                is String -> File(data)
                else -> return null
            }

            // Only handle audio files that exist
            if (!file.exists()) {
                return null
            }

            val extension = file.extension.lowercase()
            return if (extension in listOf("mp3", "m4a", "flac", "wav", "ogg", "aac", "wma", "opus")) {
                AudioAlbumArtFetcher(file, options)
            } else {
                null
            }
        }
    }
}
