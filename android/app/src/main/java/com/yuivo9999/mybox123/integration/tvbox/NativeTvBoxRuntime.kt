package com.yuivo9999.mybox123.integration.tvbox

import android.content.Context
import com.yuivo9999.mybox123.TVBoxExtensionBridge
import com.yuivo9999.mybox123.TVBoxJarBridge
import org.json.JSONObject

/**
 * Direct Kotlin/Java facade over the existing DRPY and JAR runtimes.
 * It does not create a WebView or register a JavaScript interface; legacy runtimes remain callable
 * from native services while their execution contracts are migrated.
 */
class NativeTvBoxRuntime(context: Context) : AutoCloseable {
    private val drpyRuntime = TVBoxExtensionBridge()
    private val jarRuntime = TVBoxJarBridge(context.applicationContext)
    @Volatile private var closed = false

    /**
     * Reports the real capability declarations of the underlying runtimes. JAR support is
     * intentionally marked partial while its isolated-process network proxy remains pending;
     * callers must not treat the presence of the bridge as proof that every JAR source works.
     */
    fun capabilities(): JSONObject {
        if (closed) {
            return JSONObject()
                .put("available", false)
                .put("contractVersion", CONTRACT_VERSION)
                .put("supportedKinds", org.json.JSONArray())
                .put("supportedOperations", org.json.JSONArray())
                .put("supportedOperationsByKind", JSONObject().put("drpy-js", org.json.JSONArray()).put("jar", org.json.JSONArray()))
                .put("operationReadinessByKind", JSONObject())
                .put("runtimeStatuses", JSONObject())
                .put("executionBoundary", "native-service")
                .put("status", "unavailable")
                .put("endToEndReady", false)
                .put("capabilityScope", "bridge-presence-and-exposed-operations-not-source-end-to-end-readiness")
                .put("reason", "TVBOX_RUNTIME_CLOSED")
        }

        val drpy = readCapabilities(drpyRuntime.getCapabilities())
        val jar = readCapabilities(jarRuntime.getCapabilities())
        val supportedKinds = org.json.JSONArray()
        val supportedOperations = org.json.JSONArray()
        val supportedOperationsByKind = JSONObject()
        appendStrings(supportedKinds, drpy.optJSONArray("supportedKinds"))
        appendStrings(supportedKinds, jar.optJSONArray("supportedKinds"))
        appendStrings(supportedOperations, drpy.optJSONArray("supportedOperations"))
        appendStrings(supportedOperations, jar.optJSONArray("supportedOperations"))
        // The aggregate union is retained for older callers, but callers that need to know
        // whether a particular kind exposes an operation must use this per-kind map.
        supportedOperationsByKind.put("drpy-js", drpy.optJSONArray("supportedOperations") ?: org.json.JSONArray())
        supportedOperationsByKind.put("jar", jar.optJSONArray("supportedOperations") ?: org.json.JSONArray())

        val operationReadinessByKind = JSONObject()
            .put("drpy-js", JSONObject()
                .put("ready", drpy.optBoolean("supportedOperationsReady", false))
                .put("endToEndReady", drpy.optBoolean("endToEndReady", false))
                .put("reason", drpy.optString("reason", "DRPY_NATIVE_END_TO_END_COMPATIBILITY_NOT_VERIFIED")))
            .put("jar", JSONObject()
                .put("ready", jar.optBoolean("supportedOperationsReady", false))
                .put("endToEndReady", false)
                .put("reason", jar.optString("reason", "CATVOD_SPIDER_ABI_ISOLATED_IN_MEMORY_DEX_NETWORK_PROXY_PENDING")))
        val runtimeStatuses = JSONObject()
            .put("drpy-js", JSONObject()
                .put("available", drpy.optBoolean("available", false))
                .put("status", if (drpy.optBoolean("available", false)) "partial" else "unavailable")
                .put("runtimeReady", drpy.optBoolean("runtimeReady", false))
                .put("supportedOperationsReady", drpy.optBoolean("supportedOperationsReady", false))
                .put("endToEndReady", drpy.optBoolean("endToEndReady", false))
                .put("reason", drpy.optString("reason", "")))
            .put("jar", JSONObject()
                .put("available", jar.optBoolean("available", false))
                .put("status", if (jar.optBoolean("isolatedProcessNetworkProxyRequired", false) || !jar.optBoolean("endToEndReady", false)) "partial" else if (jar.optBoolean("available", false)) "supported" else "unavailable")
                .put("runtimeReady", jar.optBoolean("runtimeReady", false))
                .put("supportedOperationsReady", jar.optBoolean("supportedOperationsReady", false))
                .put("endToEndReady", jar.optBoolean("endToEndReady", false))
                .put("reason", jar.optString("reason", ""))
                .put("isolatedProcessNetworkProxyRequired", jar.optBoolean("isolatedProcessNetworkProxyRequired", false)))
            .put("csp", JSONObject().put("available", false).put("status", "unsupported").put("reason", "TVBOX_CSP_RUNTIME_NOT_IMPLEMENTED"))
            .put("ext-script", JSONObject().put("available", false).put("status", "unsupported").put("reason", "ARBITRARY_EXT_SCRIPT_EXECUTION_NOT_ENABLED"))

        return JSONObject()
            .put("available", drpy.optBoolean("available", false) || jar.optBoolean("available", false))
            .put("contractVersion", CONTRACT_VERSION)
            .put("supportedKinds", supportedKinds)
            .put("supportedOperations", supportedOperations)
            .put("supportedOperationsByKind", supportedOperationsByKind)
            .put("operationReadinessByKind", operationReadinessByKind)
            .put("runtimeStatuses", runtimeStatuses)
            .put("executionBoundary", "native-service")
            .put("status", "partial")
            // End-to-end readiness is deliberately false until each source kind has an integrated,
            // source-level verification path. Bridge availability and operation names are weaker claims.
            .put("endToEndReady", false)
            .put("capabilityScope", "bridge-presence-and-exposed-operations-not-source-end-to-end-readiness")
            .put("reason", "TVBOX_NATIVE_SOURCE_END_TO_END_COMPATIBILITY_NOT_VERIFIED")
    }

