package buddy.app

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Desktop counterpart of the Android synthesizer: same notes, built once into clips. */
internal object DesktopSound {
    private const val SR = 44_100
    private val format = AudioFormat(SR.toFloat(), 16, 1, true, false)
    private val clips = mutableMapOf<Sound, Clip>()
    private var ambient: Clip? = null

    fun play(sound: Sound) {
        runCatching {
            val clip = clips.getOrPut(sound) { clipFor(sound) }
            clip.framePosition = 0
            clip.start()
        }
    }

    fun ambient(on: Boolean) {
        runCatching {
            if (!on) {
                ambient?.stop()
                return
            }
            if (ambient?.isRunning == true) return
            val c = ambient ?: clip(pad(), loop = true).also { ambient = it }
            c.framePosition = 0
            c.start()
        }
    }

    private fun clipFor(sound: Sound): Clip {
        val (notes, seconds) = when (sound) {
            Sound.START -> listOf(Triple(523.25f, 0f, 0.14f), Triple(659.25f, 0.10f, 0.40f)) to 0.6f
            Sound.COMPLETE -> listOf(
                Triple(523.25f, 0f, 0.14f), Triple(659.25f, 0.11f, 0.14f), Triple(783.99f, 0.22f, 0.55f),
            ) to 0.9f
            Sound.BREAK_END -> listOf(Triple(659.25f, 0f, 0.16f), Triple(523.25f, 0.14f, 0.45f)) to 0.7f
            Sound.TAP -> listOf(Triple(880f, 0f, 0.05f)) to 0.15f
        }
        return clip(synthesize(notes, seconds), loop = false)
    }

    private fun clip(pcm: ShortArray, loop: Boolean): Clip {
        val bytes = ByteArray(pcm.size * 2)
        for (i in pcm.indices) {
            bytes[i * 2] = (pcm[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (pcm[i].toInt() shr 8).toByte()
        }
        return AudioSystem.getClip().apply {
            open(format, bytes, 0, bytes.size)
            if (loop) loop(Clip.LOOP_CONTINUOUSLY)
        }
    }

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
