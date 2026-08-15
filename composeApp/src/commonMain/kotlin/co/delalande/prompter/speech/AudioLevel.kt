package co.delalande.prompter.speech

import kotlin.math.ln

/**
 * Maps an RMS signal amplitude to a 0..1 level, normalized against the peak
 * amplitude of the sample format ([maxAmplitude], e.g. 32767 for 16-bit PCM or
 * 1.0 for float samples).
 */
fun levelFromRms(rms: Double, maxAmplitude: Double): Float {
    if (rms <= 0.0) return 0f
    val db = 20.0 * ln(rms / maxAmplitude) / ln(10.0)
    return ((db + 45.0) / 45.0).coerceIn(0.0, 1.0).toFloat()
}
