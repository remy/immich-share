package app.immichshare.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tags go to Immich as `/`-separated paths and are upserted by name, so a
 * stray space or separator silently creates a second, near-identical tag
 * rather than failing — worth normalising before it reaches the server.
 */
class TagNormalisationTest {

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("Beach", normaliseTag("  Beach  "))
    }

    @Test
    fun `each nesting level is trimmed independently`() {
        assertEquals("Holiday/2026", normaliseTag(" Holiday / 2026 "))
    }

    @Test
    fun `empty nesting levels are dropped`() {
        assertEquals("Holiday/2026", normaliseTag("Holiday//2026"))
        assertEquals("Holiday", normaliseTag("/Holiday/"))
    }

    @Test
    fun `a tag of only separators and spaces normalises to empty`() {
        assertEquals("", normaliseTag(" / / "))
        assertEquals("", normaliseTag("   "))
    }

    @Test
    fun `an already-clean tag is unchanged`() {
        assertEquals("Holiday/2026/Beach", normaliseTag("Holiday/2026/Beach"))
    }
}
