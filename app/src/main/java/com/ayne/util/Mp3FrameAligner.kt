package com.ayne.util

/**
 * Aligns audio chunks to clean MPEG Layer III (MP3) frame boundaries.
 *
 * Real-time TTS streaming servers emit arbitrary byte slices that can bisect
 * an MP3 frame. When a chunk starts with a partial frame, Android's MediaPlayer
 * fails prepare() with MEDIA_ERROR_MALFORMED. This helper identifies frame
 * boundaries, outputs only complete frames, and buffers partial trailing bytes
 * to prepend to the next chunk.
 */
object Mp3FrameAligner {

    data class SplitResult(
        val completeBytes: ByteArray,
        val trailingBytes: ByteArray
    )

    private val BITRATES_MPEG1_L3 = intArrayOf(
        0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320
    )
    private val BITRATES_MPEG2_L3 = intArrayOf(
        0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160
    )

    private val SAMPLE_RATES_MPEG1 = intArrayOf(44100, 48000, 32000)
    private val SAMPLE_RATES_MPEG2 = intArrayOf(22050, 24000, 16000)
    private val SAMPLE_RATES_MPEG25 = intArrayOf(11025, 12000, 8000)

    fun splitAtLastCompleteFrame(bytes: ByteArray): SplitResult {
        if (bytes.size < 4) {
            return SplitResult(ByteArray(0), bytes)
        }

        var offset = 0
        var lastValidFrameEnd = 0

        while (offset < bytes.size - 3) {
            val b0 = bytes[offset].toInt() and 0xFF
            val b1 = bytes[offset + 1].toInt() and 0xFF

            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                val frameLength = parseFrameLength(bytes, offset)
                if (frameLength > 0) {
                    val frameEnd = offset + frameLength
                    if (frameEnd <= bytes.size) {
                        lastValidFrameEnd = frameEnd
                        offset = frameEnd
                        continue
                    } else {
                        // Frame exceeds available bytes — truncated mid-frame
                        break
                    }
                }
            }
            offset++
        }

        return if (lastValidFrameEnd > 0) {
            SplitResult(
                completeBytes = bytes.copyOfRange(0, lastValidFrameEnd),
                trailingBytes = bytes.copyOfRange(lastValidFrameEnd, bytes.size)
            )
        } else {
            // No recognizable frame header found; emit directly to avoid stalling non-standard audio
            SplitResult(bytes, ByteArray(0))
        }
    }

    private fun parseFrameLength(bytes: ByteArray, offset: Int): Int {
        if (offset + 3 >= bytes.size) return -1

        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt() and 0xFF

        val versionBits = (b1 shr 3) and 0x03
        if (versionBits == 1) return -1 // Reserved version

        val layerBits = (b1 shr 1) and 0x03
        if (layerBits != 1) return -1 // Only Layer III supported

        val bitrateIdx = (b2 shr 4) and 0x0F
        if (bitrateIdx == 0 || bitrateIdx == 15) return -1

        val sampleRateIdx = (b2 shr 2) and 0x03
        if (sampleRateIdx == 3) return -1

        val padding = (b2 shr 1) and 0x01

        val sampleRate = when (versionBits) {
            3 -> SAMPLE_RATES_MPEG1[sampleRateIdx]
            2 -> SAMPLE_RATES_MPEG2[sampleRateIdx]
            0 -> SAMPLE_RATES_MPEG25[sampleRateIdx]
            else -> return -1
        }

        val bitrate = when (versionBits) {
            3 -> BITRATES_MPEG1_L3[bitrateIdx] * 1000
            else -> BITRATES_MPEG2_L3[bitrateIdx] * 1000
        }

        return when (versionBits) {
            3 -> (144 * bitrate / sampleRate) + padding
            else -> (72 * bitrate / sampleRate) + padding
        }
    }
}
