package com.yuivo9999.mybox123.domain.movie

/**
 * Fail-closed adapter routing shared by the movie source service and JVM contract tests.
 * A TVBox capability must be explicitly recognized; otherwise it must never become generic HTTP VOD.
 */
object NativeMovieAdapterRoutingPolicy {
    enum class Route { HTTP_VOD, SAFE_JSON_EXT }

    fun resolve(
        adapterType: String,
        sourceCapability: String,
        tvboxAdapterKind: String,
        tvboxRequiresJar: Boolean,
    ): Route {
        val adapter = adapterType.trim()
        val capability = sourceCapability.trim()
        val kind = tvboxAdapterKind.trim()

        when {
            capability == "tvbox-live-provider" || capability == "direct-live" ->
                throw IllegalArgumentException("SOURCE_TYPE_NOT_MOVIE")
            capability == "tvbox-csp" || kind == "csp" ->
                throw IllegalStateException("TVBOX_EXTENSION_RUNTIME_REQUIRED:csp")
            capability == "tvbox-drpy-js" || kind == "drpy-js" ->
                throw IllegalStateException("TVBOX_EXTENSION_RUNTIME_REQUIRED:drpy-js")
            tvboxRequiresJar || capability == "tvbox-jar" || capability == "tvbox-http-vod-with-jar" || kind == "jar" ->
                throw IllegalStateException("TVBOX_EXTENSION_RUNTIME_REQUIRED:jar")
        }

        if (adapter == NativeMovieSourceService.ADAPTER_TVBOX_EXTENSION ||
            capability == "tvbox-ext-json" || capability == "tvbox-ext" ||
            capability == "tvbox-extension" || kind == "ext"
        ) {
            if (kind.isNotEmpty() && kind != "ext") {
                throw IllegalStateException("TVBOX_EXTENSION_RUNTIME_REQUIRED:$kind")
            }
            return Route.SAFE_JSON_EXT
        }

        if (adapter.isNotEmpty() && adapter != NativeMovieSourceService.ADAPTER_HTTP_VOD) {
            throw IllegalArgumentException("SOURCE_ADAPTER_UNSUPPORTED:$adapter")
        }
        if (capability.startsWith("tvbox-")) {
            throw IllegalArgumentException("SOURCE_ADAPTER_UNSUPPORTED:$capability")
        }
        return Route.HTTP_VOD
    }
}
