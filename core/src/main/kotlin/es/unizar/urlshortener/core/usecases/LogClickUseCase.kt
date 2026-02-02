package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.Click
import es.unizar.urlshortener.core.ClickProperties
import es.unizar.urlshortener.core.ClickRepositoryService
import es.unizar.urlshortener.core.InputLimits
import es.unizar.urlshortener.core.UrlHash
import es.unizar.urlshortener.core.sanitizeInput
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Use case for logging click events and analytics data.
 * 
 * This use case handles the tracking and analytics functionality of the URL shortener.
 * It records click events with associated metadata for business intelligence and
 * performance monitoring purposes.
 * 
 * **Business Value:**
 * - **Analytics**: Track usage patterns and popular URLs
 * - **Performance**: Monitor system load and user behavior
 * - **Security**: Detect suspicious activity and abuse
 * - **Business Intelligence**: Understand user demographics and preferences
 * 
 * **Design Principles:**
 * - **Non-blocking**: Click logging should never fail the main redirection flow
 * - **Resilient**: Graceful degradation when analytics systems are unavailable
 * - **Privacy-conscious**: Handle user data according to privacy regulations
 * - **Performance**: Minimal impact on redirection response times
 * 
 * **Data Collected:**
 * - URL hash (for correlation with short URLs)
 * - Timestamp (automatic)
 * - IP address (for geolocation and security)
 * - User agent (browser/platform detection)
 * - Referrer (traffic source analysis)
 * - Geographic data (country/region)
 * 
 * @see <a href="https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html">Clean Architecture</a>
 */
interface LogClickUseCase {
    /**
     * Records a click event with associated metadata.
     * 
     * This method captures analytics data for each short URL access. It's designed
     * to be called asynchronously or in a fire-and-forget manner to avoid impacting
     * the main redirection performance.
     * 
     * **Error Handling Strategy:**
     * - Click logging failures should never affect the main redirection flow
     * - Errors are logged but not propagated to the caller
     * - System continues to function even if analytics are temporarily unavailable
     * 
     * **Performance Considerations:**
     * - Should be optimized for minimal latency impact
     * - Consider batching for high-volume scenarios
     * - Database writes should be asynchronous when possible
     *
     * @param key The short URL hash key that was accessed
     * @param data The click metadata including user information and context
     */
    fun logClick(key: String, data: ClickProperties)
}

/**
 * Implementation of [LogClickUseCase] with resilient error handling.
 * 
 * This implementation demonstrates several important patterns:
 * - **Graceful Degradation**: Analytics failures don't break core functionality
 * - **Input Validation**: All inputs are sanitized before processing
 * - **Error Logging**: Failures are logged for monitoring and debugging
 * - **Fire-and-Forget**: Non-blocking operation for performance
 * 
 * **Resilience Features:**
 * - Uses `runCatching` for safe error handling
 * - Logs errors without throwing exceptions
 * - Continues operation even if repository is unavailable
 * - Provides detailed error context for debugging
 */
class LogClickUseCaseImpl(
    private val clickRepository: ClickRepositoryService
) : LogClickUseCase {

    private val logger = KotlinLogging.logger {}
    
    /**
     * Records a click event with associated metadata.
     * 
     * **Implementation Strategy:**
     * 1. **Input Validation**: Sanitize and validate the hash key
     * 2. **Entity Creation**: Build the Click entity with value objects
     * 3. **Safe Persistence**: Use runCatching to handle repository errors
     * 4. **Error Logging**: Log failures for monitoring without failing the operation
     * 
     * **Error Handling:**
     * - Repository failures are caught and logged
     * - No exceptions are thrown to maintain system stability
     * - Detailed error messages help with debugging
     * - System continues to function even with analytics issues
     *
     * @param key The short URL hash key that was accessed
     * @param data The click metadata including user information and context
     */
    override fun logClick(key: String, data: ClickProperties) {
        // Input validation and sanitization
        val sanitizedKey = sanitizeInput(key, "key", InputLimits.MAX_KEY_LENGTH)
        val urlHash = UrlHash(sanitizedKey)
        
        // Create click entity with value objects
        val cl = Click(
            hash = urlHash,
            properties = data
        )
        
        // Safe persistence with error handling
        runCatching {
            clickRepository.save(cl)
        }.onFailure { e -> 
            // Log the error but don't fail the main operation
            logger.warn(e) { "Failed to log click for key '$sanitizedKey': ${e.message}" }
        }
    }
}
