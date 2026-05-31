package io.github.pascalair.tuner

/**
 * Détection de la fréquence fondamentale par l'algorithme YIN
 * (de Cheveigné & Kawahara, 2002). Fiable du grave (~30 Hz, corde de basse)
 * à l'aigu, ce qui couvre guitare, basse et ukulélé.
 *
 * @param sampleRate fréquence d'échantillonnage de l'audio (Hz)
 * @param windowSize taille de la fenêtre d'analyse (échantillons), puissance de 2
 * @param threshold  seuil YIN ; plus bas = plus strict (0.10–0.20 typiquement)
 */
class PitchDetector(
    private val sampleRate: Int,
    private val windowSize: Int,
    private val threshold: Float = 0.15f,
) {
    private val halfSize = windowSize / 2
    private val yin = FloatArray(halfSize)

    // Bornes de recherche : on ne cherche que des hauteurs musicalement plausibles.
    private val minFreq = 28.0   // un peu sous le E1 de basse (41 Hz) par sécurité
    private val maxFreq = 1320.0 // au-dessus du plus aigu utile
    private val tauMin = (sampleRate / maxFreq).toInt().coerceAtLeast(2)
    private val tauMax = (sampleRate / minFreq).toInt().coerceAtMost(halfSize - 1)

    /** Renvoie la fréquence détectée en Hz, ou -1 si aucune hauteur claire. */
    fun detect(audio: FloatArray): Float {
        difference(audio)
        cumulativeMeanNormalized()
        val tau = absoluteThreshold()
        if (tau == -1) return -1f
        val betterTau = parabolicInterpolation(tau)
        val freq = sampleRate / betterTau
        return if (freq in minFreq..maxFreq) freq.toFloat() else -1f
    }

    /** Fonction de différence : d(tau) = Σ (x[j] - x[j+tau])². */
    private fun difference(audio: FloatArray) {
        for (tau in 0 until halfSize) {
            var sum = 0f
            for (j in 0 until halfSize) {
                val delta = audio[j] - audio[j + tau]
                sum += delta * delta
            }
            yin[tau] = sum
        }
    }

    /** Différence moyenne cumulée normalisée. */
    private fun cumulativeMeanNormalized() {
        yin[0] = 1f
        var runningSum = 0f
        for (tau in 1 until halfSize) {
            runningSum += yin[tau]
            yin[tau] = if (runningSum == 0f) 1f else yin[tau] * tau / runningSum
        }
    }

    /** Premier minimum sous le seuil, dans la plage de fréquences ciblée. */
    private fun absoluteThreshold(): Int {
        var tau = tauMin
        while (tau <= tauMax) {
            if (yin[tau] < threshold) {
                // Descendre jusqu'au creux local pour la meilleure estimation.
                while (tau + 1 <= tauMax && yin[tau + 1] < yin[tau]) tau++
                return tau
            }
            tau++
        }
        return -1
    }

    /** Interpolation parabolique autour de tau pour une précision sous-échantillon. */
    private fun parabolicInterpolation(tau: Int): Double {
        val x0 = if (tau > 0) tau - 1 else tau
        val x2 = if (tau + 1 < halfSize) tau + 1 else tau
        if (x0 == tau) return if (yin[tau] <= yin[x2]) tau.toDouble() else x2.toDouble()
        if (x2 == tau) return if (yin[tau] <= yin[x0]) tau.toDouble() else x0.toDouble()

        val s0 = yin[x0]
        val s1 = yin[tau]
        val s2 = yin[x2]
        val denom = 2 * (2 * s1 - s2 - s0)
        if (denom == 0f) return tau.toDouble()
        return tau.toDouble() + (s2 - s0) / denom
    }
}
