package com.yuivo9999.mybox123.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class NativeHttpClient(private val okHttpClient: OkHttpClient = OkHttpClient.Builder().build()) {
    data class NativeHttpRequest(
        val url: String,
        val method: String = "GET",
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null,
        val timeoutMs: Long = 10_000L,
        val maxResponseBytes: Int = 5 * 1024 * 1024,
        val followRedirects: Boolean = true
    )

    data class NativeHttpResponse(
        val ok: Boolean,
        val status: Int,
        val body: String,
        val url: String = "",
        val errorCode: String? = null
    )

    fun execute(request: NativeHttpRequest): NativeHttpResponse {
        return try {
            val reqBuilder = Request.Builder().url(request.url)
            request.headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            val client = okHttpClient.newBuilder()
                .connectTimeout(request.timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(request.timeoutMs, TimeUnit.MILLISECONDS)
                .followRedirects(request.followRedirects)
                .build()
            val response = client.newCall(reqBuilder.build()).execute()
            val code = response.code
            val bodyString = response.body?.string().orEmpty()
            NativeHttpResponse(
                ok = response.isSuccessful,
                status = code,
                body = bodyString,
                url = response.request.url.toString()
            )
        } catch (e: Exception) {
            NativeHttpResponse(
                ok = false,
                status = 0,
                body = "",
                url = request.url,
                errorCode = e.message ?: "NETWORK_ERROR"
            )
        }
    }
}
