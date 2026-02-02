package es.unizar.urlshortener.infrastructure.delivery

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import es.unizar.urlshortener.core.ReachabilityCheckResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*

class UrlReachabilityServiceImplTest {

    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var valueOps: ValueOperations<String, String>
    private lateinit var httpClient: HttpClient
    private lateinit var objectMapper: ObjectMapper
    private lateinit var reachabilityService: UrlReachabilityServiceImpl

    @BeforeEach
    fun setUp() {
        redisTemplate = mock()
        valueOps = mock()
        httpClient = mock()
        objectMapper = ObjectMapper().apply {
            registerModule(kotlinModule())
            registerModule(JavaTimeModule())
        }

        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)

        reachabilityService = UrlReachabilityServiceImpl(
            timeoutMillis = 5000,
            cacheEnabled = true,
            cacheTtlMinutes = 10,
            redisTemplate = redisTemplate,
            objectMapper = objectMapper
        ).apply {
            val httpClientField = this::class.java.getDeclaredField("httpClient")
            httpClientField.isAccessible = true
            httpClientField.set(this, httpClient)
        }
    }

    @Test
    fun `checkReachability should return cached result when available`() {
        val url = "http://example.com"
        val cachedResult = ReachabilityCheckResult(
            isReachable = true,
            statusCode = 200,
            responseTimeMs = 150,
            contentType = "text/html"
        )
        
        val cachedJson = objectMapper.writeValueAsString(cachedResult)
        
        whenever(valueOps.get(anyString())).thenReturn(cachedJson)

        val result = reachabilityService.checkReachability(url)

        assertTrue(result.isReachable)
        assertEquals(200, result.statusCode)
        assertEquals(150, result.responseTimeMs)
        assertEquals("text/html", result.contentType)
        
        verify(httpClient, never()).send(
            any<HttpRequest>(),
            any<HttpResponse.BodyHandler<Void>>()
        )
    }

    @Test
    fun `checkReachability should perform HTTP check and cache result when not in cache`() {
        val url = "http://example.com"
        
        whenever(valueOps.get(anyString())).thenReturn(null)
        
        val httpResponse = mock<HttpResponse<Void>>()
        val headers = HttpHeaders.of(
            mapOf("Content-Type" to listOf("text/html")),
            { _, _ -> true }
        )
        
        whenever(httpResponse.statusCode()).thenReturn(200)
        whenever(httpResponse.headers()).thenReturn(headers)
        
        whenever(
            httpClient.send(
                any<HttpRequest>(),
                any<HttpResponse.BodyHandler<Void>>()
            )
        ).thenReturn(httpResponse)

        val result = reachabilityService.checkReachability(url)

        assertTrue(result.isReachable)
        assertEquals(200, result.statusCode)
        assertEquals("text/html", result.contentType)
        
        verify(valueOps).set(anyString(), anyString(), any<Duration>())
    }

    @Test
    fun `checkReachability should handle cache deserialization errors gracefully`() {
        val url = "http://example.com"
        
        whenever(valueOps.get(anyString())).thenReturn("invalid json")
        
        val httpResponse = mock<HttpResponse<Void>>()
        val headers = HttpHeaders.of(
            mapOf("Content-Type" to listOf("text/html")),
            { _, _ -> true }
        )
        
        whenever(httpResponse.statusCode()).thenReturn(200)
        whenever(httpResponse.headers()).thenReturn(headers)
        
        whenever(
            httpClient.send(
                any<HttpRequest>(),
                any<HttpResponse.BodyHandler<Void>>()
            )
        ).thenReturn(httpResponse)

        val result = reachabilityService.checkReachability(url)

        assertTrue(result.isReachable)
        
        verify(redisTemplate).delete(anyString())
        verify(valueOps).set(anyString(), anyString(), any<Duration>())
    }

    @Test
    fun `checkReachability should work when cache is disabled`() {
        val reachabilityServiceWithoutCache = UrlReachabilityServiceImpl(
            timeoutMillis = 5000,
            cacheEnabled = false,
            cacheTtlMinutes = 10,
            redisTemplate = redisTemplate,
            objectMapper = objectMapper
        ).apply {
            val httpClientField = this::class.java.getDeclaredField("httpClient")
            httpClientField.isAccessible = true
            httpClientField.set(this, httpClient)
        }
        
        val url = "http://example.com"
        
        val httpResponse = mock<HttpResponse<Void>>()
        val headers = HttpHeaders.of(
            mapOf("Content-Type" to listOf("text/html")),
            { _, _ -> true }
        )
        
        whenever(httpResponse.statusCode()).thenReturn(200)
        whenever(httpResponse.headers()).thenReturn(headers)
        
        whenever(
            httpClient.send(
                any<HttpRequest>(),
                any<HttpResponse.BodyHandler<Void>>()
            )
        ).thenReturn(httpResponse)

        val result = reachabilityServiceWithoutCache.checkReachability(url)

        assertTrue(result.isReachable)
        
        verify(redisTemplate, never()).opsForValue()
        verify(valueOps, never()).get(anyString())
        verify(valueOps, never()).set(anyString(), anyString(), any<Duration>())
    }

    @Test
    fun `checkReachability should use fallback to GET when HEAD returns 405`() {
        val url = "http://example.com"
        
        whenever(valueOps.get(anyString())).thenReturn(null)
        
        val headResponse = mock<HttpResponse<Void>>()
        val headHeaders = HttpHeaders.of(
            emptyMap(),
            { _, _ -> true }
        )
        
        whenever(headResponse.statusCode()).thenReturn(405)
        whenever(headResponse.headers()).thenReturn(headHeaders)
        
        val getResponse = mock<HttpResponse<Void>>()
        val getHeaders = HttpHeaders.of(
            mapOf("Content-Type" to listOf("text/html")),
            { _, _ -> true }
        )
        
        whenever(getResponse.statusCode()).thenReturn(200)
        whenever(getResponse.headers()).thenReturn(getHeaders)
        
        whenever(
            httpClient.send(
                any<HttpRequest>(),
                any<HttpResponse.BodyHandler<Void>>()
            )
        ).thenReturn(headResponse)  
            .thenReturn(getResponse)  

        val result = reachabilityService.checkReachability(url)

        assertTrue(result.isReachable)
        assertEquals(200, result.statusCode)
        
        verify(httpClient, times(2)).send(
            any<HttpRequest>(),
            any<HttpResponse.BodyHandler<Void>>()
        )
    }

    @Test
    fun `checkReachability should handle timeout correctly`() {
        val url = "http://example.com"
        
        whenever(valueOps.get(anyString())).thenReturn(null)
        
        whenever(
            httpClient.send(
                any<HttpRequest>(),
                any<HttpResponse.BodyHandler<Void>>()
            )
        ).thenThrow(java.net.http.HttpTimeoutException("Timeout"))

        val result = reachabilityService.checkReachability(url)

        assertFalse(result.isReachable)
        assertEquals("TIMEOUT", result.errorType)
    }

    @Test
    fun `checkReachability should handle DNS errors correctly`() {
        val url = "http://example.com"
        
        whenever(valueOps.get(anyString())).thenReturn(null)
        
        whenever(
            httpClient.send(
                any<HttpRequest>(),
                any<HttpResponse.BodyHandler<Void>>()
            )
        ).thenThrow(java.net.UnknownHostException("Unknown host"))

        val result = reachabilityService.checkReachability(url)

        assertFalse(result.isReachable)
        assertEquals("DNS_ERROR", result.errorType)
    }

    @Test
    fun `checkReachability should cache unreachable results too`() {
        val url = "http://unreachable.example.com"
        
        whenever(valueOps.get(anyString())).thenReturn(null)
        
        val httpResponse = mock<HttpResponse<Void>>()
        val headers = HttpHeaders.of(
            emptyMap(),
            { _, _ -> true }
        )
        
        whenever(httpResponse.statusCode()).thenReturn(404)
        whenever(httpResponse.headers()).thenReturn(headers)
        
        whenever(
            httpClient.send(
                any<HttpRequest>(),
                any<HttpResponse.BodyHandler<Void>>()
            )
        ).thenReturn(httpResponse)

        val result = reachabilityService.checkReachability(url)

        assertFalse(result.isReachable)
        assertEquals(404, result.statusCode)
        
        verify(valueOps).set(anyString(), anyString(), any<Duration>())
    }

    @Test
    fun `checkReachability should handle network errors correctly`() {
        val url = "http://example.com"
        
        whenever(valueOps.get(anyString())).thenReturn(null)
        
        whenever(
            httpClient.send(
                any<HttpRequest>(),
                any<HttpResponse.BodyHandler<Void>>()
            )
        ).thenThrow(RuntimeException("Network error"))

        val result = reachabilityService.checkReachability(url)

        assertFalse(result.isReachable)
        assertEquals("NETWORK_ERROR", result.errorType)
    }
}