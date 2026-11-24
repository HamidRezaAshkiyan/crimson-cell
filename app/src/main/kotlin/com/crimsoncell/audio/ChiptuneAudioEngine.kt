package com.crimsoncell.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Real-time procedural 16-bit PCM chiptune synthesizer for retro combat SFX
 * and dynamic dark-chiptune biome background music. Zero external audio assets.
 */
class ChiptuneAudioEngine {

    private data class ActiveSfx(
        val samples: ShortArray,
        var cursor: Int = 0
    )

    private val sampleRate = 22050
    private val sfxLibrary = HashMap<String, ShortArray>()
    private val activeSfx = ConcurrentLinkedQueue<ActiveSfx>()

    @Volatile private var running = false
    @Volatile private var muted = false
    @Volatile private var biomeIndex = 1
    private var audioThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    init {
        buildSfxLibrary()
    }

    fun start() {
        if (running) return
        running = true
        audioThread = Thread(::audioLoop, "CrimsonCellAudio").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        try {
            audioThread?.join(300)
        } catch (_: InterruptedException) {
        }
        audioTrack?.let {
            try {
                it.stop()
                it.release()
            } catch (_: Exception) {
            }
        }
        audioTrack = null
        audioThread = null
    }

    fun setBiome(index: Int, isMuted: Boolean) {
        biomeIndex = index.coerceIn(1, 3)
        muted = isMuted
    }

    fun playSfx(name: String) {
        if (muted || !running) return
        val data = sfxLibrary[name] ?: return
        if (activeSfx.size < 10) {
            activeSfx.add(ActiveSfx(data))
        }
    }

    private fun audioLoop() {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
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
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Exception) {
            return
        }

        audioTrack = track
        track.play()

        val chunkSize = 512
        val buffer = ShortArray(chunkSize)
        var sampleCounter = 0L

        // Dark minor pentatonic / Phrygian arpeggio patterns per biome
        val patterns = arrayOf(
            floatArrayOf(146.83f, 174.61f, 220.00f, 174.61f, 164.81f, 196.00f, 220.00f, 261.63f), // Depths (D minor)
            floatArrayOf(130.81f, 138.59f, 196.00f, 155.56f, 130.81f, 174.61f, 196.00f, 233.08f), // Sewers (C Phrygian)
            floatArrayOf(164.81f, 196.00f, 246.94f, 293.66f, 329.63f, 246.94f, 220.00f, 196.00f)  // Spire (E minor)
        )

