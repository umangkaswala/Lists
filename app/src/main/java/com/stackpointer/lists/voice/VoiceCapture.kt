package com.stackpointer.lists.voice

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Speech-to-text for the capture pill and the search bar.
 *
 * Uses [RecognizerIntent] — handing the job to whichever speech app the phone
 * already has — rather than the `SpeechRecognizer` API. That choice matters for
 * two reasons: the intent route needs **no `RECORD_AUDIO` permission** (the
 * recogniser app holds it and shows its own listening UI), and it means Lists
 * never touches the microphone itself, which is a far easier thing to say
 * honestly in a privacy policy.
 *
 * The trade-off, worth stating in Settings copy (Phase 9): the recognised text
 * may be produced in Google's cloud rather than on the device, depending on
 * what the phone supports. `EXTRA_PREFER_OFFLINE` asks for on-device, but it is
 * a preference, not a guarantee.
 */
object VoiceCapture {

    fun intent(promptText: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, promptText)
            // Asks the recogniser to stay on the device where it can. Ignored
            // by recognisers that have no offline model.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

    /** Whether any app on this phone can handle dictation. */
    fun isAvailable(context: Context): Boolean =
        intent("").resolveActivity(context.packageManager) != null

    fun firstResult(data: Intent?): String? =
        data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}

/**
 * Remembers a launcher that dictates and hands back the recognised text.
 *
 * [onUnavailable] covers the phone with no speech app at all — rarer than it
 * used to be, but a silent no-op there would look exactly like a broken button.
 */
@Composable
fun rememberVoiceCaptureLauncher(
    prompt: String,
    onUnavailable: () -> Unit,
    onResult: (String) -> Unit
): () -> Unit {
    val launcher: ActivityResultLauncher<Intent> = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // A cancelled dictation comes back RESULT_CANCELED with no extras;
        // saying nothing is the right response to saying nothing.
        VoiceCapture.firstResult(result.data)?.let(onResult)
    }
    val intent = remember(prompt) { VoiceCapture.intent(prompt) }
    return remember(launcher, intent) {
        {
            try {
                launcher.launch(intent)
            } catch (error: ActivityNotFoundException) {
                onUnavailable()
            }
        }
    }
}
