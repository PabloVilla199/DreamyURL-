package es.unizar.urlshortener

import es.unizar.urlshortener.core.GeoService
import es.unizar.urlshortener.core.UrlSafety
import es.unizar.urlshortener.core.UrlValidationJob
import es.unizar.urlshortener.core.UrlValidationJobService
import es.unizar.urlshortener.core.UrlValidationMessage
import es.unizar.urlshortener.infrastructure.delivery.ShortUrlDataOut
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.jdbc.JdbcTestUtils
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import java.net.URI
import java.time.OffsetDateTime

/**
 * Integration tests for the URL Shortener application.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class IntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @MockitoBean
    private lateinit var urlValidationJobService: UrlValidationJobService

    @MockitoBean
    private lateinit var geoService: GeoService

    // In-memory job store for mocking async behavior
    private val jobs = mutableMapOf<String, UrlValidationJob>()

    @BeforeEach
    fun setup() {
        val httpClient =
            HttpClientBuilder.create()
                .disableRedirectHandling()
                .build()
        (restTemplate.restTemplate.requestFactory as HttpComponentsClientHttpRequestFactory).httpClient = httpClient

        JdbcTestUtils.deleteFromTables(jdbcTemplate, "shorturl", "click")

        // Reset the in-memory job store
        jobs.clear()

        // Mock Validation Job
        whenever(urlValidationJobService.enqueueValidation(any())).thenAnswer { invocation ->
            val msg = invocation.arguments[0] as UrlValidationMessage
            jobs[msg.id] =
                UrlValidationJob(
                    id = msg.id,
                    url = msg.url,
                    status = UrlSafety.Safe,
                    createdAt = OffsetDateTime.now(),
                )
            true
        }

        whenever(urlValidationJobService.findJob(any())).thenAnswer { invocation ->
            val id = invocation.arguments[0] as String
            jobs[id]
        }

        whenever(geoService.processClick(any())).then {
            // Do nothing
        }
    }

    @AfterEach
    fun tearDown() {
        JdbcTestUtils.deleteFromTables(jdbcTemplate, "shorturl", "click")
    }

    @Test
    fun `main page works`() {
        val response = restTemplate.getForEntity("http://localhost:$port/", String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("Transform long URLs into short, shareable links")
    }

    @Test
    fun `redirectTo returns a redirect when the key exists`() {
        val creationResponse = shortUrl("http://example.com/")
        assertThat(creationResponse.statusCode).isIn(HttpStatus.CREATED, HttpStatus.OK)

        val shortUrl = creationResponse.body?.url
        assertThat(shortUrl).isNotNull()

        val shortUrlPath = shortUrl!!.path
        val response = restTemplate.getForEntity("http://localhost:$port$shortUrlPath", String::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.TEMPORARY_REDIRECT)
        assertThat(response.headers.location).isEqualTo(URI.create("http://example.com/"))

        verify(geoService, timeout(2000)).processClick(any())
    }

    @Test
    fun `redirectTo returns a not found when the key does not exist`() {
        val response = restTemplate.getForEntity("http://localhost:$port/f684a3c4", String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)

        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "click")).isEqualTo(0)
    }

    @Test
    fun `creates returns a basic redirect if it can compute a hash`() {
        val response = shortUrl("http://example.com/")

        assertThat(response.statusCode).isIn(HttpStatus.CREATED, HttpStatus.OK)
        assertThat(response.headers.location).isEqualTo(URI.create("http://localhost:$port/f684a3c4"))
        assertThat(response.body?.url).isEqualTo(URI.create("http://localhost:$port/f684a3c4"))

        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "shorturl")).isEqualTo(1)
        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "click")).isEqualTo(0)
    }

    @Test
    fun `creates returns bad request if it can't compute a hash`() {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED

        val data: MultiValueMap<String, String> = LinkedMultiValueMap()
        data["url"] = "ftp://example.com/"

        val response =
            restTemplate.postForEntity(
                "http://localhost:$port/api/link",
                HttpEntity(data, headers),
                String::class.java,
            )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body).isNotNull()
        assertThat(response.body).contains("Invalid URL")

        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "shorturl")).isEqualTo(0)
        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "click")).isEqualTo(0)
    }

    @Test
    fun `creates returns a basic redirect using multipart form data`() {
        val response = shortUrlMultipart("http://example.com/")

        assertThat(response.statusCode).isIn(HttpStatus.CREATED, HttpStatus.OK)
        assertThat(response.headers.location).isEqualTo(URI.create("http://localhost:$port/f684a3c4"))
        assertThat(response.body?.url).isEqualTo(URI.create("http://localhost:$port/f684a3c4"))

        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "shorturl")).isEqualTo(1)
        assertThat(JdbcTestUtils.countRowsInTable(jdbcTemplate, "click")).isEqualTo(0)
    }

    // --- Helpers (sin cambios) ---

    private fun shortUrl(url: String): ResponseEntity<ShortUrlDataOut> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED

        val data: MultiValueMap<String, String> = LinkedMultiValueMap()
        data["url"] = url

        val creationResponse =
            restTemplate.postForEntity(
                "http://localhost:$port/api/link",
                HttpEntity(data, headers),
                Map::class.java,
            )

        return handleAsyncResponse(creationResponse, url)
    }

    private fun shortUrlMultipart(url: String): ResponseEntity<ShortUrlDataOut> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA

        val data: MultiValueMap<String, String> = LinkedMultiValueMap()
        data["url"] = url

        val creationResponse =
            restTemplate.postForEntity(
                "http://localhost:$port/api/link",
                HttpEntity(data, headers),
                Map::class.java,
            )

        return handleAsyncResponse(creationResponse, url)
    }

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth", "ReturnCount", "LongMethod", "UnusedParameter")
    private fun handleAsyncResponse(
        creationResponse: ResponseEntity<Map<*, *>>,
        originalUrl: String,
    ): ResponseEntity<ShortUrlDataOut> {
        fun absUri(loc: URI?): URI? {
            if (loc == null) return null
            val s = loc.toString()
            return if (s.startsWith("/")) URI.create("http://localhost:$port$s") else loc
        }

        if (creationResponse.statusCode == HttpStatus.ACCEPTED) {
            val body = creationResponse.body
            val jobId = body?.get("jobId") as? String
            requireNotNull(jobId) { "Expected jobId in response body when status is 202" }

            val maxAttempts = 10
            var attempt = 0
            while (attempt < maxAttempts) {
                val jobResp =
                    restTemplate.getForEntity(
                        "http://localhost:$port/api/validation/job/$jobId",
                        Map::class.java,
                    )

                if (jobResp.statusCode == HttpStatus.OK) {
                    val jobBody = jobResp.body
                    val status = jobBody?.get("status") as? String

                    if (status == "Safe") {
                        val locHeader = absUri(jobResp.headers.location)
                        val bodyUrlStr = jobBody?.get("url") as? String
                        val finalUrl = locHeader ?: bodyUrlStr?.let { URI.create(it) }

                        if (finalUrl != null) {
                            val out = ShortUrlDataOut(url = finalUrl)
                            val headersOut = HttpHeaders()
                            headersOut.location = finalUrl
                            return ResponseEntity(out, headersOut, HttpStatus.OK)
                        }
                    } else if (status == "Unsafe" || status == "Unreachable") {
                        error("Job failed with status: $status")
                    }
                }
                Thread.sleep(100)
                attempt++
            }
            error("Validation job $jobId did not finalize in time")
        }

        val locHeader = absUri(creationResponse.headers.location)
        if (locHeader != null) {
            val out = ShortUrlDataOut(url = locHeader)
            val headersOut = HttpHeaders()
            headersOut.location = locHeader
            return ResponseEntity(out, headersOut, creationResponse.statusCode)
        }

        val responseBody = creationResponse.body
        if (responseBody != null && responseBody.containsKey("url")) {
            val raw = responseBody["url"] as String
            val loc = URI.create(raw)
            val abs = absUri(loc)
            val out = ShortUrlDataOut(url = abs)
            val headersOut = HttpHeaders()
            headersOut.location = abs
            return ResponseEntity(out, headersOut, creationResponse.statusCode)
        }

        return ResponseEntity(null, creationResponse.headers, creationResponse.statusCode)
    }
}
