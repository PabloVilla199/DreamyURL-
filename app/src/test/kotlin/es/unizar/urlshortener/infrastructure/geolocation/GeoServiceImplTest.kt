package es.unizar.urlshortener.infrastructure.geolocation

import es.unizar.urlshortener.core.ClickEvent
import es.unizar.urlshortener.core.ClickInfo
import es.unizar.urlshortener.infrastructure.repositories.ClickRepository
import es.unizar.urlshortener.infrastructure.repositories.StatsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant

class GeoServiceImplTest {
    private val webClient: WebClient = mock()
    private val redisTemplate: StringRedisTemplate = mock()
    private val valueOps: ValueOperations<String, String> = mock()
    private val clickRepository: ClickRepository = mock()
    private val statsRepository: StatsRepository = mock()

    private val primaryBaseUrl = "http://primary"
    private val providerPath = "/{ip}/json"
    private val apiKey = "secret"
    private val timeoutMs = 500L
    private val cacheTtlDays = 1L
    private val unknownTtlMinutes = 60L
    private val fallbackBaseUrl = "http://fallback"
    private val fallbackPath = "/json/{ip}"

    private lateinit var geoService: GeoServiceImpl

    @BeforeEach
    fun setUp() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)

        geoService =
            GeoServiceImpl(
                webClient = webClient,
                redisTemplate = redisTemplate,
                clickRepository = clickRepository,
                statsRepository = statsRepository,
                primaryBaseUrl = primaryBaseUrl,
                providerPath = providerPath,
                apiKey = apiKey,
                timeoutMs = timeoutMs,
                cacheTtlDays = cacheTtlDays,
                unknownTtlMinutes = unknownTtlMinutes,
                fallbackBaseUrl = fallbackBaseUrl,
                fallbackPath = fallbackPath,
            )
    }

    @Test
    fun `processClick ignores private IPs and saves as Unknown`() {
        val privateIps =
            listOf(
                "127.0.0.1",
                "10.0.0.5",
                "192.168.1.1",
                "172.16.0.1",
                "172.31.255.255",
            )

        privateIps.forEach { ip ->
            val event = createEvent(ip)
            geoService.processClick(event)

            verify(valueOps, never()).get(any<String>())
            verify(webClient, never()).get()

            val captor = argumentCaptor<ClickInfo>()
            verify(clickRepository).save(captor.capture())
            val saved = captor.firstValue
            assertEquals(ip, saved.ip)
            assertEquals("Unknown", saved.country)

            reset(clickRepository, valueOps, webClient)
        }
    }

    @Test
    fun `processClick handles blank IP`() {
        val event = createEvent("")
        geoService.processClick(event)

        val captor = argumentCaptor<ClickInfo>()
        verify(clickRepository).save(captor.capture())
        val saved = captor.firstValue
        assertEquals("Unknown", saved.country)
    }

    @Test
    fun `processClick handles 172 public IP correctly`() {
        val publicIp = "172.32.0.1"
        val event = createEvent(publicIp)

        whenever(valueOps.get(any<String>())).thenReturn(null)
        mockWebClientError()

        geoService.processClick(event)

        verify(valueOps, atLeastOnce()).get(ArgumentMatchers.contains(publicIp))
    }

    @Test
    fun `resolveDetails uses Details Cache (JSON) when available`() {
        val ip = "1.1.1.1"
        val json =
            """
            { "countryCode": "CA",
              "city": "Toronto",
              "region": "Ontario" }
            """.trimIndent()

        whenever(valueOps.get("geo:details:$ip")).thenReturn(json)

        geoService.processClick(createEvent(ip))

        verify(webClient, never()).get()

        verify(statsRepository).incrUrlCity(any(), eq("Toronto"), eq("CA"))

        val captor = argumentCaptor<ClickInfo>()
        verify(clickRepository).save(captor.capture())
        val saved = captor.firstValue
        assertEquals("CA", saved.country)
    }

    @Test
    fun `resolveDetails handles Corrupted JSON in cache by falling back`() {
        val ip = "2.2.2.2"
        whenever(valueOps.get("geo:details:$ip")).thenReturn("{ invalid json ...")
        whenever(valueOps.get("geo:$ip")).thenReturn(null)
        mockWebClientError()

        geoService.processClick(createEvent(ip))

        verify(valueOps).get("geo:details:$ip")

        val captor = argumentCaptor<ClickInfo>()
        verify(clickRepository).save(captor.capture())
        val saved = captor.firstValue
        assertEquals("Unknown", saved.country)
    }

    @Test
    fun `resolveDetails uses Legacy Cache if Details Cache miss`() {
        val ip = "3.3.3.3"
        whenever(valueOps.get("geo:details:$ip")).thenReturn(null)
        whenever(valueOps.get("geo:$ip")).thenReturn("DE")

        geoService.processClick(createEvent(ip))

        verify(webClient, never()).get()

        val captor = argumentCaptor<ClickInfo>()
        verify(clickRepository).save(captor.capture())
        val saved = captor.firstValue
        assertEquals("DE", saved.country)
    }

    @Test
    fun `resolveDetails uses Primary Provider on cache miss`() {
        val ip = "4.4.4.4"
        whenever(valueOps.get(any<String>())).thenReturn(null)

        val response =
            GeoServiceImpl.IpApiCoResponse(
                countryCode = "IT",
                city = "Rome",
            )
        mockWebClientSuccess(response, GeoServiceImpl.IpApiCoResponse::class.java)

        geoService.processClick(createEvent(ip))

        verify(valueOps).set(
            eq("geo:details:$ip"),
            ArgumentMatchers.contains("Rome"),
            any<Duration>(),
        )
        verify(valueOps).set(
            eq("geo:$ip"),
            eq("IT"),
            any<Duration>(),
        )

        verify(statsRepository).incrUrlCountry(any(), eq("IT"))
    }

    @Test
    fun `resolveDetails uses Fallback Provider when Primary fails`() {
        val ip = "5.5.5.5"
        whenever(valueOps.get(any<String>())).thenReturn(null)

        val requestUriSpec = mock<WebClient.RequestHeadersUriSpec<*>>()
        val requestHeadersSpec = mock<WebClient.RequestHeadersSpec<*>>()
        val responseSpec = mock<WebClient.ResponseSpec>()

        whenever(webClient.get()).thenReturn(requestUriSpec)
        whenever(requestUriSpec.uri(any<String>())).thenReturn(requestHeadersSpec)
        whenever(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec)
        whenever(requestHeadersSpec.retrieve()).thenReturn(responseSpec)

        whenever(responseSpec.bodyToMono(GeoServiceImpl.IpApiCoResponse::class.java))
            .thenReturn(Mono.error(RuntimeException("Primary failed")))

        whenever(responseSpec.bodyToMono(GeoServiceImpl.IpApiComResponse::class.java))
            .thenReturn(
                Mono.just(
                    GeoServiceImpl.IpApiComResponse(
                        countryCode = "US",
                        city = "New York",
                    ),
                ),
            )

        geoService.processClick(createEvent(ip))

        val captor = argumentCaptor<ClickInfo>()
        verify(clickRepository).save(captor.capture())
        val saved = captor.firstValue
        assertEquals("US", saved.country)

        verify(valueOps).set(
            eq("geo:$ip"),
            eq("US"),
            any<Duration>(),
        )
    }

    @Test
    fun `resolveDetails caches Unknown when BOTH providers fail`() {
        val ip = "6.6.6.6"
        whenever(valueOps.get(any<String>())).thenReturn(null)

        mockWebClientError()

        geoService.processClick(createEvent(ip))

        verify(valueOps).set(
            eq("geo:$ip"),
            eq("Unknown"),
            any<Duration>(),
        )
        verify(valueOps).set(
            eq("geo:details:$ip"),
            ArgumentMatchers.contains("Unknown"),
            any<Duration>(),
        )
    }

    @Test
    fun `lookupIp interface method works`() {
        val ip = "8.8.8.8"
        whenever(valueOps.get("geo:$ip")).thenReturn("ES")

        val result = geoService.lookupIp(ip)

        assertEquals("ES", result.geolocation.countryCode)
    }

    @Test
    fun `Data Classes mapping coverage`() {
        val coResponse =
            GeoServiceImpl.IpApiCoResponse(
                countryCode = "ES",
                countryName = "Spain",
                region = "Reg",
                city = "City",
                isp = "ISP",
                organization = "Org",
            )
        val coDetails = coResponse.toGeoDetails()
        assertEquals("ES", coDetails.countryCode)
        assertEquals("ISP", coDetails.isp)

        val comResponse =
            GeoServiceImpl.IpApiComResponse(
                countryCode = "FR",
                countryName = "France",
                isp = "ISP2",
                organization = "Org2",
            )
        val comDetails = comResponse.toGeoDetails()
        assertEquals("FR", comDetails.countryCode)
        assertEquals("ISP2", comDetails.isp)
    }

    private fun createEvent(ip: String?): ClickEvent {
        return ClickEvent(
            shortUrlId = "id",
            ip = ip,
            timestamp = Instant.now(),
            referrer = null,
            browser = null,
            platform = null,
            country = null,
        )
    }

    private fun <T : Any> mockWebClientSuccess(
        response: T,
        clazz: Class<T>,
    ) {
        val requestUriSpec = mock<WebClient.RequestHeadersUriSpec<*>>()
        val requestHeadersSpec = mock<WebClient.RequestHeadersSpec<*>>()
        val responseSpec = mock<WebClient.ResponseSpec>()

        whenever(webClient.get()).thenReturn(requestUriSpec)
        whenever(requestUriSpec.uri(any<String>())).thenReturn(requestHeadersSpec)
        whenever(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec)
        whenever(requestHeadersSpec.retrieve()).thenReturn(responseSpec)

        whenever(responseSpec.bodyToMono(clazz)).thenReturn(Mono.just(response))
    }

    private fun mockWebClientError() {
        val requestUriSpec = mock<WebClient.RequestHeadersUriSpec<*>>()
        val requestHeadersSpec = mock<WebClient.RequestHeadersSpec<*>>()
        val responseSpec = mock<WebClient.ResponseSpec>()

        whenever(webClient.get()).thenReturn(requestUriSpec)
        whenever(requestUriSpec.uri(any<String>())).thenReturn(requestHeadersSpec)
        whenever(requestHeadersSpec.header(any(), any())).thenReturn(requestHeadersSpec)
        whenever(requestHeadersSpec.retrieve()).thenReturn(responseSpec)

        whenever(responseSpec.bodyToMono(GeoServiceImpl.IpApiCoResponse::class.java))
            .thenReturn(Mono.error(RuntimeException("Fail")))

        whenever(responseSpec.bodyToMono(GeoServiceImpl.IpApiComResponse::class.java))
            .thenReturn(Mono.error(RuntimeException("Fail")))
    }
}
