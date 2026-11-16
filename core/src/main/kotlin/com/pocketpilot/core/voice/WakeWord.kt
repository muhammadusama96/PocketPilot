package com.pocketpilot.core.voice

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-device wake word: MFCC features + DTW template matching.
 * No cloud, no always-on streaming to a server. The user enrols a phrase
 * by saying it 3 times; templates are stored locally as JSON.
 * Compact, dependency-free implementation (16 kHz mono PCM).
 */
object Mfcc {
    private const val SAMPLE_RATE = 16000
    private const val FRAME_MS = 25
    private const val STEP_MS = 10
    private const val NUM_FILTERS = 26
    private const val NUM_COEFFS = 13
    private const val FFT_SIZE = 512

    private val frameLen = SAMPLE_RATE * FRAME_MS / 1000
    private val frameStep = SAMPLE_RATE * STEP_MS / 1000

    /** PCM16 mono -> sequence of 13-dim MFCC vectors, one per 10 ms step. */
    fun extract(pcm: ShortArray): List<FloatArray> {
        // Pre-emphasis.
        val emphasised = FloatArray(pcm.size)
        var prev = 0f
        for (i in pcm.indices) {
            val x = pcm[i] / 32768f
            emphasised[i] = x - 0.97f * prev
            prev = x
        }
        // Mel filterbank (built once, lazily).
        val filters = melFilterbank()
        val out = mutableListOf<FloatArray>()
        var start = 0
        while (start + frameLen <= emphasised.size) {
            val frame = FloatArray(FFT_SIZE)
            for (i in 0 until frameLen) {
                // Hamming window.
                val w = (0.54 - 0.46 * cos(2.0 * PI * i / (frameLen - 1))).toFloat()
                frame[i] = emphasised[start + i] * w
            }
            val spectrum = magnitudeSpectrum(frame)
            val logEnergies = FloatArray(NUM_FILTERS)
            for (f in 0 until NUM_FILTERS) {
                var sum = 0f
                for (k in 0 until FFT_SIZE / 2) sum += spectrum[k] * filters[f][k]
                logEnergies[f] = ln((sum + 1e-6f).toDouble()).toFloat()
            }
            // DCT-II, keep 13 coeffs (drop c0 energy term's twin by keeping 1..13).
            val coeffs = FloatArray(NUM_COEFFS)
            for (n in 0 until NUM_COEFFS) {
                var sum = 0f
                for (m in 0 until NUM_FILTERS) {
                    sum += logEnergies[m] * cos(PI * (n + 1) * (m + 0.5) / NUM_FILTERS).toFloat()
                }
                coeffs[n] = sum
            }
            out += coeffs
            start += frameStep
        }
        return out
    }

    private var cachedFilters: Array<FloatArray>? = null

    private fun melFilterbank(): Array<FloatArray> {
        cachedFilters?.let { return it }
        val lowMel = 0.0
        val highMel = 2595.0 * kotlin.math.log10(1.0 + (SAMPLE_RATE / 2.0) / 700.0)
        val melPoints = DoubleArray(NUM_FILTERS + 2) { lowMel + (highMel - lowMel) * it / (NUM_FILTERS + 1) }
        val hzPoints = melPoints.map { 700.0 * (Math.pow(10.0, it / 2595.0) - 1.0) }
        val binPoints = hzPoints.map { ((FFT_SIZE + 1) * it / SAMPLE_RATE).toInt() }
        val filters = Array(NUM_FILTERS) { FloatArray(FFT_SIZE / 2) }
        for (m in 1..NUM_FILTERS) {
            for (k in 0 until FFT_SIZE / 2) {
                filters[m - 1][k] = when {
                    k < binPoints[m - 1] || k > binPoints[m + 1] -> 0f
                    k < binPoints[m] -> (k - binPoints[m - 1]).toFloat() / (binPoints[m] - binPoints[m - 1])
                    else -> (binPoints[m + 1] - k).toFloat() / (binPoints[m + 1] - binPoints[m])
                }
            }
        }
        cachedFilters = filters
        return filters
    }

    /** Iterative radix-2 FFT, returns magnitude spectrum (first half). */
    private fun magnitudeSpectrum(frame: FloatArray): FloatArray {
        val n = FFT_SIZE
        val re = frame.copyOf()
        val im = FloatArray(n)
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val t = re[i]; re[i] = re[j]; re[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wr = cos(angle).toFloat()
            val wi = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * wRe - im[i + k + len / 2] * wIm
                    val vIm = re[i + k + len / 2] * wIm + im[i + k + len / 2] * wRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nextWRe = wRe * wr - wIm * wi
                    wIm = wRe * wi + wIm * wr
                    wRe = nextWRe
                }
                i += len
            }
            len = len shl 1
        }
        return FloatArray(n / 2) { k -> sqrt(re[k] * re[k] + im[k] * im[k]) }
    }
}

/** Dynamic Time Warping distance between two MFCC sequences. Lower = closer. */
object Dtw {
    fun distance(a: List<FloatArray>, b: List<FloatArray>): Double {
        if (a.isEmpty() || b.isEmpty()) return Double.MAX_VALUE
        val n = a.size
        val m = b.size
        var prev = DoubleArray(m + 1) { Double.MAX_VALUE }
        var curr = DoubleArray(m + 1) { Double.MAX_VALUE }
        prev[0] = 0.0
        for (i in 1..n) {
            curr[0] = Double.MAX_VALUE
            for (j in 1..m) {
                var frameDist = 0.0
                for (k in a[i - 1].indices) {
                    val d = (a[i - 1][k] - b[j - 1][k]).toDouble()
                    frameDist += d * d
                }
                curr[j] = frameDist + minOf(prev[j], curr[j - 1], prev[j - 1])
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[m] / (n + m)
    }
}

/** Wake-word detector: enrol a phrase (3 samples), then match live audio. */
class WakeWordDetector(
    /** Normalised DTW distance below this = wake. Tune per phrase. */
    private val threshold: Double = 45.0,
) {
    private val templates = mutableListOf<List<FloatArray>>()

    fun enrol(samplePcm: ShortArray) {
        templates += Mfcc.extract(samplePcm)
    }

    val enrolled: Boolean get() = templates.isNotEmpty()

    /** Feed a ~1 s window of PCM; true when it matches an enrolled phrase. */
    fun matches(windowPcm: ShortArray): Boolean {
        if (templates.isEmpty()) return false
        val features = Mfcc.extract(windowPcm)
        if (features.isEmpty()) return false
        val best = templates.minOf { Dtw.distance(features, it) }
        return best < threshold
    }

    fun clear() = templates.clear()
}
