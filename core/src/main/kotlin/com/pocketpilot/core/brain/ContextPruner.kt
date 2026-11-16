package com.pocketpilot.core.brain

import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the message history inside the model's context budget.
 * Screenshots are the first thing dropped (they are the biggest); then the
 * oldest tool exchanges are summarised into a short archive note instead of
 * being deleted outright, so the agent does not forget what it already did.
 */
class ContextPruner(
    /** Rough character budget for the whole history (~4 chars per token). */
    private val maxChars: Int = 24_000,
) {
    /** Returns a pruned copy of [messages]; the original is untouched. */
    fun prune(messages: JSONArray): JSONArray {
        val kept = JSONArray()
        var total = 0
        // Walk newest-first so the latest steps always survive.
        val reversed = mutableListOf<JSONObject>()
        for (i in 0 until messages.length()) reversed += messages.getJSONObject(i)
        val droppedToolSteps = mutableListOf<String>()
        for (msg in reversed.asReversed()) {
            val text = msg.optString("content", "")
            // Tool results that carried a screenshot marker are the first to go.
            val isScreenshotResult = text.contains("[screenshot attached]")
            val cost = if (isScreenshotResult) maxChars else text.length
            if (total + cost > maxChars) {
                if (msg.optString("role") == "tool") {
                    droppedToolSteps += text.take(120)
                }
                continue
            }
            total += text.length
            kept.put(0, msg)
        }
        if (droppedToolSteps.isNotEmpty()) {
            val note = JSONObject()
                .put("role", "system")
                .put(
                    "content",
                    "Earlier steps were archived to save context. Summary of dropped tool results: " +
                        droppedToolSteps.take(5).joinToString(" | "),
                )
            val out = JSONArray().put(note)
            for (i in 0 until kept.length()) out.put(kept.getJSONObject(i))
            return out
        }
        return kept
    }
}
