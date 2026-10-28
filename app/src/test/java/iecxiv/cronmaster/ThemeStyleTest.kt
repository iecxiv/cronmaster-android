package iecxiv.cronmaster

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeStyleTest {
    @Test fun originalStyleIsTheDefault() {
        assertEquals(ThemeStyle.CRONMASTER, ThemeStyle.fromPreference(null))
    }
    @Test fun restoresMeshFabricChoice() {
        assertEquals(ThemeStyle.MESHFABRIC, ThemeStyle.fromPreference("MESHFABRIC"))
    }
    @Test fun unknownStyleFallsBackToOriginal() {
        assertEquals(ThemeStyle.CRONMASTER, ThemeStyle.fromPreference("unknown"))
    }
}
