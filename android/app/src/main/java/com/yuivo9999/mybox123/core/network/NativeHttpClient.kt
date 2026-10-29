package com.yuivo9999.mybox123.core.network

import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/** Bounded native HTTP transport for source discovery and future native content adapters. */
class NativeHttpClient(
    private val baseClient: OkHttpClient = OkHttpClient(),
) {
    fun execute(request: NativeHttpRequest): NativeHttpResponse {
        val normalizedUrl = NativeHttpUrlPolicy.normalize(request.url)
            ?: return NativeHttpResponse.failure(request.url, "INVALID_URL")
        val timeoutMs = request.timeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        val maxBytes = request.maxResponseBytes.coerceIn(1, ABSOLUTE_MAX_RESPONSE_BYTES)

        return try {
            val clientBuilder = baseClient.newBuilder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .followRedirects(request.followRedirects)
                .followSslRedirects(request.followRedirects)
            configureDoh(clientBuilder, request, timeoutMs)

            val urlBuilder = normalizedUrl.toHttpUrlOrNull()!!.newBuilder()
            request.queryParameters.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }
            val method = request.method.trim().uppercase(java.util.Locale.US).ifEmpty { "GET" }
            val requestBuilder = Request.Builder().url(urlBuilder.build())
            request.headers.forEach { (name, value) ->
                if (name.isNotBlank() && value != null) requestBuilder.header(name, value)
            }
            if (request.headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                requestBuilder.header("User-Agent", DEFAULT_USER_AGENT)
            }

            when (method) {
                "GET" -> requestBuilder.get()
                "HEAD" -> requestBuilder.head()
                else -> {
                    val contentType = request.headers.entries.firstOrNull { it.key.equals("Content-Type", true) }
                        ?.value ?: "application/json; charset=utf-8"
                    val body = (request.body ?: "").toRequestBody(contentType.toMediaTypeOrNull())
                    requestBuilder.method(method, body)
                }
            }

            clientBuilder.build().newCall(requestBuilder.build()).execute().use { response ->
                val responseBody = response.body
                val bytes = if (responseBody == null || method == "HEAD") byteArrayOf() else readBounded(responseBody.byteStream(), maxBytes)
                val charset: Charset = responseBody?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                val headers = response.headers.toMultimap().mapValues { (_, values) -> values.joinToString(", ") }
                NativeHttpResponse(
                    ok = response.isSuccessful,
                    status = response.code,
                    url = response.request.url.toString(),
                    body = String(bytes, charset),
                    headers = headers,
                    errorCode = null,
                )
            }
        } catch (error: Exception) {
            NativeHttpResponse.failure(normalizedUrl, classify(error))
        }
    }

    private fun configureDoh(builder: OkHttpClient.Builder, request: NativeHttpRequest, timeoutMs: Long) {
        val rawDohUrl = request.dohUrl?.trim().orEmpty()
        if (rawDohUrl.isEmpty()) return
        val normalizedDohUrl = NativeHttpUrlPolicy.normalizeDohUrl(rawDohUrl)
            ?: throw IllegalArgumentException("INVALID_DOH_URL")
        val parsedDohUrl = normalizedDohUrl.toHttpUrlOrNull() ?: throw IllegalArgumentException("INVALID_DOH_URL")
        val bootstrap = baseClient.newBuilder()
            .dns(Dns.SYSTEM)
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
        val dohBuilder = DnsOverHttps.Builder()
            .client(bootstrap)
            .url(parsedDohUrl)
            .systemDns(Dns.SYSTEM)
            .includeIPv6(true)
            .resolvePublicAddresses(true)
            .resolvePrivateAddresses(false)
        val bootstrapHosts = request.dohBootstrapIps.mapNotNull { value ->
            try {
                value.trim().takeIf { it.isNotEmpty() }?.let { InetAddress.getByName(it) }
            } catch (_: Exception) {
                null
            }
        }
        if (bootstrapHosts.isNotEmpty()) dohBuilder.bootstrapDnsHosts(bootstrapHosts)
        builder.dns(dohBuilder.build())
    }

    private fun readBounded(input: java.io.InputStream, maxBytes: Int): ByteArray {
        input.use { stream ->
            val output = ByteArrayOutputStream(minOf(maxBytes, 32 * 1024))
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw IOException("RESPONSE_TOO_LARGE")
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }

    private fun classify(error: Exception): String {
        val message = error.message.orEmpty().lowercase(java.util.Locale.US)
        return when {
            message.contains("response_too_large") -> "RESPONSE_TOO_LARGE"
            message.contains("timeout") -> "TIMEOUT"
            message.contains("unknownhost") || message.contains("dns") -> "DNS_ERROR"
            message.contains("ssl") || message.contains("tls") || message.contains("certificate") -> "TLS_ERROR"
            message == "invalid_doh_url" -> "INVALID_DOH_URL"
            else -> "NETWORK_ERROR"
        }
    }

    data class NativeHttpRequest(
        val url: String,
        val method: String = "GET",
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null,
        val queryParameters: Map<String, String> = emptyMap(),
        val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        val maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
        val followRedirects: Boolean = true,
        val dohUrl: String? = null,
        val dohBootstrapIps: List<String> = emptyList(),
    )

    data class NativeHttpResponse(
        val ok: Boolean,
        val status: Int,
        val url: String,
        val body: String,
        val headers: Map<String, String> = emptyMap(),
        val errorCode: String? = null,
    ) {
        companion object {
            fun failure(url: String, code: String) = NativeHttpResponse(
                ok = false,
                status = 0,
                url = url,
                body = "",
                errorCode = code,
            )
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
        const val DEFAULT_MAX_RESPONSE_BYTES = 5 * 1024 * 1024
        private const val MIN_TIMEOUT_MS = 1_000L
        private const val MAX_TIMEOUT_MS = 120_000L
        private const val ABSOLUTE_MAX_RESPONSE_BYTES = 20 * 1024 * 1024
        private const val DEFAULT_USER_AGENT = "MyBox-Android/1"
    }
}
