package com.yuivo9999.mybox123;

import java.util.HashMap;
import java.util.Map;

/** Thread-safe correlation registry for one-shot Messenger request/response pairs. */
final class TVBoxJarRequestRegistry {
    private static final class Entry {
        String response;
        String failure;
    }

    private final Map<String, Entry> pending = new HashMap<>();
    private boolean closed;

    synchronized void register(String requestId) {
        if (closed) throw new IllegalStateException("TVBOX_JAR_CLIENT_CLOSED");
        if (requestId == null || requestId.trim().isEmpty())
            throw new IllegalArgumentException("TVBOX_JAR_REQUEST_ID_REQUIRED");
        if (pending.containsKey(requestId)) throw new IllegalStateException("TVBOX_JAR_REQUEST_ID_DUPLICATE");
        pending.put(requestId, new Entry());
    }

    synchronized boolean complete(String requestId, String response) {
        Entry entry = pending.get(requestId);
        if (entry == null || entry.response != null || entry.failure != null) return false;
        entry.response = response == null ? "" : response;
        notifyAll();
        return true;
    }

    synchronized void failAll(String code) {
        String failure = code == null || code.isEmpty() ? "TVBOX_JAR_ISOLATED_DISCONNECTED" : code;
        for (Entry entry : pending.values()) {
            if (entry.response == null && entry.failure == null) entry.failure = failure;
        }
        notifyAll();
    }

    synchronized String await(String requestId, long timeoutMs) throws InterruptedException {
        Entry entry = pending.get(requestId);
        if (entry == null) throw new IllegalStateException("TVBOX_JAR_REQUEST_NOT_PENDING");
        long timeoutNanos = Math.max(0L, timeoutMs) * 1_000_000L;
        long deadline = System.nanoTime() + timeoutNanos;
        try {
            while (entry.response == null && entry.failure == null) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) throw new IllegalStateException("TVBOX_JAR_ISOLATED_REQUEST_TIMEOUT");
                long millis = remaining / 1_000_000L;
                int nanos = (int) (remaining % 1_000_000L);
                wait(millis, nanos);
            }
            if (entry.failure != null) throw new IllegalStateException(entry.failure);
            return entry.response;
        } finally {
            pending.remove(requestId);
        }
    }

    synchronized boolean cancel(String requestId) {
        boolean removed = pending.remove(requestId) != null;
        if (removed) notifyAll();
        return removed;
    }

    synchronized int pendingCount() { return pending.size(); }

    synchronized void close() {
        closed = true;
        failAll("TVBOX_JAR_CLIENT_CLOSED");
    }
}
