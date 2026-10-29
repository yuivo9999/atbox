package com.yuivo9999.mybox123;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** JVM-level tests for Messenger request correlation and teardown behavior. */
public final class TVBoxJarRequestRegistryTest {
    @Test public void correlatesResponsesAndIgnoresUnknownOrLateResponses() throws Exception {
        TVBoxJarRequestRegistry registry = new TVBoxJarRequestRegistry();
        registry.register("first");
        registry.register("second");
        assertTrue(registry.complete("second", "B"));
        assertFalse(registry.complete("unknown", "noise"));
        assertEquals("B", registry.await("second", 10));
        assertTrue(registry.complete("first", "A"));
        assertEquals("A", registry.await("first", 10));
        assertFalse(registry.complete("first", "late"));
        assertEquals(0, registry.pendingCount());
    }

    @Test public void timeoutRemovesRequestSoLateResponseCannotAccumulate() throws Exception {
        TVBoxJarRequestRegistry registry = new TVBoxJarRequestRegistry();
        registry.register("timeout");
        try { registry.await("timeout", 0); fail("expected timeout"); }
        catch (IllegalStateException expected) { assertEquals("TVBOX_JAR_ISOLATED_REQUEST_TIMEOUT", expected.getMessage()); }
        assertFalse(registry.complete("timeout", "late"));
        assertEquals(0, registry.pendingCount());
    }

    @Test public void disconnectFailureWakesWaiterAndAllowsFutureRequests() throws Exception {
        final TVBoxJarRequestRegistry registry = new TVBoxJarRequestRegistry();
        registry.register("waiting");
        final AtomicReference<String> result = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            try { registry.await("waiting", 2_000); result.set("unexpected response"); }
            catch (Exception error) { result.set(error.getMessage()); }
        });
        waiter.start();
        registry.failAll("TVBOX_JAR_ISOLATED_DISCONNECTED");
        waiter.join(1_000);
        assertFalse("waiter should have been released", waiter.isAlive());
        assertEquals("TVBOX_JAR_ISOLATED_DISCONNECTED", result.get());
        registry.register("after-reconnect");
        assertTrue(registry.complete("after-reconnect", "ok"));
        assertEquals("ok", registry.await("after-reconnect", 10));
    }

    @Test public void closeWakesWaitersAndRejectsNewRequests() throws Exception {
        final TVBoxJarRequestRegistry registry = new TVBoxJarRequestRegistry();
        registry.register("waiting-close");
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            try { registry.await("waiting-close", 2_000); result.set("unexpected response"); }
            catch (Exception error) { result.set(error.getMessage()); }
            finally { done.countDown(); }
        });
        waiter.start();
        registry.close();
        assertTrue(done.await(1, TimeUnit.SECONDS));
        assertEquals("TVBOX_JAR_CLIENT_CLOSED", result.get());
        assertFalse(registry.complete("waiting-close", "late"));
        try { registry.register("new"); fail("closed registry must reject requests"); }
        catch (IllegalStateException expected) { assertEquals("TVBOX_JAR_CLIENT_CLOSED", expected.getMessage()); }
        assertEquals(0, registry.pendingCount());
    }
}
