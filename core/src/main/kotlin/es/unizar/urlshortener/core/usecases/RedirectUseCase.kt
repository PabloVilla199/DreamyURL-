package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.Redirection
import es.unizar.urlshortener.core.RedirectionNotFound
import es.unizar.urlshortener.core.InvalidInputException
import es.unizar.urlshortener.core.InternalError
import es.unizar.urlshortener.core.ShortUrlRepositoryService
import es.unizar.urlshortener.core.InputLimits
import es.unizar.urlshortener.core.UrlHash
import es.unizar.urlshortener.core.safeCall
import es.unizar.urlshortener.core.sanitizeInput

/**
 * Use case for redirecting users from short URLs to their target destinations.
 * 
 * This is a core business operation that handles the primary functionality of a URL shortener:
 * taking a short URL hash and returning the appropriate redirection information.
 * 
 * **Business Logic:**
 * 1. Validates and sanitizes the input hash key
 * 2. Looks up the corresponding short URL in the repository
 * 3. Returns redirection information (target URL and HTTP status code)
 * 4. Handles cases where the short URL doesn't exist
 * 
 * **Performance Considerations:**
 * - This is the most frequently called operation in the system
 * - Repository lookup should be optimized for O(1) performance
 * - Input validation should be fast and lightweight
 * 
 * **Security Considerations:**
 * - Input sanitization prevents injection attacks
 * - Proper error handling prevents information leakage
 * - Rate limiting should be considered at the controller level
 * 
 * @see <a href="https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html">Clean Architecture</a>
 */
interface RedirectUseCase {
    /**
     * Retrieves redirection information for a given short URL hash.
     * 
     * This method is the core of the URL shortening service, responsible for
     * translating short URL hashes back to their original target URLs.
     * 
     * **Process Flow:**
     * 1. Input validation and sanitization
     * 2. Hash-based repository lookup
     * 3. Redirection information extraction
     * 4. Error handling for missing URLs
     *
     * @param key The short URL hash key to look up
     * @return [Redirection] containing target URL and HTTP status code
     * @throws RedirectionNotFound if no short URL exists for the given key
     * @throws InvalidInputException if the key format is invalid
     * @throws InternalError if repository access fails
     */
    fun redirectTo(key: String): Redirection
}

/**
 * Implementation of [RedirectUseCase] following Clean Architecture principles.
 * 
 * This implementation demonstrates several important patterns:
 * - **Dependency Injection**: Repository is injected, not instantiated
 * - **Input Validation**: All inputs are sanitized before processing
 * - **Error Handling**: Uses safeCall for infrastructure error handling
 * - **Domain Logic**: Business rules are clearly separated from infrastructure
 * 
 * **Design Benefits:**
 * - **Testable**: Easy to mock the repository for unit tests
 * - **Maintainable**: Clear separation of concerns
 * - **Robust**: Comprehensive error handling and validation
 */
class RedirectUseCaseImpl(
    private val shortUrlRepository: ShortUrlRepositoryService
) : RedirectUseCase {
    
    /**
     * Retrieves redirection information for a given short URL hash.
     * 
     * **Implementation Details:**
     * - Uses `sanitizeInput` to validate and clean the hash key
     * - Converts string key to `UrlHash` value object for type safety
     * - Uses `safeCall` to handle potential repository exceptions
     * - Throws domain-specific exceptions for business rule violations
     *
     * @param key The short URL hash key to look up
     * @return [Redirection] containing target URL and HTTP status code
     * @throws RedirectionNotFound if no short URL exists for the given key
     * @throws InvalidInputException if the key format is invalid
     * @throws InternalError if repository access fails
     */
    override fun redirectTo(key: String): Redirection {
        // Input validation and sanitization
        val sanitizedKey = sanitizeInput(key, "key", InputLimits.MAX_KEY_LENGTH)
        val urlHash = UrlHash(sanitizedKey)
        
        // Repository lookup with error handling
        return safeCall {
            shortUrlRepository.findByKey(urlHash)
        }?.redirection ?: throw RedirectionNotFound(sanitizedKey)
    }
}
