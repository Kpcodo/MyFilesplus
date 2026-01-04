package com.example.filemanager

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

import com.example.filemanager.utils.CrashHandler

class FileManagerApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        CrashHandler.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        return try {
            ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizePercent(0.10)
                        .build()
                }
                .components {
                    // Safe removal of VideoFrameDecoder to prevent native crashes
                    // add(VideoFrameDecoder.Factory()) 
                }
                .networkCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .crossfade(false) 
                .build()
        } catch (e: Throwable) {
            // Fallback loader to prevent crash (catches Errors too)
            ImageLoader.Builder(this).build()
        }
    }
}
