package com.yuivo9999.mybox123;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure validation policy for the TVBox JAR bridge input boundary. */
final class TVBoxJarInputPolicy {
    private static final int MAX_PROXY_BODY_BYTES = 192 * 1024;
    private static final int MAX_PROXY_HEADER_COUNT = 64;
    private static final int MAX_PROXY_HEADER_BYTES = 32 * 1024;
    private static final Set<String> FORBIDDEN_PROXY_HEADERS = new java.util.HashSet<>(java.util.Arrays.asList(
            "host", "content-length", "connection", "transfer-encoding", "proxy-authorization",
            "proxy-connection", "keep-alive", "te", "trailer", "upgrade", "expect"));

    private TVBoxJarInputPolicy() { }

    static String requireHttpUrl(String value) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.isEmpty()) throw new IllegalArgumentException("TVBOX_JAR_URL_REQUIRED");
        final URI uri;
        try {
            uri = new URI(candidate).normalize();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("TVBOX_JAR_URL_INVALID");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!("http".equals(scheme) || "https".equals(scheme))
                || uri.getHost() == null || uri.getHost().isEmpty()
                || uri.getRawUserInfo() != null
                || uri.getPort() > 65535
                || uri.getPort() == 0) {
            throw new IllegalArgumentException("TVBOX_JAR_URL_INVALID");
        }
        return uri.toASCIIString();
    }

    static String requireProxyHttpUrl(String value) {
        String normalized = requireHttpUrl(value);
        final URI uri;
        try { uri = new URI(normalized); }
        catch (URISyntaxException e) { throw new IllegalArgumentException("TVBOX_JAR_PROXY_URL_INVALID"); }
        if (uri.getRawFragment() != null) throw new IllegalArgumentException("TVBOX_JAR_URL_INVALID");
        return normalized;
    }

    static String normalizeProxyMethod(String value) {
        String method = value == null || value.trim().isEmpty() ? "GET" : value.trim().toUpperCase(Locale.ROOT);
        if (!("GET".equals(method) || "POST".equals(method) || "HEAD".equals(method))) {
            throw new IllegalArgumentException("TVBOX_JAR_PROXY_METHOD_UNSUPPORTED");
        }
        return method;
    }

    static String normalizeProxyContentType(String value) {
        String contentType = value == null || value.trim().isEmpty()
                ? "application/x-www-form-urlencoded; charset=UTF-8" : value.trim();
        if (contentType.length() > 512 || contentType.indexOf('\r') >= 0 || contentType.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("TVBOX_JAR_PROXY_CONTENT_TYPE_INVALID");
        }
        return contentType;
    }

    static String requireProxyBody(String value) {
        String body = value == null ? "" : value;
        if (body.getBytes(StandardCharsets.UTF_8).length > MAX_PROXY_BODY_BYTES) {
            throw new IllegalArgumentException("TVBOX_JAR_PROXY_REQUEST_TOO_LARGE");
        }
        return body;
    }

    static Map<String, String> normalizeProxyHeaders(Map<String, String> input) {
        Map<String, String> result = new LinkedHashMap<>();
        if (input == null) return result;
        if (input.size() > MAX_PROXY_HEADER_COUNT) throw new IllegalArgumentException("TVBOX_JAR_PROXY_HEADERS_TOO_MANY");
        int totalBytes = 0;
        for (Map.Entry<String, String> entry : input.entrySet()) {
            String name = entry.getKey() == null ? "" : entry.getKey().trim();
            String value = entry.getValue() == null ? "" : entry.getValue();
            if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,128}")) {
                throw new IllegalArgumentException("TVBOX_JAR_PROXY_HEADER_INVALID");
            }
            String lower = name.toLowerCase(Locale.ROOT);
            if (FORBIDDEN_PROXY_HEADERS.contains(lower)) throw new IllegalArgumentException("TVBOX_JAR_PROXY_HEADER_FORBIDDEN");
            if (value.length() > 8192 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                throw new IllegalArgumentException("TVBOX_JAR_PROXY_HEADER_INVALID");
            }
            totalBytes += name.getBytes(StandardCharsets.UTF_8).length + value.getBytes(StandardCharsets.UTF_8).length;
            if (totalBytes > MAX_PROXY_HEADER_BYTES) throw new IllegalArgumentException("TVBOX_JAR_PROXY_HEADERS_TOO_LARGE");
            result.put(name, value);
        }
        return result;
    }

    static File requireChildFile(File candidate, File root, String deniedCode) throws java.io.IOException {
        if (candidate == null || root == null) throw new SecurityException(deniedCode);
        File canonicalRoot = root.getCanonicalFile();
        File canonicalCandidate = candidate.getCanonicalFile();
        String prefix = canonicalRoot.getPath() + File.separator;
        if (!canonicalCandidate.getPath().startsWith(prefix)) throw new SecurityException(deniedCode);
        return canonicalCandidate;
    }

    static void requireSafeRedirect(String first, String second, String method) {
        URI from;
        URI to;
        try {
            from = new URI(requireProxyHttpUrl(first));
            to = new URI(requireProxyHttpUrl(second));
        } catch (Exception e) {
            throw new SecurityException("DRPY_HTTP_REDIRECT_URL_INVALID");
        }
        if ("https".equalsIgnoreCase(from.getScheme()) && "http".equalsIgnoreCase(to.getScheme())) {
            throw new SecurityException("DRPY_HTTP_REDIRECT_DOWNGRADE_DENIED");
        }
        if ("POST".equalsIgnoreCase(method) && !sameOrigin(from.toString(), to.toString())) {
            throw new SecurityException("DRPY_HTTP_CROSS_ORIGIN_POST_REDIRECT_DENIED");
        }
    }

    static boolean sameOrigin(String first, String second) {
        try {
            URI a = new URI(requireProxyHttpUrl(first));
            URI b = new URI(requireProxyHttpUrl(second));
            int portA = a.getPort() >= 0 ? a.getPort() : ("https".equalsIgnoreCase(a.getScheme()) ? 443 : 80);
            int portB = b.getPort() >= 0 ? b.getPort() : ("https".equalsIgnoreCase(b.getScheme()) ? 443 : 80);
            return a.getScheme().equalsIgnoreCase(b.getScheme())
                    && a.getHost().equalsIgnoreCase(b.getHost()) && portA == portB;
        } catch (Exception ignored) { return false; }
    }

    static String normalizeExpectedMd5(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return "";
        if (!normalized.matches("^[0-9a-f]{32}$")) {
            throw new IllegalArgumentException("TVBOX_JAR_MD5_INVALID");
        }
        return normalized;
    }

    static String requireClassName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 512) {
            throw new IllegalArgumentException("TVBOX_JAR_CLASS_NAME_REQUIRED");
        }
        String[] parts = normalized.split("\\.", -1);
        if (parts.length < 2) throw new IllegalArgumentException("TVBOX_JAR_CLASS_NAME_INVALID");
        for (String part : parts) {
            if (!part.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
                throw new IllegalArgumentException("TVBOX_JAR_CLASS_NAME_INVALID");
            }
        }
        return normalized;
    }

}
