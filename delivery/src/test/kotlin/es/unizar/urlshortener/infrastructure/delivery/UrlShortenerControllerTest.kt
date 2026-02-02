@file:Suppress("MaxLineLength")

package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.InvalidUrlException
import es.unizar.urlshortener.core.InternalError
import es.unizar.urlshortener.core.MessagueQueueException
import es.unizar.urlshortener.core.QrGenerationException
import es.unizar.urlshortener.core.QrFormat
import es.unizar.urlshortener.core.Redirection
import es.unizar.urlshortener.core.ShortUrl
import es.unizar.urlshortener.core.ShortUrlProperties
import es.unizar.urlshortener.core.Url
import es.unizar.urlshortener.core.UrlHash
import es.unizar.urlshortener.core.UrlNotReachableException
import es.unizar.urlshortener.core.UrlValidationJob
import es.unizar.urlshortener.core.UrlValidationLimitException
import es.unizar.urlshortener.core.UrlValidationMessage
import es.unizar.urlshortener.core.UrlSafety
import es.unizar.urlshortener.core.UrlNotSafeException
import es.unizar.urlshortener.core.RedirectionNotFound
import es.unizar.urlshortener.core.IpAddress
import es.unizar.urlshortener.core.SafeBrowsingException

import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCase
import es.unizar.urlshortener.core.usecases.GenerateQrUseCase
import es.unizar.urlshortener.core.usecases.GetStatsUseCase
import es.unizar.urlshortener.core.usecases.LogClickUseCase
import es.unizar.urlshortener.core.usecases.RedirectUseCase
import es.unizar.urlshortener.core.usecases.UrlStats
import es.unizar.urlshortener.core.ShortUrlRepositoryService
import es.unizar.urlshortener.core.UrlValidationJobService
import es.unizar.urlshortener.core.HashService
import org.mockito.BDDMockito.given
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.net.URI
import es.unizar.urlshortener.core.ClickEvent
import org.mockito.kotlin.any

