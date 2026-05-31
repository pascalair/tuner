package io.github.pascalair.tuner

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Fréquence de référence du La (A4), base de tout le calcul des notes. */
const val A4_FREQ = 440.0

/** Une corde cible : son nom affiché (ex. "E2") et sa fréquence en Hz. */
data class TuningString(val label: String, val frequency: Double)

/** Un instrument avec la liste de ses cordes, de la plus grave à la plus aiguë. */
data class Instrument(val name: String, val strings: List<TuningString>)

/** Construit une corde à partir de son numéro MIDI (ex. 40 = E2). */
private fun fromMidi(midi: Int): TuningString =
    TuningString(midiToName(midi), midiToFrequency(midi))

fun midiToFrequency(midi: Int): Double = A4_FREQ * 2.0.pow((midi - 69) / 12.0)

private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/** Nom d'une note MIDI avec son octave, ex. 40 -> "E2". */
fun midiToName(midi: Int): String {
    val name = NOTE_NAMES[midi % 12]
    val octave = midi / 12 - 1
    return "$name$octave"
}

/** Numéro MIDI (réel, non arrondi) correspondant à une fréquence. */
fun frequencyToMidi(freq: Double): Double = 69.0 + 12.0 * ln(freq / A4_FREQ) / ln(2.0)

/**
 * Écart en cents entre une fréquence mesurée et une fréquence cible.
 * Positif = trop aigu, négatif = trop grave.
 */
fun centsBetween(measured: Double, target: Double): Double =
    1200.0 * ln(measured / target) / ln(2.0)

/** Note la plus proche d'une fréquence, sous forme de nom (ex. "E2"). */
fun nearestNoteName(freq: Double): String = midiToName(frequencyToMidi(freq).roundToInt())

// Accordages standard. Les numéros MIDI : C-1 = 0, A4 = 69.
val INSTRUMENTS: List<Instrument> = listOf(
    Instrument(
        name = "Guitare",
        strings = listOf(40, 45, 50, 55, 59, 64).map { fromMidi(it) } // E2 A2 D3 G3 B3 E4
    ),
    Instrument(
        name = "Basse",
        strings = listOf(28, 33, 38, 43).map { fromMidi(it) }         // E1 A1 D2 G2
    ),
    Instrument(
        name = "Ukulélé",
        strings = listOf(67, 60, 64, 69).map { fromMidi(it) }         // G4 C4 E4 A4 (sol aigu)
    ),
)
