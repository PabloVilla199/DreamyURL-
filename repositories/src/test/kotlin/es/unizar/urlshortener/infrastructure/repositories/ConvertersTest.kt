package es.unizar.urlshortener.infrastructure.repositories

import es.unizar.urlshortener.core.Click
import es.unizar.urlshortener.core.ClickProperties
import es.unizar.urlshortener.core.IpAddress
import es.unizar.urlshortener.core.Owner
import es.unizar.urlshortener.core.Redirection
import es.unizar.urlshortener.core.RedirectionType
import es.unizar.urlshortener.core.ShortUrl
import es.unizar.urlshortener.core.ShortUrlProperties
import es.unizar.urlshortener.core.Sponsor
import es.unizar.urlshortener.core.Url
import es.unizar.urlshortener.core.UrlHash
import es.unizar.urlshortener.core.UrlSafety
import es.unizar.urlshortener.core.ValidationResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class ConvertersTest {

    @Test
    fun `ClickEntity toDomain maps correctly`() {
        val entity = ClickEntity(
            id = 1L,
            hash = "hash",
            created = OffsetDateTime.now(),
            ip = "1.2.3.4",
            referrer = "google",
            browser = "Chrome",
            platform = "Linux",
            country = "ES"
        )

        val domain = entity.toDomain()

        assertEquals(entity.hash, domain.hash.value)
        assertEquals(entity.created, domain.created)
        assertEquals(entity.ip, domain.properties.ip?.value)
        assertEquals(entity.referrer, domain.properties.referrer)
        assertEquals(entity.browser, domain.properties.browser)
        assertEquals(entity.platform, domain.properties.platform)
        assertEquals(entity.country, domain.properties.country)
    }

    @Test
    fun `Click toEntity maps correctly`() {
        val domain = Click(
            hash = UrlHash("hash"),
            properties = ClickProperties(
                ip = IpAddress("1.1.1.1"),
                referrer = "ref",
                browser = "b",
                platform = "p",
                country = "US"
            ),
            created = OffsetDateTime.now()
        )

        val entity = domain.toEntity()

        assertEquals(domain.hash.value, entity.hash)
        assertEquals(domain.properties.ip?.value, entity.ip)
        assertEquals("ref", entity.referrer)
    }

    @Test
    fun `ShortUrlEntity toDomain maps safe and reachable`() {
        val entity = ShortUrlEntity(
            hash = "hash",
            target = "http://target.com",
            sponsor = "sponsor",
            created = OffsetDateTime.now(),
            owner = "owner",
            mode = 307,
            safe = true,
            ip = "127.0.0.1",
            country = "ES",
            accessible = true,
            validatedAt = OffsetDateTime.now(),
            validationStatusCode = 200,
            validationResponseTime = "100ms",
            validationContentType = "text/html"
        )

        val domain = entity.toDomain()

        assertEquals(UrlSafety.Safe, domain.properties.safety)
        assertTrue(domain.properties.accessible)
        assertEquals(200, domain.properties.validationResult?.statusCode)
        assertEquals("100ms", domain.properties.validationResult?.responseTime)
    }

    @Test
    fun `ShortUrlEntity toDomain handles unsafe and nulls`() {
        val entity = ShortUrlEntity(
            hash = "hash",
            target = "http://target.com",
            sponsor = null,
            created = OffsetDateTime.now(),
            owner = null,
            mode = 301,
            safe = false, 
            ip = null,
            country = null,
            accessible = false,
            validationStatusCode = null
        )

        val domain = entity.toDomain()

        assertEquals(UrlSafety.Unsafe, domain.properties.safety)
        assertEquals(RedirectionType.Permanent, domain.redirection.type)
        assertNull(domain.properties.sponsor)
        assertNull(domain.properties.owner)
        assertNull(domain.properties.ip)
        assertNull(domain.properties.validationResult)
    }

    @Test
    fun `ShortUrl toEntity maps correctly`() {
        val domain = ShortUrl(
            hash = UrlHash("hash"),
            redirection = Redirection(Url("http://target.com")),
            properties = ShortUrlProperties(
                safety = UrlSafety.Safe,
                owner = Owner("me"),
                sponsor = Sponsor("corp"),
                validationResult = ValidationResult(statusCode = 200)
            )
        )

        val entity = domain.toEntity()

        assertEquals("hash", entity.hash)
        assertTrue(entity.safe)
        assertEquals("me", entity.owner)
        assertEquals(200, entity.validationStatusCode)
    }
}
