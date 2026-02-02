package es.unizar.urlshortener.core

/**
 * Core service interfaces (ports) for the URL Shortener application.
 * 
 * This file defines the **ports** in the Hexagonal Architecture pattern, which represent
 * the interfaces that the core domain uses to interact with external systems. These ports
 * define the contracts that adapters must implement, ensuring that the core domain remains
 * independent of external dependencies.
 * 
 * **Key Concepts:**
 * - **Ports**: Interfaces that define what the core needs from external systems
 * - **Adapters**: Implementations of these ports in the infrastructure layer
 * - **Dependency Inversion**: Core depends on abstractions, not concrete implementations
 * - **Testability**: Easy to mock these interfaces for unit testing
 * 
 * @see <a href="https://alistair.cockburn.us/hexagonal-architecture/">Hexagonal Architecture</a>
 */

/**
 * Repository port for managing [Click] entities.
 * 
 * This interface defines the contract for persisting click tracking data.
 * It follows the Repository pattern, providing a clean abstraction over
 * data storage mechanisms.
 * 
 * **Design Benefits:**
 * - **Abstraction**: Core doesn't know about specific database technology
 * - **Testability**: Easy to create in-memory implementations for testing
 * - **Flexibility**: Can switch between different storage backends
 * 
 * @see <a href="https://martinfowler.com/eaaCatalog/repository.html">Repository Pattern</a>
 */
interface ClickRepositoryService {
    /**
     * Persists a click event to the repository.
     * 
     * This method saves click tracking data including user information,
     * timestamps, and metadata for analytics purposes.
     *
     * @param cl The [Click] entity containing all tracking information
     * @return The persisted [Click] entity (may include generated IDs)
     * @throws InternalError if persistence fails due to infrastructure issues
     */
    fun save(cl: Click): Click
    fun getClicks(id: String): Int
    fun findByHash(hash: String): List<Click>  
}

/**
 * Repository port for managing [ShortUrl] entities.
 * 
 * This interface defines the contract for persisting and retrieving short URL
 * mappings. It's the primary data access interface for the core domain.
 * 
 * **Key Responsibilities:**
 * - Store URL hash to target URL mappings
 * - Retrieve short URLs by their hash keys
 * - Handle concurrent access safely
 * 
 * **Performance Considerations:**
 * - Hash-based lookups should be O(1) for optimal performance
 * - Consider indexing strategies for large datasets
 */
interface ShortUrlRepositoryService {
    /**
     * Retrieves a short URL by its hash key.
     * 
     * This is the primary lookup method used during redirection.
     * Performance is critical as this method is called for every
     * short URL access.
     *
     * @param id The [UrlHash] key to search for
     * @return The matching [ShortUrl] entity or null if not found
     * @throws InternalError if database access fails
     */
    fun findByKey(id: UrlHash): ShortUrl?

    /**
     * Persists a short URL mapping to the repository.
     * 
     * This method stores the complete short URL entity including
     * the hash, target URL, and metadata.
     *
     * @param su The [ShortUrl] entity to persist
     * @return The persisted [ShortUrl] entity (may include generated fields)
     * @throws InternalError if persistence fails
     */
    fun save(su: ShortUrl): ShortUrl
}

/**
 * Service port for caching/storing generated QR codes.
 * This avoids re-generation of existing QRs.
 */
interface QrCodeRepositoryService {
    /**
     * Finds a cached QR code.
     * @return The byte array of the image, or null if not found.
     */
    fun find(url: String, size: Int, format: QrFormat): ByteArray?

    /**
     * Saves a QR code to the cache.
     */
    fun save(url: String, size: Int, format: QrFormat, bytes: ByteArray)
}

/**
 * Service port for URL validation.
 * 
 * This interface abstracts URL validation logic, allowing the core to
 * validate URLs without depending on specific validation libraries.
 * 
 * **Design Decision**: This could be part of the core domain, but extracting
 * it as a port allows for:
 * - **Flexibility**: Different validation strategies (strict vs. permissive)
 * - **Testing**: Easy to mock for unit tests
 * - **Extensibility**: Can add complex validation rules without changing core
 * 
 * **Validation Criteria:**
 * - URL format compliance (RFC 3986)
 * - Supported protocols (HTTP/HTTPS)
 * - Security considerations (malicious URLs)
 */
interface ValidatorService {
    /**
     * Validates whether a URL can be shortened.
     * 
     * This method performs comprehensive URL validation including
     * format checking, protocol validation, and security screening.
     *
     * @param url The URL string to validate
     * @return true if the URL is valid and safe to shorten, false otherwise
     * @throws InternalError if validation service is unavailable
     */
    fun isValid(url: String): Boolean
}

