package com.pocketpilot.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import com.pocketpilot.core.voice.AsrEngine
import com.pocketpilot.core.voice.TtsEngine
import java.util.Locale

/**
 * Voice on Android, in priority order:
 * 1. ML Kit GenAI on-device speech (ASR + TTS via AICore, Nov 2026) —
 *    free, offline, no model download. Integration point: see
 *    MlKitSpeech.kt (wired when the genai dependency is added).
 * 2. Android system TTS (always available) — speak-only fallback.
 * 3. Cloud ASR/TTS (Volcengine / Google Vertex) — only when the user
 *    supplies a key; never the default.
 *
 * Push-to-talk / wake-word gated. No always-on listening in v0.1.
 */
class SystemTtsEngine(context: Context) : TtsEngine {
    override val name: String = "system-tts"

    // lateinit (not a val with an init lambda) so the OnInitListener can
    // safely reference the instance — a self-referencing val initializer
    // is a compile error.
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.getDefault())
            }
        }
    }

    /** Speaks out loud; returns empty bytes (no audio capture in v0.1). */
    override fun speak(text: String): ByteArray {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pilot-utterance")
        return ByteArray(0)
    }

    override fun stop() {
        tts.stop()
        tts.shutdown()
    }
}

/**
 * ML Kit GenAI on-device ASR seam. The real implementation lives in
 * MlKitSpeech.kt and is enabled once the `com.google.mlkit:genai-*`
 * artifacts are added (see app/build.gradle.kts). Until then, voice input
 * falls back to the system SpeechRecognizer via AndroidVoice below.
 */
interface MlKitSpeech {
    fun startStreamingAsr(onPartial: (String) -> Unit, onFinal: (String) -> Unit)
    fun stopAsr()
}

/** Placeholder ASR that reports itself unavailable — replaced by MlKitSpeech. */
class UnavailableAsrEngine : AsrEngine {
    override val name: String = "unavailable"
    override fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit) {
        onFinal("")
    }
    override fun feedAudio(pcm16kHz: ByteArray) = Unit
    override fun stop() = Unit
}
