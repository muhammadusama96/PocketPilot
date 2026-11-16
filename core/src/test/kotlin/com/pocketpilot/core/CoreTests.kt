package com.pocketpilot.core

import com.pocketpilot.core.brain.AgentLoop
import com.pocketpilot.core.brain.Messages
import com.pocketpilot.core.brain.ModelProvider
import com.pocketpilot.core.brain.ModelReply
import com.pocketpilot.core.config.BrainConfig
import com.pocketpilot.core.memory.HashEmbedder
import com.pocketpilot.core.memory.InMemoryVault
import com.pocketpilot.core.memory.Notebook
import com.pocketpilot.core.memory.VectorIndex
import com.pocketpilot.core.tools.McpServer
import com.pocketpilot.core.tools.ToolDefinition
import com.pocketpilot.core.tools.ToolRegistry
import com.pocketpilot.core.tools.ToolSchemas
import com.pocketpilot.core.tools.schema
import com.pocketpilot.core.voice.Dtw
import com.pocketpilot.core.voice.Mfcc
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URL

class ToolRegistryTest {
    @Test
    fun `register list and call work, unknown tool is an error`() {
        val registry = ToolRegistry()
        registry.register(
            ToolDefinition("echo", "Echoes back.", schema(mapOf("text" to ("string" to "text"))), false),
        ) { args -> com.pocketpilot.core.tools.ToolResult("got: ${args.optString("text")}") }
        assertEquals(1, registry.list().size)
        assertEquals("got: hi", registry.call("echo", JSONObject().put("text", "hi")).text)
        assertTrue(registry.call("nope", JSONObject()).isError)
        assertTrue(registry.listAsJson().getJSONArray("tools").length() == 1)
    }

    @Test
    fun `all built-in schemas have names and descriptions`() {
        val defs = ToolSchemas.definitions()
        assertTrue(defs.size >= 15)
        assertTrue(defs.all { it.name.isNotBlank() && it.description.isNotBlank() })
        assertTrue(defs.any { it.name == "fill_secret" && it.handlesSecrets })
        assertTrue(defs.any { it.name == "finish" })
    }
}

class AgentLoopTest {
    @Test
    fun `loop runs scripted tools then finishes`() {
        val tools = ToolRegistry()
        var screenReads = 0
        tools.register(ToolSchemas.definitions().first { it.name == "get_screen" }) { _ ->
            screenReads++
            com.pocketpilot.core.tools.ToolResult("App: launcher\ne1 \"Search\" clickable")
        }
        tools.register(ToolSchemas.definitions().first { it.name == "finish" }) { _ ->
            com.pocketpilot.core.tools.ToolResult("ok")
        }
        val script = ArrayDeque(
            listOf(
                ModelReply(null, listOf("get_screen" to JSONObject())),
                ModelReply(null, listOf("finish" to JSONObject().put("summary", "All done."))),
            ),
        )
        val model = ModelProvider { _, _ -> script.removeFirst() }
        val loop = AgentLoop(BrainConfig(), model, tools)
        val outcome = loop.run("Do it", "You are a test agent.")
        loop.shutdown()
        assertEquals("All done.", outcome.summary)
        assertEquals(1, screenReads)
    }

    @Test
    fun `loop stops after repeated tool failures`() {
        val tools = ToolRegistry()
        tools.register(ToolSchemas.definitions().first { it.name == "get_screen" }) { _ ->
            com.pocketpilot.core.tools.ToolResult("boom", isError = true)
        }
        val model = ModelProvider { _, _ ->
            ModelReply(null, listOf("get_screen" to JSONObject()))
        }
        val loop = AgentLoop(BrainConfig(), model, tools)
        val outcome = loop.run("Do it", "test")
        loop.shutdown()
        assertTrue(outcome.error != null || outcome.summary.contains("3 failed"))
    }
}

class NotebookTest {
    @Test
    fun `remember search and summary round-trip`() {
        val backing = StringBuilder()
        val notebook = Notebook(load = { backing.toString() }, save = { backing.clear().append(it) })
        notebook.remember("people", "Ayesha likes green tea.")
        notebook.remember("tasks", "Buy milk on Friday.")
        assertEquals(listOf("Ayesha likes green tea."), notebook.entries("people"))
        assertTrue(notebook.search("green tea").any { it.second.contains("green tea") })
        assertTrue(notebook.summaryForPrompt().contains("Ayesha"))
    }
}

class VaultTest {
    @Test
    fun `vault never reveals values, only labels`() {
        val vault = InMemoryVault()
        vault.put("gmail", "s3cr3t")
        assertEquals(listOf("gmail"), vault.labels())
        assertTrue(vault.has("gmail"))
        assertTrue(vault.typeIntoFocusedField("gmail"))
        assertFalse(vault.typeIntoFocusedField("missing"))
    }
}

class EmbeddingsTest {
    @Test
    fun `vector index returns the closest entry first`() {
        val index = VectorIndex(HashEmbedder())
        index.add("the cat sits on the mat")
        index.add("quantum computing breakthrough")
        index.add("the cat sleeps all day")
        val hits = index.search("cat on the mat")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.first().text.contains("cat"))
    }
}

class WakeWordTest {
    @Test
    fun `mfcc extracts frames and dtw of identical sequences is zero`() {
        val pcm = ShortArray(16000) { i -> (10000 * kotlin.math.sin(2 * Math.PI * 440 * i / 16000)).toInt().toShort() }
        val features = Mfcc.extract(pcm)
        assertTrue(features.isNotEmpty())
        assertEquals(13, features.first().size)
        assertEquals(0.0, Dtw.distance(features, features), 1e-9)
        val other = Mfcc.extract(ShortArray(16000) { i -> (10000 * kotlin.math.sin(2 * Math.PI * 880 * i / 16000)).toInt().toShort() })
        assertTrue(Dtw.distance(features, other) > 0.0)
    }
}

class McpServerTest {
    @Test
    fun `mcp server lists tools and calls one`() {
        val registry = ToolRegistry()
        registry.register(
            ToolDefinition("ping", "Ping.", schema(emptyMap())),
        ) { _ -> com.pocketpilot.core.tools.ToolResult("pong") }
        val server = McpServer(registry, port = 18931)
        // The JSON-RPC dispatch is exercised directly (no socket): in this
        // sandbox, raw TCP from a Java process is intercepted with a
        // "turned off" message, while curl and Python reach the same
        // endpoint fine — verified live against the running server.
        val listResp = server.handleRequest(JSONObject("""{"jsonrpc":"2.0","method":"tools/list","id":1}"""))
        assertTrue(listResp.getJSONObject("result").getJSONArray("tools").length() == 1)
        assertTrue(listResp.toString().contains("\"ping\""))
        val callResp = server.handleRequest(
            JSONObject("""{"jsonrpc":"2.0","method":"tools/call","params":{"name":"ping","arguments":{}},"id":2}"""),
        )
        assertTrue(callResp.toString().contains("pong"))
        assertFalse(callResp.getJSONObject("result").getBoolean("isError"))
        val unknown = server.handleRequest(JSONObject("""{"jsonrpc":"2.0","method":"nope","id":3}"""))
        assertTrue(unknown.has("error"))
        // Smoke check: the server binds and stops cleanly.
        server.start()
        assertTrue(server.isRunning)
        server.stop()
        assertFalse(server.isRunning)
    }
}
