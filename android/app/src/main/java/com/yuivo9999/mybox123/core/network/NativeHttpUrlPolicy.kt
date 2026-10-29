package com.yuivo9999.mybox123.core.network

import java.net.URI
import java.util.Locale

/** URL policy shared by native HTTP execution and its JVM-only contract tests. */
object NativeHttpUrlPolicy {
    fun normalize(raw: String): String? = try {
        val uri = URI(raw.trim())
        val scheme = uri.scheme?.lowercase(Locale.US)
        if ((scheme != "http" && scheme != "https") || uri.host.isNullOrBlank()) null else uri.toASCIIString()
    } catch (_: Exception) {
        null
    }

    /** DNS-over-HTTPS endpoints must use TLS; credentials embedded in endpoint URLs are rejected. */
    fun normalizeDohUrl(raw: String): String? = try {
        val uri = URI(raw.trim())
        if (uri.scheme?.lowercase(Locale.US) != "https" || uri.host.isNullOrBlank() || uri.rawUserInfo != null) {
            null
        } else if (uri.port != -1 && uri.port !in 1..65535) {
            null
        } else {
            uri.toASCIIString()
        }
    } catch (_: Exception) {
        null
    }
}
