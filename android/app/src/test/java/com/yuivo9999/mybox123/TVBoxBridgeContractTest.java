package com.yuivo9999.mybox123;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Runtime contract tests for the pure DRPY bridge boundary.
 * These tests do not execute scripts or contact remote sources.
 */
public final class TVBoxBridgeContractTest {
    private final TVBoxExtensionBridge bridge = new TVBoxExtensionBridge();

    @Test
    public void capabilitiesDeclareOnlySupportedDrpyOperations() throws Exception {
        JSONObject capabilities = new JSONObject(bridge.getCapabilities());
        assertEquals("1", capabilities.getString("contractVersion"));
        assertTrue(capabilities.getBoolean("available"));
        JSONArray kinds = capabilities.getJSONArray("supportedKinds");
        assertEquals(1, kinds.length());
        assertEquals("drpy-js", kinds.getString(0));
        assertFalse(capabilities.getJSONArray("supportedKinds").toString().contains("csp"));
        assertEquals(7, capabilities.getJSONArray("supportedOperations").length());
        assertEquals("partial", capabilities.getString("status"));
        assertFalse(capabilities.getBoolean("runtimeReady"));
        assertFalse(capabilities.getBoolean("supportedOperationsReady"));
        assertFalse(capabilities.getBoolean("endToEndReady"));
    }

    @Test
    public void healthCheckReturnsVersionedSuccessEnvelope() throws Exception {
        JSONObject response = new JSONObject(bridge.execute(
            "{\"operation\":\"healthCheck\",\"definition\":{\"kind\":\"drpy-js\"}}"
        ));
        assertTrue(response.getBoolean("ok"));
        assertEquals("healthCheck", response.getString("operation"));
        assertEquals("drpy-js", response.getString("kind"));
        assertEquals("1", response.getString("contractVersion"));
        assertEquals("healthy", response.getString("status"));
    }

    @Test
    public void unsupportedKindAndOperationReturnStableErrorEnvelope() throws Exception {
        JSONObject unsupportedKind = new JSONObject(bridge.execute(
            "{\"operation\":\"healthCheck\",\"definition\":{\"kind\":\"csp\"}}"
        ));
        assertFalse(unsupportedKind.getBoolean("ok"));
        assertEquals("TVBOX_EXTENSION_OPERATION_UNSUPPORTED", unsupportedKind.getString("code"));
        assertEquals("healthCheck", unsupportedKind.getString("operation"));
        assertEquals("csp", unsupportedKind.getString("kind"));
        assertEquals("1", unsupportedKind.getString("contractVersion"));

        JSONObject unsupportedOperation = new JSONObject(bridge.execute(
            "{\"operation\":\"eval\",\"definition\":{\"kind\":\"drpy-js\"}}"
        ));
        assertFalse(unsupportedOperation.getBoolean("ok"));
        assertEquals("TVBOX_EXTENSION_OPERATION_UNSUPPORTED", unsupportedOperation.getString("code"));
    }

    @Test
    public void oversizedPayloadIsRejectedBeforeDispatch() throws Exception {
        String payload = "{\"operation\":\"healthCheck\",\"definition\":{\"kind\":\"drpy-js\"},\"padding\":\"" +
            new String(new char[512 * 1024]).replace('\\0', 'x') + "\"}";
        JSONObject response = new JSONObject(bridge.execute(payload));
        assertFalse(response.getBoolean("ok"));
        assertEquals("TVBOX_EXTENSION_PAYLOAD_TOO_LARGE", response.getString("code"));
    }
}
