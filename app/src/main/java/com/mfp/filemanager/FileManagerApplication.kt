package com.mfp.filemanager

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.mfp.filemanager.data.SettingsRepository
import com.mfp.filemanager.utils.CrashHandler

class FileManagerApplication : Application(), ImageLoaderFactory {

    private var settingsRepository: SettingsRepository? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        CrashHandler.init(applicationContext)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(applicationContext)
            .components {
                add(VideoFrameDecoder.Factory())
                add(com.mfp.filemanager.utils.AudioAlbumArtFetcher.Factory())
            }
            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }
}
