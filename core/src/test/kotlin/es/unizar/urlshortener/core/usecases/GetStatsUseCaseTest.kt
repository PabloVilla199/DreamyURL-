package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.Click
import es.unizar.urlshortener.core.ClickProperties
import es.unizar.urlshortener.core.ClickRepositoryService
import es.unizar.urlshortener.core.IpAddress
import es.unizar.urlshortener.core.UrlHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime

class GetStatsUseCaseTest {

    @Test
    fun `getStats returns empty stats when no clicks exist`() {
        val clickRepository = mock<ClickRepositoryService>()
        val useCase = GetStatsUseCaseImpl(clickRepository)
        val hash = "hash1"

        // findByHash del repositorio sí acepta String según tu implementación, 
        // así que esto está bien.
        whenever(clickRepository.findByHash(hash)).thenReturn(emptyList())

        val stats = useCase.getStats(hash, "http://target.com")

        assertEquals(0L, stats.totalClicks)
        assertEquals(emptyMap<String, Int>(), stats.browserStats)
        assertEquals("http://target.com", stats.target)
    }

    @Test
    fun `getStats aggregates data correctly`() {
        val clickRepository = mock<ClickRepositoryService>()
        val useCase = GetStatsUseCaseImpl(clickRepository)
        val hashString = "hash1"
        val hashVo = UrlHash(hashString) // Envolvemos en Value Object

        // Create dummy clicks usando los Value Objects correctos
        val click1 = Click(
            hash = hashVo,
            properties = ClickProperties(
                browser = "Chrome",
                platform = "Windows",
                country = "ES",
                referrer = "google.com",
                ip = IpAddress("1.1.1.1") // Envolvemos IP
            ),
            created = OffsetDateTime.now()
        )
        
        val click2 = Click(
            hash = hashVo,
            properties = ClickProperties(
                browser = "Chrome",
                platform = "Linux",
                country = "ES",
                referrer = "direct",
                ip = IpAddress("1.1.1.2")
            ),
            created = OffsetDateTime.now()
        )
        
        val click3 = Click(
            hash = hashVo,
            properties = ClickProperties(
                browser = "Firefox",
                platform = "Linux",
                country = "FR",
                referrer = "google.com",
                ip = IpAddress("1.1.1.3")
            ),
            created = OffsetDateTime.now()
        )

        whenever(clickRepository.findByHash(hashString)).thenReturn(listOf(click1, click2, click3))

        val stats = useCase.getStats(hashString, "http://target.com")

        // Assert Total
        assertEquals(3L, stats.totalClicks)

        // Assert Browsers
        assertEquals(2, stats.browserStats["Chrome"])
        assertEquals(1, stats.browserStats["Firefox"])

        // Assert Platforms
        assertEquals(1, stats.platformStats["Windows"])
        assertEquals(2, stats.platformStats["Linux"])

        // Assert Countries
        assertEquals(2, stats.countryStats["ES"])
        assertEquals(1, stats.countryStats["FR"])
        
        // Assert Referrers
        assertEquals(2, stats.referrerStats["google.com"])
        assertEquals(1, stats.referrerStats["direct"])
    }

    @Test
    fun `getStats handles null properties as Unknown or Direct`() {
        val clickRepository = mock<ClickRepositoryService>()
        val useCase = GetStatsUseCaseImpl(clickRepository)
        val hashString = "hashNull"
        val hashVo = UrlHash(hashString)

        // Click with null properties
        val clickNull = Click(
            hash = hashVo,
            properties = ClickProperties(
                browser = null,
                platform = null,
                country = null,
                referrer = null,
                ip = IpAddress("1.1.1.1")
            ),
            created = OffsetDateTime.now()
        )

        whenever(clickRepository.findByHash(hashString)).thenReturn(listOf(clickNull))

        val stats = useCase.getStats(hashString, "http://target.com")

        assertEquals(1, stats.browserStats["Unknown"])
        assertEquals(1, stats.platformStats["Unknown"])
        assertEquals(1, stats.countryStats["Unknown"])
        assertEquals(1, stats.referrerStats["Direct"])
    }
}
