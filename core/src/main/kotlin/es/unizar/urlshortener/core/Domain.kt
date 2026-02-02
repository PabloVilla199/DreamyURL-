package es.unizar.urlshortener.core

import java.time.OffsetDateTime
import java.util.UUID
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonSubTypes


/**
 * HTTP status codes used in the application.
 * 
 * These constants follow HTTP standards for URL redirection:
 * - 307: Temporary redirect (default) - indicates the resource is temporarily moved
 * - 301: Permanent redirect - indicates the resource has permanently moved
 * 
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9110.html#section-15.4">RFC 9110 - HTTP Semantics</a>
 */
object HttpStatusCodes {
    const val TEMPORARY_REDIRECT = 307
    const val PERMANENT_REDIRECT = 301
}

/**
 * Input validation constants for domain boundaries.
 * 
 * These limits help prevent:
 * - Buffer overflow attacks
 * - Database storage issues
 * - Performance problems with extremely long inputs
 * 
 * Based on common web standards and security best practices.
 */
object InputLimits {
    /** Maximum URL length following RFC 9110 recommendations */
    const val MAX_URL_LENGTH = 2048
    /** Maximum hash key length for optimal performance */
    const val MAX_KEY_LENGTH = 100
}

enum class ValidationStep {
    CHECK_REACHABILITY,
    CHECK_SAFETY
}

/**
 * Represents a URL hash as a value object.
 * 
 * This is a modern Kotlin value class that provides:
 * - **Type Safety**: Prevents mixing URL hashes with other strings
 * - **Zero Runtime Overhead**: Compiled away to the underlying String
 * - **Validation**: Ensures hash meets domain constraints
 * - **Immutability**: Cannot be modified after creation
 * 
 * @param value The hash string value
 * @throws IllegalArgumentException if the hash is blank or too long
 * 
 * @see <a href="https://kotlinlang.org/docs/inline-classes.html">Kotlin Value Classes</a>
 */
@JvmInline
value class UrlHash(val value: String) {
    init {
        require(value.isNotBlank()) { "URL hash cannot be blank" }
        require(value.length <= InputLimits.MAX_KEY_LENGTH) { 
            "URL hash length ${value.length} exceeds maximum ${InputLimits.MAX_KEY_LENGTH}" 
        }
    }
    
    override fun toString(): String = value
}

/**
 * Represents a URL as a value object.
 * Provides type safety and validation for URLs.
 */
@JvmInline
value class Url(val value: String) {
    init {
        require(value.isNotBlank()) { "URL cannot be blank" }
        require(value.length <= InputLimits.MAX_URL_LENGTH) { 
            "URL length ${value.length} exceeds maximum ${InputLimits.MAX_URL_LENGTH}" 
        }
    }
    
    override fun toString(): String = value
}

/**
 * Represents an IP address as a value object.
 * Provides type safety for IP address values.
 */
@JvmInline
value class IpAddress(val value: String) {
    init {
        require(value.isNotBlank()) { "IP address cannot be blank" }
    }
    
    override fun toString(): String = value
}

/**
 * Represents a country code as a value object.
 * Provides type safety for country identifiers.
 */
@JvmInline
value class CountryCode(val value: String) {
    init {
        require(value.isNotBlank()) { "Country code cannot be blank" }
        require(value.length == 2) { "Country code must be 2 characters long" }
    }
    
    override fun toString(): String = value
}

/**
 * Represents a browser identifier as a value object.
 */
@JvmInline
value class Browser(val value: String) {
    init {
        require(value.isNotBlank()) { "Browser cannot be blank" }
    }
    
    override fun toString(): String = value
}

/**
 * Represents a platform identifier as a value object.
 */
@JvmInline
value class Platform(val value: String) {
    init {
        require(value.isNotBlank()) { "Platform cannot be blank" }
    }
    
    override fun toString(): String = value
}

/**
 * Represents a referrer URL as a value object.
 */
@JvmInline
value class Referrer(val value: String) {
    init {
        require(value.isNotBlank()) { "Referrer cannot be blank" }
    }
    
    override fun toString(): String = value
}

/**
 * Represents a sponsor identifier as a value object.
 */
@JvmInline
value class Sponsor(val value: String) {
    init {
        require(value.isNotBlank()) { "Sponsor cannot be blank" }
    }
    
    override fun toString(): String = value
}

/**
 * Represents an owner identifier as a value object.
 */
@JvmInline
value class Owner(val value: String) {
    init {
        require(value.isNotBlank()) { "Owner cannot be blank" }
    }
    
    override fun toString(): String = value
}

/**
 * Represents the safety status of a URL as a sealed class.
 * 
 * Sealed classes in Kotlin provide:
 * - **Exhaustive Pattern Matching**: Compiler ensures all cases are handled
 * - **Type Safety**: Cannot create invalid states
 * - **Performance**: No runtime overhead compared to enums
 * - **Extensibility**: Easy to add new states in the same module
 * 
 * @see <a href="https://kotlinlang.org/docs/sealed-classes.html">Kotlin Sealed Classes</a>
 */

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = UrlSafety.Safe::class, name = "Safe"),
    JsonSubTypes.Type(value = UrlSafety.Unsafe::class, name = "Unsafe"),
    JsonSubTypes.Type(value = UrlSafety.Unknown::class, name = "Unknown"),
    JsonSubTypes.Type(value = UrlSafety.Pending::class, name = "Pending"),
    JsonSubTypes.Type(value = UrlSafety.Error::class, name = "Error"),
    JsonSubTypes.Type(value = UrlSafety.Unreachable::class, name = "Unreachable")
)
sealed class UrlSafety {
    /** URL has been verified as safe */
    object Safe : UrlSafety()
    /** URL has been identified as potentially unsafe */
    object Unsafe : UrlSafety()
    /** URL safety status is unknown or not yet determined */
    object Unknown : UrlSafety()
    /** URL safety check is deferred for later processing */
    object Pending : UrlSafety()
    /** An error occurred while determining URL safety */
    object Error : UrlSafety() 
    /** URL is unreachable */
    object Unreachable : UrlSafety()

}

