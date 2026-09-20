package com.stremiolivetv.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object ImageLoader {
    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 10).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    fun load(scope: CoroutineScope, view: ImageView, url: String?) {
        view.setImageDrawable(null)
        if (url.isNullOrBlank()) return
        view.tag = url
        memory.get(url)?.let { view.setImageBitmap(it); return }
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val cache = java.io.File(view.context.cacheDir, "posters/${url.sha256()}.img")
                    val bytes = if (cache.exists() && cache.length() > 0) cache.readBytes() else download(url).also {
                        cache.parentFile?.mkdirs()
                        cache.writeBytes(it)
                    }
                    decodeSampled(bytes, 480, 720)
                }.getOrNull()
            }
            if (bitmap != null) memory.put(url, bitmap)
            if (view.tag == url && bitmap != null) view.setImageBitmap(bitmap)
        }
    }

    private fun download(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 7_000
        connection.readTimeout = 12_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "StreamFusionTV/0.7")
        return connection.inputStream.use { it.readBytes() }
    }

    private fun decodeSampled(bytes: ByteArray, width: Int, height: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        })
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray()).joinToString("") { "%02x".format(it) }
}
