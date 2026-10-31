package com.ayne.util

import android.content.Context
import android.util.Log
import java.io.File

object TempAudioStore {
    private const val TAG = "TempAudioStore"
    const val MAX_RETAINED_FILES = 3
    private const val AUDIO_DIR_NAME = "audio"

    private var applicationContext: Context? = null

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    fun getAudioDir(context: Context? = null): File {
        val baseDir = (context ?: applicationContext)?.cacheDir
            ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        val dir = File(baseDir, AUDIO_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    @Synchronized
    fun createTempAudioFile(
        context: Context? = null,
        prefix: String = "audio_",
        suffix: String = ".mp3"
    ): File {
        val audioDir = getAudioDir(context)
        pruneToLimit(audioDir, MAX_RETAINED_FILES - 1)
        return File.createTempFile(prefix, suffix, audioDir)
    }

    @Synchronized
    fun deleteFile(file: File?): Boolean {
        if (file == null || !file.exists()) return false
        return try {
            file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete audio file: ${file.absolutePath}", e)
            false
        }
    }

    @Synchronized
    fun prune(context: Context? = null, maxRetained: Int = MAX_RETAINED_FILES) {
        val audioDir = getAudioDir(context)
        pruneToLimit(audioDir, maxRetained)
    }

    @Synchronized
    fun clear(context: Context? = null) {
        val audioDir = getAudioDir(context)
        audioDir.listFiles()?.forEach { file ->
            try {
                file.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear audio file: ${file.absolutePath}", e)
            }
        }
    }

    private fun pruneToLimit(audioDir: File, maxRetained: Int) {
        val files = audioDir.listFiles()?.filter { it.isFile } ?: return
        if (files.size <= maxRetained) return

        val sortedFiles = files.sortedBy { it.lastModified() }
        val toDeleteCount = files.size - maxRetained

        for (i in 0 until toDeleteCount) {
            try {
                sortedFiles[i].delete()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to prune file: ${sortedFiles[i].absolutePath}", e)
            }
        }
    }
}
