package com.pocketpilot.core.memory

import org.json.JSONObject

/**
 * Long-term memory (the notebook). One encrypted JSON blob, split into
 * sections. The encryption itself lives in the app module (Android Keystore
 * + AES/GCM); :core only defines the shape and the rule that memory is
 * NEVER sent to the model wholesale — only short retrieved snippets are.
 */
class Notebook(
    /** Loads the raw JSON blob (decrypted by the caller). Empty = fresh. */
    private val load: () -> String,
    /** Persists the raw JSON blob (the caller encrypts it). */
    private val save: (String) -> Unit,
) {
    private val data: JSONObject by lazy {
        val raw = runCatching { load() }.getOrDefault("")
        if (raw.isBlank()) JSONObject() else JSONObject(raw)
    }

    /** Sections: self, people, tasks, memories, flows, wiki, guard. */
    fun entries(section: String): List<String> {
        val arr = data.optJSONArray(section) ?: return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }

    fun remember(section: String, text: String) {
        val arr = data.optJSONArray(section) ?: org.json.JSONArray().also { data.put(section, it) }
        arr.put(text)
        persist()
    }

    fun forget(section: String, text: String) {
        val arr = data.optJSONArray(section) ?: return
        for (i in arr.length() - 1 downTo 0) {
            if (arr.getString(i) == text) arr.remove(i)
        }
        persist()
    }

    /** Short summary for the system prompt — a few lines, never the whole blob. */
    fun summaryForPrompt(maxLines: Int = 12): String {
        val lines = mutableListOf<String>()
        for (section in listOf("self", "people", "tasks", "memories")) {
            for (entry in entries(section)) {
                lines += "[$section] $entry"
                if (lines.size >= maxLines) return lines.joinToString("\n")
            }
        }
        return lines.joinToString("\n").ifBlank { "(no memories yet)" }
    }

    /** Keyword recall for the `recall` tool. */
    fun search(query: String, maxResults: Int = 5): List<Pair<String, String>> {
        val words = query.lowercase().split(" ").filter { it.length > 2 }
        val scored = mutableListOf<Triple<Int, String, String>>()
        for (section in listOf("self", "people", "tasks", "memories", "flows", "wiki")) {
            for (entry in entries(section)) {
                val score = words.count { entry.lowercase().contains(it) }
                if (score > 0) scored += Triple(score, section, entry)
            }
        }
        return scored.sortedByDescending { it.first }.take(maxResults)
            .map { it.second to it.third }
    }

    private fun persist() = save(data.toString())
}
