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
    private const val CHUNK = 512
    private const val CONTEXT = 64
    private const val STATE_SIZE = 128

    private var session: OrtSession? = null

    @Synchronized
    private fun session(context: Context): OrtSession = session ?: run {
        val model = context.assets.open("silero_vad.onnx").use { it.readBytes() }
        OrtEnvironment.getEnvironment().createSession(model, OrtSession.SessionOptions()).also { session = it }
    }

    @Suppress("UNCHECKED_CAST")
    fun probabilities(context: Context, samples: FloatArray): FloatArray {
        val env = OrtEnvironment.getEnvironment()
        val model = session(context)
        val frames = samples.size / CHUNK
        val result = FloatArray(frames)
        val input = FloatArray(CONTEXT + CHUNK)
        val contextSamples = FloatArray(CONTEXT)
        var state = FloatArray(2 * STATE_SIZE)

        OnnxTensor.createTensor(env, SAMPLE_RATE.toLong()).use { rate ->
            for (frame in 0 until frames) {
                // The model expects the last 64 samples of the previous chunk before each chunk.
                System.arraycopy(contextSamples, 0, input, 0, CONTEXT)
                System.arraycopy(samples, frame * CHUNK, input, CONTEXT, CHUNK)
                OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, input.size.toLong())).use { x ->
                    OnnxTensor.createTensor(env, FloatBuffer.wrap(state), longArrayOf(2, 1, STATE_SIZE.toLong())).use { s ->
                        model.run(mapOf("input" to x, "state" to s, "sr" to rate)).use { outputs ->
                            result[frame] = (outputs.get(0).value as Array<FloatArray>)[0][0]
                            val next = outputs.get(1).value as Array<Array<FloatArray>>
                            state = FloatArray(2 * STATE_SIZE).also {
                                System.arraycopy(next[0][0], 0, it, 0, STATE_SIZE)
                                System.arraycopy(next[1][0], 0, it, STATE_SIZE, STATE_SIZE)
                            }
                        }
                    }
                }
                System.arraycopy(samples, frame * CHUNK + CHUNK - CONTEXT, contextSamples, 0, CONTEXT)
            }
        }
        return result
    }
}
