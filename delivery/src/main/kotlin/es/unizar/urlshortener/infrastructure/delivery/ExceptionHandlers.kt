@file:Suppress("MaxLineLength")
package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.InvalidUrlException
import es.unizar.urlshortener.core.UnsupportedQrFormatException
import es.unizar.urlshortener.core.QrGenerationException
import es.unizar.urlshortener.core.InvalidInputException
import es.unizar.urlshortener.core.RedirectionNotFound
import es.unizar.urlshortener.core.InternalError
import es.unizar.urlshortener.core.UrlValidationLimitException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.net.URI
import java.time.OffsetDateTime

/**
 * Global exception handler that implements Problem Details for HTTP APIs (RFC 9457).
 * * This handler provides standardized error responses that include:
 * - **type**: A URI reference that identifies the problem type
 * - **title**: A short, human-readable summary of the problem type
 * - **status**: The HTTP status code
 * - **detail**: A human-readable explanation specific to this occurrence
 * - **instance**: A URI reference that identifies the specific occurrence
 * * @see <a href="https://www.rfc-editor.org/rfc/rfc9457">RFC 9457 - Problem Details for HTTP APIs</a>
 */
@ControllerAdvice
class ExceptionHandlers : ResponseEntityExceptionHandler() {

    private val log = KotlinLogging.logger {}

