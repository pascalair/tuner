package io.github.pascalair.tuner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.ceil

/** Tolérance pour considérer une corde "juste" (en cents, de part et d'autre). */
const val IN_TUNE_CENTS = 5f

/** Durée d'historique du tracé (~3 s), exprimée en nombre de mesures. */
val HISTORY_SIZE: Int = (3.0 * AudioEngine.SAMPLE_RATE / AudioEngine.HOP).toInt()

/** État affiché par l'interface. */
data class TunerState(
    val instrument: Instrument = INSTRUMENTS.first(),
    val forcedString: Int? = null,        // null = mode auto ; sinon index de corde imposé
    val hasSignal: Boolean = false,        // une hauteur est-elle entendue ?
    val targetIndex: Int = 0,              // corde visée actuellement
    val cents: Float = 0f,                 // écart lissé vers la cible (+ = trop aigu)
    val inTune: Boolean = false,
    val history: List<Float> = emptyList(), // écarts récents (NaN = silence), pour le tracé
)

/**
 * Cœur logique : reçoit les fréquences du micro, les stabilise, choisit la corde
 * visée, calcule l'écart en cents et émet un son quand une corde devient juste.
 */
class TunerController {

    var state by mutableStateOf(TunerState())
        private set

    private val engine = AudioEngine(::onPitch)
    private val sound = SuccessSound()

    // Stabilisation : médiane anti-aberrations puis lissage progressif de l'affichage.
    private val recentFreqs = ArrayDeque<Float>()
    private var smoothedCents = 0f
    private var smoothing = false
    private var lastIndex = -1

    private val history = ArrayDeque<Float>()

    // Son "juste" : déclenché après un maintien stable, ré-armé quand on s'en éloigne.
    private var holdFrames = 0
    private var lastSoundedString = -1

    private companion object {
        const val MEDIAN_WINDOW = 5
        const val SMOOTH_ALPHA = 0.2f        // plus bas = plus lisse
        const val IN_TUNE_HOLD_MS = 500      // durée juste avant le son
        const val REARM_CENTS = 15f          // au-delà, on ré-autorise le son sur la même corde
        val HOLD_FRAMES =
            ceil(IN_TUNE_HOLD_MS / 1000.0 * AudioEngine.SAMPLE_RATE / AudioEngine.HOP).toInt()
    }

    fun start() = engine.start()

    fun stop() = engine.stop()

    fun release() = sound.release()

    fun selectInstrument(instrument: Instrument) {
        resetTracking()
        state = TunerState(instrument = instrument)
    }

    /** Tape sur une corde : impose la cible (utile si l'instrument est très faux). */
    fun toggleString(index: Int) {
        resetTracking()
        state = state.copy(
            forcedString = if (state.forcedString == index) null else index,
            hasSignal = false, inTune = false, history = emptyList(),
        )
    }

    private fun onPitch(rawFreq: Float) {
        if (rawFreq <= 0f) {
            recentFreqs.clear()
            smoothing = false
            holdFrames = 0
            pushHistory(Float.NaN)
            state = state.copy(hasSignal = false, inTune = false, history = history.toList())
            return
        }

        recentFreqs.addLast(rawFreq)
        while (recentFreqs.size > MEDIAN_WINDOW) recentFreqs.removeFirst()
        val freq = median(recentFreqs)

        val (index, rawCents) = resolveTarget(freq)
        // On "saute" directement à la valeur quand on (re)commence ou que la corde change,
        // sinon on lisse pour éviter le tremblement.
        if (!smoothing || index != lastIndex) {
            smoothedCents = rawCents
            smoothing = true
        } else {
            smoothedCents += SMOOTH_ALPHA * (rawCents - smoothedCents)
        }
        lastIndex = index

        val inTune = abs(smoothedCents) <= IN_TUNE_CENTS
        if (inTune) {
            holdFrames++
            if (holdFrames >= HOLD_FRAMES && lastSoundedString != index) {
                sound.play()
                lastSoundedString = index
            }
        } else {
            holdFrames = 0
            if (index != lastSoundedString || abs(smoothedCents) > REARM_CENTS) lastSoundedString = -1
        }

        pushHistory(smoothedCents)
        state = state.copy(
            hasSignal = true, targetIndex = index, cents = smoothedCents,
            inTune = inTune, history = history.toList(),
        )
    }

    /** Détermine la corde visée et l'écart en cents associé. */
    private fun resolveTarget(freq: Float): Pair<Int, Float> {
        val strings = state.instrument.strings
        state.forcedString?.let { forced ->
            return forced to centsBetween(freq.toDouble(), strings[forced].frequency).toFloat()
        }
        var bestIndex = 0
        var bestCents = Float.MAX_VALUE
        for (i in strings.indices) {
            val c = centsBetween(freq.toDouble(), strings[i].frequency).toFloat()
            if (abs(c) < abs(bestCents)) {
                bestCents = c
                bestIndex = i
            }
        }
        return bestIndex to bestCents
    }

    private fun median(values: Collection<Float>): Float {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun pushHistory(value: Float) {
        history.addLast(value)
        while (history.size > HISTORY_SIZE) history.removeFirst()
    }

    private fun resetTracking() {
        recentFreqs.clear()
        history.clear()
        smoothing = false
        smoothedCents = 0f
        holdFrames = 0
        lastIndex = -1
        lastSoundedString = -1
    }
}
