package com.pocketpilot.app.model

import com.pocketpilot.core.brain.ModelReply
import com.pocketpilot.core.brain.ModelProvider
import com.pocketpilot.core.brain.OnDeviceModelProvider
import org.json.JSONArray

/**
 * On-device model runtime seam (Feature 2).
 *
 * - LiteRT-LM path: loads a `.litertlm` file (Gemma 4 E2B/E4B) via the
 *   LiteRT-LM / MediaPipe LLM Inference API. Runs on CPU/GPU/NPU.
 * - llama.cpp path: loads a GGUF file (Qwen3, Phi-4-mini, Llama 3.2)
 *   via llama.cpp JNI. Fallback when LiteRT cannot load a model.
 *
 * Both runtimes are wired here once their dependencies are added (see
 * app/build.gradle.kts). The implementation below is the integration
 * skeleton: the download + model-picker flow is real, the native inference
 * call is the single method to fill in per runtime. Until a runtime is
 * wired, the provider reports not-loaded and ModelRouter falls back to
 * cloud — the app never dead-ends.
 */
class OnDeviceModel(
    override val modelDisplayName: String,
) : OnDeviceModelProvider {

    private var loadedPath: String? = null

    override val isLoaded: Boolean get() = loadedPath != null

    override fun load(modelFilePath: String) {
        val file = java.io.File(modelFilePath)
        require(file.exists()) { "Model file not found: $modelFilePath" }
        // TODO(v0.2): branch on extension —
        //   .litertlm -> LiteRT-LM LlmInference.createFromOptions(...)
        //   .gguf    -> llama.cpp JNI load (llama.cpp Android bindings)
        // Until then, mark loaded so the router can select it, and fail
        // loudly on complete() instead of silently pretending to reason.
        loadedPath = modelFilePath
    }

    override fun unload() {
        // TODO(v0.2): release the native inference session.
        loadedPath = null
    }

    override fun complete(messages: JSONArray, tools: JSONArray?): ModelReply {
        val path = loadedPath
            ?: throw IllegalStateException("No on-device model loaded.")
        throw UnsupportedOperationException(
            "On-device inference runtime not wired yet for $path. " +
                "Add the LiteRT-LM or llama.cpp runtime (see OnDeviceModel.kt) " +
                "or switch to cloud mode in Settings.",
        )
    }
}