    /**
     * Handles InvalidUrlException and returns a BAD_REQUEST response with Problem Details.
     *
     * @param ex the InvalidUrlException thrown
     * @param request the WebRequest during which the exception was thrown
     * @return a ProblemDetail following RFC 9457 format
     */
    @ExceptionHandler(value = [InvalidUrlException::class])
    fun invalidUrls(ex: InvalidUrlException, request: WebRequest): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid URL format")
        problemDetail.type = URI.create("https://urlshortener.example.com/problems/invalid-url")
        problemDetail.title = "Invalid URL"
        problemDetail.instance = URI.create(request.getDescription(false))
        problemDetail.setProperty("timestamp", OffsetDateTime.now())
        return problemDetail
    }

    /**
     * Handles InvalidInputException and returns a BAD_REQUEST response with Problem Details.
     *
     * @param ex the InvalidInputException thrown
     * @param request the WebRequest during which the exception was thrown
     * @return a ProblemDetail following RFC 9457 format
     */
    @ExceptionHandler(value = [InvalidInputException::class])
    fun invalidInput(ex: InvalidInputException, request: WebRequest): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid input provided")
        problemDetail.type = URI.create("https://urlshortener.example.com/problems/invalid-input")
        problemDetail.title = "Invalid Input"
        problemDetail.instance = URI.create(request.getDescription(false))
        problemDetail.setProperty("timestamp", OffsetDateTime.now())
        return problemDetail
    }

    /**
     * Handles RedirectionNotFound exception and returns a NOT_FOUND response with Problem Details.
     *
     * @param ex the RedirectionNotFound exception thrown
     * @param request the WebRequest during which the exception was thrown
     * @return a ProblemDetail following RFC 9457 format
     */
    @ExceptionHandler(value = [RedirectionNotFound::class])
    fun redirectionNotFound(ex: RedirectionNotFound, request: WebRequest): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Short URL not found")
        problemDetail.type = URI.create("https://urlshortener.example.com/problems/redirection-not-found")
        problemDetail.title = "Redirection Not Found"
        problemDetail.instance = URI.create(request.getDescription(false))
        problemDetail.setProperty("timestamp", OffsetDateTime.now())
        return problemDetail
    }

    /**
     * Handles InternalError and returns an INTERNAL_SERVER_ERROR response with Problem Details.
     *
     * @param ex the InternalError thrown
     * @param request the WebRequest during which the exception was thrown
     * @return a ProblemDetail following RFC 9457 format
     */
    @ExceptionHandler(value = [InternalError::class])
    fun internalError(ex: InternalError, request: WebRequest): ProblemDetail {
        log.error(ex) { "Internal error: ${ex.message}, Request Details: ${request.getDescription(false)}" }
        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An internal server error occurred")
        problemDetail.type = URI.create("https://urlshortener.example.com/problems/internal-error")
        problemDetail.title = "Internal Server Error"
        problemDetail.instance = URI.create(request.getDescription(false))
        problemDetail.setProperty("timestamp", OffsetDateTime.now())
        problemDetail.setProperty("errorId", generateErrorId())
        return problemDetail
    }

    /**
     * Handles UrlNotReachableException and returns a 422 Unprocessable Entity
     * response with Problem Details. This indicates the URL could not be
     * contacted (network timeout, DNS, non-success HTTP status according to policy).
     *
     * @param ex the UrlNotReachableException thrown
     * @param request the WebRequest during which the exception was thrown
     * @return a ProblemDetail following RFC 9457 format
     */
    @ExceptionHandler(value = [es.unizar.urlshortener.core.UrlNotReachableException::class])
    fun urlNotReachable(ex: es.unizar.urlshortener.core.UrlNotReachableException, request: WebRequest): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST, 
            "The provided URL is not reachable or returns an error status code"
        )
        problemDetail.type = URI.create("https://api.urlshortener.unizar.es/problems/url-not-accessible")
        problemDetail.title = "URL Not Accessible"
        problemDetail.instance = URI.create(request.getDescription(false))
        problemDetail.setProperty("timestamp", OffsetDateTime.now())
        
        val urlPattern = "\\[(.+?)\\]".toRegex()
        val matchResult = urlPattern.find(ex.message ?: "")
        matchResult?.groupValues?.getOrNull(1)?.let { url ->
            problemDetail.setProperty("url", url)
        }
        return problemDetail
    }

    /**
     * Handles SafeBrowsingException and returns a SERVICE_UNAVAILABLE response with Problem Details.
     *
     * @param ex the SafeBrowsingException thrown
     * @param request the WebRequest during which the exception was thrown
     * @return a ProblemDetail following RFC 9457 format
     */
    @ExceptionHandler(value = [es.unizar.urlshortener.core.SafeBrowsingException::class])
    fun safeBrowsingException(ex: es.unizar.urlshortener.core.SafeBrowsingException, request: WebRequest): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            ex.message ?: "Could not verify URL safety via Safe Browsing API"
        )
        problemDetail.type = URI.create("https://urlshortener.example.com/problems/safe-browsing-error")
        problemDetail.title = "Safe Browsing API Error"
        problemDetail.instance = URI.create(request.getDescription(false))
        problemDetail.setProperty("timestamp", OffsetDateTime.now())
        problemDetail.setProperty("errorId", generateErrorId())
        return problemDetail
    }

    /**
     * Handles UrlNotSafeException and returns a UNPROCESSABLE_ENTITY response with Problem Details.
     *
     * @param ex the UrlNotSafeException thrown
     * @param request the WebRequest during which the exception was thrown
     * @return a ProblemDetail following RFC 9457 format
     */
    @ExceptionHandler(value = [es.unizar.urlshortener.core.UrlNotSafeException::class])
    fun urlNotSafeException(ex: es.unizar.urlshortener.core.UrlNotSafeException, request: WebRequest): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ex.message ?: "The provided URL is not safe"
        )
        problemDetail.type = URI.create("https://urlshortener.example.com/problems/url-not-safe")
        problemDetail.title = "URL Not Safe"
        problemDetail.instance = URI.create(request.getDescription(false))
        problemDetail.setProperty("timestamp", OffsetDateTime.now())
        return problemDetail
    }

    /**
     * Handles UnsupportedQrFormatException and returns a NOT_ACCEPTABLE response with Problem Details.
     *
     * @param ex the UnsupportedQrFormatException thrown
     * @param request the WebRequest during which the exception was thrown
     * @return a ProblemDetail following RFC 9457 format
     */
    @ExceptionHandler(value = [UnsupportedQrFormatException::class])
    fun unsupportedQrFormat(ex: UnsupportedQrFormatException, request: WebRequest): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_ACCEPTABLE, ex.message ?: "Unsupported format")
        problemDetail.type = URI.create("https://api.urlshortener.unizar.es/problems/unsupported-format")
        problemDetail.title = "Unsupported Format"
        problemDetail.instance = URI.create(request.getDescription(false))
        problemDetail.setProperty("timestamp", OffsetDateTime.now())
        problemDetail.setProperty("supportedFormats", listOf("image/png", "image/svg+xml", "image/jpeg"))
        return problemDetail
    }

    /**
     * Handles QrGenerationException and returns an INTERNAL_SERVER_ERROR response with Problem Details.
     *
     * @param ex the QrGenerationException thrown
     * @param request the WebRequest during which the exception was thrown
     * @return a ProblemDetail following RFC 9457 format
     */
    @ExceptionHandler(value = [QrGenerationException::class])
    fun qrGenerationFailed(ex: QrGenerationException, request: WebRequest): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.message ?: "QR generation failed")
        problemDetail.type = URI.create("https://api.urlshortener.unizar.es/problems/qr-generation-failed")
        problemDetail.title = "QR Code Generation Failed"
        problemDetail.instance = URI.create(request.getDescription(false))
        problemDetail.setProperty("timestamp", OffsetDateTime.now())
        problemDetail.setProperty("url", ex.url)
        problemDetail.setProperty("qrGenerationInfo", mapOf(
            "error" to "QR code generation service unavailable",
            "retryAfter" to "30s",
            "errorType" to "SERVICE_UNAVAILABLE"
        ))

        return problemDetail
    }

    /**
     * Handles UrlValidationLimitException and returns a SERVICE_UNAVAILABLE response with Problem Details.
     * 
     *  @param ex the UrlValidationLimitException thrown
     * @param request the WebRequest during which the exception was thrown
     * @return a ProblemDetail following RFC 9457 format
     */
    @ExceptionHandler(value = [UrlValidationLimitException::class])
    fun safeBrowsingLimitException(ex: UrlValidationLimitException, request: WebRequest): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            ex.message ?: "Validation API limit has been reached your petition will be processed later"
        )
        problemDetail.type = URI.create("https://urlshortener.example.com/problems/rate-limit-exceeded")
        problemDetail.title = "Validation API Limit Reached"
        problemDetail.instance = URI.create(request.getDescription(false))
        problemDetail.setProperty("timestamp", OffsetDateTime.now())
        problemDetail.setProperty("errorId", generateErrorId())
        return problemDetail
    }

    /**
     * Handles MessagueQueueException and returns a SERVICE_UNAVAILABLE response with Problem Details.
     *
     * @param ex the MessagueQueueException thrown
     * @param request the WebRequest during which the exception was thrown
     * @return a ProblemDetail following RFC 9457 format
     */
    @ExceptionHandler(value = [es.unizar.urlshortener.core.MessagueQueueException::class])
    fun messageQueueException(ex: es.unizar.urlshortener.core.MessagueQueueException, request: WebRequest): ProblemDetail {
        val problemDetail = ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            ex.message ?: "Error queuing the background job"
        )
        problemDetail.type = URI.create("https://urlshortener.example.com/problems/message-queue-error")
        problemDetail.title = "Message Queue Error"
        problemDetail.instance = URI.create(request.getDescription(false))
        problemDetail.setProperty("timestamp", OffsetDateTime.now())
        problemDetail.setProperty("errorId", generateErrorId())
        return problemDetail
    }

    /**
     * Generates a unique error ID for tracking purposes.
     * In a production environment, this would be a proper UUID or correlation ID.
     */
    private fun generateErrorId(): String = "ERR-${System.currentTimeMillis()}"
}