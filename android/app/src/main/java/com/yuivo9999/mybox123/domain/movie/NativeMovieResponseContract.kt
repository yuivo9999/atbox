package com.yuivo9999.mybox123.domain.movie

import org.json.JSONTokener

/** Normalizes and classifies HTTP VOD response bodies before JSON/domain parsing. */
object NativeMovieResponseContract {
    fun normalizeBody(body: String, headers: Map<String, String>, maxCharacters: Int): String {
        val normalized = body.removePrefix("\uFEFF").trim()
        if (normalized.isEmpty()) throw IllegalStateException("MOVIE_SOURCE_EMPTY_RESPONSE")
        if (normalized.length > maxCharacters) throw IllegalArgumentException("MOVIE_SOURCE_RESPONSE_TOO_LARGE")
        val contentType = headers.entries.firstOrNull { it.key.equals("content-type", ignoreCase = true) }
            ?.value.orEmpty()
        if (contentType.contains("text/html", ignoreCase = true) && !normalized.startsWith("{") && !normalized.startsWith("[")) {
            throw IllegalStateException("MOVIE_SOURCE_HTML_RESPONSE")
        }
        return normalized
    }

    fun validateJsonSyntax(body: String) {
        try {
            val tokener = JSONTokener(body)
            tokener.nextValue()
            if (tokener.nextClean() != '\u0000') throw IllegalArgumentException("MOVIE_SOURCE_JSON_INVALID")
        } catch (error: IllegalArgumentException) {
            if (error.message == "MOVIE_SOURCE_JSON_INVALID") throw error
            throw IllegalArgumentException("MOVIE_SOURCE_JSON_INVALID", error)
        } catch (error: Exception) {
            throw IllegalArgumentException("MOVIE_SOURCE_JSON_INVALID", error)
        }
    }

    fun requestFailureCode(errorCode: String?, status: Int): String {
        return when (errorCode) {
            "RESPONSE_TOO_LARGE" -> "MOVIE_SOURCE_RESPONSE_TOO_LARGE"
            "TIMEOUT" -> "MOVIE_SOURCE_TIMEOUT"
            "DNS_ERROR" -> "MOVIE_SOURCE_DNS_ERROR"
            "TLS_ERROR" -> "MOVIE_SOURCE_TLS_ERROR"
            "INVALID_URL" -> "MOVIE_SOURCE_ENDPOINT_INVALID"
            else -> "MOVIE_SOURCE_REQUEST_FAILED:${errorCode ?: "HTTP_$status"}"
        }
    }
}
