package app.immichshare.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A wrong header here fails at the proxy, before Immich is reached, so the
 * error looks like a bad API key. Worth pinning the exact behaviour.
 */
class AccessHeadersTest {

    @Test
    fun `defaults are the Cloudflare Access header names`() {
        val headers = AccessHeaders()
        assertEquals("CF-Access-Client-Id", headers.idName)
        assertEquals("CF-Access-Client-Secret", headers.secretName)
    }

    @Test
    fun `nothing is sent until values are filled in`() {
        val headers = AccessHeaders()
        assertTrue(headers.asMap().isEmpty())
        assertFalse(headers.isConfigured)
    }

    @Test
    fun `both headers are sent when configured`() {
        val headers = AccessHeaders(idValue = "abc.access", secretValue = "s3cret")

        assertEquals(
            mapOf(
                "CF-Access-Client-Id" to "abc.access",
                "CF-Access-Client-Secret" to "s3cret",
            ),
            headers.asMap(),
        )
        assertTrue(headers.isConfigured)
    }

    @Test
    fun `a blank value omits that header rather than sending it empty`() {
        val headers = AccessHeaders(idValue = "abc.access", secretValue = "   ")

        assertEquals(mapOf("CF-Access-Client-Id" to "abc.access"), headers.asMap())
    }

    @Test
    fun `whitespace around names and values is trimmed`() {
        val headers = AccessHeaders(
            idName = "  X-Token  ",
            idValue = "  abc  ",
            secretName = "",
            secretValue = "ignored-without-a-name",
        )

        assertEquals(mapOf("X-Token" to "abc"), headers.asMap())
    }

    @Test
    fun `both header names are redacted from logs`() {
        val headers = AccessHeaders(idValue = "abc", secretValue = "s3cret")

        assertEquals(
            listOf("CF-Access-Client-Id", "CF-Access-Client-Secret"),
            headers.sensitiveHeaderNames(),
        )
    }

    @Test
    fun `custom header names are supported for other proxies`() {
        val headers = AccessHeaders(
            idName = "X-Auth-User",
            idValue = "remy",
            secretName = "X-Auth-Token",
            secretValue = "t0ken",
        )

        assertEquals(
            mapOf("X-Auth-User" to "remy", "X-Auth-Token" to "t0ken"),
            headers.asMap(),
        )
    }
}
