package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.infrastructure.repositories.StatsRepository
import es.unizar.urlshortener.infrastructure.repositories.UrlAggregates
import es.unizar.urlshortener.infrastructure.repositories.SystemAggregates
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class AnalyticsServiceTest {

    @Test
    fun `getUrlStats returns aggregated stats`() {
        val repo = Mockito.mock(StatsRepository::class.java)
        val countries = mapOf("ES" to 7L, "US" to 3L)
        val cities = mapOf("Madrid|ES" to 5L, "Barcelona|ES" to 2L, "NY|US" to 3L)
        Mockito.`when`(repo.getUrlAggregates("abc")).thenReturn(UrlAggregates(10L, countries, cities))

        val svc = AnalyticsService(repo)
        val res = svc.getUrlStats("abc")

        assertEquals(10L, res.totalClicks)
        assertEquals(2, res.countries.size)
        assertTrue(res.countries.any { it.countryCode == "ES" && it.clicks == 7L })
        // Ensure Madrid entry exists with expected clicks; country name may vary by locale
        assertTrue(res.cities.any { it.city == "Madrid" && it.clicks == 5L })
    }

    @Test
    fun `getSystemStats returns aggregated stats`() {
        val repo = Mockito.mock(StatsRepository::class.java)
        val countries = mapOf("XX" to 1L)
        val cities = mapOf("UnknownCity" to 1L)
        Mockito.`when`(repo.getSystemAggregates()).thenReturn(SystemAggregates(1L, countries, cities))

        val svc = AnalyticsService(repo)
        val res = svc.getSystemStats()

        assertEquals(1L, res.totalClicks)
        assertEquals(1, res.countries.size)
        assertEquals(1, res.cities.size)
    }
}
