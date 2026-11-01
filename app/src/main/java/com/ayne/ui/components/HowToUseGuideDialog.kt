package com.ayne.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ayne.R
import com.ayne.ui.theme.AyneOnYellow
import com.ayne.ui.theme.AynePureBlack
import com.ayne.ui.theme.AynePureWhite
import com.ayne.ui.theme.AyneVibrantYellow
import com.ayne.viewmodel.SupportedLanguage

/**
 * Trilingual how-to-use narration script. Shown as the on-screen subtitle and
 * spoken by device TTS when the backend guide clip is not (yet) cached. Keep
 * this text in sync with the script sent to the backend for the `how_to_use`
 * clips — the recorded Amharic voice should say exactly this.
 */
fun howToUseScriptFor(language: SupportedLanguage): String = when (language) {
    SupportedLanguage.ENGLISH ->
        "Welcome to Ayne, your eyes for the world around you. Ayne has three main actions. " +
            "Describe Surroundings takes a photo and tells you what is in front of you. " +
            "Read Text reads signs, documents, and labels out loud. " +
            "Ask Question lets you speak a question about what you see; it submits automatically when you pause. " +
            "You can also use the volume keys: press Volume Up to capture and describe your surroundings, " +
            "and Volume Down to ask a question. " +
            "When you press Next, Ayne will ask for camera and microphone permission. " +
            "Please allow both, so Ayne can see and listen for you."
    SupportedLanguage.AMHARIC ->
        "እንኳን ወደ አይኔ በደህና መጡ። አይኔ ዙሪያዎን የሚያሳይዎት የዓይን እርዳታ መተግበሪያ ነው። ሦስት ዋና አገልግሎቶች አሉ። " +
            "ዙሪያውን አስረዳ ፎቶ ነስቶ በፊትዎ ያለውን ይናገርልዎታል። " +
            "ጽሑፍ አንብብ ምልክቶችን፣ ሰነዶችንና መለያዎችን ጮክ ብሎ ያነብብልዎታል። " +
            "ጠይቅ በድምፅ ጥያቄ እንዲጠይቁ ያስችልዎታል፤ ሲያቆሙም በራሱ ይላካል። " +
            "የድምፅ አዝራሮችንም መጠቀም ይችላሉ፦ የድምፅ መጨመሪያውን ተጭነው ዙሪያዎን ያስረዱ፤ " +
            "የድምፅ መቀነሻውን ተጭነው ጥያቄ ይጠይቁ። " +
            "ቀጥል ስትጫኑ አይኔ የካሜራና የማይክሮፎን ፈቃድ ይጠይቅዎታል። " +
            "አይኔ ለእርስዎ እንዲያይና እንዲሰማ ሁለቱንም ይፍቀዱላቸው።"
    SupportedLanguage.AFAN_OROMOO ->
        "Baga nagaan Ayne dhuftan. Ayne ija keessaniif naannoo isin agarsiisa. Tajaajila gurguddoo sadii qaba. " +
            "Naannoo Ibsi suuraa kaasee duraatti waan jiru isinitti hima. " +
            "Barruu Dubbisi asxaa, barreeffama fi mallattoo dhageessisee dubbisa. " +
            "Gaaffii Gaafadhu gaaffii keessan sagaleedhaan gaafachuuf isin dandeessisa; yeroo dhaabdanis ofumaan ni ergama. " +
            "Volume fayyadamuu dandeessu: Volume+ dhiibuun naannoo keessan ibsi; Volume− dhiibuun gaaffii gaafadhu. " +
            "Yeroo Itti fuf dhiibdu, Ayne kabaja kameraa fi maayikiroofonii gaafata. " +
            "Ayne isinitti arguufi isin dhaggeeffachuuf, isaan lachuu hayyami."
}

/**
 * Full-screen how-to-use guide: one backend-generated narration clip plays
 * start-to-finish while the same script is shown as on-screen subtitle text.
 * The guide audio itself is started by the caller ([com.ayne.ui.MainScreen]);
 * this composable only renders the script, the Replay control, and Next.
 *
 * Shown automatically after the first-launch language picker and on demand
 * from the top-bar help button.
 */
@Composable
fun HowToUseGuideDialog(
    script: String,
    onNext: () -> Unit,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nextDesc = stringResource(R.string.how_to_use_next_desc)
    val replayDesc = stringResource(R.string.how_to_use_replay_desc)

    Dialog(
        onDismissRequest = onNext,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag("how_to_use_guide_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AynePureBlack)
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Replay sits alone in the top corner so the earcon-style icon
                // is easy to find without reading the script text.
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onReplay,
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.TopEnd)
                            .semantics { contentDescription = replayDesc }
                            .testTag("how_to_use_replay_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = AyneVibrantYellow,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.how_to_use_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AyneVibrantYellow,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Subtitle: the full narration script, scrollable so long
                // scripts never clip and TalkBack can read every line.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = script,
                        color = AynePureWhite,
                        fontSize = 17.sp,
                        lineHeight = 26.sp,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onNext,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AyneVibrantYellow,
                        contentColor = AyneOnYellow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .border(1.dp, AyneVibrantYellow, RoundedCornerShape(12.dp))
                        .semantics { contentDescription = nextDesc }
                        .testTag("how_to_use_next_button")
                ) {
                    Text(
                        text = stringResource(R.string.how_to_use_next),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
