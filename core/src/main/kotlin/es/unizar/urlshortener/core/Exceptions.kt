package es.unizar.urlshortener.core

import java.lang.RuntimeException

/**
 * A base class for domain-specific exceptions in the application.
 * This sealed class serves as a root for all exceptions related to the domain logic.
 * It extends [RuntimeException], allowing for an optional [cause].
 *
 * @param message The detail message for the exception.
 * @param cause The cause of the exception, which can be null.
 */
sealed class DomainException(message: String, cause: Throwable? = null):
    RuntimeException(message, cause)

/**
 * An exception indicating that a provided URL does not follow a supported schema.
 * This exception is thrown when the format or schema of a URL does not match the expected pattern.
 *
 * @param url The URL that caused the exception.
 */
class InvalidUrlException(url: String) : DomainException("[$url] does not follow a supported schema")

/**
 * An exception indicating that the provided input is invalid (null, empty, or too long).
 *
 * @param field The field name that caused the exception.
 * @param value The value that caused the exception.
 */
class InvalidInputException(field: String, value: String?) : 
    DomainException("Invalid input for field '$field': ${value ?: "null"}")

/**
 * An exception indicating that a redirection key could not be found.
 * This exception is thrown when a specified redirection key does not exist in the system.
 *
 * @param key The redirection key that was not found.
 */
class RedirectionNotFound(key: String) : DomainException("[$key] is not known")

/**
 * An exception indicating an internal error within the application.
 * This exception can be used to represent unexpected issues that occur within the application,
 * providing both a message and a cause for the error.
 *
 * @param message The detail message for the exception.
 * @param cause The cause of the exception.
 */
class InternalError(message: String, cause: Throwable? = null) : RuntimeException(message, cause)


/**
 * Exception thrown when a URL cannot be reached (network error, timeout,
 * DNS failure or non-successful HTTP status according to reachability policy).
 *
 * This is distinct from [InvalidUrlException] (format/schema problems).
 *
 * @param url The URL that was tested and found unreachable.
 */
class UrlNotReachableException(url: String) : DomainException("[$url] could not be reached")


/**
 * Exception thrown when a URL cannot be checked against the Safe Browsing API.
 *
 * This exception is used to indicate that the safety of a URL could not be verified
 * due to network errors, API failures, or other issues when contacting the Safe Browsing service.
 *
 * @param url The URL that could not be checked
 */
class SafeBrowsingException(url: String) : DomainException("[$url] could not be checked that is URL Safe")

/**
 * Exception thrown when a URL is not safe according to the Safe Browsing API.
 *
 * This exception is used to indicate that a URL has been flagged as unsafe
 * by the Safe Browsing service.
 *
 * @param url The URL that is not safe.
 */
class UrlNotSafeException(url: String) : DomainException("[$url] is not safe")

class UnsupportedQrFormatException(requestedFormat: String, supportedFormats: List<String>) : 
    DomainException("Requested format '$requestedFormat' is not supported. Supported formats: ${supportedFormats.joinToString()}")

class QrGenerationException(
    val url: String,
    message: String, 
    cause: Throwable? = null
) : DomainException("QR generation failed for URL: $url - $message", cause)

/**
 * Exception thrown when Jbucket gets the limit.
 *
 * This exception is used to indicate that a URL has been flagged as unsafe temporarily
 * because the Safe Browsing API limit has been reached.
 *
 * @param url The URL that is not safe.
 */
class UrlValidationLimitException(val jobId: String? = null) : DomainException(
    "The Url cannot be checked right now due to rate limits, back later${if (jobId != null) ", jobId=$jobId" else ""}"
)

/**
 * Exception thrown when there is an error queuing a message to the broker.
 *
 * This exception is used to indicate that a message could not be queued
 * for processing, typically due to infrastructure issues or misconfigurations.
 *
 */
class MessagueQueueException() : DomainException("Error queuing the message to the broker")
/**
 * Sanitizes and validates basic input constraints (null, empty, length).
 * This is for basic input sanitization, not business logic validation.
 *
 * @param input The input string to validate.
 * @param fieldName The name of the field for error reporting.
 * @param maxLength The maximum allowed length (default: 2048).
 * @return The sanitized input string.
 * @throws InvalidInputException if the input is invalid.
 */
@Suppress("ThrowsCount")
fun sanitizeInput(input: String?, fieldName: String, maxLength: Int = 2048): String {
    return when {
        input == null -> throw InvalidInputException(fieldName, null)
        input.isBlank() -> throw InvalidInputException(fieldName, input)
        input.length > maxLength -> throw InvalidInputException(
            fieldName, 
            "length ${input.length} exceeds maximum $maxLength"
        )
        else -> input.trim()
    }
}

inline fun <T> safeCall(
    onFailure: (Throwable) -> Throwable = { e -> InternalError("Unexpected error", e) },
    block: () -> T
): T = runCatching {
    block()
}.fold(
    onSuccess = { it },
    onFailure = { throw onFailure(it) }
)
