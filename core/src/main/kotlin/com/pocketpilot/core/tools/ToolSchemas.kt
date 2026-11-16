package com.pocketpilot.core.tools

import org.json.JSONObject

/**
 * Built-in tool vocabulary. These are the tools the agent loop can call;
 * the Android app registers handlers that bind them to the real
 * AccessibilityService, device controllers and memory stores.
 * Names and shapes are PocketPilot's own; the *idea* of a ~100-tool
 * vocabulary split into screen/device/memory/flow groups follows the
 * established phone-agent pattern.
 */
object ToolSchemas {

    /** Actions the agent refuses outright, whatever the user asks. */
    val REFUSAL_LIST: Set<String> = setOf(
        // Tapping inside WeChat can permanently black its screenshots on some
        // devices (known phone-agent hazard). Reading is fine; tapping is not.
        "tap:com.tencent.mm",
    )

    fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            "get_screen",
            "Read the current screen: list of visible UI elements with id, text, type and bounds, plus whether each is clickable/editable.",
            schema(emptyMap()),
        ),
        ToolDefinition(
            "take_screenshot",
            "Take a screenshot of the current screen. Use sparingly — at most once every few steps — screenshots cost context.",
            schema(emptyMap()),
        ),
        ToolDefinition(
            "tap",
            "Tap a UI element by its element id (e1, e2, ...) from get_screen, or by normalised 0-1000 x/y coordinates.",
            schema(
                mapOf(
                    "element_id" to ("string" to "Element id from get_screen, e.g. e12"),
                    "x" to ("integer" to "Normalised x 0-1000 (use when no element id)"),
                    "y" to ("integer" to "Normalised y 0-1000 (use when no element id)"),
                    "text" to ("string" to "Visible text to tap (fallback anchor)"),
                ),
            ),
        ),
        ToolDefinition(
            "type_text",
            "Type text into the currently focused field, or into the element with the given id.",
            schema(
                mapOf(
                    "text" to ("string" to "Text to type"),
                    "element_id" to ("string" to "Optional element id to focus first"),
                    "submit" to ("boolean" to "Press Enter after typing"),
                ),
                required = listOf("text"),
            ),
        ),
        ToolDefinition(
            "swipe",
            "Swipe on the screen, e.g. to scroll a list.",
            schema(
                mapOf(
                    "direction" to ("string" to "up, down, left or right"),
                    "distance" to ("integer" to "Swipe length in pixels, default 600"),
                ),
                required = listOf("direction"),
            ),
        ),
        ToolDefinition(
            "open_app",
            "Launch an installed app by package name or display name.",
            schema(
                mapOf("app" to ("string" to "Package name (com.example.app) or display name")),
                required = listOf("app"),
            ),
        ),
        ToolDefinition(
            "press_key",
            "Press a system key: back, home, recents, volume_up, volume_down.",
            schema(
                mapOf("key" to ("string" to "back | home | recents | volume_up | volume_down")),
                required = listOf("key"),
            ),
        ),
        ToolDefinition(
            "wait",
            "Wait a number of seconds for the screen to settle (e.g. after a slow load).",
            schema(
                mapOf("seconds" to ("integer" to "Seconds to wait, 1-10")),
                required = listOf("seconds"),
            ),
        ),
        ToolDefinition(
            "read_notifications",
            "Read recent notifications (sender, app, text). Never open a notification's content to the model beyond its text.",
            schema(emptyMap()),
        ),
        ToolDefinition(
            "get_device_info",
            "Battery level, time, network state, location (coarse) and current foreground app.",
            schema(emptyMap()),
        ),
        ToolDefinition(
            "remember",
            "Save a fact to long-term memory (notebook): preferences, people, tasks, app layouts the user taught you.",
            schema(
                mapOf(
                    "section" to ("string" to "Section: self, people, tasks, memories, flows, wiki"),
                    "text" to ("string" to "The fact to remember"),
                ),
                required = listOf("section", "text"),
            ),
        ),
        ToolDefinition(
            "recall",
            "Search long-term memory for a fact. Returns matching entries with a relevance score.",
            schema(
                mapOf("query" to ("string" to "What to look up")),
                required = listOf("query"),
            ),
        ),
        ToolDefinition(
            "fill_secret",
            "Type a saved secret (password, card number) from the vault into the focused field. The secret value is NEVER shown to the model and never appears in the result — this tool only types it locally.",
            schema(
                mapOf("label" to ("string" to "Vault label, e.g. 'gmail'")),
                required = listOf("label"),
            ),
            handlesSecrets = true,
        ),
        ToolDefinition(
            "record_flow",
            "Start recording the user's taps as a reusable flow with the given name.",
            schema(
                mapOf("name" to ("string" to "Flow name")),
                required = listOf("name"),
            ),
        ),
        ToolDefinition(
            "run_flow",
            "Replay a recorded flow by name. Flows self-check each step and stop on mismatch.",
            schema(
                mapOf("name" to ("string" to "Flow name")),
                required = listOf("name"),
            ),
        ),
        ToolDefinition(
            "ask_user",
            "Ask the user a question when you are stuck or before an irreversible action. Use sparingly — prefer finishing the task.",
            schema(
                mapOf("question" to ("string" to "The question")),
                required = listOf("question"),
            ),
            irreversible = false,
        ),
        ToolDefinition(
            "finish",
            "End the task with a short summary of what was done for the user.",
            schema(
                mapOf("summary" to ("string" to "One or two sentences for the user")),
                required = listOf("summary"),
            ),
        ),
    )

    /** A tool call is refused when it targets a package on the refusal list. */
    fun isRefused(toolName: String, args: JSONObject): Boolean {
        if (toolName != "tap") return false
        val text = args.optString("text", "")
        return REFUSAL_LIST.any { it.removePrefix("tap:") in text }
    }
}
