package com.yuivo9999.mybox123.core.network

object NativeHttpUrlPolicy {
    fun normalize(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            null
        }
    }
}
