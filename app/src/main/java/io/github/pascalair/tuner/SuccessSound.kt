package io.github.pascalair.tuner

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Petit son de validation "ding-ding" ascendant (deux notes, la seconde plus aiguë),
 * joué quand une corde devient juste. Le signal PCM est généré une fois puis rejoué.
 */
class SuccessSound {

    private val sampleRate = 44100
    private val samples = buildSound()

    private val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(samples.size * 2)
        .build()
        .also { it.write(samples, 0, samples.size) }

    fun play() {
        track.stop()
        track.reloadStaticData()
        track.play()
    }

    fun release() = track.release()

    /** Deux notes courtes ascendantes avec un léger fondu pour éviter les clics. */
    private fun buildSound(): ShortArray {
        val notes = doubleArrayOf(880.0, 1320.0) // La5 puis Mi6
        val perNote = sampleRate / 10            // 100 ms par note
        val fade = sampleRate / 200              // 5 ms de fondu entrée/sortie
        val out = ShortArray(notes.size * perNote)
        var idx = 0
        for (freq in notes) {
            for (i in 0 until perNote) {
                val envelope = when {
                    i < fade -> i.toDouble() / fade
                    i > perNote - fade -> (perNote - i).toDouble() / fade
                    else -> 1.0
                }
                val v = sin(2.0 * PI * freq * i / sampleRate) * envelope * 0.35
                out[idx++] = (v * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }
}