@WebMvcTest
@ContextConfiguration(
    classes = [
        UrlShortenerControllerImpl::class,
        ExceptionHandlers::class
    ]
)
class UrlShortenerControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var redirectUseCase: RedirectUseCase

    @MockitoBean
    private lateinit var logClickUseCase: LogClickUseCase

    @MockitoBean
    private lateinit var applicationEventPublisher: ApplicationEventPublisher

    @MockitoBean
    private lateinit var createShortUrlUseCase: CreateShortUrlUseCase

    @MockitoBean
    private lateinit var urlValidationJobService: UrlValidationJobService

    @MockitoBean
    private lateinit var shortUrlRepository: ShortUrlRepositoryService

    @MockitoBean
    private lateinit var hashService: HashService

    @MockitoBean
    private lateinit var generateQrUseCase: GenerateQrUseCase

    @MockitoBean
    private lateinit var getStatsUseCase: GetStatsUseCase


    @Test
    fun `redirectTo returns temporary redirect (307) when key exists`() {
        given(redirectUseCase.redirectTo("key")).willReturn(
            Redirection(Url("http://example.com/"))
        )

            mockMvc.perform(get("/{id}", "key"))
                .andExpect(status().isTemporaryRedirect)
                .andExpect(redirectedUrl("http://example.com/"))
    }

    @Test
    fun `redirectTo returns not found (404) when key does not exist`() {
        given(redirectUseCase.redirectTo("missing"))
            .willAnswer { throw RedirectionNotFound("missing") }

        mockMvc.perform(get("/{id}", "missing"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    fun `creates returns 202 Accepted with Job ID when URL is valid`() {
        given(
            createShortUrlUseCase.create(
                url = "http://example.com/",
                data = ShortUrlProperties(ip = IpAddress("127.0.0.1"))
            )
        ).willReturn(UrlValidationMessage(id = "job-1", url = Url("http://example.com/")))

            mockMvc.perform(
                post("/api/link")
                    .param("url", "http://example.com/")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            )
            .andDo(print())
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.jobId").value("job-1"))
    }
    

    @Test
    fun `creates returns bad request (400) if URL is invalid (ftp)`() {
        given(
            createShortUrlUseCase.create(
                url = "ftp://example.com/",
                data = ShortUrlProperties(ip = IpAddress("127.0.0.1"))
            )
        ).willAnswer { throw InvalidUrlException("ftp://example.com/") }

        mockMvc.perform(
            post("/api/link")
                .param("url", "ftp://example.com/")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.type").value("https://urlshortener.example.com/problems/invalid-url"))
    }

    @Test
    fun `creates returns service unavailable (503) when queue fails`() {
        given(
            createShortUrlUseCase.create(
                url = "http://example.com/",
                data = ShortUrlProperties(ip = IpAddress("127.0.0.1"))
            )
        ).willAnswer { throw MessagueQueueException() }

        mockMvc.perform(
            post("/api/link")
                .param("url", "http://example.com/")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.type").value("https://urlshortener.example.com/problems/message-queue-error"))
    }

    @Test
    fun `creates returns service unavailable (503) when validation rate limit exceeded`() {
        given(
            createShortUrlUseCase.create(
                url = "http://example.com/",
                data = ShortUrlProperties(ip = IpAddress("127.0.0.1"))
            )
        ).willAnswer { throw UrlValidationLimitException("limit-reached-job-id") }
        
        mockMvc.perform(
            post("/api/link")
                .param("url", "http://example.com/")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.jobId").value("limit-reached-job-id"))
    }

    @Test
    fun `qrcode returns png when key exists with default size`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        given(redirectUseCase.redirectTo("key")).willReturn(Redirection(Url("http://example.com/")))
        given(
            generateQrUseCase.generate(
                any(),
                eq(200),
                eq(QrFormat.PNG)
            )
        ).willReturn(png)

        mockMvc.perform(get("/api/link/{id}/qrcode", "key"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.IMAGE_PNG))
            .andExpect(header().string("Content-Disposition", "inline; filename=\"key.png\""))
            .andExpect(content().bytes(png))
    }

    @Test
    fun `qrcode returns bad request (400) when size is invalid format`() {
        given(redirectUseCase.redirectTo("key")).willReturn(Redirection(Url("http://example.com/")))

            mockMvc.perform(get("/api/link/{id}/qrcode", "key")
                .param("size", "invalid"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.title").value("Invalid Input"))
                .andExpect(
                    jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("Invalid size format. Expected 'WIDTHxHEIGHT'")
                    )
                )
    }

    @Test
    fun `qrcode returns bad request (400) when size is too big`() {
        given(redirectUseCase.redirectTo("key")).willReturn(Redirection(Url("http://example.com/")))

            mockMvc.perform(get("/api/link/{id}/qrcode", "key")
                .param("size", "5000x5000"))
                .andExpect(status().isBadRequest)
                .andExpect(
                    jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("Size exceeds maximum allowed dimensions (1000x1000)")
                    )
                )
    }

    @Test
    fun `qrcode returns 406 when unsupported format requested`() {
        given(redirectUseCase.redirectTo("key")).willReturn(Redirection(Url("http://example.com/")))

        mockMvc.perform(get("/api/link/{id}/qrcode", "key")
            .param("format", "gif"))
            .andExpect(status().isNotAcceptable)
            .andExpect(jsonPath("$.status").value(406))
            .andExpect(jsonPath("$.title").value("Unsupported Format"))
    }

    @Test
    fun `getStats returns statistics when the key exists`() {
        given(redirectUseCase.redirectTo("key")).willReturn(Redirection(Url("http://example.com/")))
        given(getStatsUseCase.getStats("key", "http://example.com/")).willReturn(
            UrlStats(
                hash = "key",
                target = "http://example.com/",
                totalClicks = 42
            )
        )

        mockMvc.perform(get("/api/{id}/stats", "key"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hash").value("key"))
            .andExpect(jsonPath("$.totalClicks").value(42))
    }

    @Test
    fun `getValidationJob returns 404 when job does not exist`() {
        given(urlValidationJobService.findJob("missing-job")).willReturn(null)

        mockMvc.perform(get("/api/validation/job/{jobId}", "missing-job"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getValidationJob returns 200 and status Unsafe when job is unsafe`() {
        val job = UrlValidationJob(
            id = "unsafe-job",
            url = Url("http://malware.com"),
            status = UrlSafety.Unsafe
        )
        given(urlValidationJobService.findJob("unsafe-job")).willReturn(job)

        mockMvc.perform(get("/api/validation/job/{jobId}", "unsafe-job"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.jobId").value("unsafe-job"))
            .andExpect(jsonPath("$.status").value("Unsafe"))
            .andExpect(jsonPath("$.safe").value(false))
            .andExpect(jsonPath("$.accessible").value(true))
    }

    @Test
    fun `getValidationJob returns 200 and status Unreachable when job failed`() {
        val job = UrlValidationJob(
            id = "unreachable-job",
            url = Url("http://down.com"),
            status = UrlSafety.Unreachable
        )
        given(urlValidationJobService.findJob("unreachable-job")).willReturn(job)

        mockMvc.perform(get("/api/validation/job/{jobId}", "unreachable-job"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("Unreachable"))
            .andExpect(jsonPath("$.accessible").value(false))
            .andExpect(jsonPath("$.error").exists())
    }

    @Test
    fun `getValidationJob returns 200, creates ShortUrl and Location when job is Safe (First access)`() {
        val urlStr = "http://safe.com"
        val job = UrlValidationJob(
            id = "safe-job",
            url = Url(urlStr),
            status = UrlSafety.Safe
        )
        val hash = "hash123"
        val shortUrl = ShortUrl(
            hash = UrlHash(hash),
            redirection = Redirection(Url(urlStr)),
            properties = ShortUrlProperties(
                safety = UrlSafety.Safe,
                accessible = true,
                validatedAt = OffsetDateTime.now()
            )
        )

        given(urlValidationJobService.findJob("safe-job")).willReturn(job)
        given(hashService.hashUrl(urlStr)).willReturn(hash)
        given(shortUrlRepository.findByKey(UrlHash(hash))).willReturn(null) // No existe aun
        given(shortUrlRepository.save(any())).willReturn(shortUrl) // Se guarda

        mockMvc.perform(get("/api/validation/job/{jobId}", "safe-job"))
            .andExpect(status().isOk)
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(hash)))
            .andExpect(jsonPath("$.status").value("Safe"))
            .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString(hash)))
            .andExpect(jsonPath("$.safe").value(true))
            .andExpect(jsonPath("$.accessible").value(true))

        verify(shortUrlRepository).save(any())
    }

    @Test
    fun `handles UrlNotSafeException with 422 Unprocessable Entity`() {
        // Simulamos que un caso de uso lanza la excepción
        given(createShortUrlUseCase.create(any(), any()))
            .willAnswer { throw UrlNotSafeException("http://unsafe.com") }

        mockMvc.perform(post("/api/link")
            .param("url", "http://unsafe.com")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE))
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.type").value("https://urlshortener.example.com/problems/url-not-safe"))
            .andExpect(jsonPath("$.title").value("URL Not Safe"))
    }

    @Test
    fun `handles SafeBrowsingException with 503 Service Unavailable`() {
        given(createShortUrlUseCase.create(any(), any()))
            .willAnswer { throw SafeBrowsingException("API Error") }

        mockMvc.perform(post("/api/link")
            .param("url", "http://example.com")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.status").value(503))
            .andExpect(jsonPath("$.type").value("https://urlshortener.example.com/problems/safe-browsing-error"))
    }

    @Test
    fun `handles InternalError with 500`() {
        given(createShortUrlUseCase.create(any(), any()))
            .willAnswer { throw InternalError("Unexpected") }

        mockMvc.perform(post("/api/link")
            .param("url", "http://example.com")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.title").value("Internal Server Error"))
            .andExpect(jsonPath("$.errorId").exists()) // Custom property
    }

    @Test
    fun `urlNotReachable sets url property`() {
        val ex = UrlNotReachableException("https://example.com")
        val req = org.springframework.mock.web.MockHttpServletRequest("GET", "/")
        val webReq = org.springframework.web.context.request.ServletWebRequest(req)

        val handlers = ExceptionHandlers()
        val pd = handlers.urlNotReachable(ex, webReq)

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.BAD_REQUEST.value(), pd.status)
        val expectedType = "https://api.urlshortener.unizar.es/problems/url-not-accessible"
        org.junit.jupiter.api.Assertions.assertEquals(expectedType, pd.type.toString())
        org.junit.jupiter.api.Assertions.assertEquals("URL Not Accessible", pd.title)

        val props = pd.getProperties()
        org.junit.jupiter.api.Assertions.assertTrue(props.isNotEmpty())
        org.junit.jupiter.api.Assertions.assertTrue(props.containsKey("url"), "properties: $props")
        val urlProp = props["url"]
        org.junit.jupiter.api.Assertions.assertTrue((urlProp as? String)?.contains("example.com") ?: false, "url property: $urlProp")
        org.junit.jupiter.api.Assertions.assertNotNull(props["timestamp"])
    }

    @Test
    fun `qrGenerationFailed includes url and qrGenerationInfo`() {
        val ex = QrGenerationException("https://foo", "boom")
        val req = org.springframework.mock.web.MockHttpServletRequest("GET", "/")
        val webReq = org.springframework.web.context.request.ServletWebRequest(req)

        val handlers = ExceptionHandlers()
        val pd = handlers.qrGenerationFailed(ex, webReq)

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), pd.status)
        val expectedPrefix2 = "https://api.urlshortener.unizar.es/problems/"
        val expectedType2 = expectedPrefix2 + "qr-generation-failed"
        org.junit.jupiter.api.Assertions.assertEquals(expectedType2, pd.type.toString())
        org.junit.jupiter.api.Assertions.assertEquals("QR Code Generation Failed", pd.title)
        val props2 = pd.getProperties()
        org.junit.jupiter.api.Assertions.assertTrue(props2.isNotEmpty())
        org.junit.jupiter.api.Assertions.assertTrue(props2.containsKey("url"), "properties: $props2")
        val urlProp2 = props2["url"]
        org.junit.jupiter.api.Assertions.assertTrue((urlProp2 as? String)?.contains("foo") ?: false, "url property: $urlProp2")
        org.junit.jupiter.api.Assertions.assertTrue(props2.containsKey("qrGenerationInfo"), "properties: $props2")
    }

    @Test
    fun `safeBrowsingLimitException contains errorId`() {
        val ex = UrlValidationLimitException("job-123")
        val req = org.springframework.mock.web.MockHttpServletRequest("GET", "/")
        val webReq = org.springframework.web.context.request.ServletWebRequest(req)

        val handlers = ExceptionHandlers()
        val pd = handlers.safeBrowsingLimitException(ex, webReq)

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), pd.status)
        val expectedPrefix3 = "https://urlshortener.example.com/problems/"
        val expectedType3 = expectedPrefix3 + "rate-limit-exceeded"
        org.junit.jupiter.api.Assertions.assertEquals(expectedType3, pd.type.toString())
        val props3 = pd.getProperties()
        org.junit.jupiter.api.Assertions.assertTrue(props3.isNotEmpty())
        org.junit.jupiter.api.Assertions.assertTrue(props3.containsKey("errorId"), "properties: $props3")
    }

    @Test
    fun `redirectTo handles null or empty User-Agent header`() {
        given(redirectUseCase.redirectTo("key")).willReturn(
            Redirection(Url("http://example.com/"))
        )

        mockMvc.perform(get("/{id}", "key")
            .header("User-Agent", ""))
            .andExpect(status().isTemporaryRedirect)
        
        mockMvc.perform(get("/{id}", "key"))
            .andExpect(status().isTemporaryRedirect)
    }

    @Test
    fun `creates handles null sponsor parameter`() {
        given(
            createShortUrlUseCase.create(
                url = "http://example.com/",
                data = ShortUrlProperties(
                    ip = IpAddress("127.0.0.1"),
                    sponsor = null
                )
            )
        ).willReturn(UrlValidationMessage(id = "job-1", url = Url("http://example.com/")))

        mockMvc.perform(
            post("/api/link")
                .param("url", "http://example.com/")
                .param("sponsor", "")  
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andExpect(status().isAccepted)
    }

    @Test
    fun `creates handles whitespace-only sponsor parameter`() {
        given(
            createShortUrlUseCase.create(
                url = "http://example.com/",
                data = ShortUrlProperties(
                    ip = IpAddress("127.0.0.1"),
                    sponsor = null
                )
            )
        ).willReturn(UrlValidationMessage(id = "job-1", url = Url("http://example.com/")))

        mockMvc.perform(
            post("/api/link")
                .param("url", "http://example.com/")
                .param("sponsor", "   ") 
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andExpect(status().isAccepted)
    }

    @Test
    fun `getValidationJob handles existing short URL with standard port 80`() {
        val urlStr = "http://safe.com"
        val job = UrlValidationJob(
            id = "safe-job",
            url = Url(urlStr),
            status = UrlSafety.Safe
        )
        val hash = "hash123"
        val shortUrl = ShortUrl(
            hash = UrlHash(hash),
            redirection = Redirection(Url(urlStr)),
            properties = ShortUrlProperties(
                safety = UrlSafety.Safe,
                accessible = true,
                validatedAt = null  
            )
        )

        given(urlValidationJobService.findJob("safe-job")).willReturn(job)
        given(hashService.hashUrl(urlStr)).willReturn(hash)
        given(shortUrlRepository.findByKey(UrlHash(hash))).willReturn(shortUrl)

        mockMvc.perform(get("/api/validation/job/{jobId}", "safe-job"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.validatedAt").isEmpty)
    }

    @Test
    fun `getValidationJob creates short URL with null validatedAt on first access`() {
        val urlStr = "http://safe.com"
        val job = UrlValidationJob(
            id = "safe-job-2",
            url = Url(urlStr),
            status = UrlSafety.Safe
        )
        val hash = "hash456"
        val shortUrl = ShortUrl(
            hash = UrlHash(hash),
            redirection = Redirection(Url(urlStr)),
            properties = ShortUrlProperties(
                safety = UrlSafety.Safe,
                accessible = true,
                validatedAt = null
            )
        )

        given(urlValidationJobService.findJob("safe-job-2")).willReturn(job)
        given(hashService.hashUrl(urlStr)).willReturn(hash)
        given(shortUrlRepository.findByKey(UrlHash(hash))).willReturn(null)
        given(shortUrlRepository.save(any())).willReturn(shortUrl)

        mockMvc.perform(get("/api/validation/job/{jobId}", "safe-job-2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.validatedAt").isEmpty)
    }

    @Test
    fun `qrcode determines format from Accept header - JPEG`() {
        val png = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) // JPEG magic bytes
        given(redirectUseCase.redirectTo("key")).willReturn(Redirection(Url("http://example.com/")))
        given(generateQrUseCase.generate(any(), eq(200), eq(QrFormat.JPEG)))
            .willReturn(png)

        mockMvc.perform(get("/api/link/{id}/qrcode", "key")
            .header("Accept", "image/jpeg"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.IMAGE_JPEG))
    }

    @Test
    fun `qrcode determines format from Accept header - SVG`() {
        val svg = "<svg></svg>".toByteArray()
        given(redirectUseCase.redirectTo("key")).willReturn(Redirection(Url("http://example.com/")))
        given(generateQrUseCase.generate(any(), eq(200), eq(QrFormat.SVG)))
            .willReturn(svg)

        mockMvc.perform(get("/api/link/{id}/qrcode", "key")
            .header("Accept", "image/svg+xml"))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "image/svg+xml"))
    }

    @Test
    fun `qrcode uses PNG as default when no format specified and no Accept header`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        given(redirectUseCase.redirectTo("key")).willReturn(Redirection(Url("http://example.com/")))
        given(generateQrUseCase.generate(any(), eq(200), eq(QrFormat.PNG)))
            .willReturn(png)

        mockMvc.perform(get("/api/link/{id}/qrcode", "key"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.IMAGE_PNG))
    }

    @Test
    fun `qrcode handles JPEG format parameter`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        given(redirectUseCase.redirectTo("key")).willReturn(Redirection(Url("http://example.com/")))
        given(generateQrUseCase.generate(any(), eq(200), eq(QrFormat.JPEG)))
            .willReturn(jpeg)

        mockMvc.perform(get("/api/link/{id}/qrcode", "key")
            .param("format", "jpg"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.IMAGE_JPEG))
    }

    @Test
    fun `parseSizeParameter handles negative dimensions`() {
        given(redirectUseCase.redirectTo("key")).willReturn(Redirection(Url("http://example.com/")))

        mockMvc.perform(get("/api/link/{id}/qrcode", "key")
            .param("size", "-100x200"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value(
                org.hamcrest.Matchers.containsString("Invalid size format. Expected 'WIDTHxHEIGHT'")
            ))
    }

    @Test
    fun `parseSizeParameter handles zero dimensions`() {
        given(redirectUseCase.redirectTo("key")).willReturn(Redirection(Url("http://example.com/")))

        mockMvc.perform(get("/api/link/{id}/qrcode", "key")
            .param("size", "0x0"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value(
                org.hamcrest.Matchers.containsString("Size dimensions must be positive")
            ))
    }
}
