package com.yuivo9999.mybox123.domain.movie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeMovieAdapterRoutingPolicyTest {
    @Test
    fun explicitSafeJsonExtRoutesToJsonAdapter() {
        assertEquals(
            NativeMovieAdapterRoutingPolicy.Route.SAFE_JSON_EXT,
            NativeMovieAdapterRoutingPolicy.resolve("", "tvbox-extension", "", false),
        )
        assertEquals(
            NativeMovieAdapterRoutingPolicy.Route.SAFE_JSON_EXT,
            NativeMovieAdapterRoutingPolicy.resolve("tvbox-extension", "tvbox-ext-json", "ext", false),
        )
    }

    @Test
    fun jarRequiredSourcesCannotFallThroughToHttpVod() {
        assertThrows(IllegalStateException::class.java) {
            NativeMovieAdapterRoutingPolicy.resolve("", "tvbox-http-vod-with-jar", "", false)
        }
        assertThrows(IllegalStateException::class.java) {
            NativeMovieAdapterRoutingPolicy.resolve("http-vod", "direct-http-vod", "", true)
        }
        assertThrows(IllegalStateException::class.java) {
            NativeMovieAdapterRoutingPolicy.resolve("", "", "jar", false)
        }
    }

    @Test
    fun unknownTvboxCapabilitiesCannotFallThroughToHttpVod() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeMovieAdapterRoutingPolicy.resolve("", "tvbox-future-extension", "", false)
        }
        assertThrows(IllegalStateException::class.java) {
            NativeMovieAdapterRoutingPolicy.resolve("", "tvbox-extension", "drpy-js", false)
        }
    }

    @Test
    fun ordinaryHttpVodStillUsesHttpRouteAndLiveIsRejected() {
        assertEquals(
            NativeMovieAdapterRoutingPolicy.Route.HTTP_VOD,
            NativeMovieAdapterRoutingPolicy.resolve("http-vod", "direct-http-vod", "", false),
        )
        assertThrows(IllegalArgumentException::class.java) {
            NativeMovieAdapterRoutingPolicy.resolve("", "direct-live", "", false)
        }
    }
}
