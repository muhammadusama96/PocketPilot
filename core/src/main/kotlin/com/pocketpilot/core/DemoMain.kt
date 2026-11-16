package com.pocketpilot.core

import com.pocketpilot.core.brain.AgentLoop
import com.pocketpilot.core.brain.Messages
import com.pocketpilot.core.brain.ModelProvider
import com.pocketpilot.core.brain.ModelReply
import com.pocketpilot.core.brain.Prompt
import com.pocketpilot.core.config.BrainConfig
import com.pocketpilot.core.memory.InMemoryVault
import com.pocketpilot.core.memory.Notebook
import com.pocketpilot.core.tools.ToolRegistry
import com.pocketpilot.core.tools.ToolSchemas
import org.json.JSONArray
import org.json.JSONObject

/**
 * JVM demo: runs the agent loop with a scripted fake model and a fake
 * screen, proving the loop, tools and memory work with no Android and no
 * network. Run: `java -cp <core jar + deps> com.pocketpilot.core.DemoMain`
 */
object DemoMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val notebookData = StringBuilder()
        val notebook = Notebook(
            load = { notebookData.toString() },
            save = { notebookData.clear().append(it) },
        )
        notebook.remember("self", "User's name is Usama.")
        val vault = InMemoryVault()
        vault.put("gmail", "••••••••")

        val tools = ToolRegistry()
        for (def in ToolSchemas.definitions()) {
            tools.register(def) { callArgs -> fakeTool(def.name, callArgs, notebook, vault) }
        }

        // Scripted model: read screen -> tap search -> type -> finish.
        val script = ArrayDeque<ModelReply>(
            listOf(
                ModelReply(null, listOf("get_screen" to JSONObject())),
                ModelReply("I can see the home screen. Opening search.", listOf("tap" to JSONObject().put("text", "Search"))),
                ModelReply(null, listOf("type_text" to JSONObject().put("text", "weather").put("submit", true))),
                ModelReply("Done.", listOf("finish" to JSONObject().put("summary", "Searched for weather."))),
            ),
        )
        val model = ModelProvider { _, _ -> script.removeFirst() }

        val loop = AgentLoop(
            config = BrainConfig(),
            model = model,
            tools = tools,
            onStep = { println("  $it") },
        )
        val outcome = loop.run(
            "Check the weather",
            Prompt.systemPrompt(userName = "Usama", notebookSummary = notebook.summaryForPrompt()),
        )
        loop.shutdown()
        println("Outcome: ${outcome.summary} (steps: ${outcome.stepsTaken})")
        println("Memory still holds: ${notebook.entries("self")}")
    }

    private fun fakeTool(
        name: String,
        args: JSONObject,
        notebook: Notebook,
        vault: InMemoryVault,
    ): com.pocketpilot.core.tools.ToolResult {
        return when (name) {
            "get_screen" -> com.pocketpilot.core.tools.ToolResult(
                "App: com.example.launcher\n" +
                    "e1 [TextView] \"Search\" at (500,120) clickable \n" +
                    "e2 [ImageView] \"Weather\" at (200,400) clickable \n",
            )
            "tap" -> com.pocketpilot.core.tools.ToolResult("Tapped ${args.optString("text")}. Screen updated.")
            "type_text" -> com.pocketpilot.core.tools.ToolResult("Typed '${args.optString("text")}'.")
            "remember" -> {
                notebook.remember(args.optString("section"), args.optString("text"))
                com.pocketpilot.core.tools.ToolResult("Remembered.")
            }
            "recall" -> com.pocketpilot.core.tools.ToolResult(
                notebook.search(args.optString("query")).joinToString("; ") { "[${it.first}] ${it.second}" },
            )
            "fill_secret" -> {
                val ok = vault.typeIntoFocusedField(args.optString("label"))
                com.pocketpilot.core.tools.ToolResult(if (ok) "Secret typed locally." else "No secret saved under that label.")
            }
            "finish" -> com.pocketpilot.core.tools.ToolResult("Finished.")
            else -> com.pocketpilot.core.tools.ToolResult("Fake $name executed with $args")
        }
    }
}
