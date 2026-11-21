package com.phimbud.mobile.ui.aurora

import android.graphics.Bitmap
import android.graphics.Canvas
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.phimbud.mobile.core.platform.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun extractPosterPalette(platformContext: PlatformContext, url: String): AuroraPalette? =
    withContext(Dispatchers.Default) {
        runCatching {
            val context = platformContext.androidContext
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
                ?: return@runCatching null
            val drawable = result.image.asDrawable(context.resources)

            // Draw into a 32x32 bitmap: plenty to find dominant hues, and cheap.
            val size = 32
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(bitmap))
            val pixels = IntArray(size * size)
            bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
            bitmap.recycle()
            paletteFromPixels(pixels)
        }.getOrNull()
    }
