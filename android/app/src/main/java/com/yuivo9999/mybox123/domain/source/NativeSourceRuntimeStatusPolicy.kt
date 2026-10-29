package com.yuivo9999.mybox123.domain.source

import com.yuivo9999.mybox123.data.source.NativeContentSource

/**
 * Describes current native implementation scope, not whether a remote endpoint responds.
 * Keep TVBox live providers ahead of generic/local-format checks so they cannot be mislabeled
 * as ordinary descriptor sources just because they carry a TXT/M3U-looking reference.
 */
internal object NativeSourceRuntimeStatusPolicy {
    fun liveStatus(source: NativeContentSource): String {
        if (source.sourceType != "live") return "not-applicable"
        if (!source.enabled) return "disabled"
        val raw = source.toJson()
        val capability = raw.optString("sourceCapability").trim().lowercase()
        val adapterType = source.adapterType.orEmpty().trim().lowercase()
        val kind = source.tvboxAdapterKind.orEmpty().trim().lowercase()
        val isTvBoxProvider = raw.optBoolean("tvboxLiveProvider", false) ||
            capability == "tvbox-live-provider" ||
            kind == "live-provider" ||
            adapterType == "tvbox-live-extension"
        if (isTvBoxProvider) return "unsupported-tvbox-live-provider"
        if (capability.startsWith("tvbox-") || kind in setOf("drpy-js", "jar", "csp", "ext", "http-vod", "http-vod-with-jar")) {
            return "unsupported-tvbox-live-extension"
        }
        val localFormat = raw.optString("localFormat").trim().lowercase()
        val liveMode = raw.optString("liveMode").trim().lowercase()
        if (liveMode == "tv1" || localFormat in setOf("txt", "m3u", "xml", "xmltv", "json")) {
            return "descriptor-service-available-runtime-pending"
        }
        val ref = source.sourceRef.trim()
        return if (ref.startsWith("http://", true) || ref.startsWith("https://", true)) {
            "descriptor-service-available-runtime-pending"
        } else {
            "unsupported-source-reference"
        }
    }
}
