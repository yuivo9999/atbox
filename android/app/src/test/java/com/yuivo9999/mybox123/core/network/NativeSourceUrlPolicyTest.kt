package com.yuivo9999.mybox123.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM-only checks for the supported URL schemes; these do not require Android framework classes. */
class NativeSourceUrlPolicyTest {
    @Test
    fun acceptsHttpAndHttpsUrls() {
        assertTrue(NativeHttpUrlPolicy.normalize("https://example.com/source.json") != null)
        assertTrue(NativeHttpUrlPolicy.normalize("http://127.0.0.1:8080/live.m3u") != null)
    }

    @Test
    fun acceptsOnlyTlsProtectedDohEndpoints() {
        assertTrue(NativeHttpUrlPolicy.normalizeDohUrl("https://dns.example/dns-query") != null)
        assertTrue(NativeHttpUrlPolicy.normalizeDohUrl("https://dns.example:8443/dns-query") != null)
        assertFalse(NativeHttpUrlPolicy.normalizeDohUrl("http://dns.example/dns-query") != null)
        assertFalse(NativeHttpUrlPolicy.normalizeDohUrl("https://user:pass@dns.example/dns-query") != null)
        assertFalse(NativeHttpUrlPolicy.normalizeDohUrl("file:///dns-query") != null)
    }

    @Test
    fun rejectsBrowserAndNonHttpSchemes() {
        assertFalse(NativeHttpUrlPolicy.normalize("file:///data/local/tmp/source.json") != null)
        assertFalse(NativeHttpUrlPolicy.normalize("javascript:alert(1)") != null)
        assertFalse(NativeHttpUrlPolicy.normalize("proxy://source?id=1") != null)
        assertFalse(NativeHttpUrlPolicy.normalize("not a url") != null)
    }
}
