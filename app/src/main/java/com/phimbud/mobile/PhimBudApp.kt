package com.phimbud.mobile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.phimbud.mobile.core.repository.PhimBudRepository

class PhimBudApp : Application(), ImageLoaderFactory {

    lateinit var repository: PhimBudRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = PhimBudRepository(this)
    }

    override fun newImageLoader(): ImageLoader {
        // Share ConnectionPool & Dispatcher with apiClient, but do not attach phimbud cookies/headers to external TMDB CDN
        val imageOkHttpClient = okhttp3.OkHttpClient.Builder()
            .connectionPool(repository.apiClient.okHttpClient.connectionPool)
            .dispatcher(repository.apiClient.okHttpClient.dispatcher)
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(imageOkHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024L * 1024L) // 100 MB
                    .build()
            }
            .allowRgb565(true)
            .crossfade(150)
            .respectCacheHeaders(false)
            .build()
    }
}
