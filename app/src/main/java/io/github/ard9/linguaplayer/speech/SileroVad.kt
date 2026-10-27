package io.github.ard9.linguaplayer.speech

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer

/**
 * Silero VAD (MIT, bundled in assets): the probability that each 32 ms of 16 kHz mono audio
 * contains speech. Runs on the device; call from a background thread.
 */
object SileroVad {
    const val SAMPLE_RATE = 16_000
    /** Samples per probability (32 ms). */
    const val CHUNK = 512
    private const val CONTEXT = 64
    private const val STATE_SIZE = 128

    private var session: OrtSession? = null

    @Synchronized
    private fun session(context: Context): OrtSession = session ?: run {
        val model = context.assets.open("silero_vad.onnx").use { it.readBytes() }
        OrtEnvironment.getEnvironment().createSession(model, OrtSession.SessionOptions()).also { session = it }
    }

    /** One probability per [CHUNK] samples of [samples]. */
    fun probabilities(context: Context, samples: FloatArray): FloatArray {
        val stream = Stream(context)
        val frames = samples.size / CHUNK
        val chunk = FloatArray(CHUNK)
        return FloatArray(frames) { frame ->
            System.arraycopy(samples, frame * CHUNK, chunk, 0, CHUNK)
            stream.next(chunk)
        }
    }

    /** Speech probabilities for audio that arrives piece by piece (e.g. from the microphone). */
    class Stream(context: Context) {
        private val env = OrtEnvironment.getEnvironment()
        private val model = session(context)
        private val input = FloatArray(CONTEXT + CHUNK)
        private val contextSamples = FloatArray(CONTEXT)
        private var state = FloatArray(2 * STATE_SIZE)

        /** Probability of speech in the next [CHUNK] samples. */
        @Suppress("UNCHECKED_CAST")
        fun next(chunk: FloatArray): Float {
            require(chunk.size == CHUNK)
            // The model expects the last 64 samples of the previous chunk before each chunk.
            System.arraycopy(contextSamples, 0, input, 0, CONTEXT)
            System.arraycopy(chunk, 0, input, CONTEXT, CHUNK)
            var probability = 0f
            OnnxTensor.createTensor(env, SAMPLE_RATE.toLong()).use { rate ->
                OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, input.size.toLong())).use { x ->
                    OnnxTensor.createTensor(env, FloatBuffer.wrap(state), longArrayOf(2, 1, STATE_SIZE.toLong())).use { s ->
                        model.run(mapOf("input" to x, "state" to s, "sr" to rate)).use { outputs ->
                            probability = (outputs.get(0).value as Array<FloatArray>)[0][0]
                            val next = outputs.get(1).value as Array<Array<FloatArray>>
                            state = FloatArray(2 * STATE_SIZE).also {
                                System.arraycopy(next[0][0], 0, it, 0, STATE_SIZE)
                                System.arraycopy(next[1][0], 0, it, STATE_SIZE, STATE_SIZE)
                            }
                        }
                    }
                }
            }
            System.arraycopy(chunk, CHUNK - CONTEXT, contextSamples, 0, CONTEXT)
            return probability
        }
    }
}
