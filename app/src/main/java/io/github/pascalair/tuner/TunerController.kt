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
    val hasSignal: Boolean = false,        // une note est-elle affichée ?
    val targetIndex: Int = 0,              // corde visée actuellement
    val cents: Float = 0f,                 // écart lissé vers la cible (+ = trop aigu)
    val inTune: Boolean = false,
    val history: List<Float> = emptyList(), // écarts récents (NaN = silence), pour le tracé
)

/**
 * Cœur logique : reçoit les fréquences du micro, filtre les parasites, maintient
 * la note pendant son extinction, calcule l'écart en cents et émet un son quand
 * une corde devient juste.
 *
 * Principe : on "verrouille" une note tant que les mesures restent cohérentes.
 * Une mesure isolée qui bondit au loin (attaque, harmonique, bruit) est ignorée ;
 * il faut qu'elle se confirme sur plusieurs mesures pour changer de note.
 */
class TunerController {

    var state by mutableStateOf(TunerState())
        private set

    private val engine = AudioEngine(::onPitch)
    private val sound = SuccessSound()

    private var lockedFreq = -1f          // note actuellement suivie (-1 = aucune)
    private var candidateFreq = -1f       // note "en attente de confirmation"
    private var candidateFrames = 0
    private var lostFrames = 0            // mesures consécutives sans détection valable

    private val recentFreqs = ArrayDeque<Float>()
    private var smoothedCents = 0f
    private var smoothing = false
    private var lastIndex = -1

    private val history = ArrayDeque<Float>()

    private var holdFrames = 0            // mesures consécutives "juste" (pour le son)
    private var lastSoundedString = -1

    private companion object {
        const val MEDIAN_WINDOW = 5
        const val SMOOTH_ALPHA = 0.2f        // plus bas = plus lisse
        const val IN_TUNE_HOLD_MS = 500      // durée juste avant le son
        const val REARM_CENTS = 15f          // au-delà, on ré-autorise le son sur la même corde
        const val JUMP_LIMIT_CENTS = 90f     // au-delà, une mesure est jugée suspecte
        const val ACQUIRE_FRAMES = 2         // mesures cohérentes pour afficher une nouvelle note
        const val RELOCK_FRAMES = 3          // mesures cohérentes pour confirmer un changement de note
        const val GRACE_MS = 650             // on garde la note affichée pendant ce délai sans signal
        val HOLD_FRAMES = framesFor(IN_TUNE_HOLD_MS)
        val GRACE_FRAMES = framesFor(GRACE_MS)
        private fun framesFor(ms: Int) =
            ceil(ms / 1000.0 * AudioEngine.SAMPLE_RATE / AudioEngine.HOP).toInt()
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
        val newForced = if (state.forcedString == index) null else index
        resetTracking()
        state = state.copy(forcedString = newForced, hasSignal = false, inTune = false, history = emptyList())
    }

    private fun onPitch(rawFreq: Float) {
        if (rawFreq <= 0f) {
            onMiss()
            return
        }

        if (lockedFreq <= 0f) {
            // Aucune note suivie : on exige quelques mesures cohérentes avant d'afficher,
            // pour ne pas réagir à un bruit ou au tout début d'une attaque.
            if (isCandidateClose(rawFreq)) candidateFrames++ else startCandidate(rawFreq)
            if (candidateFrames >= ACQUIRE_FRAMES) {
                lockTo(rawFreq)
                accept(rawFreq)
            } else {
                idle()
            }
            return
        }

        val jump = centsApart(rawFreq, lockedFreq)
        if (jump <= JUMP_LIMIT_CENTS) {
            candidateFrames = 0
            accept(rawFreq)
        } else {
            // Mesure éloignée : parasite isolé, ou vrai changement de corde s'il se confirme.
            if (isCandidateClose(rawFreq)) candidateFrames++ else startCandidate(rawFreq)
            if (candidateFrames >= RELOCK_FRAMES) {
                lockTo(rawFreq)
                accept(rawFreq)
            } else {
                onMiss() // on tient la note précédente le temps de trancher
            }
        }
    }

    /** Mesure retenue : met à jour la note suivie, l'écart lissé, le son et le tracé. */
    private fun accept(rawFreq: Float) {
        lostFrames = 0

        recentFreqs.addLast(rawFreq)
        while (recentFreqs.size > MEDIAN_WINDOW) recentFreqs.removeFirst()
        val freq = median(recentFreqs)
        lockedFreq = freq

        val (index, rawCents) = resolveTarget(freq)
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

    /** Pas de mesure valable : on maintient la note un court instant, puis on la lâche. */
    private fun onMiss() {
        lostFrames++
        if (lockedFreq > 0f && lostFrames <= GRACE_FRAMES) {
            // On prolonge la note affichée (tracé continu) pendant l'extinction.
            pushHistory(if (smoothing) smoothedCents else Float.NaN)
            state = state.copy(history = history.toList())
        } else {
            releaseLock()
            idle()
        }
    }

    private fun idle() {
        pushHistory(Float.NaN)
        state = state.copy(hasSignal = false, inTune = false, history = history.toList())
    }

    private fun lockTo(freq: Float) {
        lockedFreq = freq
        recentFreqs.clear()
        smoothing = false
        lastIndex = -1
        candidateFreq = -1f
        candidateFrames = 0
    }

    private fun releaseLock() {
        lockedFreq = -1f
        recentFreqs.clear()
        smoothing = false
        holdFrames = 0
        candidateFreq = -1f
        candidateFrames = 0
    }

    private fun startCandidate(freq: Float) {
        candidateFreq = freq
        candidateFrames = 1
    }

    private fun isCandidateClose(freq: Float): Boolean =
        candidateFreq > 0f && centsApart(freq, candidateFreq) <= JUMP_LIMIT_CENTS

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

    private fun centsApart(a: Float, b: Float): Float =
        abs(centsBetween(a.toDouble(), b.toDouble())).toFloat()

    private fun median(values: Collection<Float>): Float {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun pushHistory(value: Float) {
        history.addLast(value)
        while (history.size > HISTORY_SIZE) history.removeFirst()
    }

    private fun resetTracking() {
        releaseLock()
        history.clear()
        lostFrames = 0
        smoothedCents = 0f
        lastIndex = -1
        lastSoundedString = -1
    }
}
