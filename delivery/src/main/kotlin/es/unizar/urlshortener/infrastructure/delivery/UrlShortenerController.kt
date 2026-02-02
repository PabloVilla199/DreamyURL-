package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.ClickProperties
import es.unizar.urlshortener.core.QrFormat
import es.unizar.urlshortener.core.InvalidInputException
import es.unizar.urlshortener.core.UnsupportedQrFormatException
import es.unizar.urlshortener.core.usecases.UrlStats
import es.unizar.urlshortener.core.usecases.GetStatsUseCase
import es.unizar.urlshortener.core.UrlValidationLimitException
import org.springframework.web.bind.annotation.RequestParam
import es.unizar.urlshortener.core.ShortUrlProperties
import es.unizar.urlshortener.core.IpAddress
import es.unizar.urlshortener.core.Sponsor
import es.unizar.urlshortener.core.UrlSafety
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCase
import es.unizar.urlshortener.core.UrlValidationJob
import es.unizar.urlshortener.core.usecases.LogClickUseCase
import es.unizar.urlshortener.core.usecases.RedirectUseCase
import es.unizar.urlshortener.core.usecases.GenerateQrUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.hateoas.server.mvc.linkTo
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import kotlin.math.min
import org.springframework.context.ApplicationEventPublisher
import es.unizar.urlshortener.core.ClickEvent
import es.unizar.urlshortener.core.UrlValidationJobService
import es.unizar.urlshortener.core.Url
import es.unizar.urlshortener.core.UrlValidationMessage
import es.unizar.urlshortener.core.RateLimitStatus
import java.time.OffsetDateTime

import java.time.Instant
import eu.bitwalker.useragentutils.UserAgent

/**
 * REST API controller interface for the URL Shortener application.
 * * This interface defines the contract for the REST API endpoints, following the
 * **Adapter** pattern in Hexagonal Architecture. It acts as the primary adapter
 * that translates HTTP requests into domain operations and domain responses back
 * to HTTP responses.
 * * **Architecture Role:**
 * - **Adapter**: Translates between HTTP protocol and domain operations
 * - **Interface Segregation**: Clean separation between API contract and implementation
 * - **Dependency Inversion**: Depends on domain use cases, not infrastructure details
 * * **API Design Principles:**
 * - **RESTful**: Follows REST conventions for resource-based URLs
 * - **Stateless**: Each request contains all necessary information
 * - **Idempotent**: Safe to retry operations (where applicable)
 * - **Content Negotiation**: Supports multiple content types
 * * **Security Considerations:**
 * - Input validation and sanitization
 * - Rate limiting (implemented at infrastructure level)
 * - CORS configuration for web clients
 * - HTTPS enforcement in production
 * * @see <a href="https://alistair.cockburn.us/hexagonal-architecture/">Hexagonal Architecture</a>
 * @see <a href="https://restfulapi.net/">REST API Design</a>
 */
interface UrlShortenerController {

    /**
     * Redirects users from short URLs to their target destinations.
     * * This is the primary endpoint that handles short URL redirections. It performs
     * two main operations:
     * 1. **Redirection**: Looks up the target URL and returns appropriate HTTP redirect
     * 2. **Analytics**: Logs the click event for tracking and analytics
     * * **HTTP Semantics:**
     * - Returns 307 (Temporary Redirect) or 301 (Permanent Redirect) on success
     * - Returns 404 (Not Found) if short URL doesn't exist
     * - Returns 400 (Bad Request) for invalid input
     * * **Performance Considerations:**
     * - This is the most frequently called endpoint
     * - Analytics logging should not block the redirect response
     * - Consider caching for popular short URLs
     *
     * @param id The short URL hash key to redirect
     * @param request The HTTP request containing client information
     * @return HTTP redirect response or error response
     */
    fun redirectTo(id: String, request: HttpServletRequest): ResponseEntity<Unit>

    /**
     * Creates new short URLs from long URLs.
     * * This endpoint handles the creation of short URLs, accepting a long URL
     * and optional metadata, then returning the generated short URL information.
     * * **Content Types Supported:**
     * - `application/x-www-form-urlencoded` (traditional form submission)
     * - `multipart/form-data` (file upload forms, modern web apps)
     * * **Response Format:**
     * - Returns 201 (Created) with Location header and JSON body
     * - Location header contains the short URL for immediate use
     * - JSON body includes additional metadata and properties
     * * **Business Logic:**
     * - Validates input URL format and safety
     * - Generates unique hash for the URL
     * - Stores mapping in repository
     * - Returns short URL with metadata
     *
     * @param data The request data containing URL and optional metadata
     * @param request The HTTP request for extracting client information
     * @return HTTP response with created short URL information
     */
    fun shortener(data: ShortUrlDataIn, request: HttpServletRequest): ResponseEntity<Any>

