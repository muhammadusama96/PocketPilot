package com.pocketpilot.core.config

/**
 * How the brain reasons. Clean-room design inspired by the general
 * architecture of phone agents (on-device loop + cloud fallback),
 * written from scratch for PocketPilot.
 */
enum class BrainMode {
    /** Reason fully on-device with a small local model. No prompt leaves the phone. */
    ON_DEVICE,

    /** Reason in the cloud via an OpenAI-compatible /chat/completions endpoint. */
    CLOUD,

    /** Brain runs on a remote hub over WebSocket; the phone is only hands + eyes. */
    HUB,
}

data class LlmEndpoint(
    /** Base URL of an OpenAI-compatible API, e.g. https://api.deepseek.com */
    val baseUrl: String,
    /** Model id, e.g. "deepseek-chat", "gpt-4o-mini", "gemini-2.5-flash" */
    val model: String,
    /** API key. NEVER baked into a build — supplied by the user at runtime only. */
    val apiKey: String = "",
    val readTimeoutSeconds: Long = 180,
    val maxTokens: Int = 2048,
    val temperature: Double = 0.3,
)

data class OnDeviceModelSpec(
    /** Human name shown in the model picker, e.g. "Gemma 4 E2B (int4)" */
    val displayName: String,
    /** Hugging Face repo id to download from, e.g. "google/gemma-3-1b-it-litertlm" */
    val repoId: String,
    /** File inside the repo, e.g. "gemma-3-1b-it-int4.litertlm" */
    val fileName: String,
    /** Approximate size in MB, shown before downloading. */
    val sizeMb: Long,
    /** Runtime that can load it: "litert" or "llamacpp". */
    val runtime: String,
)

/** Default model catalogue shown in the picker. Small = low RAM, capable = better reasoning. */
val DEFAULT_ON_DEVICE_MODELS: List<OnDeviceModelSpec> = listOf(
    OnDeviceModelSpec("Gemma 3 1B (int4) — low RAM", "google/gemma-3-1b-it-litertlm", "gemma-3-1b-it-int4.litertlm", 720, "litert"),
    OnDeviceModelSpec("Qwen3 1.7B (GGUF Q4) — multilingual", "Qwen/Qwen3-1.7B-GGUF", "Qwen3-1.7B-Q4_K_M.gguf", 1100, "llamacpp"),
    OnDeviceModelSpec("Gemma 4 E2B (int4) — capable", "google/gemma-4-e2b-it-litertlm", "gemma-4-e2b-it-int4.litertlm", 2600, "litert"),
    OnDeviceModelSpec("Phi-4-mini 3.8B (GGUF Q4) — reasoning", "microsoft/Phi-4-mini-instruct-GGUF", "Phi-4-mini-instruct-Q4_K_M.gguf", 2700, "llamacpp"),
)

data class BrainConfig(
    val mode: BrainMode = BrainMode.CLOUD,
    val cloud: LlmEndpoint = LlmEndpoint(
        baseUrl = "https://api.deepseek.com",
        model = "deepseek-chat",
    ),
    /** On-device model to use when mode == ON_DEVICE. */
    val onDeviceModel: OnDeviceModelSpec = DEFAULT_ON_DEVICE_MODELS[0],
    /** Max agent steps per user request before asking the user. */
    val maxSteps: Int = 25,
    /** Per tool-call deadline. A slow tool is killed, not waited on forever. */
    val toolTimeoutSeconds: Long = 30,
    /** Ask the user before irreversible actions (pay / send / delete / buy). */
    val confirmIrreversible: Boolean = true,
)
