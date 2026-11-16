package com.pocketpilot.core.tools

import org.json.JSONObject

/**
 * One tool the agent can call. Tools are described with JSON Schema so any
 * model — cloud or on-device — can discover and call them the same way, and
 * so the same registry can be served as MCP `tools/list` / `tools/call`.
 * This is the MCP-shaped tool layer (Feature 5).
 */
data class ToolDefinition(
    /** Stable id, e.g. "tap", "open_app", "read_screen". */
    val name: String,
    /** Plain-language description for the model. */
    val description: String,
    /** JSON Schema object for the arguments. */
    val parameters: JSONObject,
    /** True when the action cannot be undone (pay, send, delete, buy). */
    val irreversible: Boolean = false,
    /** True when the tool reads secrets (vault). Such tools never receive LLM text. */
    val handlesSecrets: Boolean = false,
)

data class ToolResult(
    /** Short text the model sees. Keep screenshots out of here; attach separately. */
    val text: String,
    /** True when the call failed (model should re-plan, not repeat blindly). */
    val isError: Boolean = false,
    /** Optional binary payload (e.g. a screenshot) — rationed, never on every call. */
    val attachment: ByteArray? = null,
    val attachmentMime: String? = null,
)

/** A tool implementation: pure function from parsed args to a result. */
fun interface ToolHandler {
    fun call(args: JSONObject): ToolResult
}

/**
 * Registry + dispatcher. Tools run one at a time, each with a deadline
 * enforced by the caller (AgentLoop). Unknown tools are an error, never a guess.
 */
class ToolRegistry {
    private val tools = linkedMapOf<String, Pair<ToolDefinition, ToolHandler>>()

    fun register(definition: ToolDefinition, handler: ToolHandler) {
        tools[definition.name] = definition to handler
    }

    fun definition(name: String): ToolDefinition? = tools[name]?.first

    fun list(): List<ToolDefinition> = tools.values.map { it.first }

    /** MCP `tools/list` shape. */
    fun listAsJson(): JSONObject {
        val arr = org.json.JSONArray()
        for ((def, _) in tools.values) {
            arr.put(
                JSONObject()
                    .put("name", def.name)
                    .put("description", def.description)
                    .put("inputSchema", def.parameters),
            )
        }
        return JSONObject().put("tools", arr)
    }

    /** MCP `tools/call` shape. Returns an error result for unknown tools. */
    fun call(name: String, args: JSONObject): ToolResult {
        val entry = tools[name]
            ?: return ToolResult("Unknown tool: $name. Available: ${tools.keys.joinToString()}", isError = true)
        return try {
            entry.second.call(args)
        } catch (e: Exception) {
            ToolResult("Tool $name failed: ${e.message}", isError = true)
        }
    }

    /** OpenAI-compatible function schema, for /chat/completions `tools`. */
    fun toOpenAiTools(): org.json.JSONArray {
        val arr = org.json.JSONArray()
        for ((def, _) in tools.values) {
            arr.put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", def.name)
                            .put("description", def.description)
                            .put("parameters", def.parameters),
                    ),
            )
        }
        return arr
    }
}

/** Helper to build a JSON Schema object without hand-writing JSON. */
fun schema(
    properties: Map<String, Pair<String, String>>,
    required: List<String> = emptyList(),
): JSONObject {
    val props = JSONObject()
    for ((name, typeAndDesc) in properties) {
        props.put(name, JSONObject().put("type", typeAndDesc.first).put("description", typeAndDesc.second))
    }
    return JSONObject()
        .put("type", "object")
        .put("properties", props)
        .put("required", org.json.JSONArray(required))
}