    /**
     * Returns a QR code image for the provided short URL identifier.
     *
     * Example: GET /api/link/{id}/qrcode?size=300x300&format=png
     *
     * @param id The short URL identifier
     * @param size Optional size in pixels (format: WIDTHxHEIGHT). Default 200x200.
     * @param format Optional format (png, jpg, svg). Default png.
     */
    fun qrcode(id: String, size: String?, format: String?, request: HttpServletRequest): ResponseEntity<ByteArray>

    /**
     * Returns the current status of a validation job.
     *
     * @param jobId the id of the validation job
     */
    fun getValidationJob(jobId: String, request: HttpServletRequest): ResponseEntity<Any?>

    // [CORREGIDO] Añadido método a la interfaz
    fun getStats(id: String): ResponseEntity<UrlStats>
}

/**
 * Request data transfer object for creating short URLs.
 * * This DTO represents the input data for the short URL creation endpoint.
 * It follows the **Data Transfer Object** pattern to encapsulate request
 * data and provide clear API contracts.
 * * **Validation:**
 * - URL field is required and validated by the domain layer
 * - Sponsor field is optional for campaign tracking
 * - All fields are sanitized before processing
 * * **OpenAPI Documentation:**
 * - Fully documented with examples for API consumers
 * - Schema validation ensures type safety
 * - Clear descriptions help with API integration
 */
@Schema(description = "Request data for creating a short URL")
data class ShortUrlDataIn(
    @field:Schema(
        description = "The URL to shorten", 
        example = "https://www.example.com/very/long/url/path", 
        required = true
    )
    val url: String,
    
    @field:Schema(description = "Optional sponsor information", example = "Marketing Campaign 2024")
    val sponsor: String? = null
)

/**
 * Response data transfer object for short URL creation.
 * * This DTO represents the output data returned after successfully creating
 * a short URL. It provides both the short URL and additional metadata
 * for client applications.
 * * **Response Structure:**
 * - **url**: The complete short URL ready for use
 * - **properties**: Additional metadata (safety status, creation info, etc.)
 * * **HTTP Response:**
 * - Status: 201 Created
 * - Location header: Contains the short URL
 * - Body: JSON with URL and properties
 */
@Schema(description = "Response data after creating a short URL")
data class ShortUrlDataOut(
    @field:Schema(description = "The created short URL", example = "http://localhost:8080/f684a3c4")
    val url: URI? = null,
    
    @field:Schema(description = "Additional properties of the short URL")
    val properties: Map<String, Any?> = emptyMap()
)

@Schema(description = "Response returned when a validation check is deferred and a job is created")
data class UrlValidationJobCreatedDTO(
    @field:Schema(description = "Job identifier", example = "abc-123")
    val jobId: String,
    @field:Schema(description = "Human readable message", example = "Validation check enqueued")
    val message: String? = null
)

/**
 * REST API controller implementation for the URL Shortener application.
 * * This class implements the [UrlShortenerController] interface and serves as the
 * primary **Adapter** in the Hexagonal Architecture. It translates HTTP requests
 * into domain operations and converts domain responses back to HTTP responses.
 * * **Architecture Responsibilities:**
 * - **HTTP Protocol Translation**: Converts HTTP requests/responses to/from domain objects
 * - **Content Type Handling**: Supports multiple content types (form data, JSON)
 * - **Error Translation**: Converts domain exceptions to appropriate HTTP status codes
 * - **Request Context**: Extracts client information (IP, headers) for analytics
 * * **Spring Boot Integration:**
 * - **Auto-discovery**: Automatically registered as a REST controller
 * - **Dependency Injection**: Use cases are injected via constructor
 * - **OpenAPI Integration**: Annotations provide API documentation
 * - **Content Negotiation**: Handles different request/response formats
 * * **Performance Optimizations:**
 * - **Async Analytics**: Click logging doesn't block redirect responses
 * - **Efficient Lookups**: Optimized for high-frequency redirect operations
 * - **Caching Headers**: Appropriate cache control for different endpoints
 * * **Security Features:**
 * - **Input Validation**: All inputs are validated and sanitized
 * - **Rate Limiting**: Can be configured at infrastructure level
 * - **CORS Support**: Configurable for web client access
 * - **HTTPS Enforcement**: Production-ready security headers
 * * @see <a href="https://alistair.cockburn.us/hexagonal-architecture/">Hexagonal Architecture</a>
 * @see <a href="https://spring.io/guides/gs/rest-service/">Spring REST Services</a>
 */
