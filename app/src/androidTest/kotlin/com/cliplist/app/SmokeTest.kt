package com.cliplist.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Trivial instrumented test — validates the androidTest build + the FTL `--type instrumentation`
 * pipeline end-to-end before the real UIAutomator flow test (Chunk B) is added.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @Test
    fun targetContext_isThisApp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.cliplist.app", ctx.packageName)
    }
}
