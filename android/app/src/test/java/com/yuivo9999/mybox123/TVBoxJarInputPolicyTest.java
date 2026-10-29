package com.yuivo9999.mybox123;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** Deterministic tests for pure JAR bridge input policy; no Android runtime or network needed. */
public final class TVBoxJarInputPolicyTest {
    @Test public void acceptsOnlyAbsoluteHttpAndHttpsUrls() {
        assertEquals("https://example.com/spider.jar", TVBoxJarInputPolicy.requireHttpUrl(" https://example.com/spider.jar "));
        assertEquals("http://example.com:8080/spider.jar", TVBoxJarInputPolicy.requireHttpUrl("http://example.com:8080/spider.jar"));
        assertRejected("file:///tmp/spider.jar", "TVBOX_JAR_URL_INVALID");
        assertRejected("javascript:alert(1)", "TVBOX_JAR_URL_INVALID");
        assertRejected("https:///missing-host.jar", "TVBOX_JAR_URL_INVALID");
        assertRejected("https://user:pass@example.com/spider.jar", "TVBOX_JAR_URL_INVALID");
        assertRejected("https://example.com:0/spider.jar", "TVBOX_JAR_URL_INVALID");
    }

    @Test public void distinguishesAbsentDigestFromMalformedDigest() {
        assertEquals("", TVBoxJarInputPolicy.normalizeExpectedMd5("  "));
        assertEquals("0123456789abcdef0123456789abcdef",
                TVBoxJarInputPolicy.normalizeExpectedMd5("0123456789ABCDEF0123456789ABCDEF"));
        assertRejectedDigest("abc");
        assertRejectedDigest("g123456789abcdef0123456789abcdef");
    }

    @Test public void classNameMustBeFullyQualifiedAndSyntacticallySafe() {
        assertEquals("com.github.catvod.spider.Demo", TVBoxJarInputPolicy.requireClassName(" com.github.catvod.spider.Demo "));
        assertRejectedClassName("");
        assertRejectedClassName("Demo");
        assertRejectedClassName("com.github.catvod.spider..Demo");
        assertRejectedClassName("com.github.catvod.spider.Demo/../../Other");
        assertRejectedClassName("com.github.catvod.spider.Demo;System.exit(0)");
    }

    private static void assertRejected(String input, String expectedCode) {
        try {
            TVBoxJarInputPolicy.requireHttpUrl(input);
            fail("Expected URL to be rejected: " + input);
        } catch (IllegalArgumentException expected) {
            assertEquals(expectedCode, expected.getMessage());
        }
    }

    private static void assertRejectedDigest(String input) {
        try {
            TVBoxJarInputPolicy.normalizeExpectedMd5(input);
            fail("Expected digest to be rejected: " + input);
        } catch (IllegalArgumentException expected) {
            assertEquals("TVBOX_JAR_MD5_INVALID", expected.getMessage());
        }
    }

    private static void assertRejectedClassName(String input) {
        try {
            TVBoxJarInputPolicy.requireClassName(input);
            fail("Expected class name to be rejected: " + input);
        } catch (IllegalArgumentException expected) {
            if (!"TVBOX_JAR_CLASS_NAME_REQUIRED".equals(expected.getMessage())) {
                assertEquals("TVBOX_JAR_CLASS_NAME_INVALID", expected.getMessage());
            }
        }
    }
}
