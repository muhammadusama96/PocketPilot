package com.pocketpilot.core.brain

import com.pocketpilot.core.config.LlmEndpoint
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** One model reply: either text, or a tool call, or both. */
data class ModelReply(
    val text: String?,
    /** Parsed tool calls: (tool name, arguments object). */
    val toolCalls: List<Pair<String, JSONObject>>,
    /** Raw usage block when the provider returns one. */
    val usage: JSONObject? = null,
)

/**
 * Thin, non-streaming client for any OpenAI-compatible /chat/completions
 * endpoint. Deliberately thin: no provider-specific SDK, no retries —
 * the agent loop owns retries and deadlines.
 */
class LlmClient(
    private val endpoint: LlmEndpoint,
    private val http: OkHttpClient = defaultHttp(endpoint),
) {
    companion object {
        fun defaultHttp(endpoint: LlmEndpoint): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(endpoint.readTimeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false)
                .build()
    }

    private val jsonMedia = "application/json".toMediaType()

    /**
     * One chat turn. [messages] are OpenAI-shaped objects
     * ({role, content} plus tool_calls / tool role as needed).
     * [tools] is the OpenAI `tools` array from ToolRegistry, or null.
     */
    fun chat(messages: JSONArray, tools: JSONArray? = null): ModelReply {
        val body = JSONObject()
            .put("model", endpoint.model)
            .put("messages", messages)
            .put("temperature", endpoint.temperature)
            .put("max_tokens", endpoint.maxTokens)
        if (tools != null) {
            body.put("tools", tools)
            body.put("tool_choice", "auto")
        }
        val request = Request.Builder()
            .url(endpoint.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer ${endpoint.apiKey}")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw LlmException("LLM HTTP ${response.code}: ${raw.take(300)}")
            }
            val root = JSONObject(raw)
            val choice = root.getJSONArray("choices").getJSONObject(0)
            val message = choice.getJSONObject("message")
            val text = message.optString("content", "").ifBlank { null }
            val calls = mutableListOf<Pair<String, JSONObject>>()
            val toolCalls = message.optJSONArray("tool_calls")
            if (toolCalls != null) {
                for (i in 0 until toolCalls.length()) {
                    val fn = toolCalls.getJSONObject(i).getJSONObject("function")
                    val argsRaw = fn.optString("arguments", "{}")
                    calls += fn.getString("name") to JSONObject(argsRaw.ifBlank { "{}" })
                }
            }
            return ModelReply(text, calls, root.optJSONObject("usage"))
        }
    }
}

class LlmException(message: String) : Exception(message)

/** Builds OpenAI-shaped message objects. */
object Messages {
    fun system(content: String): JSONObject =
        JSONObject().put("role", "system").put("content", content)

    fun user(content: String): JSONObject =
        JSONObject().put("role", "user").put("content", content)

    fun assistant(text: String?, toolCalls: List<Pair<String, JSONObject>> = emptyList()): JSONObject {
        val msg = JSONObject().put("role", "assistant")
        if (text != null) msg.put("content", text)
        if (toolCalls.isNotEmpty()) {
            val arr = JSONArray()
            for ((index, call) in toolCalls.withIndex()) {
                arr.put(
                    JSONObject()
                        .put("id", "call_$index")
                        .put("type", "function")
                        .put(
                            "function",
                            JSONObject()
                                .put("name", call.first)
                                .put("arguments", call.second.toString()),
                        ),
                )
            }
            msg.put("tool_calls", arr)
        }
        return msg
    }

    fun toolResult(callId: String, content: String): JSONObject =
        JSONObject().put("role", "tool").put("tool_call_id", callId).put("content", content)
}
