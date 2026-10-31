package buddy.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Synthesized chimes and a soft ambient pad, so the app ships no audio assets. */
internal object SoundFx {
    private const val SR = 44_100
    private var ambientTrack: AudioTrack? = null

    fun play(sound: Sound) {
        val (notes, tail) = when (sound) {
            Sound.START -> listOf(Triple(523.25f, 0f, 0.14f), Triple(659.25f, 0.10f, 0.40f)) to 600L
            Sound.COMPLETE -> listOf(
                Triple(523.25f, 0f, 0.14f), Triple(659.25f, 0.11f, 0.14f), Triple(783.99f, 0.22f, 0.55f),
            ) to 900L
            Sound.BREAK_END -> listOf(Triple(659.25f, 0f, 0.16f), Triple(523.25f, 0.14f, 0.45f)) to 700L
            Sound.TAP -> listOf(Triple(880f, 0f, 0.05f)) to 150L
        }
        val pcm = synthesize(notes, seconds = (tail + 100) / 1000f)
        val track = static(pcm, loop = false) ?: return
        track.play()
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { track.stop() }; track.release() }, tail)
    }

    fun ambient(on: Boolean) {
        if (!on) {
            ambientTrack?.let { runCatching { it.stop() }; it.release() }
            ambientTrack = null
            return
        }
        if (ambientTrack != null) return
        val track = static(pad(), loop = true) ?: return
        track.setVolume(0.5f)
        track.play()
        ambientTrack = track
    }

    private fun static(pcm: ShortArray, loop: Boolean): AudioTrack? = runCatching {
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(if (loop) AudioAttributes.CONTENT_TYPE_MUSIC else AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
            .also {
                it.write(pcm, 0, pcm.size)
                if (loop) it.setLoopPoints(0, pcm.size, -1)
            }
    }.getOrNull()

    /** notes: list of (freq, startSec, lengthSec); sines with a fast attack and exponential decay. */
    private fun synthesize(notes: List<Triple<Float, Float, Float>>, seconds: Float): ShortArray {
        val n = (seconds * SR).toInt()
        val buf = FloatArray(n)
        for ((freq, start, len) in notes) {
            val s0 = (start * SR).toInt()
            val s1 = ((start + len) * SR).toInt().coerceAtMost(n)
            for (i in s0 until s1) {
                val t = (i - s0) / SR.toFloat()
                val env = (1f - exp(-t * 220f)) * exp(-t * 9f)
                buf[i] += sin(2 * PI * freq * t).toFloat() * env * 0.28f
            }
        }
        return ShortArray(n) { (buf[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
    }

    /** Eight-second pad: a slow A-minor chord with breathing amplitude. */
    private fun pad(): ShortArray {
        val seconds = 8f
        val n = (seconds * SR).toInt()
        val freqs = floatArrayOf(110f, 164.81f, 220f, 261.63f, 329.63f)
        val amps = floatArrayOf(0.30f, 0.20f, 0.16f, 0.12f, 0.08f)
        return ShortArray(n) { i ->
            val t = i / SR.toFloat()
            var v = 0f
            for (k in freqs.indices) {
                val drift = 1f + 0.002f * sin(2 * PI * 0.07f * t * (k + 1))
                v += sin(2 * PI * freqs[k] * drift * t).toFloat() * amps[k]
            }
            val breathe = 0.75f + 0.25f * sin(2 * PI * t / seconds).toFloat()
            val fade = minOf(1f, t / 0.8f, (seconds - t) / 0.8f).coerceAtLeast(0f)
            (v * breathe * fade * 0.12f * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
