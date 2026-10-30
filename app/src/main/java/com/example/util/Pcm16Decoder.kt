package com.example.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Decodes a compressed recording (AAC/M4A as produced by VoiceRecorderHelper)
 * into raw 16 kHz mono PCM16 bytes — the input format required by the
 * Addis AI Realtime Mode B SSE endpoint.
 *
 * Pure-byte decode: no playback, no temp-file leftovers.
 */
object Pcm16Decoder {

    /**
     * @return 16 kHz mono little-endian PCM16 bytes, or null when decoding
     *         fails or yields no usable audio.
     */
    fun decodeTo16kMonoPcm16(context: Context, compressedBase64: String): ByteArray? {
        return try {
            val tempFile = File.createTempFile("mirkuz_rt_in_", ".m4a", context.cacheDir)
            try {
                FileOutputStream(tempFile).use { fos ->
                    fos.write(Base64.decode(compressedBase64, Base64.DEFAULT))
                    fos.flush()
                }
                decodeFileToPcm(tempFile)?.let { raw ->
                    normalizeTo16kMono16Bit(raw)
                }
            } finally {
                tempFile.delete()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Synchronous MediaExtractor + MediaCodec decode → PCM bytes at the source rate. */
    private fun decodeFileToPcm(input: File): DecodedPcm? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(input.absolutePath)
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                if (trackFormat.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }
            if (audioTrackIndex < 0 || format == null) return null

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val out = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIndex = codec.dequeueInputBuffer(10_000L)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                sawInputEos = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, 10_000L)
                when {
                    outIndex >= 0 -> {
                        val outBuffer = codec.getOutputBuffer(outIndex)
                        if (outBuffer != null && info.size > 0) {
                            val chunk = ByteArray(info.size)
                            outBuffer.position(info.offset)
                            outBuffer.get(chunk)
                            out.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEos = true
                        }
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Final output format; captured implicitly via codec.outputFormat later.
                    }
                    // INFO_TRY_AGAIN_LATER and INFO_OUTPUT_BUFFERS_CHANGED → keep looping.
                }
            }

            val outFormat = codec.outputFormat
            val rate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val bytes = out.toByteArray()
            if (bytes.isEmpty()) return null
            return DecodedPcm(bytes, rate, channels)
        } catch (_: Exception) {
            return null
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {}
            try {
                codec?.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }

    private data class DecodedPcm(val bytes: ByteArray, val sampleRate: Int, val channels: Int)

    /**
     * Converts decoded PCM (16-bit) to 16 kHz mono: downmixes stereo by
     * averaging and resamples with linear interpolation when needed.
     */
    private fun normalizeTo16kMono16Bit(decoded: DecodedPcm): ByteArray? {
        var samples = toShortSamples(decoded.bytes) ?: return null

        // Downmix to mono.
        if (decoded.channels >= 2) {
            val frames = samples.size / decoded.channels
            val mono = ShortArray(frames)
            for (f in 0 until frames) {
                var sum = 0
                for (c in 0 until decoded.channels) sum += samples[f * decoded.channels + c]
                mono[f] = (sum / decoded.channels).toShort()
            }
            samples = mono
        }

        // Resample to 16 kHz.
        val rate = decoded.sampleRate
        val resampled = if (rate == TARGET_SAMPLE_RATE || rate <= 0) {
            samples
        } else {
            val ratio = rate.toDouble() / TARGET_SAMPLE_RATE
            val targetLen = (samples.size / ratio).toInt().coerceAtLeast(1)
            val out = ShortArray(targetLen)
            for (i in 0 until targetLen) {
                val srcPos = i * ratio
                val i0 = srcPos.toInt().coerceIn(0, samples.size - 1)
                val i1 = (i0 + 1).coerceIn(0, samples.size - 1)
                val frac = (srcPos - i0).toFloat()
                out[i] = (samples[i0] * (1f - frac) + samples[i1] * frac).toInt().toShort()
            }
            out
        }

        if (resampled.size < MIN_SAMPLES) return null
        val result = ByteArray(resampled.size * 2)
        for (i in resampled.indices) {
            val v = resampled[i].toInt()
            result[i * 2] = (v and 0xFF).toByte()
            result[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return result
    }

    private fun toShortSamples(bytes: ByteArray): ShortArray? {
        if (bytes.size < 2) return null
        val count = bytes.size / 2
        val out = ShortArray(count)
        for (i in 0 until count) {
            val lo = bytes[i * 2].toInt() and 0xFF
            val hi = bytes[i * 2 + 1].toInt() // signed
            out[i] = ((hi shl 8) or lo).toShort()
        }
        return out
    }

    private const val TARGET_SAMPLE_RATE = 16_000

    /** <100 ms of audio is not worth sending. */
    private const val MIN_SAMPLES = 1_600
}
