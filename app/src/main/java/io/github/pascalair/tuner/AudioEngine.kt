package io.github.pascalair.tuner

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import kotlin.math.sqrt

/**
 * Capture le micro en continu et estime la hauteur jouée à chaque "saut" (hop).
 * Le résultat est renvoyé sur le thread principal via [onPitch] : la fréquence
 * en Hz, ou -1 quand rien d'exploitable n'est entendu (silence, bruit).
 */
class AudioEngine(private val onPitch: (Float) -> Unit) {

    companion object {
        const val SAMPLE_RATE = 44100
        const val WINDOW_SIZE = 4096          // fenêtre d'analyse
        const val HOP = 2048                  // nouveaux échantillons entre deux analyses
        private const val SILENCE_RMS = 0.006 // sous ce niveau, on considère qu'il n'y a pas de son
    }

    private val detector = PitchDetector(SAMPLE_RATE, WINDOW_SIZE)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread { recordLoop() }.apply { start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    private fun createRecord(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferBytes = maxOf(minBuffer, WINDOW_SIZE * 2)

        // UNPROCESSED évite les traitements (AGC, réduction de bruit) qui faussent la hauteur ;
        // on retombe sur le micro standard si l'appareil ne le gère pas.
        for (source in intArrayOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.MIC)) {
            val record = AudioRecord(
                source, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes
            )
            if (record.state == AudioRecord.STATE_INITIALIZED) return record
            record.release()
        }
        return null
    }

    private fun recordLoop() {
        val record = createRecord() ?: return
        val window = FloatArray(WINDOW_SIZE)
        val incoming = ShortArray(HOP)

        record.startRecording()
        try {
            while (running) {
                var read = 0
                while (read < HOP && running) {
                    val n = record.read(incoming, read, HOP - read)
                    if (n <= 0) break
                    read += n
                }
                if (read < HOP) continue

                // Décale la fenêtre puis ajoute les nouveaux échantillons (recouvrement 50 %).
                System.arraycopy(window, HOP, window, 0, WINDOW_SIZE - HOP)
                var sumSq = 0.0
                for (i in 0 until HOP) {
                    val s = incoming[i] / 32768f
                    window[WINDOW_SIZE - HOP + i] = s
                    sumSq += s * s
                }

                val rms = sqrt(sumSq / HOP)
                val freq = if (rms < SILENCE_RMS) -1f else detector.detect(window)
                mainHandler.post { onPitch(freq) }
            }
        } finally {
            record.stop()
            record.release()
        }
    }
}
