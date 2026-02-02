package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.GeoDetails
import es.unizar.urlshortener.core.GeoLookupResult
import es.unizar.urlshortener.core.GeoService
import es.unizar.urlshortener.core.LookupMethod
import es.unizar.urlshortener.infrastructure.delivery.dto.*
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.Instant

@WebMvcTest
@ContextConfiguration(
    classes = [
        GeolocationAnalyticsController::class,
        AnalyticsService::class
    ]
)
class GeolocationAnalyticsControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var analyticsService: AnalyticsService

    @MockitoBean
    private lateinit var geoService: GeoService

    @Test
    fun `GET link geolocation stats returns 200 with data`() {
        // Given
        val shortUrlId = "test123"
        val stats = GeolocationStats(
            totalClicks = 100,
            countries = listOf(
                CountryStats(country = "United States", countryCode = "US", clicks = 60, percentage = 60.0),
                CountryStats(country = "Spain", countryCode = "ES", clicks = 40, percentage = 40.0)
            ),
            cities = listOf(
                CityStats(city = "New York", country = "United States", clicks = 30, percentage = 30.0),
                CityStats(city = "Madrid", country = "Spain", clicks = 25, percentage = 25.0)
            )
        )

        given(analyticsService.getUrlStats(shortUrlId)).willReturn(stats)

        // When/Then
        mockMvc.perform(get("/api/link/$shortUrlId/geolocation"))
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/json"))
            .andExpect(jsonPath("$.geolocationStats.totalClicks").value(100))
            .andExpect(jsonPath("$.geolocationStats.countries[0].country").value("United States"))
            .andExpect(jsonPath("$.geolocationStats.countries[0].countryCode").value("US"))
            .andExpect(jsonPath("$.geolocationStats.countries[0].clicks").value(60))
            .andExpect(jsonPath("$.geolocationStats.countries[0].percentage").value(60.0))
            .andExpect(jsonPath("$.geolocationStats.cities[0].city").value("New York"))
            .andExpect(jsonPath("$.geolocationStats.cities[0].country").value("United States"))
    }

    @Test
    fun `GET link geolocation stats returns 404 when no data exists`() {
        // Given
        val shortUrlId = "nonexistent"
        val emptyStats = GeolocationStats(
            totalClicks = 0,
            countries = emptyList(),
            cities = emptyList()
        )
        given(analyticsService.getUrlStats(shortUrlId)).willReturn(emptyStats)

        // When/Then
        mockMvc.perform(get("/api/link/$shortUrlId/geolocation"))
            .andExpect(status().isNotFound)
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(jsonPath("$.type").value("https://api.urlshortener.unizar.es/problems/no-geolocation-data"))
            .andExpect(jsonPath("$.title").value("No Geolocation Data"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("No geolocation data available"))
    }

    @Test
    fun `GET system geolocation stats returns 200 with data`() {
        // Given
        val stats = GeolocationStats(
            totalClicks = 500,
            countries = listOf(
                CountryStats(country = "United States", countryCode = "US", clicks = 250, percentage = 50.0),
                CountryStats(country = "United Kingdom", countryCode = "GB", clicks = 150, percentage = 30.0)
            ),
            cities = listOf(
                CityStats(city = "London", country = "United Kingdom", clicks = 100, percentage = 20.0)
            )
        )

        given(analyticsService.getSystemStats()).willReturn(stats)

        // When/Then
        mockMvc.perform(get("/api/geolocation/system"))
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/json"))
            .andExpect(jsonPath("$.systemGeolocationStats.totalClicks").value(500))
            .andExpect(jsonPath("$.systemGeolocationStats.countries[0].country").value("United States"))
            .andExpect(jsonPath("$.systemGeolocationStats.countries[0].countryCode").value("US"))
            .andExpect(jsonPath("$.systemGeolocationStats.countries[1].country").value("United Kingdom"))
            .andExpect(jsonPath("$.systemGeolocationStats.cities[0].city").value("London"))
    }

    @Test
    fun `GET system geolocation stats returns 404 when no data exists`() {
        // Given
        val emptyStats = GeolocationStats(
            totalClicks = 0,
            countries = emptyList(),
            cities = emptyList()
        )
        given(analyticsService.getSystemStats()).willReturn(emptyStats)

        // When/Then
        mockMvc.perform(get("/api/geolocation/system"))
            .andExpect(status().isNotFound)
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(jsonPath("$.type").value("https://api.urlshortener.unizar.es/problems/no-geolocation-data"))
            .andExpect(jsonPath("$.title").value("No Geolocation Data"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("No geolocation data available"))
    }

    @Test
    fun `GET geolocation lookup returns 200 with details for valid IP`() {
        // Given
        val ip = "8.8.8.8"
        val details = GeoDetails(
            countryCode = "US",
            countryName = "United States",
            region = "California",
            city = "Mountain View",
            latitude = 37.386,
            longitude = -122.0838,
            timezone = "America/Los_Angeles",
            isp = "Google LLC",
            organization = "Google Public DNS"
        )
        val lookupResult = GeoLookupResult(
            ip = ip,
            geolocation = details,
            lookupMethod = LookupMethod.CACHE,
            lookupTimestamp = Instant.parse("2025-11-14T20:00:00Z")
        )

        given(geoService.lookupIp(ip)).willReturn(lookupResult)

        // When/Then
        mockMvc.perform(get("/api/geolocation/lookup?ip=$ip"))
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/json"))
            .andExpect(jsonPath("$.ip").value(ip))
            .andExpect(jsonPath("$.geolocation.country").value("United States"))
            .andExpect(jsonPath("$.geolocation.countryCode").value("US"))
            .andExpect(jsonPath("$.geolocation.region").value("California"))
            .andExpect(jsonPath("$.geolocation.city").value("Mountain View"))
            .andExpect(jsonPath("$.geolocation.latitude").value(37.386))
            .andExpect(jsonPath("$.geolocation.longitude").value(-122.0838))
            .andExpect(jsonPath("$.geolocation.timezone").value("America/Los_Angeles"))
            .andExpect(jsonPath("$.geolocation.isp").value("Google LLC"))
            .andExpect(jsonPath("$.geolocation.organization").value("Google Public DNS"))
            .andExpect(jsonPath("$.lookupMethod").value("cache"))
            .andExpect(jsonPath("$.lookupTimestamp").value("2025-11-14T20:00:00Z"))
    }

    @Test
    fun `GET geolocation lookup returns 200 with Unknown for private IP`() {
        // Given
        val ip = "192.168.1.1"
        val details = GeoDetails.UNKNOWN
        val lookupResult = GeoLookupResult(
            ip = ip,
            geolocation = details,
            lookupMethod = LookupMethod.UNKNOWN,
            lookupTimestamp = Instant.parse("2025-11-14T20:00:00Z")
        )

        given(geoService.lookupIp(ip)).willReturn(lookupResult)

        // When/Then
        mockMvc.perform(get("/api/geolocation/lookup?ip=$ip"))
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/json"))
            .andExpect(jsonPath("$.ip").value(ip))
            .andExpect(jsonPath("$.geolocation.country").value("Unknown"))
            .andExpect(jsonPath("$.geolocation.countryCode").value("XX"))
            .andExpect(jsonPath("$.lookupMethod").value("unknown"))
    }

    @Test
    fun `GET geolocation lookup returns 400 for invalid IP format`() {
        // When/Then
        mockMvc.perform(get("/api/geolocation/lookup?ip=invalid-ip"))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(jsonPath("$.type").value("https://api.urlshortener.unizar.es/problems/invalid-ip"))
            .andExpect(jsonPath("$.title").value("Invalid Input"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Invalid IP address"))
    }

    @Test
    fun `GET geolocation lookup returns 400 when IP parameter is missing`() {
        // When/Then
        mockMvc.perform(get("/api/geolocation/lookup"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `controller handles empty countries list gracefully`() {
        // Given
        val shortUrlId = "test456"
        val stats = GeolocationStats(
            totalClicks = 10,
            countries = emptyList(),
            cities = emptyList()
        )

        given(analyticsService.getUrlStats(shortUrlId)).willReturn(stats)

        // When/Then
        mockMvc.perform(get("/api/link/$shortUrlId/geolocation"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.geolocationStats.totalClicks").value(10))
            .andExpect(jsonPath("$.geolocationStats.countries").isEmpty)
            .andExpect(jsonPath("$.geolocationStats.cities").isEmpty)
    }

    @Test
    fun `system stats endpoint handles zero clicks gracefully`() {
        // Given
        val stats = GeolocationStats(
            totalClicks = 0,
            countries = emptyList(),
            cities = emptyList()
        )

        given(analyticsService.getSystemStats()).willReturn(stats)

        // When/Then
        mockMvc.perform(get("/api/geolocation/system"))
            .andExpect(status().isNotFound)
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.detail").value("No geolocation data available"))
    }
}