/**
 * Service port for URL hashing.
 * 
 * This interface abstracts hash generation, allowing the core to create
 * unique identifiers for URLs without depending on specific hashing
 * algorithms or libraries.
 * 
 * **Design Decision**: This could be part of the core domain, but extracting
 * it as a port allows for:
 * - **Algorithm Flexibility**: Can switch between MD5, SHA-256, custom algorithms
 * - **Collision Handling**: Different strategies for hash collisions
 * - **Performance Tuning**: Optimize for specific use cases
 * 
 * **Hash Requirements:**
 * - **Uniqueness**: Minimize collision probability
 * - **Consistency**: Same URL always produces same hash
 * - **Length**: Balance between uniqueness and URL length
 * - **Security**: Consider if hashes should be predictable
 */
interface HashService {
    /**
     * Generates a unique hash for the given URL.
     * 
     * This method creates a deterministic hash that serves as the
     * unique identifier for the short URL. The hash should be:
     * - Consistent for the same input
     * - Sufficiently unique to avoid collisions
     * - Short enough for user-friendly URLs
     *
     * @param url The URL to generate a hash for
     * @return A unique hash string for the URL
     * @throws InternalError if hashing service fails
     */
    fun hashUrl(url: String): String
}

/**
 * Port that abstracts URL accessibility checks.
 *
 * This service is responsible for answering whether a given URL is rechable.
 *
 * Design notes:
 * - Kept intentionally simple for the PoC: returns boolean.
 * - Adapters should handle infra errors and return false when appropriate.
 * - Being a port allows easy mocking in unit tests.
 */
interface UrlReachabilityService {
    /**
     * 
     * Implementations should apply reasonable timeouts and a HEAD->GET fallback
     * strategy. They should not throw unchecked infra exceptions (use safeCall
     * at caller or convert infra errors to false).
     *
     * @param url the sanitized URL string (already validated by caller)
     * @return [ReachabilityCheckResult], in which we can find the value isReachable
     */
    fun checkReachability(url: String): ReachabilityCheckResult
}


/**
 * Service port for QR code generation.
 *
 * This interface abstracts QR generation from the core domain. The adapter
 * will produce a PNG image encoded as a [ByteArray] for a given URL and size.
 *
 * Decoupling generation from libraries (ZXing, QRGen) keeps the core testable.
 */
interface QrCodeService {
    /**
     * Generate a PNG image with a QR code encoding [url].
     *
     * @param url the URL (or text) to encode in the QR code
     * @param size the width/height of the square QR image in pixels
     * @return PNG bytes representing the QR code image
     * @throws InternalError if generation fails
     */
    fun generateFor(url: String, size: Int = 200, format: QrFormat = QrFormat.PNG): ByteArray
}

interface UrlValidationJobService {
    /**
     * Enqueues a new URL safety check for asynchronous processing.
     *
     * The core domain will call this method when a new URL is shortened.
     * The implementation should publish the [SafeBrowsingMessage] to
     * a message broker.
     *
     * @param message a SafeBrowsingMessage describing the URL to check
     * @return true if the message was successfully enqueued, false otherwise
     */
    fun enqueueValidation(message: UrlValidationMessage): Boolean

    /**
     * Updates the safety status of a SafeBrowsingJob once the background
     * worker has completed the check.
     *
     * This is called when the Safe Browsing API response is received,
     * to persist the new safety status (SAFE, UNSAFE, ERROR, etc.).
     *
     * @param jobId the ID of the job to update
     * @param status the resulting [UrlSafety] value
     * @return true if the job status was updated successfully, false otherwise
     */
    fun updateJobStatus(jobId: String, status: UrlSafety): Boolean

    /**
     * Retrieves the current status of a SafeBrowsingJob.
     *
     * Useful for polling endpoints or the frontend to check whether
     * a URL’s safety verification has completed.
     *
     * @param jobId the unique ID of the job
     * @return the SafeBrowsingJob if found, or null otherwise
     */
    fun findJob(jobId: String): UrlValidationJob?
}

interface UrlValidationWorkerService {
    fun processMessage(message: UrlValidationMessage): Boolean
}

interface UrlSafeBrowsingService {
    /**
     * Checks the given URL against Google Safe Browsing API.
     *
     * @param url the sanitized URL string (already validated by caller)
     * @return SafeBrowsingResult indicating if the URL is safe and details
     */
     fun checkUrl(url: String): Boolean
}

interface RateLimiterService {

    /**
     * Attempts to consume a single token from the rate limiter bucket.
     *
     * @return true if a token was successfully consumed (request allowed),
     *         false if the bucket is empty (rate limit exceeded).
     */
    fun tryConsume(): Boolean

    /**
     * Returns the current rate limit status including:
     * - Remaining tokens
     * - Reset time (when the bucket refills)
     * - Whether the limit has been exceeded
     *
     * @return [RateLimitStatus] representing the current rate limit state.
     */
    fun getStatus(): RateLimitStatus   
}

interface SafeBrowsingWorkerService {
    
    /**
     * Processes a SafeBrowsingMessage by checking the URL against
     * the Safe Browsing API and updating the job status.
     *
     * @param message the SafeBrowsingMessage to process
     * @return true if processing was successful, false otherwise
     */
    fun processMessage(message: UrlValidationMessage): Boolean

}

