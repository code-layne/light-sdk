package com.thelightphone.sample

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.Random
import kotlin.concurrent.thread

/**
 * Streaming brown-noise generator built on [AudioTrack].
 *
 * A leaky integrator over Gaussian white noise produces the −6 dB/octave slope
 * of brown noise; the leak factor keeps the running sum from drifting to the
 * rail (DC offset). This is pure `android.media` and holds no service or wake
 * lock of its own — see [HomeScreenViewModel] for the lifecycle that drives it.
 *
 * NOTE: on LightOS this only produces sound while the tool is in the
 * foreground. Continuous / screen-off playback needs a LightOS-side bridge that
 * the SDK does not yet expose (see docs/sdk-request-background-audio.md in the
 * ambient_lp repo). [stop] is called when the app is paused so we never leave a
 * silent AudioTrack + thread running in the background.
 */
class BrownNoiseGenerator {

    private var audioTrack: AudioTrack? = null

    @Volatile
    private var playing = false

    // Leaky integrator state — avoids DC drift while keeping the brown character.
    private var runningSum = 0.0

    fun start() {
        if (playing) return
        playing = true

        val sampleRate = 44100
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(8192)

        val track = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
            AudioTrack.MODE_STREAM,
        )
        audioTrack = track
        track.play()

        thread(name = "brown-noise") {
            val buf = ShortArray(bufferSize / 2)
            val leak = 0.999    // integrator leak — keeps the sum off the rail
            val scale = 3200.0  // tuned so the signal stays well inside Short range
            val rng = Random()

            while (playing) {
                for (i in buf.indices) {
                    runningSum = runningSum * leak + rng.nextGaussian()
                    // Soft-clip rather than hard clamp to avoid clicks on transients.
                    val sample = runningSum / scale
                    val clamped = when {
                        sample > 1.0 -> 1.0
                        sample < -1.0 -> -1.0
                        else -> sample
                    }
                    buf[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
                }
                // Guard the write: the track may be released from another thread.
                val current = audioTrack ?: break
                if (!playing) break
                current.write(buf, 0, buf.size)
            }
        }
    }

    fun stop() {
        if (!playing && audioTrack == null) return
        playing = false
        audioTrack?.let { track ->
            runCatching { track.stop() }
            track.release()
        }
        audioTrack = null
        runningSum = 0.0
    }
}
