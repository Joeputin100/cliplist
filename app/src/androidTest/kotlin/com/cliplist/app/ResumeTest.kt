package com.cliplist.app

import android.Manifest
import android.app.Application
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.cliplist.app.workflow.GenerateUiState
import com.cliplist.app.workflow.ScanViewModel
import com.cliplist.app.workflow.SelectedFolder
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * "Resume when foregrounded again" — a build started in one ViewModel must surface through a
 * FRESH ViewModel (simulating the app being reopened / its process recreated).
 *
 * True process-death can't be driven from an in-process instrumented test (the test dies with the
 * process), so this exercises the durable mechanism that makes resume work: the build runs as
 * WorkManager *unique persisted* work and writes its result to a file, which any new observer
 * (here, a second ScanViewModel) reads via getWorkInfosForUniqueWorkFlow.
 */
@RunWith(AndroidJUnit4::class)
class ResumeTest {
    @get:Rule
    val permission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private lateinit var app: Application
    private lateinit var testDir: File

    @Before
    fun setUp() {
        app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        testDir = File(app.getExternalFilesDir(null), "ResumeTest").apply {
            deleteRecursively()
            mkdirs()
        }
        File(testDir, "01 Song.mp3").writeBytes(TestAudio.mp3Bytes())
        File(testDir, "02 Song.mp3").writeBytes(TestAudio.mp3Bytes())
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun buildStartedInOneViewModel_surfacesInAFreshOne() {
        val instr = InstrumentationRegistry.getInstrumentation()

        // Original session: enqueue the build (unique work).
        instr.runOnMainSync {
            val vm1 = ScanViewModel(app)
            vm1.setFolder(SelectedFolder(Uri.fromFile(testDir), "ResumeTest", isRemovable = false))
            vm1.generate()
        }

        // Reopen: a brand-new ViewModel observes the same unique work and should reach Done.
        lateinit var vm2: ScanViewModel
        instr.runOnMainSync { vm2 = ScanViewModel(app) }

        val deadline = System.currentTimeMillis() + 60_000
        var state: GenerateUiState = vm2.generateState.value
        while (System.currentTimeMillis() < deadline) {
            state = vm2.generateState.value
            if (state is GenerateUiState.Done || state is GenerateUiState.Error) break
            Thread.sleep(250)
        }

        assertTrue("expected Done, was $state", state is GenerateUiState.Done)
        assertTrue("expected all playlists to succeed", (state as GenerateUiState.Done).result.allSucceeded)
    }
}
