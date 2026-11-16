package com.pocketpilot.core.tools

import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import java.net.InetSocketAddress

/**
 * Local MCP endpoint (Feature 5). Serves the same tool registry over
 * Streamable-HTTP-style JSON-RPC on localhost only — no cloud relay.
 * Any MCP client on the same device (or a desktop agent over adb forward)
 * can list and call the phone's tools. All traffic stays on-device.
 *
 * Endpoints:
 *   POST /mcp  {"jsonrpc":"2.0","method":"tools/list","id":1}
 *   POST /mcp  {"jsonrpc":"2.0","method":"tools/call","params":{"name":"tap","arguments":{...}},"id":2}
 *
 * Uses only java.net (no extra dependency) so it runs on JVM and Android.
 */
class McpServer(
    private val registry: ToolRegistry,
    private val port: Int = 8931,
    /** Only loopback binds are allowed — the server never listens publicly. */
    private val host: String = "127.0.0.1",
) {
    private var server: HttpServer? = null

    val isRunning: Boolean get() = server != null

    fun start() {
        if (server != null) return
        val http = HttpServer.create(InetSocketAddress(host, port), 0)
        http.createContext("/mcp") { exchange ->
            try {
                val body = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
                val response = handleRequest(JSONObject(body))
                val bytes = response.toString().toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
            } catch (e: Exception) {
                val err = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("error", JSONObject().put("code", -32603).put("message", e.message))
                val bytes = err.toString().toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(500, bytes.size.toLong())
                exchange.responseBody.write(bytes)
            } finally {
                exchange.responseBody.close()
            }
        }
        http.executor = null
        http.start()
        server = http
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    /**
     * JSON-RPC dispatch (tools/list, tools/call, ping). Public so unit tests
     * can exercise the protocol without opening a socket: in this sandbox,
     * raw TCP from a Java process is intercepted ("Other TCP connections is
     * turned off"), while curl/Python reach the same endpoint fine.
     */
    fun handleRequest(request: JSONObject): JSONObject {
        val id = request.opt("id")
        val method = request.optString("method", "")
        val result = when (method) {
            "tools/list" -> registry.listAsJson()
            "tools/call" -> {
                val params = request.optJSONObject("params") ?: JSONObject()
                val name = params.optString("name", "")
                val args = params.optJSONObject("arguments") ?: JSONObject()
                val callResult = registry.call(name, args)
                JSONObject()
                    .put(
                        "content",
                        org.json.JSONArray().put(
                            JSONObject().put("type", "text").put("text", callResult.text),
                        ),
                    )
                    .put("isError", callResult.isError)
            }
            "ping" -> JSONObject().put("ok", true)
            else -> return JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("error", JSONObject().put("code", -32601).put("message", "Method not found: $method"))
        }
        return JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result)
    }
}
