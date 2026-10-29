package com.yuivo9999.mybox123.domain.movie

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Pure URL/query policy shared by the native HTTP VOD adapter and JVM contract tests. */
object NativeMovieRequestUrlPolicy {
    fun build(
        endpoint: String,
        categoriesOnly: Boolean,
        categoryId: String? = null,
        itemId: String? = null,
        page: Int = 1,
        pageSize: Int = NativeMovieSourceService.DEFAULT_PAGE_SIZE,
        keyword: String? = null,
        forceStaticJson: Boolean = false,
    ): String {
        val parsed = endpoint.toHttpUrlOrNull() ?: throw IllegalArgumentException("MOVIE_SOURCE_ENDPOINT_INVALID")
        if (forceStaticJson || !isCatVodEndpoint(parsed)) return endpoint
        val builder = parsed.newBuilder()
        builder.setQueryParameter("ac", if (categoriesOnly) "list" else "detail")
        // A detail request's explicit item ID must win over a stale endpoint-level `ids` value.
        // Other configured query parameters remain intact for backward compatibility.
        if (!categoriesOnly && !itemId.isNullOrBlank()) {
            builder.setQueryParameter("ids", itemId)
        }
        fun addIfMissing(key: String, value: String?) {
            if (!value.isNullOrBlank() && parsed.queryParameterNames.none { it.equals(key, ignoreCase = true) }) {
                builder.addQueryParameter(key, value)
            }
        }
        if (!categoriesOnly) {
            addIfMissing("t", categoryId)
            addIfMissing("ids", itemId)
            addIfMissing("pg", page.coerceAtLeast(1).toString())
            addIfMissing("wd", keyword)
            addIfMissing("limit", pageSize.coerceIn(1, NativeMovieSourceService.MAX_PAGE_SIZE).toString())
        } else {
            addIfMissing("limit", "1")
        }
        return builder.build().toString()
    }

    private fun isCatVodEndpoint(url: HttpUrl): Boolean {
        val path = url.encodedPath.lowercase()
        return path.contains("api.php") || path.contains("provide/vod") || path.contains("/vod/")
    }
}
