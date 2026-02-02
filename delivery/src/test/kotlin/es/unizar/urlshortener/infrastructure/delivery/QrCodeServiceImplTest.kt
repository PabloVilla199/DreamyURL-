package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.QrFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QrCodeServiceImplTest {

    private val service = QrCodeServiceImpl()

    @Test
    fun `generate PNG returns PNG signature`() {
        val bytes = service.generateFor("https://example.com", 200, QrFormat.PNG)
        assertTrue(bytes.isNotEmpty())
        // PNG files start with 89 50 4E 47
        assertTrue(bytes.size >= 4)
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals(0x50.toByte(), bytes[1])
        assertEquals(0x4E.toByte(), bytes[2])
        assertEquals(0x47.toByte(), bytes[3])
    }

    @Test
    fun `generate JPEG returns JPEG signature`() {
        val bytes = service.generateFor("https://example.com", 150, QrFormat.JPEG)
        assertTrue(bytes.isNotEmpty())
        // JPEG files start with FF D8
        assertTrue(bytes.size >= 2)
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
    }

    @Test
    fun `generate SVG contains svg root`() {
        val bytes = service.generateFor("https://example.com", 120, QrFormat.SVG)
        val s = String(bytes, Charsets.UTF_8)
        assertTrue(s.startsWith("<?xml") || s.contains("<svg"))
        assertTrue(s.contains("<rect"))
    }
}
