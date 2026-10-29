package com.yuivo9999.mybox123.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.yuivo9999.mybox123.core.network.NativeHttpUrlPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit

/** Bounded native poster loader. It accepts absolute HTTP(S) image URLs only and never follows redirects. */
@Composable
internal fun NativePosterImage(url: String, modifier: Modifier = Modifier) {
    val bitmap = produceState<android.graphics.Bitmap?>(initialValue = null, url) {
        value = withContext(Dispatchers.IO) { loadPosterBitmap(url) }
    }.value
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "影视海报",
            modifier = modifier.widthIn(max = 240.dp).heightIn(max = 360.dp).fillMaxWidth(),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = modifier.widthIn(max = 240.dp).heightIn(min = 72.dp, max = 120.dp).fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("海报暂不可用", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun loadPosterBitmap(rawUrl: String): android.graphics.Bitmap? {
    val normalized = NativeHttpUrlPolicy.normalize(rawUrl) ?: return null
    val uri = try { URI(normalized) } catch (_: Exception) { return null }
    val host = uri.host?.lowercase() ?: return null
    if (uri.rawUserInfo != null || host == "localhost" || host.endsWith(".localhost")) return null
    if (host.any { it == ':' } || host.firstOrNull()?.isDigit() == true) {
        val address = try { InetAddress.getByName(host) } catch (_: Exception) { return null }
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return null
    }

    return try {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val request = Request.Builder().url(normalized).header("Accept", "image/*").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            val mediaType = body.contentType()?.type?.lowercase()
            if (mediaType != "image") return null
            val declaredLength = body.contentLength()
            if (declaredLength > MAX_POSTER_BYTES) return null
            val bytes = body.byteStream().use { input ->
                val output = ByteArrayOutputStream(minOf(MAX_POSTER_BYTES, 32 * 1024))
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_POSTER_BYTES) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > MAX_SOURCE_DIMENSION || bounds.outHeight > MAX_SOURCE_DIMENSION) return null
            val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (_: Exception) {
        null
    }
}

private fun calculateSampleSize(width: Int, height: Int): Int {
    var sample = 1
    while (width / sample > MAX_DECODE_DIMENSION || height / sample > MAX_DECODE_DIMENSION) sample *= 2
    return sample
}

private const val MAX_POSTER_BYTES = 3 * 1024 * 1024
private const val MAX_SOURCE_DIMENSION = 12_000
private const val MAX_DECODE_DIMENSION = 1_600
