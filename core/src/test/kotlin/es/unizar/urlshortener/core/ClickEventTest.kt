package es.unizar.urlshortener.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ClickEventTest {

    @Test
    fun `equals and hashCode and toString and copy behave as expected`() {
        val now = Instant.now()
        val a = ClickEvent("key", "1.2.3.4", "ref", "Chrome", "Linux", now, "ES")
        val b = ClickEvent("key", "1.2.3.4", "ref", "Chrome", "Linux", now, "ES")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a.toString().contains("shortUrlId='key'"))

        val c = a.copy(ip = "5.6.7.8")
        assertEquals("5.6.7.8", c.ip)
        assertNotEquals(a, c)
    }
}

