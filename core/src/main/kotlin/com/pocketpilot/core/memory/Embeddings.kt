package com.pocketpilot.core.memory

import kotlin.math.sqrt

/**
 * On-device semantic recall. Embeddings are computed locally (e.g. with
 * EmbeddingGemma on-device) and stored next to the text — no cloud vector
 * database, no account, no sync. The index is brute-force top-k, which is
 * fast enough for a personal memory store (hundreds to low thousands of
 * entries) and has zero native dependencies.
 */
interface Embedder {
    /** One vector per text. Implementations run fully on-device. */
    fun embed(text: String): FloatArray
}

/** Deterministic hash-based embedder for tests/demo only — NOT semantic. */
class HashEmbedder(private val dim: Int = 64) : Embedder {
    override fun embed(text: String): FloatArray {
        val vec = FloatArray(dim)
        val words = text.lowercase().split(Regex("\\W+")).filter { it.isNotEmpty() }
        for (word in words) {
            val idx = (word.hashCode() and 0x7fffffff) % dim
            vec[idx] += 1f
        }
        val norm = sqrt(vec.sumOf { (it * it).toDouble() }).toFloat().takeIf { it > 0 } ?: 1f
        return vec.map { it / norm }.toFloatArray()
    }
}

data class MemoryHit(val text: String, val score: Float)

class VectorIndex(private val embedder: Embedder) {
    private data class Entry(val text: String, val vector: FloatArray)
    private val entries = mutableListOf<Entry>()

    fun add(text: String) {
        entries += Entry(text, embedder.embed(text))
    }

    fun remove(text: String) {
        entries.removeAll { it.text == text }
    }

    /** Cosine similarity top-k. Returns hits sorted best-first. */
    fun search(query: String, k: Int = 5): List<MemoryHit> {
        if (entries.isEmpty()) return emptyList()
        val q = embedder.embed(query)
        return entries
            .map { entry ->
                var dot = 0f
                for (i in q.indices) dot += q[i] * entry.vector[i]
                MemoryHit(entry.text, dot)
            }
            .sortedByDescending { it.score }
            .take(k)
            .filter { it.score > 0.05f }
    }

    fun size(): Int = entries.size
}
