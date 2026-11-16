package com.pocketpilot.core.screen

/** One visible UI element, from the accessibility tree. Bounds are 0-1000 normalised. */
data class UiNode(
    /** Stable id for this screen snapshot, e.g. "e12". */
    val id: String,
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val clickable: Boolean,
    val editable: Boolean,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val label: String get() = text?.takeIf { it.isNotBlank() } ?: contentDescription ?: (className ?: "?")
}

/** A snapshot of what the agent currently sees. */
data class ScreenState(
    val packageName: String,
    val activityName: String?,
    val nodes: List<UiNode>,
    /** Screenshot bytes when one was taken this step, else null. */
    val screenshot: ByteArray? = null,
) {
    fun findByText(text: String): UiNode? =
        nodes.firstOrNull { it.text?.contains(text, ignoreCase = true) == true }

    fun clickables(): List<UiNode> = nodes.filter { it.clickable }

    /** Short text dump for the model — the `get_screen` tool result. */
    fun toTextDump(): String {
        val sb = StringBuilder()
        sb.append("App: $packageName\n")
        for (node in nodes) {
            sb.append("${node.id} [${node.className?.substringAfterLast('.')}] \"${node.label}\" ")
            sb.append("at (${node.centerX},${node.centerY}) ")
            if (node.clickable) sb.append("clickable ")
            if (node.editable) sb.append("editable ")
            sb.append("\n")
        }
        return sb.toString()
    }
}

/**
 * Grounding: turn "tap the Send button" into coordinates.
 * v1 is tree-first (exact bounds, no guessing). A vision grounder is only
 * consulted when the tree has no match — and every vision tap is validated
 * against a fresh tree before executing.
 */
interface Grounder {
    /** Returns normalised 0-1000 (x, y) or null when it cannot ground. */
    fun ground(state: ScreenState, target: String): Pair<Int, Int>?
}

class TreeGrounder : Grounder {
    override fun ground(state: ScreenState, target: String): Pair<Int, Int>? {
        val node = state.findByText(target) ?: return null
        return node.centerX to node.centerY
    }
}

/**
 * Vision grounder (Feature 4). On-device 2B grounding model (GUI-Owl 1.5 /
 * Qwen-UI-Agent 2B, GGUF 4-bit via llama.cpp) that answers one action per
 * screenshot. Wired in the app module; :core defines the seam.
 * STUB in v0.1 — tree grounding covers most apps; vision is the fallback.
 */
class VisionGrounder : Grounder {
    override fun ground(state: ScreenState, target: String): Pair<Int, Int>? {
        // TODO(v0.2): load GUI-Owl 1.5 GGUF via llama.cpp, run on screenshot,
        // parse the returned point, validate against a fresh tree.
        return null
    }
}

/** Fused grounder: tree first, vision only as fallback. */
class FusedGrounder(
    private val tree: Grounder = TreeGrounder(),
    private val vision: Grounder = VisionGrounder(),
) : Grounder {
    override fun ground(state: ScreenState, target: String): Pair<Int, Int>? =
        tree.ground(state, target) ?: vision.ground(state, target)
}