    private fun readCapabilities(raw: String): JSONObject = try {
        JSONObject(raw)
    } catch (_: Exception) {
        JSONObject().put("available", false).put("reason", "TVBOX_RUNTIME_CAPABILITIES_INVALID")
    }

    private fun appendStrings(target: org.json.JSONArray, values: org.json.JSONArray?) {
        if (values == null) return
        val existing = (0 until target.length()).map { target.optString(it) }.toMutableSet()
        for (index in 0 until values.length()) {
            val value = values.optString(index).trim()
            if (value.isNotEmpty() && existing.add(value)) target.put(value)
        }
    }

    fun execute(payload: String): String {
        if (closed) return error("TVBOX_RUNTIME_CLOSED")
        return try {
            val input = JSONObject(payload)
            val definition = input.optJSONObject("definition")
            val data = input.optJSONObject("payload")
            val kind = definition?.optString("kind").orEmpty()
                .ifEmpty { data?.optString("kind").orEmpty() }
            when (kind) {
                "drpy-js" -> drpyRuntime.execute(payload)
                "jar" -> jarRuntime.execute(payload)
                else -> error("TVBOX_EXTENSION_KIND_UNSUPPORTED")
            }
        } catch (exception: Exception) {
            error(exception.message ?: "TVBOX_RUNTIME_PAYLOAD_INVALID")
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        jarRuntime.release()
    }

    private fun error(code: String): String = JSONObject()
        .put("ok", false)
        .put("code", code)
        .put("contractVersion", CONTRACT_VERSION)
        .toString()

    companion object {
        private const val CONTRACT_VERSION = "1"
    }
}
