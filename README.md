# Studiare

Studiare is an Android flashcard and spaced-repetition study app. Decks of cards can be studied through more than a dozen different study modes, each with its own configurable options, spanning plain review, typing/speech practice, and word games.

**Status:** Alpha (`0.1.0`). Expect rough edges and in-progress features — see the roadmap notes in the project's planning docs for current work.

## Study categories & modes

Every deck can be studied under five categories — **Learn**, **Practice**, **Quiz**, **Guided** (FSRS-driven spaced repetition), and **Games** — each offering a subset of these modes:

- **Flashcard** — classic front/back review, with auto-flip and FSRS grading in Guided sessions.
- **List / Multiple Choice / Matching** — pick-the-answer formats.
- **Typing / Typing (Scored)** — type the answer, with configurable formatting/autocorrect leniency.
- **Spoken Listen / Typed Listen** — audio-driven recall, backed by on-device speech synthesis and recognition.
- **Audio** — hands-free listening mode.
- **Freeform** — swipe/button card browsing with configurable navigation and layout.
- **Anagram, Hangman, Crossword, Memory, Word Search** — game-style review.

Nearly every mode has its own tunable options (timing, hints, auto-advance, highlighting, grid size, etc.) — editable per-session or as per-mode defaults in Settings.

## Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3 Expressive) for the entire UI, built against a shared, adaptive pane-based layout (phone/tablet/Chromebook).
- **Room** for local persistence (decks, cards, active sessions) with versioned migrations.
- **Firebase** — Firestore for cross-device sync, Auth for accounts, Crashlytics for crash reporting.
- **Sherpa-ONNX** for offline, on-device text-to-speech and speech-to-text, so audio-based modes work without a network connection.
- **DataStore** for app-wide preferences (keyboard shortcuts, accessibility toggles, sort order, etc.).

## Mode options: `mode-options.yaml`

Every mode option (what it's called, its type/default/range, which modes offer it, and any per-mode default override) is defined in one place: `app/src/main/defaults/mode-options.yaml`. A Gradle task (`generateModeOptionDefaults`, wired into every build) parses it at build time into generated Kotlin constants and a mode→option lookup table — edit the YAML, not the generated file, to add or change an option default.

## Project layout

```
app/src/main/java/net/ericclark/studiare/
├── data/            # Room entities, Firestore models, enums (SessionMode, StudyCategory, ...)
├── components/      # Session lifecycle, audio service, speech engine
├── studymodes/       # One screen (and supporting composables) per study mode
├── screens/          # Decks, deck editor, settings, dialogs, shared UI components
└── ui/                # Theming and adaptive-layout dimensions
app/src/main/defaults/mode-options.yaml   # Source of truth for every mode option
```

## Building

Standard Android Studio / Gradle project — open the root folder and sync, or build from the command line:

```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Requires a Firebase project configuration (`google-services.json`) to build the `app` module, since Crashlytics/Firestore/Auth are wired in via the Google Services Gradle plugin.
