package app.immichshare.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Host normalisation is pure, so it is unit-testable without an emulator —
 * and it is the one setting a self-hoster is most likely to mistype.
 */
class SettingsTest {

    @Test
    fun `trailing slashes are stripped`() {
        assertEquals("https://photos.example.com", normaliseHost("https://photos.example.com///"))
    }

    @Test
    fun `bare host gets https`() {
        assertEquals("https://photos.example.com", normaliseHost("  photos.example.com  "))
    }

    @Test
    fun `explicit scheme is preserved`() {
        assertEquals("http://192.168.1.10:2283", normaliseHost("http://192.168.1.10:2283/"))
    }

    @Test
    fun `empty stays empty`() {
        assertEquals("", normaliseHost("   "))
    }
}
