package net.ericclark.studiare.baselineprofile

import android.os.ParcelFileDescriptor
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

private const val PACKAGE_NAME = "net.ericclark.studiare"
private const val WAIT_TIMEOUT_MS = 8_000L
private const val SAMPLE_DECK_NAME = "Baseline Profile Deck"
// A single-card deck was found to be a misleading proxy for Simple Editor's real cost: a
// user-observed experiment (gate removed on the grid's deferred reveal) showed the NavHost
// transition works fine for a 1-card deck but gets swallowed for "considerably more cards" —
// and a baseline profile only ever records methods that actually ran during collection, so a
// 1-card profile can't capture whatever multi-item grid layout/measurement code only executes
// once there's enough cards to fill the initial viewport.
//
// Filling many cards by hand through Bulk Editor's "endless form" via UiAutomator was tried
// first and abandoned: it's genuinely fragile (confirmed the hard way — the trailing blank
// card's append needs a real Compose recomposition pass that a fixed wait can't reliably catch,
// and a filled card renders noticeably taller than a blank one, so the newly-appended card can
// end up off-screen before the next fill attempt can find it). Importing a real, pre-built CSV
// via the app's own "Import Decks" flow is both more realistic (exercises real import code, not
// a synthetic UI loop) and far more reliable (one file picker tap vs. dozens of fragile steps).
//
// Portability note: this depends on a file already present on the test device at
// /storage/emulated/0/Download/, not bundled with the test — fine for now (this is for one-off
// local verification), but would need to be pushed as part of test setup to be CI-safe.
private const val IMPORT_CSV_FILENAME = "baseline_profile_sample_data.csv"
private const val IMPORTED_DECK_NAME = "Mexican States"

