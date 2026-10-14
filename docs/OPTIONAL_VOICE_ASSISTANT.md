> Історичний опис: зміни 2.0 та актуальні обмеження див. UPDATE_2_UK.md.

# Optional voice assistant (isolated module)

This app includes an optional, opt-in voice helper that lives entirely in
`app/src/main/kotlin/com/semmyx/ledger/voice/OptionalVoiceAssistant.kt`. It is
deliberately **not wired into any existing screen, ViewModel, navigation
graph, manifest permission, or persisted setting**. Dropping this file into
the project changes nothing about the app's current behavior until some
screen explicitly creates an `OptionalVoiceAssistant` and calls into it.

## Why it's isolated

You asked for this in "a separate file so nothing breaks." Concretely:

- No `RECORD_AUDIO` permission is declared or required. Speech-to-text is
  delegated to the **system speech recognizer** via the standard
  `RecognizerIntent` activity result contract — the same picker used by
  Google's own microphone button elsewhere on the device. This app never
  touches raw microphone audio itself.
- No data is written to Room, DataStore, or any file. Everything the class
  holds (a `TextToSpeech` engine handle) lives only in memory for as long as
  the owning Activity is alive.
- No analytics, network calls, or third-party SDKs are involved.
- Speech output uses Android's built-in `TextToSpeech` engine only.
- Nothing in `Models.kt`, `Repositories.kt`, any ViewModel, or any existing
  screen imports or references this file, so adding it cannot change any
  existing behavior or test result.

## Public API

```kotlin
val assistant = OptionalVoiceAssistant(activity) // create once per Activity

// 1. Build the recognizer Intent and launch it from an
//    ActivityResultLauncher<Intent> (after requesting RECORD_AUDIO):
launcher.launch(assistant.recognizerIntent(AppLanguage.ENGLISH))

// 2. In the launcher's callback, pull out what was heard:
val heard = assistant.extractSpokenText(activityResult.data)

// 3. Optionally, map a few simple EN/UK phrases to an action:
val command = heard?.let { OptionalVoiceAssistant.classify(it) }
when (command) {
    VoiceCommand.ShowBalance -> { /* e.g. navigate to Dashboard */ }
    VoiceCommand.ShowSpentToday -> { /* e.g. show today's total */ }
    VoiceCommand.OpenAddEntry -> { /* e.g. open the add-entry screen */ }
    null -> { /* didn't understand — never guess, just say so */ }
}

// 4. Talk back any time:
assistant.speak("Got it.", AppLanguage.ENGLISH)

// 5. Release the TextToSpeech engine when the Activity is destroyed:
assistant.shutdown()
```

`OptionalVoiceAssistant.classify(heard: String)` is a small, best-effort,
case-insensitive matcher over English and Ukrainian phrases. It intentionally
returns `null` ("didn't understand") for anything it isn't confident about,
rather than guessing and triggering the wrong action:

- `VoiceCommand.ShowBalance` — "what's my balance" / "який в мене баланс" / "скільки в мене грошей"
- `VoiceCommand.ShowSpentToday` — "what did I spend today" / "скільки я витратив сьогодні"
- `VoiceCommand.OpenAddEntry` — "add a new expense" / "додати новий запис"

`OptionalVoiceAssistantParserTest.kt` covers all four branches plus blank
input, entirely as plain JVM unit tests (no Android framework needed, since
`classify` is pure string matching).

## Relationship to the TG Plus mascot

The premium mascot card (`cat/CatAssistantCard.kt`) is
a **separate, independent feature** that happens to also use Android's
`TextToSpeech` engine to speak its own greeting. It does not depend on or
reuse `OptionalVoiceAssistant` — each keeps its own engine instance, so you
can change, remove, or extend either one without touching the other. The
mascot never listens for speech input; it only talks.

## Wiring it in later

When you're ready to expose this to users:

1. In an Activity (or via a Compose `rememberLauncherForActivityResult`
   wrapper you write in a screen file), register an
   `ActivityResultLauncher<Intent>` for `ActivityResultContracts.StartActivityForResult()`.
2. Request the microphone permission through the normal Android runtime
   permission flow — this file does not request permissions for you.
3. Launch `assistant.recognizerIntent(language)`, read the result with
   `assistant.extractSpokenText(...)`, and decide what to do with
   `OptionalVoiceAssistant.classify(...)`.
4. For safety, prefer routing a recognized `DraftEntry`-style command into a
   prefilled "add entry" screen rather than saving directly, so the user
   always confirms before anything is written.

None of these steps require touching any other existing file.
