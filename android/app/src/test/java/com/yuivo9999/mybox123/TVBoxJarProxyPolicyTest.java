package com.yuivo9999.mybox123;

import org.junit.Test;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.*;

/** Pure policy tests for the host-side isolated JAR proxy; no network or Android runtime. */
public final class TVBoxJarProxyPolicyTest {
    @Test public void acceptsOnlyAbsoluteHttpUrlsWithoutCredentialsOrFragments() {
        assertEquals("https://example.com/api", TVBoxJarInputPolicy.requireProxyHttpUrl("https://example.com/api"));
        rejectUrl("file:///tmp/a");
        rejectUrl("https:///missing-host");
        rejectUrl("https://user:pass@example.com/api");
        rejectUrl("https://example.com/api#fragment");
    }

    @Test public void restrictsMethodsAndBoundsUtf8RequestBody() {
        assertEquals("POST", TVBoxJarInputPolicy.normalizeProxyMethod(" post "));
        assertEquals("GET", TVBoxJarInputPolicy.normalizeProxyMethod(""));
        try { TVBoxJarInputPolicy.normalizeProxyMethod("DELETE"); fail("DELETE must be rejected"); }
        catch (IllegalArgumentException expected) { assertEquals("TVBOX_JAR_PROXY_METHOD_UNSUPPORTED", expected.getMessage()); }
        char[] chars = new char[192 * 1024 + 1];
        java.util.Arrays.fill(chars, 'a');
        try { TVBoxJarInputPolicy.requireProxyBody(new String(chars)); fail("oversized body must be rejected"); }
        catch (IllegalArgumentException expected) { assertEquals("TVBOX_JAR_PROXY_REQUEST_TOO_LARGE", expected.getMessage()); }
        assertEquals("é", TVBoxJarInputPolicy.requireProxyBody("é"));
    }

    @Test public void rejectsHopByHopAndMalformedHeaders() {
        Map<String, String> allowed = new LinkedHashMap<>();
        allowed.put("Accept", "application/json");
        allowed.put("Referer", "https://example.com/");
        assertEquals(2, TVBoxJarInputPolicy.normalizeProxyHeaders(allowed).size());
        Map<String, String> forbidden = Collections.singletonMap("Host", "attacker.example");
        try { TVBoxJarInputPolicy.normalizeProxyHeaders(forbidden); fail("Host override must be rejected"); }
        catch (IllegalArgumentException expected) { assertEquals("TVBOX_JAR_PROXY_HEADER_FORBIDDEN", expected.getMessage()); }
        Map<String, String> injected = Collections.singletonMap("X-Test", "ok\r\nInjected: yes");
        try { TVBoxJarInputPolicy.normalizeProxyHeaders(injected); fail("CRLF must be rejected"); }
        catch (IllegalArgumentException expected) { assertEquals("TVBOX_JAR_PROXY_HEADER_INVALID", expected.getMessage()); }
    }

    @Test public void canonicalFileBoundaryRejectsRootAndTraversal() throws Exception {
        File base = Files.createTempDirectory("jar-proxy-policy").toFile();
        File root = new File(base, "cache/jar");
        assertTrue(root.mkdirs());
        File child = new File(root, "safe.json");
        Files.write(child.toPath(), "{}".getBytes(StandardCharsets.UTF_8));
        assertEquals(child.getCanonicalFile(), TVBoxJarInputPolicy.requireChildFile(child, root, "DENIED"));
        try { TVBoxJarInputPolicy.requireChildFile(root, root, "DENIED"); fail("root itself must not be readable"); }
        catch (SecurityException expected) { assertEquals("DENIED", expected.getMessage()); }
        try { TVBoxJarInputPolicy.requireChildFile(new File(root, "../outside.txt"), root, "DENIED"); fail("traversal must be rejected"); }
        catch (SecurityException expected) { assertEquals("DENIED", expected.getMessage()); }
    }

    @Test public void crossOriginRedirectIsNotSameOriginAndSensitiveRedirectsAreRejected() {
        assertTrue(TVBoxJarInputPolicy.sameOrigin("https://example.com/a", "https://EXAMPLE.com:443/b"));
        assertFalse(TVBoxJarInputPolicy.sameOrigin("https://example.com/a", "https://other.example/b"));
        assertFalse(TVBoxJarInputPolicy.sameOrigin("https://example.com/a", "http://example.com/b"));
        TVBoxJarInputPolicy.requireSafeRedirect("https://example.com/a", "https://other.example/b", "GET");
        rejectRedirect("https://example.com/a", "http://example.com/b", "GET", "DRPY_HTTP_REDIRECT_DOWNGRADE_DENIED");
        rejectRedirect("https://example.com/a", "https://other.example/b", "POST", "DRPY_HTTP_CROSS_ORIGIN_POST_REDIRECT_DENIED");
    }

    private static void rejectRedirect(String from, String to, String method, String code) {
        try { TVBoxJarInputPolicy.requireSafeRedirect(from, to, method); fail("redirect must be rejected"); }
        catch (SecurityException expected) { assertEquals(code, expected.getMessage()); }
    }

    private static void rejectUrl(String value) {
        try { TVBoxJarInputPolicy.requireProxyHttpUrl(value); fail("URL must be rejected: " + value); }
        catch (IllegalArgumentException expected) { assertEquals("TVBOX_JAR_URL_INVALID", expected.getMessage()); }
    }
}
