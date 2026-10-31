package com.example.util

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.example.viewmodel.AyneAction
import com.example.viewmodel.AyneViewModel

class VolumeKeyHandler(private val activity: Activity) {
    private var onVolumeCaptureTriggered: (() -> Unit)? = null
    private var onVolumeFollowUpTriggered: (() -> Unit)? = null

    private var volumeUpPressTime = 0L
    private var volumeDownPressTime = 0L
    private var isVolumeUpLongPress = false
    private var isVolumeDownLongPress = false
    private var lastVolumeDownReleaseTime = 0L
    private val volumeDownHandler = Handler(Looper.getMainLooper())
    private var pendingVolumeDownRunnable: Runnable? = null

    fun registerVolumeHandlers(onCapture: () -> Unit, onFollowUp: () -> Unit) {
        onVolumeCaptureTriggered = onCapture
        onVolumeFollowUpTriggered = onFollowUp
    }

    fun onDestroy() {
        pendingVolumeDownRunnable?.let { volumeDownHandler.removeCallbacks(it) }
    }

    fun onKeyDown(keyCode: Int, event: KeyEvent?, viewModel: AyneViewModel): Boolean {
        if (event == null) return false

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            viewModel.stopPlaybackOnly()
        }

        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event.repeatCount > 0) {
                    isVolumeUpLongPress = true
                    return false
                }
                volumeUpPressTime = System.currentTimeMillis()
                isVolumeUpLongPress = false
                event.startTracking()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event.repeatCount > 0) {
                    isVolumeDownLongPress = true
                    return false
                }
                volumeDownPressTime = System.currentTimeMillis()
                isVolumeDownLongPress = false
                event.startTracking()
                return true
            }
        }
        return false
    }

    fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                isVolumeUpLongPress = true
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                isVolumeDownLongPress = true
                return true
            }
        }
        return false
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent?, viewModel: AyneViewModel): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!isVolumeUpLongPress && (System.currentTimeMillis() - volumeUpPressTime < 600)) {
                    onVolumeCaptureTriggered?.invoke()
                        ?: viewModel.onAction(activity, AyneAction.VolumeCaptureTriggered)
                    return true
                }
                return false
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!isVolumeDownLongPress && (System.currentTimeMillis() - volumeDownPressTime < 600)) {
                    if (viewModel.uiState.value.isRecordingVoice) {
                        viewModel.stopAndSubmitVoiceRecording(activity)
                        return true
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastVolumeDownReleaseTime < 350) {
                        pendingVolumeDownRunnable?.let { volumeDownHandler.removeCallbacks(it) }
                        pendingVolumeDownRunnable = null
                        lastVolumeDownReleaseTime = 0L
                        viewModel.onAction(activity, AyneAction.RepeatLastAnswer)
                        return true
                    } else {
                        lastVolumeDownReleaseTime = now
                        val runnable = Runnable {
                            onVolumeFollowUpTriggered?.invoke()
                                ?: viewModel.onAction(activity, AyneAction.VolumeFollowUpTriggered)
                            pendingVolumeDownRunnable = null
                        }
                        pendingVolumeDownRunnable = runnable
                        volumeDownHandler.postDelayed(runnable, 350)
                        return true
                    }
                }
                return false
            }
        }
        return false
    }
}
