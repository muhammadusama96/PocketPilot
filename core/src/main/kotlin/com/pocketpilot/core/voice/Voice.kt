package com.pocketpilot.core.voice

/** Streaming ASR: audio in, partial + final transcripts out. */
interface AsrEngine {
    val name: String
    fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit)
    fun feedAudio(pcm16kHz: ByteArray)
    fun stop()
}

/** TTS: text in, audio out (played by the app module). */
interface TtsEngine {
    val name: String
    /** Synthesise text to 24 kHz PCM. Blocking; call off the UI thread. */
    fun speak(text: String): ByteArray
    fun stop()
}

/**
 * Voice pipeline: microphone -> ASR -> agent loop -> TTS.
 * The app module wires real engines (ML Kit GenAI on-device speech,
 * system TTS, or cloud Volcengine/Google). :core only defines the flow
 * so it can be tested with fake engines.
 */
class VoicePipeline(
    private val asr: AsrEngine,
    private val tts: TtsEngine,
    private val onUserSaid: (String) -> String,
) {
    fun startListening() {
        asr.start(
            onPartial = {},
            onFinal = { text ->
                if (text.isNotBlank()) {
                    val reply = onUserSaid(text)
                    if (reply.isNotBlank()) {
                        runCatching { tts.speak(reply) }
                    }
                }
            },
        )
    }

    fun feedAudio(pcm16kHz: ByteArray) = asr.feedAudio(pcm16kHz)

    fun stop() {
        runCatching { asr.stop() }
        runCatching { tts.stop() }
    }
}