        while (running) {
            if (muted) {
                buffer.fill(0)
                track.write(buffer, 0, chunkSize)
                continue
            }

            val pattern = patterns[(biomeIndex - 1).coerceIn(0, 2)]
            val stepSamples = (sampleRate * 0.19f).toInt()

            for (i in 0 until chunkSize) {
                var mix = 0f

                // Subtle retro bassline + pulse arpeggio
                val step = ((sampleCounter / stepSamples) % pattern.size).toInt()
                val noteFreq = pattern[step]
                val t = sampleCounter.toFloat() / sampleRate.toFloat()
                val lead = if (sin(2.0 * PI * noteFreq * t) > 0.2) 1f else -1f
                val bass = sin(2.0 * PI * (noteFreq * 0.5f) * t).toFloat()
                val env = 1f - ((sampleCounter % stepSamples).toFloat() / stepSamples.toFloat()) * 0.65f
                mix += (lead * 0.055f + bass * 0.065f) * env

                // Mix active SFX
                val iter = activeSfx.iterator()
                while (iter.hasNext()) {
                    val s = iter.next()
                    if (s.cursor < s.samples.size) {
                        mix += (s.samples[s.cursor] / 32767f) * 0.55f
                        s.cursor++
                    } else {
                        iter.remove()
                    }
                }

                buffer[i] = (mix.coerceIn(-0.95f, 0.95f) * 32767f).toInt().toShort()
                sampleCounter++
            }
            track.write(buffer, 0, chunkSize)
        }
    }

    private fun buildSfxLibrary() {
        sfxLibrary["slash"] = synthSweep(0.09f, 520f, 180f, noiseMix = 0.45f)
        sfxLibrary["heavy_slash"] = synthSweep(0.16f, 310f, 95f, noiseMix = 0.55f)
        sfxLibrary["hit"] = synthSweep(0.08f, 240f, 90f, noiseMix = 0.6f)
        sfxLibrary["crit"] = synthArp(floatArrayOf(587f, 880f, 1175f), 0.045f)
        sfxLibrary["parry"] = synthArp(floatArrayOf(988f, 1318f, 1760f), 0.055f)
        sfxLibrary["clang"] = synthSweep(0.11f, 920f, 440f, noiseMix = 0.35f)
        sfxLibrary["bow"] = synthSweep(0.08f, 680f, 320f, noiseMix = 0.25f)
        sfxLibrary["throw"] = synthSweep(0.10f, 420f, 260f, noiseMix = 0.2f)
        sfxLibrary["explosion"] = synthSweep(0.28f, 160f, 42f, noiseMix = 0.85f)
        sfxLibrary["slam"] = synthSweep(0.22f, 190f, 55f, noiseMix = 0.75f)
        sfxLibrary["freeze"] = synthArp(floatArrayOf(1046f, 1318f, 1568f), 0.04f)
        sfxLibrary["fire"] = synthSweep(0.20f, 340f, 140f, noiseMix = 0.7f)
        sfxLibrary["phaser"] = synthSweep(0.12f, 280f, 980f, noiseMix = 0.15f)
        sfxLibrary["deploy"] = synthArp(floatArrayOf(330f, 440f), 0.05f)
        sfxLibrary["heal"] = synthArp(floatArrayOf(440f, 554f, 659f, 880f), 0.055f)
        sfxLibrary["coin"] = synthArp(floatArrayOf(987f, 1318f), 0.045f)
        sfxLibrary["scroll"] = synthArp(floatArrayOf(523f, 659f, 784f, 1046f), 0.06f)
        sfxLibrary["hurt"] = synthSweep(0.16f, 220f, 75f, noiseMix = 0.5f)
        sfxLibrary["kill"] = synthSweep(0.14f, 360f, 110f, noiseMix = 0.5f)
        sfxLibrary["boss_death"] = synthSweep(0.45f, 260f, 38f, noiseMix = 0.8f)
        sfxLibrary["death"] = synthArp(floatArrayOf(330f, 293f, 246f, 164f), 0.11f)
        sfxLibrary["shield_raise"] = synthSweep(0.06f, 480f, 620f, noiseMix = 0.1f)
        sfxLibrary["menu"] = synthSweep(0.045f, 660f, 760f, noiseMix = 0f)
    }

    private fun synthSweep(durationSec: Float, startHz: Float, endHz: Float, noiseMix: Float): ShortArray {
        val count = (sampleRate * durationSec).toInt()
        val out = ShortArray(count)
        val rng = Random(startHz.toInt())
        var phase = 0.0
        for (i in 0 until count) {
            val frac = i.toFloat() / count.toFloat()
            val freq = startHz + (endHz - startHz) * frac
            phase += 2.0 * PI * freq / sampleRate
            val sq = if (sin(phase) >= 0.0) 1f else -1f
            val noise = rng.nextFloat() * 2f - 1f
            val env = (1f - frac) * (1f - frac * 0.4f)
            val sample = (sq * (1f - noiseMix) + noise * noiseMix) * env * 0.7f
            out[i] = (sample.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
        return out
    }

    private fun synthArp(notes: FloatArray, noteSec: Float): ShortArray {
        val perNote = (sampleRate * noteSec).toInt()
        val out = ShortArray(perNote * notes.size)
        for ((idx, freq) in notes.withIndex()) {
            var phase = 0.0
            for (i in 0 until perNote) {
                val env = 1f - (i.toFloat() / perNote.toFloat()) * 0.6f
                phase += 2.0 * PI * freq / sampleRate
                val sq = if (sin(phase) >= 0.0) 1f else -1f
                out[idx * perNote + i] = (sq * env * 0.55f * 32767f).toInt().toShort()
            }
        }
        return out
    }
}
