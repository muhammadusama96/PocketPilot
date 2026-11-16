package com.pocketpilot.core.brain

import com.pocketpilot.core.config.BrainConfig
import com.pocketpilot.core.config.BrainMode
import com.pocketpilot.core.config.LlmEndpoint
import org.json.JSONArray

/**
 * One reasoning step: the model sees the message history and returns a
 * reply (text and/or tool calls). Every backend — cloud, on-device SLM,
 * hub — implements this one interface, so the agent loop never cares
 * where the brain lives. This is the "any LLM backend" seam.
 */
fun interface ModelProvider {
    fun complete(messages: JSONArray, tools: JSONArray?): ModelReply
}

/** Cloud backend: OpenAI-compatible endpoint (DeepSeek, OpenAI, Gemini, ...). */
class CloudModelProvider(endpoint: LlmEndpoint) : ModelProvider {
    private val client = LlmClient(endpoint)
    override fun complete(messages: JSONArray, tools: JSONArray?): ModelReply =
        client.chat(messages, tools)
}

/**
 * On-device backend. Implementations:
 * - LiteRTModelProvider: Gemma 4 E2B/E4B via LiteRT-LM / MediaPipe LLM
 *   Inference (see app module: OnDeviceModel.kt). Loads a .litertlm file
 *   the user downloaded; runs on CPU/GPU/NPU; never sends data out.
 * - LlamaCppModelProvider: GGUF models (Qwen3, Phi-4-mini, Llama 3.2)
 *   via llama.cpp JNI. Fallback when LiteRT cannot load a model.
 * Both are wired in the app module, where the Android runtimes live;
 * :core only defines the seam so the loop stays platform-neutral.
 */
interface OnDeviceModelProvider : ModelProvider {
    val modelDisplayName: String
    val isLoaded: Boolean
    fun load(modelFilePath: String)
    fun unload()
}

/** Hub backend: brain runs elsewhere, reached over WebSocket (stub seam). */
class HubModelProvider(private val hubUrl: String) : ModelProvider {
    override fun complete(messages: JSONArray, tools: JSONArray?): ModelReply {
        throw UnsupportedOperationException(
            "Hub mode needs the app module's WebSocket client (hub: $hubUrl).",
        )
    }
}

/**
 * Picks the backend for a task. Default policy: try on-device first when
 * the mode allows it; fall back to cloud when the on-device model is not
 * loaded or the task needs more power. The user can pin a mode in settings.
 */
class ModelRouter(
    private val config: BrainConfig,
    private val onDevice: OnDeviceModelProvider?,
    private val cloud: ModelProvider,
    private val hub: ModelProvider? = null,
) {
    fun providerFor(taskNeedsCloud: Boolean = false): ModelProvider = when (config.mode) {
        BrainMode.ON_DEVICE -> {
            val local = onDevice?.takeIf { it.isLoaded }
            local ?: cloud // graceful fallback, never a dead end
        }
        BrainMode.CLOUD -> cloud
        BrainMode.HUB -> hub ?: cloud
    }.also {
        // If the task explicitly needs cloud power (long context, hard reasoning),
        // prefer cloud even in ON_DEVICE mode — the user can disable this.
        if (taskNeedsCloud && config.mode == BrainMode.ON_DEVICE) return cloud
    }
}
