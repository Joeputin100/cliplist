package com.cliplist.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StorageHeuristicsTest {
    private val external = StorageHeuristics.EXTERNAL_STORAGE_AUTHORITY

    @Test fun `primary internal storage has no removable uuid`() {
        assertNull(StorageHeuristics.removableVolumeUuid(external, "primary:Music"))
        assertNull(StorageHeuristics.removableVolumeUuid(external, "home:Documents"))
    }

    @Test fun `an SD card or USB volume yields its uuid`() {
        assertEquals("1A2B-3C4D", StorageHeuristics.removableVolumeUuid(external, "1A2B-3C4D:Music"))
        assertEquals("0123-4567", StorageHeuristics.removableVolumeUuid(external, "0123-4567:"))
    }

    @Test fun `network or cloud document providers are never removable`() {
        assertNull(StorageHeuristics.removableVolumeUuid("com.google.android.apps.docs.storage", "root:folder1"))
        assertNull(StorageHeuristics.removableVolumeUuid("com.example.smb.provider", "1A2B-3C4D:Music"))
    }

    @Test fun `missing authority is never removable`() {
        assertNull(StorageHeuristics.removableVolumeUuid(null, "1A2B-3C4D:Music"))
    }

    @Test fun `blank volume id is never removable`() {
        assertNull(StorageHeuristics.removableVolumeUuid(external, ":Music"))
    }
}
