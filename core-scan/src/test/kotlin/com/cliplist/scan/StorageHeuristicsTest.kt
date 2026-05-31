package com.cliplist.scan

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StorageHeuristicsTest {
    @Test fun `primary internal storage is not removable`() {
        assertFalse(StorageHeuristics.isRemovableTreeDocumentId("primary:Music"))
        assertFalse(StorageHeuristics.isRemovableTreeDocumentId("home:Documents"))
    }
    @Test fun `a volume uuid (SD card) is removable`() {
        assertTrue(StorageHeuristics.isRemovableTreeDocumentId("1A2B-3C4D:Music"))
        assertTrue(StorageHeuristics.isRemovableTreeDocumentId("0123-4567:"))
    }
}