@RestController
@Tag(name = "URL Shortener", description = "Operations for shortening URLs and managing redirects")
@Suppress("TooManyFunctions", "MaxLineLength", "UnusedPrivateProperty")
class UrlShortenerControllerImpl(
    val redirectUseCase: RedirectUseCase,
    val logClickUseCase: LogClickUseCase,
    val createShortUrlUseCase: CreateShortUrlUseCase,
    val generateQrUseCase: GenerateQrUseCase,
    val getStatsUseCase: GetStatsUseCase,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val urlValidationJobService: UrlValidationJobService,
    private val shortUrlRepository: es.unizar.urlshortener.core.ShortUrlRepositoryService,
    private val hashService: es.unizar.urlshortener.core.HashService
) : UrlShortenerController {

    private val logger = KotlinLogging.logger {}

    /**
     * Redirects and logs a short url identified by its [id].
     *
     * @param id the identifier of the short url
     * @param request the HTTP request
     * @return a ResponseEntity with the redirection details
     */
    @Operation(
        summary = "Redirect to original URL",
        description = "Redirects users to the original URL associated with the short URL identifier and logs the click event.",
        tags = ["URL Shortening"]
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "307",
                description = "Temporary redirect to the original URL",
                content = [Content()]
            ),
            ApiResponse(
                responseCode = "301",
                description = "Permanent redirect to the original URL",
                content = [Content()]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Short URL not found",
                content = [Content()]
            )
        ]
    )
    @GetMapping("/{id:(?!api|index).*}")
    override fun redirectTo(
        @Parameter(description = "The short URL identifier", example = "f684a3c4")
        @PathVariable id: String,
        request: HttpServletRequest
    ): ResponseEntity<Unit> =
        redirectUseCase.redirectTo(id).run {
            logger.info { "Redirecting key '$id' to '${target.value}' with status ${statusCode}" }

            val ipString = extractRealIp(request)

            val referrerHeader = request.getHeader("Referer")

            val userAgentString = request.getHeader("User-Agent")
            var browser: String? = null
            var platform: String? = null
            if (!userAgentString.isNullOrBlank()) {
                val ua = UserAgent.parseUserAgentString(userAgentString)
                browser = ua.browser?.name
                platform = ua.operatingSystem?.name
            }

            val clickEvent = ClickEvent(
                shortUrlId = id,
                ip = ipString,
                referrer = referrerHeader,
                browser = browser,
                platform = platform,
                timestamp = Instant.now(),
                country = null 
            )
            applicationEventPublisher.publishEvent(clickEvent)

            val headers = HttpHeaders()
            headers.location = URI.create(target.value)
            ResponseEntity<Unit>(headers, HttpStatus.valueOf(statusCode))
        }

    /**
     * Creates a short url from details provided in [data].
     *
     * @param data the data required to create a short url
     * @param request the HTTP request
     * @return a ResponseEntity with the created short url details
     */
    @Operation(
        summary = "Create a short URL",
        description = "Creates a short URL from a provided long URL. The URL is validated for safety and accessibility.",
        tags = ["URL Shortening"]
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Short URL created successfully",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ShortUrlDataOut::class),
                    examples = [ExampleObject(
                        name = "Success Response",
                        summary = "Successful URL shortening",
                        value = """
                        {
                            "url": "http://localhost:8080/f684a3c4",
                            "properties": {
                                "safe": true
                            }
                        }
                        """
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid URL or bad request",
                content = [Content()]
            ),
            ApiResponse(
                responseCode = "500",
                description = "Internal server error",
                content = [Content()]
            )
            ,
            ApiResponse(
                responseCode = "202",
                description = "Validation check deferred; job created",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = UrlValidationJobCreatedDTO::class),
                    examples = [ExampleObject(
                        name = "Deferred",
                        summary = "Validation check enqueued",
                                                value = """
                                                {
                                                    "jobId": "abc-123",
                                                    "message": "Validation check enqueued"
                                                }
                                                """
                    )]
                )]
            )
        ]
    )
    @PostMapping(
        "/api/link", 
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE]
    )
    override fun shortener(
        @Parameter(description = "The data required to create a short URL")
        data: ShortUrlDataIn, 
        request: HttpServletRequest
    ): ResponseEntity<Any> {
        try {
            val resp = createShortUrlUseCase.create(
                url = data.url,
                data = ShortUrlProperties(
                    ip = request.remoteAddr?.takeIf { it.isNotBlank() }?.let { IpAddress(it) },
                    sponsor = data.sponsor?.takeIf { it.isNotBlank() }?.let { Sponsor(it) }
                )
            )

            val body = UrlValidationJobCreatedDTO(jobId = resp.id, message = "Validation check enqueued")
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(body)
        } catch (ex: UrlValidationLimitException) {
            val body = mapOf("jobId" to ex.jobId)
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(body)
        }
    }

    // ==================== QR Code ====================

    @Operation(
        summary = "Get QR code for a short URL",
        description = "Returns a QR code image that links to the short URL",
        tags = ["QR Code"]
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "QR code image",
                content = [Content(mediaType = "image/png")]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Short URL not found",
                content = [Content()]
            )
        ]
    )
    @GetMapping("/api/link/{id}/qrcode")
    override fun qrcode(
        @Parameter(description = "The short URL identifier", example = "f684a3c4")
        @PathVariable id: String,
        @Parameter(description = "Size in pixels (format: WIDTHxHEIGHT)", example = "300x300")
        @RequestParam(required = false) size: String?,
        @Parameter(description = "Format (png, jpg, svg)", example = "png")
        @RequestParam(required = false) format: String?,
        request: HttpServletRequest
    ): ResponseEntity<ByteArray> {
        val redirect = redirectUseCase.redirectTo(id)

        logger.info { "Generating QR for key '$id' with size $size and format $format" }

        val (width, height) = parseSizeParameter(size)
        
        val qrFormat = determineFormat(format, request.getHeader("Accept"))
        
        val shortUri = linkTo<UrlShortenerControllerImpl> { redirectTo(id, request) }.toUri().toString()

        val imageBytes = generateQrUseCase.generate(shortUri, width, qrFormat)

        val headers = HttpHeaders()
        headers.contentType = org.springframework.http.MediaType.valueOf(qrFormat.mimeType)
        headers.contentLength = imageBytes.size.toLong()
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"$id.${qrFormat.fileExtension}\"")
        
        headers.set(HttpHeaders.CACHE_CONTROL, "public, max-age=3600") 
        headers.set(HttpHeaders.EXPIRES, "3600")
        
        return ResponseEntity(imageBytes, headers, HttpStatus.OK)
    }

    // ==================== Analytics ====================

    @Operation(
        summary = "Get URL statistics",
        description = "Retrieves cumulative usage statistics (such as total clicks) for the provided short URL identifier.",
        tags = ["Analytics"]
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Statistics retrieved successfully",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = UrlStats::class)
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Short URL not found",
                content = [Content()]
            )
        ]
    )
    @GetMapping("/api/{id}/stats")
    override fun getStats(
        @Parameter(description = "The short URL identifier", example = "f684a3c4")
        @PathVariable id: String
    ): ResponseEntity<UrlStats> {
        val redirect = redirectUseCase.redirectTo(id)
        val stats = getStatsUseCase.getStats(id, redirect.target.value)
        return ResponseEntity.ok(stats)
    }

    // ==================== UrlSafeBrowser / Validation ====================

    @Operation(
        summary = "Get Validation job status",
        description = "Query the status of an asynchronous validation job by job ID. Returns the safety and accessibility status of the URL.",
        tags = ["UrlSafeBrowser"]
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Job status",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = es.unizar.urlshortener.core.UrlValidationJob::class)
                )]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Job not found",
                content = [Content()]
            )
        ]
    )
    @GetMapping("/api/validation/job/{jobId}")
    @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
    override fun getValidationJob(
        @Parameter(description = "The job identifier", example = "123e4567-e89b-12d3-a456-426614174000")
        @PathVariable jobId: String,
        request: HttpServletRequest
    ): ResponseEntity<Any?> {
        val job = try {
            urlValidationJobService.findJob(jobId)
        } catch (e: Exception) {
            logger.error(e) { "Error fetching Validation job $jobId" }
            null
        }

        if (job == null) return ResponseEntity.notFound().build()

        if (job.status is UrlSafety.Safe) {
            try {
                val urlString = job.url.value
                val hash = hashService.hashUrl(urlString)
                val existing = shortUrlRepository.findByKey(es.unizar.urlshortener.core.UrlHash(hash))
                
                val port = if (request.serverPort == 80 || request.serverPort == 443) "" else ":${request.serverPort}"
                val scheme = request.scheme
                val host = request.serverName
                val base = "$scheme://$host$port"

                if (existing != null) {
                    val abs = URI.create("$base/${existing.hash.value}")
                    val responseBody = mapOf(
                        "jobId" to job.id,
                        "status" to "Safe",
                        "url" to abs.toString(),
                        "safe" to true,
                        "accessible" to existing.properties.accessible,
                        "validatedAt" to existing.properties.validatedAt?.toString()
                    )
                    val headers = HttpHeaders()
                    headers.location = abs
                    return ResponseEntity(responseBody, headers, HttpStatus.OK)
                }

                val shortUrl = es.unizar.urlshortener.core.ShortUrl(
                    hash = es.unizar.urlshortener.core.UrlHash(hash),
                    redirection = es.unizar.urlshortener.core.Redirection(job.url, es.unizar.urlshortener.core.RedirectionType.Temporary),
                    properties = es.unizar.urlshortener.core.ShortUrlProperties(
                        safety = UrlSafety.Safe,
                        accessible = true,
                        validatedAt = java.time.OffsetDateTime.now()
                    )
                )
                val saved = shortUrlRepository.save(shortUrl)
                val abs = URI.create("$base/${saved.hash.value}")
                
                val responseBody = mapOf(
                    "jobId" to job.id,
                    "status" to "Safe",
                    "url" to abs.toString(), 
                    "safe" to true,
                    "accessible" to saved.properties.accessible,
                    "validatedAt" to saved.properties.validatedAt?.toString()
                )
                val headers = HttpHeaders()
                headers.location = abs
                return ResponseEntity(responseBody, headers, HttpStatus.OK)
            } catch (e: Exception) {
                logger.error(e) { "Error creating short URL from safe job $jobId" }
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
            }
        }

        val statusStr = job.status::class.simpleName ?: job.status.toString()
        
        val safeFlag: Boolean? = when (job.status) {
            is UrlSafety.Safe -> true
            is UrlSafety.Unsafe -> false
            else -> null
        }
        
        val accessibleFlag: Boolean? = when (job.status) {
            is UrlSafety.Unreachable -> false
            is UrlSafety.Safe, is UrlSafety.Unsafe -> true 
            else -> null
        }

        val responseBody = mutableMapOf(
            "jobId" to job.id,
            "status" to statusStr,
            "url" to job.url.value, 
            "safe" to safeFlag,
            "accessible" to accessibleFlag,
            "createdAt" to job.createdAt.toString()
        )
        
        if (job.status is UrlSafety.Unreachable) {
            responseBody["error"] = "The URL provided could not be reached"
        }

        return ResponseEntity.ok(responseBody)
    }

    // ==================== Helper Methods ====================

    private fun extractRealIp(request: HttpServletRequest): String? {
        val headers = listOf(
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_CLIENT_IP"
        )
        
        for (header in headers) {
            val ip = request.getHeader(header)
            if (!ip.isNullOrBlank() && ip != "unknown") {
                return ip.split(",").firstOrNull()?.trim()
            }
        }
        
        return request.remoteAddr?.takeIf { it.isNotBlank() && it != "unknown" }
    }

    private fun parseSizeParameter(sizeParam: String?): Pair<Int, Int> {
        if (sizeParam == null) return 200 to 200
        
        val sizeRegex = """^(\d+)x(\d+)$""".toRegex()
        val matchResult = sizeRegex.find(sizeParam)
        
        if (matchResult == null) {
            throw InvalidInputException("size", "Invalid size format. Expected 'WIDTHxHEIGHT'")
        }
        
        val (widthStr, heightStr) = matchResult.destructured
        val width = widthStr.toIntOrNull() ?: throw InvalidInputException("size", "Invalid width: $widthStr")
        val height = heightStr.toIntOrNull() ?: throw InvalidInputException("size", "Invalid height: $heightStr")
        
        if (width <= 0 || height <= 0) {
            throw InvalidInputException("size", "Size dimensions must be positive")
        }
        
        if (width > 1000 || height > 1000) {
            throw InvalidInputException("size", "Size exceeds maximum allowed dimensions (1000x1000)")
        }
        
        val squareSize = min(width, height)
        return squareSize to squareSize
    }

    private fun determineFormat(formatParam: String?, acceptHeader: String?): QrFormat {
        formatParam?.let { param ->
            return when (param.lowercase()) {
                "png" -> QrFormat.PNG
                "jpg", "jpeg" -> QrFormat.JPEG
                "svg" -> QrFormat.SVG
                else -> throw UnsupportedQrFormatException(param, listOf("png", "jpg", "svg"))
            }
        }
        
        acceptHeader?.let { accept ->
            when {
                accept.contains("image/svg+xml") -> return QrFormat.SVG
                accept.contains("image/jpeg") -> return QrFormat.JPEG
                accept.contains("image/png") -> return QrFormat.PNG
            }
        }
        
        return QrFormat.PNG
    }
}