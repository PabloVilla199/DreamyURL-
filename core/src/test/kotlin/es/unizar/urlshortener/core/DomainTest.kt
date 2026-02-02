package es.unizar.urlshortener.core

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
// REMOVE: import org.junit.jupiter.api.Assertions.assertThrows
// ADD THIS:
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class DomainTest {
    @Test
    fun `UrlHash validations and toString`() {
        val validHash = "someHash"
        assertEquals(validHash, UrlHash(validHash).toString())
        assertEquals(validHash, UrlHash(validHash).value)

        assertThrows<IllegalArgumentException> { UrlHash("   ") }
        
        val longHash = "a".repeat(InputLimits.MAX_KEY_LENGTH + 1)
        assertThrows<IllegalArgumentException> { UrlHash(longHash) }
    }

    @Test
    fun `Url validations`() {
        val validUrl = "http://google.com"
        assertEquals(validUrl, Url(validUrl).toString())

        assertThrows<IllegalArgumentException> { Url("   ") }
        
        val longUrl = "a".repeat(InputLimits.MAX_URL_LENGTH + 1)
        assertThrows<IllegalArgumentException> { Url(longUrl) }
    }

    @Test
    fun `IpAddress validations`() {
        assertEquals("127.0.0.1", IpAddress("127.0.0.1").toString())
        assertThrows<IllegalArgumentException> { IpAddress("   ") }
    }

    @Test
    fun `CountryCode validations`() {
        assertEquals("ES", CountryCode("ES").toString())
        
        assertThrows<IllegalArgumentException> { CountryCode("") }
        assertThrows<IllegalArgumentException> { CountryCode("ESP") }
        assertThrows<IllegalArgumentException> { CountryCode("E") }
    }

    @Test
    fun `Domain Value Objects validations`() {
        assertEquals("Chrome", Browser("Chrome").toString())
        assertThrows<IllegalArgumentException> { Browser("") }

        assertEquals("Linux", Platform("Linux").toString())
        assertThrows<IllegalArgumentException> { Platform("") }

        assertEquals("google.com", Referrer("google.com").toString())
        assertThrows<IllegalArgumentException> { Referrer("") }

        assertEquals("Nike", Sponsor("Nike").toString())
        assertThrows<IllegalArgumentException> { Sponsor("") }

        assertEquals("Alice", Owner("Alice").toString())
        assertThrows<IllegalArgumentException> { Owner("") }
    }

    @Test
    fun `UrlValidationLimitException handles jobId`() {
        val ex1 = UrlValidationLimitException("job-123")
        assertEquals("job-123", ex1.jobId)
        assertTrue(ex1.message!!.contains("job-123"))

        val ex2 = UrlValidationLimitException(null)
        assertNull(ex2.jobId)
        assertFalse(ex2.message!!.contains("jobId="))
    }

    @Test
    fun `InvalidInputException stores properties`() {
        val ex = InvalidInputException("fieldX", "valY")
        assertTrue(ex.message!!.contains("fieldX"))
        assertTrue(ex.message!!.contains("valY"))
        
        val exNull = InvalidInputException("fieldZ", null)
        assertTrue(exNull.message!!.contains("null"))
    }

    @Test
    fun `Specific Exceptions checks`() {
        val ex1 = InvalidUrlException("bad-url")
        assertTrue(ex1.message!!.contains("bad-url"))

        val ex2 = RedirectionNotFound("key1")
        assertTrue(ex2.message!!.contains("key1"))

        val ex3 = UrlNotReachableException("http://unreachable.com")
        assertTrue(ex3.message!!.contains("http://unreachable.com"))
        
        val ex4 = UrlNotSafeException("http://unsafe.com")
        assertTrue(ex4.message!!.contains("http://unsafe.com"))
        
        val ex5 = SafeBrowsingException("http://error.com")
        assertTrue(ex5.message!!.contains("http://error.com"))
        
        val ex6 = UnsupportedQrFormatException("gif", listOf("png"))
        assertTrue(ex6.message!!.contains("gif"))
        
        val ex7 = QrGenerationException("url", "error")
        assertTrue(ex7.message!!.contains("QR generation failed"))
        
        val ex8 = MessagueQueueException()
        assertNotNull(ex8.message)
    }

    @Test
    fun `sanitizeInput logic`() {
        assertEquals("clean", sanitizeInput(" clean ", "field"))
        
        assertThrows<InvalidInputException> { sanitizeInput(null, "field") }
        
        assertThrows<InvalidInputException> { sanitizeInput("   ", "field") }
        
        assertThrows<InvalidInputException> { sanitizeInput("abc", "field", maxLength = 2) }
    }

    @Test
    fun `safeCall functionality`() {
        val result = safeCall { "success" }
        assertEquals("success", result)

        assertThrows<InternalError> {
            safeCall { throw IllegalArgumentException("boom") }
        }

        assertThrows<InvalidUrlException> {
            safeCall(onFailure = { InvalidUrlException("converted") }) {
                throw IllegalArgumentException("boom")
            }
        }
    }

    @Test
    fun `ClickEvent equals and hashCode with nulls`() {
        val now = Instant.now()
        
        val full = ClickEvent("id", "ip", "ref", "brows", "plat", now, "ES")
        val partial = ClickEvent("id", null, null, null, null, now, null)
        val diff = ClickEvent("other", null, null, null, null, now, null)

        assertEquals(partial, partial)
        assertNotEquals(partial, full) 
        assertNotEquals(partial, diff) 
        assertNotEquals(partial, null) 
        assertNotEquals(partial, "string") 

        assertDoesNotThrow { partial.hashCode() }
        assertNotEquals(full.hashCode(), partial.hashCode())
        
        assertTrue(partial.toString().contains("ip=null"))
        
        val copy = partial.copy(ip = "newIP")
        assertEquals("newIP", copy.ip)
        assertEquals(partial.shortUrlId, copy.shortUrlId)
    }
}
