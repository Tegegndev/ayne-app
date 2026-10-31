package com.ayne

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ayne.util.TempAudioStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TempAudioStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TempAudioStore.clear(context)
    }

    @Test
    fun testRetainsNoMoreThanThreeFilesAfterTenCreations() {
        val audioDir = TempAudioStore.getAudioDir(context)

        for (i in 1..10) {
            val file = TempAudioStore.createTempAudioFile(context, "test_turn_", ".mp3")
            file.writeText("audio payload $i")
            Thread.sleep(10) // Ensure distinct lastModified timestamps
        }

        val remainingFiles = audioDir.listFiles()?.filter { it.isFile } ?: emptyList()
        assertTrue("Remaining files should be <= 3, but was ${remainingFiles.size}", remainingFiles.size <= 3)
    }

    @Test
    fun testClearDeletesAllFiles() {
        val audioDir = TempAudioStore.getAudioDir(context)

        for (i in 1..3) {
            val file = TempAudioStore.createTempAudioFile(context, "cleanup_", ".mp3")
            file.writeText("temp")
        }

        assertTrue((audioDir.listFiles()?.size ?: 0) > 0)
        TempAudioStore.clear(context)
        assertEquals(0, audioDir.listFiles()?.size ?: 0)
    }
}
