package com.ayne

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.ayne.ui.AyneAppScreen
import com.ayne.ui.theme.AyneTheme
import com.ayne.util.TempAudioStore
import com.ayne.util.VolumeKeyHandler
import com.ayne.viewmodel.AyneViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: AyneViewModel by viewModels()
    private val volumeKeyHandler = VolumeKeyHandler(this)

    fun registerVolumeHandlers(onCapture: () -> Unit, onFollowUp: () -> Unit) {
        volumeKeyHandler.registerVolumeHandlers(onCapture, onFollowUp)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TempAudioStore.init(this)
        viewModel.initLanguagePreference(this)
        setContent {
            AyneTheme {
                AyneAppScreen(
                    viewModel = viewModel,
                    onRegisterVolumeHandlers = ::registerVolumeHandlers
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        volumeKeyHandler.onDestroy()
        TempAudioStore.prune(this)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN && viewModel.isSpeaking()) {
            viewModel.bargeIn()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        if (volumeKeyHandler.onKeyDown(keyCode, event, viewModel)) true
        else super.onKeyDown(keyCode, event)

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean =
        if (volumeKeyHandler.onKeyLongPress(keyCode, event)) true
        else super.onKeyLongPress(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        if (volumeKeyHandler.onKeyUp(keyCode, event, viewModel)) true
        else super.onKeyUp(keyCode, event)
}
