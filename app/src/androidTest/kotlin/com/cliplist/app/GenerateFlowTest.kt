package com.cliplist.app

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end on-device test of the real build flow: pick folder -> scan -> generate -> Results.
 *
 * The SAF picker is stubbed via Espresso-Intents to return a seeded file:// folder, which the app
 * reads through FileStorageVolume (StorageVolumes.forUri). This exercises the WorkManager
 * foreground worker, the progress notification, and the full ViewModel/UI wiring on a real device —
 * the part FTL-Robo can't reach because it can't drive the system folder picker.
 */
@RunWith(AndroidJUnit4::class)
class GenerateFlowTest {

    private val compose = createAndroidComposeRule<MainActivity>()

    // Grant POST_NOTIFICATIONS before the activity launches, so the runtime prompt never appears.
    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS))
        .around(compose)

    private lateinit var testDir: File

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        testDir = File(ctx.getExternalFilesDir(null), "MPCTest").apply {
            deleteRecursively()
            mkdirs()
        }
        File(testDir, "01 Song.mp3").writeText("x")
        File(testDir, "02 Song.mp3").writeText("x")

        Intents.init()
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT_TREE)).respondWith(
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(testDir))),
        )
    }

    @After
    fun tearDown() {
        Intents.release()
        testDir.deleteRecursively()
    }

    private fun present(text: String): Boolean =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun pickFolder_scan_generate_showsResults() {
        // First-run wizard -> dismiss (checkbox stays checked, so this just closes it).
        compose.waitForIdle()
        if (present("Get started")) {
            compose.onNodeWithText("Get started").performClick()
            compose.waitForIdle()
        }

        // Tap the folder card -> stubbed picker returns the seeded file:// folder.
        compose.onNodeWithText("Tap to choose a folder").performClick()
        compose.waitUntil(10_000) { present("MPCTest") }

        // Scan -> navigates to Preview (which has the Generate button).
        compose.onNodeWithText("Scan folder").performClick()
        compose.waitUntil(15_000) { present("Generate playlists") }

        // Generate -> foreground worker runs the build -> Results.
        compose.onNodeWithText("Generate playlists").performClick()
        compose.waitUntil(45_000) {
            present("All playlists created") || present("Finished with some issues")
        }

        compose.onNodeWithText("All playlists created").assertIsDisplayed()
    }
}
