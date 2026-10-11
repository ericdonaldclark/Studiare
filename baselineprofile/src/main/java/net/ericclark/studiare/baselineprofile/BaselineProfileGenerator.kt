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

/**
 * Generates a baseline profile for Studiare's startup path.
 *
 * Two journeys are collected into the same profile:
 *  - [firstLaunch]: a fresh install with no local data (splash -> empty deck list -> deck
 *    creation flow -> settings), matching what a brand-new user's first cold start touches.
 *  - [subsequentLaunch]: a returning user with an existing deck already in the Room database,
 *    which exercises the deck-list-with-content and deck-open code paths a typical cold start
 *    after day one touches.
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

    /** Runs `pm clear` and blocks until it has actually finished, to avoid racing the next launch. */
    private fun clearAppData() {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm clear $PACKAGE_NAME")
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    /** Launches the app once (outside profile collection) to seed one deck, if none exists yet. */
    private fun ensureSampleDeckExists() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val launchIntent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
            ?: error("Could not find launch intent for $PACKAGE_NAME")
        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launchIntent)

        val device = androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
        device.wait(Until.hasObject(By.desc("Create Deck")), WAIT_TIMEOUT_MS)

        if (!device.hasObject(By.text(SAMPLE_DECK_NAME))) {
            device.findObject(By.desc("Create Deck"))?.click()
            val nameField = device.wait(Until.findObject(By.clazz("android.widget.EditText")), WAIT_TIMEOUT_MS)
            nameField?.text = SAMPLE_DECK_NAME
            device.waitForIdle()
            device.findObject(By.text("Save"))?.click()
            device.waitForIdle()
        }

        device.pressHome()
    }
}
