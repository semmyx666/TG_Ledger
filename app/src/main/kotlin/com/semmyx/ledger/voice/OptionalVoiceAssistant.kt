package com.semmyx.ledger.voice

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.os.Handler
import android.os.Looper
import com.semmyx.ledger.domain.AppLanguage
import java.util.Locale

/**
 * Optional, self-contained voice assistant helper.
 *
 * This file is intentionally isolated from the rest of the app (Models, ViewModels,
 * screens) so it can be adopted gradually without risking regressions elsewhere.
 * It is NOT wired into any screen yet. To use it:
 *  1. Create an instance in an Activity: `val assistant = OptionalVoiceAssistant(activity)`
 *  2. Call `assistant.recognizerIntent(language)` from an ActivityResultLauncher<Intent>,
 *     after requesting the RECORD_AUDIO permission.
 *  3. Read the result with `assistant.extractSpokenText(result.data)` and, optionally,
 *     `OptionalVoiceAssistant.classify(heard)` to react to a few simple EN/UK commands.
 *  4. Call `assistant.speak("...")` any time to have it talk back.
 *  5. Call `assistant.shutdown()` in onDestroy.
 *
 * Recognition uses the standard Android RecognizerIntent (speech-to-text is handled by the
 * device's configured recognizer; this app never receives or stores raw audio). Speech output
 * uses the on-device TextToSpeech engine. See docs/OPTIONAL_VOICE_ASSISTANT.md.
 */
class OptionalVoiceAssistant(activity: Activity) {
    private val app = activity.applicationContext
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var closed = false
    private var pendingSpeech: Pair<String, AppLanguage>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        // Some devices (custom ROMs, some emulators, phones with no TTS data installed) can
        // throw here instead of just reporting TextToSpeech.ERROR through the callback. This is
        // a purely optional feature, so never let it take the whole app down with it.
        tts = try {
            TextToSpeech(app) { status ->
                mainHandler.post {
                    if (!closed) {
                        ttsReady = status == TextToSpeech.SUCCESS
                        val pending = pendingSpeech
                        pendingSpeech = null
                        if (ttsReady && pending != null) speak(pending.first, pending.second)
                    }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun speak(text: String, language: AppLanguage = AppLanguage.ENGLISH) {
        if (closed) return
        val engine = tts ?: return
        if (!ttsReady) { pendingSpeech = text to language; return }
        try {
            engine.language = Locale.forLanguageTag(language.tag)
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "optionalVoiceAssistant")
        } catch (_: Throwable) {
            // Speaking is best-effort; a missing voice/locale on the device should never crash.
        }
    }

    /** Build the Intent to pass to an ActivityResultLauncher<Intent> for speech recognition. */
    fun recognizerIntent(language: AppLanguage): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

    /** Extract the top recognized utterance from an ActivityResult's data Intent, if any. */
    fun extractSpokenText(resultData: Intent?): String? =
        resultData?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()

    fun shutdown() {
        closed = true
        pendingSpeech = null
        mainHandler.removeCallbacksAndMessages(null)
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Throwable) {
            // Ignore -- we're tearing down anyway.
        }
        tts = null
    }

    /** Stop output before opening microphone: the recognizer must not transcribe the cat. */
    fun stopSpeaking() { pendingSpeech = null; try { tts?.stop() } catch (_: Exception) { } }

    companion object {
        /**
         * Whether any app on this device can handle speech recognition. Many phones (custom
         * ROMs, Google-app-free builds, some emulators) have none installed. Always check this
         * -- or catch ActivityNotFoundException around the launch -- before starting the
         * recognizer intent, otherwise the missing handler crashes the app with no on-screen error.
         */
        fun isAvailable(context: Context): Boolean {
            val probe = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            return probe.resolveActivity(context.packageManager) != null
        }

        /**
         * Very small, best-effort command classifier for simple EN/UK phrases such as
         * "what's my balance" / "скільки в мене грошей". Returns null when nothing matches;
         * callers should fall back to just repeating what was heard.
         */
        fun classify(heard: String): VoiceCommand? {
            val text = heard.trim().lowercase()
            return when {
                text.isBlank() -> null
                BALANCE_EN.any { text.contains(it) } || BALANCE_UK.any { text.contains(it) } -> VoiceCommand.ShowBalance
                SPENT_EN.any { text.contains(it) } || SPENT_UK.any { text.contains(it) } -> VoiceCommand.ShowSpentToday
                ADD_EN.any { text.contains(it) } || ADD_UK.any { text.contains(it) } -> VoiceCommand.OpenAddEntry
                else -> null
            }
        }
        private val BALANCE_EN = listOf("balance", "how much money", "how much do i have")
        private val BALANCE_UK = listOf("баланс", "скільки в мене грошей", "скільки грошей")
        private val SPENT_EN = listOf("spent today", "spend today", "what did i spend")
        private val SPENT_UK = listOf("витратив сьогодні", "витратили сьогодні", "скільки витратив")
        private val ADD_EN = listOf("add entry", "new entry", "add expense", "add income", "new expense", "new income")
        private val ADD_UK = listOf("додати запис", "новий запис", "додати витрату", "додати доход")
    }
}

sealed interface VoiceCommand {
    data object ShowBalance : VoiceCommand
    data object ShowSpentToday : VoiceCommand
    data object OpenAddEntry : VoiceCommand
}
