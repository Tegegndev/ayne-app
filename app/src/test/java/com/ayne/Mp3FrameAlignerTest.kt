package com.ayne

import com.ayne.util.Mp3FrameAligner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp3FrameAlignerTest {

    private fun createSyntheticMp3Frame(length: Int = 417): ByteArray {
        val frame = ByteArray(length)
        // MPEG 1 Layer III, 128 kbps, 44100 Hz, no padding
        frame[0] = 0xFF.toByte()
        frame[1] = 0xFB.toByte() // 1111 1011: sync, MPEG1, Layer3, no CRC
        frame[2] = 0x90.toByte() // 1001 0000: 128kbps, 44100Hz, no padding
        frame[3] = 0x00.toByte()
        return frame
    }

    @Test
    fun testCompleteFrameEmittedWithoutTrailing() {
        val frame = createSyntheticMp3Frame(417)
        val result = Mp3FrameAligner.splitAtLastCompleteFrame(frame)

        assertEquals(417, result.completeBytes.size)
        assertEquals(0, result.trailingBytes.size)
    }

    @Test
    fun testSplitFramePreservesCompleteAndDefersTrailing() {
        val frame1 = createSyntheticMp3Frame(417)
        val frame2 = createSyntheticMp3Frame(417)
        // Simulate a chunk cut in the middle of frame 2 (only first 100 bytes of frame 2 present)
        val partialFrame2 = frame2.copyOfRange(0, 100)
        val input = frame1 + partialFrame2

        val result = Mp3FrameAligner.splitAtLastCompleteFrame(input)

        assertEquals(417, result.completeBytes.size)
        assertEquals(100, result.trailingBytes.size)

        // Combining trailingBytes with the remainder of frame 2 should complete frame 2
        val remainderFrame2 = frame2.copyOfRange(100, 417)
        val nextInput = result.trailingBytes + remainderFrame2
        val nextResult = Mp3FrameAligner.splitAtLastCompleteFrame(nextInput)

        assertEquals(417, nextResult.completeBytes.size)
        assertEquals(0, nextResult.trailingBytes.size)
    }

    @Test
    fun testNonMp3DataPassesThroughSafely() {
        val dummyData = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val result = Mp3FrameAligner.splitAtLastCompleteFrame(dummyData)

        assertEquals(dummyData.size, result.completeBytes.size)
        assertEquals(0, result.trailingBytes.size)
    }
}
