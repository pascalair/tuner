package io.github.pascalair.tuner

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/** Tolérance pour considérer une corde "juste" (en cents, de part et d'autre). */
const val IN_TUNE_CENTS = 5f

/** État affiché par l'interface. */
data class TunerState(
    val instrument: Instrument = INSTRUMENTS.first(),
    val forcedString: Int? = null,   // null = mode auto ; sinon index de corde imposé
    val hasSignal: Boolean = false,  // une hauteur est-elle entendue ?
    val targetIndex: Int = 0,        // corde visée actuellement
    val cents: Float = 0f,           // écart signé vers la cible (+ = trop aigu)
    val inTune: Boolean = false,
)

/**
 * Cœur logique : reçoit les fréquences du micro, choisit la corde visée,
 * calcule l'écart en cents et émet un bip quand une corde devient juste.
 */
class TunerController {

    var state by mutableStateOf(TunerState())
        private set

    private val engine = AudioEngine(::onPitch)
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)

    // Bip "juste" : déclenché une seule fois par corde, ré-armé quand on s'en éloigne.
    private var stableFrames = 0
    private var lastBeepedString = -1

    private companion object {
        const val STABLE_FRAMES = 2   // analyses consécutives justes avant le bip (~100 ms)
        const val REARM_CENTS = 15f   // au-delà, on ré-autorise un bip sur la même corde
    }

    fun start() = engine.start()

    fun stop() = engine.stop()

    fun release() = tone.release()

    fun selectInstrument(instrument: Instrument) {
        resetBeep()
        state = state.copy(instrument = instrument, forcedString = null, hasSignal = false)
    }

    /** Tape sur une corde : impose la cible (utile si l'instrument est très faux). */
    fun toggleString(index: Int) {
        resetBeep()
        state = state.copy(forcedString = if (state.forcedString == index) null else index)
    }

    private fun onPitch(freq: Float) {
        if (freq <= 0f) {
            stableFrames = 0
            state = state.copy(hasSignal = false, inTune = false)
            return
        }

        val (index, cents) = resolveTarget(freq)
        val inTune = abs(cents) <= IN_TUNE_CENTS

        if (inTune) {
            stableFrames++
            if (stableFrames >= STABLE_FRAMES && lastBeepedString != index) {
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                lastBeepedString = index
            }
        } else {
            stableFrames = 0
            if (index != lastBeepedString || abs(cents) > REARM_CENTS) lastBeepedString = -1
        }

        state = state.copy(hasSignal = true, targetIndex = index, cents = cents, inTune = inTune)
    }

    /** Détermine la corde visée et l'écart en cents associé. */
    private fun resolveTarget(freq: Float): Pair<Int, Float> {
        val strings = state.instrument.strings
        state.forcedString?.let { forced ->
            return forced to centsBetween(freq.toDouble(), strings[forced].frequency).toFloat()
        }
        // Mode auto : corde dont l'écart en cents est le plus petit.
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

    private fun resetBeep() {
        stableFrames = 0
        lastBeepedString = -1
    }
}
