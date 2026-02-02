package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.ClickEvent
import es.unizar.urlshortener.core.Redirection
import es.unizar.urlshortener.core.Url
import es.unizar.urlshortener.core.usecases.RedirectUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.util.ReflectionTestUtils
import org.junit.jupiter.api.Assertions.assertNull

class UrlShortenerControllerIpTest {

    private val redirectUseCase = mock(RedirectUseCase::class.java)
    private val applicationEventPublisher = mock(ApplicationEventPublisher::class.java)
    
    private val controller = UrlShortenerControllerImpl(
        redirectUseCase = redirectUseCase,
        logClickUseCase = mock(),
        createShortUrlUseCase = mock(),
        generateQrUseCase = mock(),
        getStatsUseCase = mock(),
        applicationEventPublisher = applicationEventPublisher,
        urlValidationJobService = mock(),
        shortUrlRepository = mock(),
        hashService = mock()
    )

    @Test
    fun `extractRealIp returns X-Forwarded-For first IP`() {
        val request = MockHttpServletRequest()
        request.addHeader("X-Forwarded-For", "10.0.0.1, 192.168.1.1")
        request.remoteAddr = "1.2.3.4"

        val ip = ReflectionTestUtils.invokeMethod<String>(controller, "extractRealIp", request)
        
        assertEquals("10.0.0.1", ip)
    }

    @Test
    fun `extractRealIp returns X-Real-IP if X-Forwarded-For is missing`() {
        val request = MockHttpServletRequest()
        request.addHeader("X-Real-IP", "10.0.0.2")
        request.remoteAddr = "1.2.3.4"

        val ip = ReflectionTestUtils.invokeMethod<String>(controller, "extractRealIp", request)
        
        assertEquals("10.0.0.2", ip)
    }

    @Test
    fun `extractRealIp skips 'unknown' headers`() {
        val request = MockHttpServletRequest()
        request.addHeader("X-Forwarded-For", "unknown")
        request.addHeader("Proxy-Client-IP", "10.0.0.3")
        request.remoteAddr = "1.2.3.4"

        val ip = ReflectionTestUtils.invokeMethod<String>(controller, "extractRealIp", request)
        
        assertEquals("10.0.0.3", ip)
    }

    @Test
    fun `extractRealIp falls back to remoteAddr`() {
        val request = MockHttpServletRequest()
        request.remoteAddr = "127.0.0.1"

        val ip = ReflectionTestUtils.invokeMethod<String>(controller, "extractRealIp", request)
        
        assertEquals("127.0.0.1", ip)
    }

    @Test
    fun `extractRealIp handles blank X-Forwarded-For`() {
        val request = MockHttpServletRequest()
        request.addHeader("X-Forwarded-For", "   ")
        request.addHeader("X-Real-IP", "10.0.0.5")
        request.remoteAddr = "1.2.3.4"

        val ip = ReflectionTestUtils.invokeMethod<String>(controller, "extractRealIp", request)
        
        assertEquals("10.0.0.5", ip)
    }

    @Test
    fun `extractRealIp handles unknown remoteAddr as fallback`() {
        val request = MockHttpServletRequest()
        request.remoteAddr = "unknown"

        val ip = ReflectionTestUtils.invokeMethod<String?>(controller, "extractRealIp", request)
        
        assertNull(ip)
    }

    @Test
    fun `extractRealIp handles blank remoteAddr as fallback`() {
        val request = MockHttpServletRequest()
        request.remoteAddr = "   "

        val ip = ReflectionTestUtils.invokeMethod<String?>(controller, "extractRealIp", request)
        
        assertNull(ip)
    }
}