/**
 * Generates a baseline profile for Studiare's startup path.
 *
 * Three journeys are collected into the same profile:
 *  - [firstLaunch]: a fresh install with no local data (splash -> empty deck list -> deck
 *    creation flow -> settings), matching what a brand-new user's first cold start touches.
 *  - [subsequentLaunch]: a returning user with an existing deck already in the Room database,
 *    which exercises the deck-list-with-content and deck-open code paths a typical cold start
 *    after day one touches.
 *  - [openSimpleEditor]: Decks screen -> Simple Editor, a common mid-session navigation (not a
 *    startup path, so not included in the startup profile) covering the deck tile's Edit icon,
 *    the "Decks -> Simple Editor" NavHost transition, and Simple Editor's own first composition —
 *    against a realistic many-card deck (imported from a CSV, see [IMPORT_CSV_FILENAME]'s
 *    comment), not a trivial 1-card one.
 *
 * Run with: `./gradlew :baselineprofile:pixel6Api34BenchmarkAndroidTest` (or any configured
 * device/Gradle Managed Device), or via `./gradlew :app:generateBaselineProfile` to also merge
 * the result into app/src/main/baseline-prof.txt.
 */
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun firstLaunch() {
        clearAppData()

        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            maxIterations = 5,
            includeInStartupProfile = true
        ) {
            pressHome()
            startActivityAndWait()

            // Empty deck list -> jump into deck creation, the first thing a new user does.
            device.wait(Until.hasObject(By.desc("Create Deck")), WAIT_TIMEOUT_MS)
            device.findObject(By.desc("Create Deck"))?.click()

            val nameField = device.wait(Until.findObject(By.clazz("android.widget.EditText")), WAIT_TIMEOUT_MS)
            nameField?.text = SAMPLE_DECK_NAME
            device.waitForIdle()
            device.findObject(By.text("Save"))?.click()
            device.waitForIdle()

            device.pressBack()
            device.wait(Until.hasObject(By.desc("Create Deck")), WAIT_TIMEOUT_MS)

            // Also touch settings, the other entry point a first-run user commonly explores.
            device.findObject(By.desc("Settings"))?.click()
            device.waitForIdle()
            device.pressBack()
        }
    }

    @Test
    fun subsequentLaunch() {
        ensureSampleDeckExists()

        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            maxIterations = 8,
            includeInStartupProfile = true
        ) {
            pressHome()
            startActivityAndWait()

            // Returning user: deck list already has content, open the existing deck.
            device.wait(Until.hasObject(By.text(SAMPLE_DECK_NAME)), WAIT_TIMEOUT_MS)
            device.findObject(By.text(SAMPLE_DECK_NAME))?.click()
            device.waitForIdle()
            device.pressBack()

            device.wait(Until.hasObject(By.desc("Settings")), WAIT_TIMEOUT_MS)
            device.findObject(By.desc("Settings"))?.click()
            device.waitForIdle()
            device.pressBack()
        }
    }

    @Test
    fun openSimpleEditor() {
        // JUnit4 doesn't guarantee method execution order within a class, so this can't rely on
        // firstLaunch()'s own clearAppData() having already run first — clear it here too, both
        // for a clean, reproducible starting point and because ensureImportedDeckExists()'s
        // empty-vs-populated-deck-list duality handling (same as ensureSampleDeckExists()'s
        // "Create Deck" duality) specifically needs to be exercised via the actual empty-state
        // "Import Decks" entry point, not just assumed to work the same as the FAB-menu one.
        clearAppData()

        // A real, many-card deck (imported from a CSV), not the small one subsequentLaunch()
        // uses — see IMPORTED_DECK_NAME's comment for why a 1-card deck is the wrong fixture for
        // profiling this screen.
        ensureImportedDeckExists()

        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            maxIterations = 5,
            // Not a startup path — this happens well after startActivityAndWait() settles, so
            // it belongs in the baseline profile but not the startup-profile subset.
            includeInStartupProfile = false
        ) {
            pressHome()
            startActivityAndWait()

            // Every step below asserts instead of using the unchecked wait()/findObject()?.click()
            // pattern the rest of this file uses — that pattern silently no-ops on a missing
            // element (wait()'s Boolean result goes unread, ?.click() on a null findObject() is a
            // no-op), so a selector miss or a slow cold start makes the whole test body do nothing
            // while still reporting as passed. Confirmed the hard way: a prior run "passed" with 0
            // failures while never actually opening the editor. These checks make that loud instead
            // of silent.
            check(device.wait(Until.hasObject(By.text(IMPORTED_DECK_NAME)), WAIT_TIMEOUT_MS)) {
                "Imported deck \"$IMPORTED_DECK_NAME\" never appeared on the deck list"
            }
            val editButton = device.findObject(By.desc("Edit"))
            checkNotNull(editButton) { "No \"Edit\" icon found on the deck list" }
            editButton.click()

            // Edit icon's route depends on the alwaysOpenBulkEditor preference, which defaults
            // to false — so this should be Simple Editor specifically, not Bulk Editor. "Add Card"
            // is a safe positive signal for that: confirmed it isn't used anywhere in
            // DeckEditorScreen.kt (Bulk Editor). (A "not on Bulk Editor" negative check isn't
            // viable here — Simple Editor's own toolbar has a button literally labeled "Bulk
            // Editor" to switch to it, so checking that text is absent would misfire.)
            check(device.wait(Until.hasObject(By.desc("Add Card")), WAIT_TIMEOUT_MS)) {
                "Simple Editor's \"Add Card\" button never appeared after tapping Edit"
            }
            device.waitForIdle()

            device.pressBack()
            check(device.wait(Until.hasObject(By.text(IMPORTED_DECK_NAME)), WAIT_TIMEOUT_MS)) {
                "Didn't return to the deck list after pressing back from the editor"
            }
        }
    }

    /** Runs `pm clear` and blocks until it has actually finished, to avoid racing the next launch. */
    private fun clearAppData() {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm clear $PACKAGE_NAME")
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    /**
     * Launches the app once (outside profile collection) to seed one deck, if none exists yet.
     *
     * Every wait()/findObject() here asserts — confirmed the hard way that the previous unchecked
     * version (device.wait()'s Boolean result never read, findObject()?.click() a silent no-op on
     * a miss) could fail to create the deck without raising anything, leaving every caller's own
     * "wait for the deck" check to fail downstream with no indication this was where it actually
     * went wrong.
     */
    private fun ensureSampleDeckExists(deckName: String = SAMPLE_DECK_NAME) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // A screenshot taken right after a failed run showed the launcher's app drawer, not
        // Studiare at all — context.startActivity() was being silently dropped by Android's
        // background-activity-launch restrictions (the instrumentation process calling it isn't
        // itself in the foreground right after a prior pressHome()).
        //
        // Instrumentation.startActivitySync() (tried next) isn't the fix either: it requires the
        // launched activity to be in the SAME process as the instrumentation, and threw
        // "Intent in process net.ericclark.studiare.baselineprofile resolved to different process
        // net.ericclark.studiare" — this is a cross-process setup (the baseline profile test APK
        // instruments a separate target app), unlike a self-instrumenting test.
        //
        // `am start` via the shell is what actually works for this architecture — it's what
        // MacrobenchmarkScope.startActivityAndWait() itself uses under the hood (used inside the
        // profiled collect{} block below; this helper runs outside that, so it needs its own
        // equivalent). Shell-initiated launches are privileged and bypass the in-app restriction.
        val launchPfd = instrumentation.uiAutomation.executeShellCommand(
            "am start -n $PACKAGE_NAME/net.ericclark.studiare.MainActivity"
        )
        ParcelFileDescriptor.AutoCloseInputStream(launchPfd).use { it.readBytes() }

        val device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation)

        // A fresh/cleared install shows the first-launch tutorial overlay (TutorialDialog.kt) on
        // top of the deck list, blocking the real chrome behind it ("Create Deck" isn't a
        // reliable readiness signal by itself either — confirmed it only renders as visible text,
        // and only in the empty-deck-list state at that).
        //
        // A one-shot "wait briefly for Skip, then wait for Home" (what this used to be) races a
        // cold, not-yet-AOT-compiled release-type start: if the tutorial's first frame rendered
        // AFTER that short Skip-check window already gave up, it was never dismissed and then
        // blocked the readiness check for the rest of the timeout. Polling instead so the
        // tutorial gets dismissed no matter when it actually shows up.
        //
        // "Home" on the bottom dock (tried first) looked like a safe state-independent signal but
        // wasn't: an in-test accessibility-tree dump (logged via Log.d — device.dumpWindowHierarchy
        // to a File hit the same scoped-storage failure as the screenshot above, and the `shell
        // uiautomator dump` CLI conflicts with this test's own active UiAutomation connection, so
        // neither file-based approach worked) proved "Home" has no content-desc in the tree at all
        // once it's the CURRENTLY SELECTED dock item — Material3's NavigationBarItem evidently
        // represents the selected/tab state differently (merged with role/selected semantics)
        // than an unselected item's ("Settings" was present as plain "Settings" in the same dump).
        // "All Decks" (the app bar title, confirmed present in that same dump) doesn't have this
        // problem — it's plain text, not a stateful nav item.
        val deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS
        var decksScreenReady = false
        while (System.currentTimeMillis() < deadline) {
            if (device.hasObject(By.text("All Decks"))) {
                decksScreenReady = true
                break
            }
            if (device.hasObject(By.desc("Skip"))) {
                device.findObject(By.desc("Skip"))?.click()
                device.waitForIdle()
            }
            Thread.sleep(200)
        }
        if (!decksScreenReady) captureFailureDiagnostics(instrumentation, device, "decksScreenReady")
        check(decksScreenReady) {
            "Decks screen never appeared after launching the app (\"All Decks\" title not found)"
        }

        if (!device.hasObject(By.text(deckName))) {
            // "Create Deck" only ever exists as visible text, never as a contentDescription —
            // confirmed by reading DecksScreen.kt directly (grepped for it as a desc anywhere in
            // the app: zero matches). On an empty deck list it's a direct full-width button; once
            // decks exist, it's a FloatingActionButtonMenuItem hidden behind the FAB toggle
            // (desc "Deck Actions") until that's expanded. Only one of these two can be on screen
            // at a time, so opening the FAB toggle first is a harmless no-op when the empty-state
            // button is already showing directly.
            if (device.hasObject(By.desc("Deck Actions"))) {
                device.findObject(By.desc("Deck Actions"))?.click()
                device.waitForIdle()
            }
            val createDeckButton = device.wait(Until.findObject(By.text("Create Deck")), WAIT_TIMEOUT_MS)
            checkNotNull(createDeckButton) { "\"Create Deck\" button/menu item never appeared" }
            createDeckButton.click()

            // Both paths above navigate into the deck editor for a brand-new deck, which
            // auto-opens DeckSettingsDialog (DeckEditorSettings.kt: showSettingsDialog starts
            // true when deckWithCards == null) prompting for a name — not an inline dialog on the
            // Decks screen. Its save button reads "Save & Create" specifically for a new deck
            // (R.string.save_and_create), not "Save".
            val nameField = device.wait(Until.findObject(By.clazz("android.widget.EditText")), WAIT_TIMEOUT_MS)
            checkNotNull(nameField) { "Deck name field never appeared in the new-deck dialog" }
            nameField.text = deckName
            device.waitForIdle()

            val saveButton = device.findObject(By.text("Save & Create"))
            checkNotNull(saveButton) { "\"Save & Create\" button not found in the new-deck dialog" }
            saveButton.click()
            device.waitForIdle()

            // Saving the deck leaves us inside the Bulk Editor for it, with one auto-created
            // blank card (empty Front/Back fields) — a deck needs at least one filled-in card to
            // actually save (confirmed directly, not assumed), and that blank card's own
            // "Unsaved Changes" guard intercepts a bare pressBack() with a Discard/Save dialog
            // instead of navigating back (confirmed via an in-test screenshot after this exact
            // step first failed). Fill it in for real instead of discarding it.
            val cardFields = device.findObjects(By.clazz("android.widget.EditText"))
            check(cardFields.size >= 2) {
                "Expected at least 2 text fields (Front/Back) for the new deck's auto-created card, found ${cardFields.size}"
            }
            cardFields[0].text = "Sample Front"
            cardFields[1].text = "Sample Back"
            device.waitForIdle()

            device.pressBack()
            // If the "Unsaved Changes" dialog still appears (e.g. the card content isn't
            // considered committed until here), choose "Save" now that there's real content —
            // unlike the blank-card case, this is worth keeping.
            if (device.wait(Until.hasObject(By.text("Save")), 2_000L)) {
                device.findObject(By.text("Save"))?.click()
                device.waitForIdle()
            }
            val savedDeckVisible = device.wait(Until.hasObject(By.text(deckName)), WAIT_TIMEOUT_MS)
            if (!savedDeckVisible) captureFailureDiagnostics(instrumentation, device, "savedDeckVisible")
            check(savedDeckVisible) {
                "Sample deck \"$deckName\" wasn't on the deck list after saving and backing out"
            }
        }

        device.pressHome()
    }

    /**
     * Launches the app once (outside profile collection) to seed a realistic many-card deck via
     * the app's own "Import Decks" CSV flow, if it doesn't already exist. See
     * [IMPORT_CSV_FILENAME]'s comment for why this replaced filling many cards by hand.
     *
     * Confirmed via direct, interactive exploration (not assumed) before writing this:
     *  - "Import Decks" has the same empty-vs-populated duality as "Create Deck" — a direct
     *    full-width button when the deck list is empty, a FloatingActionButtonMenuItem hidden
     *    behind the FAB toggle (desc "Deck Actions") once decks exist. Tested both paths.
     *  - The system file picker (DocumentsUI) opens straight onto the device's Downloads folder
     *    for this app's importLauncher call (requesting any file type) — no drawer/root
     *    navigation needed to reach the target file.
     *  - A single tap on the target file selects it and returns immediately — no separate "Open"
     *    confirmation button, unlike some picker flows.
     */
    private fun ensureImportedDeckExists() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val launchPfd = instrumentation.uiAutomation.executeShellCommand(
            "am start -n $PACKAGE_NAME/net.ericclark.studiare.MainActivity"
        )
        ParcelFileDescriptor.AutoCloseInputStream(launchPfd).use { it.readBytes() }

        val device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation)

        // Same tutorial-dismiss + readiness poll as ensureSampleDeckExists() — see its own
        // comments for why this is a poll, not a one-shot wait, and why "All Decks" rather than
        // "Home" is the actual readiness signal.
        val deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS
        var decksScreenReady = false
        while (System.currentTimeMillis() < deadline) {
            if (device.hasObject(By.text("All Decks"))) {
                decksScreenReady = true
                break
            }
            if (device.hasObject(By.desc("Skip"))) {
                device.findObject(By.desc("Skip"))?.click()
                device.waitForIdle()
            }
            Thread.sleep(200)
        }
        if (!decksScreenReady) captureFailureDiagnostics(instrumentation, device, "importDecksScreenReady")
        check(decksScreenReady) {
            "Decks screen never appeared after launching the app (\"All Decks\" title not found)"
        }

        if (!device.hasObject(By.text(IMPORTED_DECK_NAME))) {
            if (device.hasObject(By.desc("Deck Actions"))) {
                device.findObject(By.desc("Deck Actions"))?.click()
                device.waitForIdle()
            }
            val importButton = device.wait(Until.findObject(By.text("Import Decks")), WAIT_TIMEOUT_MS)
            checkNotNull(importButton) { "\"Import Decks\" button/menu item never appeared" }
            importButton.click()

            val csvFile = device.wait(Until.findObject(By.text(IMPORT_CSV_FILENAME)), WAIT_TIMEOUT_MS)
            if (csvFile == null) captureFailureDiagnostics(instrumentation, device, "csvFileInPicker")
            checkNotNull(csvFile) { "\"$IMPORT_CSV_FILENAME\" never appeared in the system file picker" }
            csvFile.click()

            val importedDeckVisible = device.wait(Until.hasObject(By.text(IMPORTED_DECK_NAME)), WAIT_TIMEOUT_MS)
            if (!importedDeckVisible) captureFailureDiagnostics(instrumentation, device, "importedDeckVisible")
            check(importedDeckVisible) {
                "Imported deck \"$IMPORTED_DECK_NAME\" wasn't on the deck list after import"
            }
        }

        device.pressHome()
    }

    /**
     * Captures the exact on-screen state at the moment of a failure, for logs pulled after the
     * fact — the app gets uninstalled as cleanup after a failed BaselineProfileRule.collect()
     * attempt, so a screenshot taken after the Gradle invocation returns only ever shows whatever
     * launcher/home-screen state is left afterward, never what was actually on screen at failure
     * time.
     *
     * Screenshot: via `shell screencap`, not device.takeScreenshot(File) — the latter failed
     * silently (unchecked return value) writing directly to /sdcard/ from the instrumentation
     * process, almost certainly scoped storage; shell-invoked commands run with broader
     * privileges and are what every successful manual adb screenshot this session has used.
     *
     * Hierarchy: dumped to an in-memory stream and logged via Log.d (pull with
     * `adb logcat -d | grep BaselineProfileDebug`), not a file — device.dumpWindowHierarchy(File)
     * hit the same scoped-storage failure as the screenshot, and the separate `shell uiautomator
     * dump` CLI silently produced no file at all, because it opens its own UiAutomation
     * connection, which conflicts with this test's already-active one via UiDevice.
     */
    private fun captureFailureDiagnostics(
        instrumentation: android.app.Instrumentation,
        device: androidx.test.uiautomator.UiDevice,
        label: String
    ) {
        val screenshotPfd = instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/baseline_failure_$label.png")
        ParcelFileDescriptor.AutoCloseInputStream(screenshotPfd).use { it.readBytes() }

        val hierarchyOutput = java.io.ByteArrayOutputStream()
        device.dumpWindowHierarchy(hierarchyOutput)
        val hierarchyXml = hierarchyOutput.toString("UTF-8")
        hierarchyXml.chunked(3000).forEachIndexed { i, chunk ->
            android.util.Log.d("BaselineProfileDebug", "[$label] hierarchy[$i]: $chunk")
        }
    }
}
