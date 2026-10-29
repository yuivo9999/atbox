package com.yuivo9999.mybox123.core.network

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

/** Loopback-only transport contract test; it never contacts a public data source. */
class NativeHttpClientMockServerTest {
    @Test
    fun readsJsonAndEnforcesResponseByteLimitAgainstMockHttpServer() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/json") { exchange ->
            val body = "{\"list\":[]}".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/large") { exchange ->
            val body = "0123456789abcdef".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/empty") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/plain")
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.createContext("/not-found") { exchange ->
            val body = "missing".toByteArray()
            exchange.sendResponseHeaders(404, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/json")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val client = NativeHttpClient()
            val ok = client.execute(NativeHttpClient.NativeHttpRequest("$base/json", maxResponseBytes = 1024))
            assertTrue(ok.ok)
            assertEquals("{\"list\":[]}", ok.body)

            val tooLarge = client.execute(NativeHttpClient.NativeHttpRequest("$base/large", maxResponseBytes = 8))
            assertFalse(tooLarge.ok)
            assertEquals("RESPONSE_TOO_LARGE", tooLarge.errorCode)

            val empty = client.execute(NativeHttpClient.NativeHttpRequest("$base/empty"))
            assertTrue(empty.ok)
            assertEquals("", empty.body)

            val notFound = client.execute(NativeHttpClient.NativeHttpRequest("$base/not-found"))
            assertFalse(notFound.ok)
            assertEquals(404, notFound.status)
            assertEquals("missing", notFound.body)

            val followed = client.execute(NativeHttpClient.NativeHttpRequest("$base/redirect"))
            assertTrue(followed.ok)
            assertEquals("{\\"list\\":[]}", followed.body)
            assertTrue(followed.url.endsWith("/json"))

            val notFollowed = client.execute(
                NativeHttpClient.NativeHttpRequest("$base/redirect", followRedirects = false),
            )
            assertFalse(notFollowed.ok)
            assertEquals(302, notFollowed.status)
        } finally {
            server.stop(0)
        }
    }
}