/**
 * Represents the type of redirection as a sealed class.
 * 
 * This sealed class encapsulates HTTP redirection semantics:
 * - **Temporary**: Resource temporarily moved (307)
 * - **Permanent**: Resource permanently moved (301)
 * 
 * @param statusCode The HTTP status code for this redirection type
 */
sealed class RedirectionType(val statusCode: Int) {
    /** Temporary redirect - resource temporarily moved */
    object Temporary : RedirectionType(HttpStatusCodes.TEMPORARY_REDIRECT)
    /** Permanent redirect - resource permanently moved */
    object Permanent : RedirectionType(HttpStatusCodes.PERMANENT_REDIRECT)
}

/**
 * A [Redirection] specifies the [target] and the [type] of a redirection.
 * Uses value objects and sealed classes for type safety.
 */
data class Redirection(
    val target: Url,
    val type: RedirectionType = RedirectionType.Temporary
) {
    val statusCode: Int get() = type.statusCode
}

/**
 * Resultado de validación de accesibilidad de URL
 */
data class ValidationResult(
    val statusCode: Int? = null,
    val responseTime: String? = null,
    val contentType: String? = null,
    val errorType: String? = null,
    val timeoutDuration: String? = null,
    val notFound: Boolean? = null
)


/**
 * A [ShortUrlProperties] is the bag of properties that a [ShortUrl] may have.
 * Uses value objects for type safety.
 */
data class ShortUrlProperties(
    val ip: IpAddress? = null,
    val sponsor: Sponsor? = null,
    val safety: UrlSafety = UrlSafety.Unknown,
    val owner: Owner? = null,
    val country: CountryCode? = null,
    val accessible: Boolean = false,
    val validatedAt: OffsetDateTime? = null,
    val validationResult: ValidationResult? = null
) {
    @Deprecated("Use safety property instead", ReplaceWith("safety"))
    val safe: Boolean get() = safety == UrlSafety.Safe
}

/**
 * 
 * 
 */
data class ReachabilityCheckResult(
    val isReachable: Boolean,
    val statusCode: Int? = null,
    val responseTimeMs: Long? = null,
    val contentType: String? = null,
    val errorType: String? = null
)

/**
 * A [ClickProperties] is the bag of properties that a [Click] may have.
 * Uses value objects for type safety.
 */
data class ClickProperties(
    val ip: IpAddress? = null,
    val referrer: String? = null,
    val browser: String? = null,
    val platform: String? = null,
    val country: String? = null
)

/**
 * A [Click] captures a request of redirection of a [ShortUrl] identified by its [hash].
 * Uses value objects for type safety.
 */
data class Click(
    val hash: UrlHash,
    val properties: ClickProperties = ClickProperties(),
    val created: OffsetDateTime = OffsetDateTime.now()
)

/**
 * A [ShortUrl] is the mapping between a remote url identified by [redirection]
 * and a local short url identified by [hash].
 * Uses value objects for type safety.
 */
data class ShortUrl(
    val hash: UrlHash,
    val redirection: Redirection,
    val created: OffsetDateTime = OffsetDateTime.now(),
    val properties: ShortUrlProperties = ShortUrlProperties()
)

sealed class QrFormat(val mimeType: String, val fileExtension: String) {
    object PNG : QrFormat("image/png", "png")
    object JPEG : QrFormat("image/jpeg", "jpg")
    object SVG : QrFormat("image/svg+xml", "svg")
}

/**
 * Represents the rate limit status for API requests.
 * 
 * This data class encapsulates information about the current
 * rate limiting state, including remaining tokens and reset time.
 */

data class RateLimitStatus(
    val remainingTokens: Long,
    val resetTime: OffsetDateTime,
    val isLimitExceeded: Boolean
)

data class UrlValidationMessage(
    val id: String = UUID.randomUUID().toString(),
    val url: Url,
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    val retries: Int = 0,
    val step: ValidationStep = ValidationStep.CHECK_REACHABILITY
)

data class UrlValidationJob(
    val id: String = UUID.randomUUID().toString(),
    val url: Url,
    val status: UrlSafety = UrlSafety.Pending,
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    val updatedAt: OffsetDateTime? = null,
    val retries: Int = 0
)

/**
 * Data Transfer Object (DTO) representing the result of a validation job.
 * * This object is sent to the "result queue" (return queue) by the workers
 * once processing is complete. It serves to decouple the processing logic 
 * from the database persistence logic, allowing the system to scale workers
 * without exhausting database connections.
 *
 * @property jobId The unique identifier of the validation job.
 * @property status The final determined safety status of the URL.
 */
data class UrlValidationResult(
    val jobId: String,
    val status: UrlSafety
)