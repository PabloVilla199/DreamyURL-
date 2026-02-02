@file:Suppress("WildcardImport")

package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.*
import es.unizar.urlshortener.core.sanitizeInput
import es.unizar.urlshortener.core.ClickRepositoryService
import java.time.OffsetDateTime
import mu.KotlinLogging

/**
 * Use case for creating short URLs from long URLs.
 * * This is a core business operation that has been modernized to support
 * **Asynchronous Validation**.
 * * **New Flow:**
 * 1. Validate format (Regex).
 * 2. Create a [UrlValidationJob] and enqueue a [UrlValidationMessage].
 * 3. Return the Job ID immediately (202 Accepted).
 * 4. The client polls for status using [finalizeIfSafe].
 * * @see <a href="https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html">Clean Architecture</a>
 */
interface CreateShortUrlUseCase {
    /**
     * Initiates the creation process for a short URL.
     * * This method performs synchronous input validation and then **enqueues** * the URL for background processing (Reachability + Safety).
     *
     * @param url The URL to be shortened (must be valid HTTP/HTTPS URL)
     * @param data Optional metadata (IP, sponsor, owner, etc.)
     * @return A [UrlValidationMessage] containing the Job ID to track progress.
     * @throws InvalidUrlException if the URL format is invalid
     * @throws InvalidInputException if input validation fails
     */
    fun create(url: String, data: ShortUrlProperties): es.unizar.urlshortener.core.UrlValidationMessage

    /**
     * Finalizes the creation if the background job has completed successfully.
     * * The frontend calls this method with the `jobId`.
     * - If [UrlSafety.Safe]: Creates and persists the [ShortUrl].
     * - If [UrlSafety.Unsafe]: Throws exception.
     * - If [UrlSafety.Unreachable]: Throws exception.
     * - If [UrlSafety.Pending]: Returns the job status (client should retry).
     * * @param jobId The ID of the asynchronous validation job.
     * @return The [UrlValidationJob] with updated status.
     */
    fun finalizeIfSafe(jobId: String): es.unizar.urlshortener.core.UrlValidationJob
}

/**
 * Implementation of [CreateShortUrlUseCase].
 * 
 * This class handles the creation of short URLs with asynchronous validation.
 * It validates the input URL, enqueues a validation job, and finalizes the
 * creation process based on the validation results.
 * 
 * @param shortUrlRepository Service for persisting and retrieving short URLs.
 * @param validatorService Service for validating URL formats.
 * @param hashService Service for hashing URLs.
 * @param urlValidationJobService Service for managing URL validation jobs.
 */
class CreateShortUrlUseCaseImpl(
    private val shortUrlRepository: ShortUrlRepositoryService,
    private val validatorService: ValidatorService,
    private val hashService: HashService,
    private val urlValidationJobService: UrlValidationJobService, 
) : CreateShortUrlUseCase {

    private val logger = KotlinLogging.logger {}

    override fun create(url: String, data: ShortUrlProperties): es.unizar.urlshortener.core.UrlValidationMessage {
        val sanitizedUrl = sanitizeInput(url, "url", InputLimits.MAX_URL_LENGTH)

        if (!safeCall { validatorService.isValid(sanitizedUrl) }) {
            throw InvalidUrlException(sanitizedUrl)
        }

        logger.info { "Preparing to enqueue reachability check for: $sanitizedUrl (async)" }

        val message = UrlValidationMessage(
            url = Url(sanitizedUrl),
            step = ValidationStep.CHECK_REACHABILITY
        )

        val enqueued = try {
            safeCall { urlValidationJobService.enqueueValidation(message) }
        } catch (e: Throwable) {
            logger.error(e) { "Failed to enqueue safety check for: $sanitizedUrl" }
            throw MessagueQueueException()
        }

        if (!enqueued) {
            logger.error { "Failed to enqueue message for: $sanitizedUrl" }
            throw MessagueQueueException()
        }

        logger.info { "Pipeline started with jobId: ${message.id} (step: CHECK_REACHABILITY)" }
        return message
    }

    override fun finalizeIfSafe(jobId: String): es.unizar.urlshortener.core.UrlValidationJob {
        val job = urlValidationJobService.findJob(jobId)
            ?: throw SafeBrowsingException("Job not found: $jobId")

        when (job.status) {
            UrlSafety.Pending -> {
                return job
            }
            UrlSafety.Unsafe -> {
                throw UrlNotSafeException(job.url.value)
            }
            UrlSafety.Unreachable -> {
                throw UrlNotReachableException(job.url.value)
            }
            UrlSafety.Safe -> {
                try {
                    val urlString = job.url.value
                    val hash = safeCall { hashService.hashUrl(urlString) }
                    val existing = safeCall { shortUrlRepository.findByKey(UrlHash(hash)) }
                    if (existing != null) return job

                    val props = ShortUrlProperties().copy(
                        safety = UrlSafety.Safe,
                        accessible = true,
                        validatedAt = OffsetDateTime.now()
                    )

                    val shortUrl = ShortUrl(
                        hash = UrlHash(hash),
                        redirection = Redirection(job.url),
                        properties = props
                    )

                    safeCall { shortUrlRepository.save(shortUrl) }
                    return job
                } catch (e: Throwable) {
                    logger.error(e) { "Error finalizing safe job $jobId" }
                    throw InternalError("finalize_failed")
                }
            }
            UrlSafety.Unknown, UrlSafety.Error -> throw SafeBrowsingException("Job in unexpected state: ${job.status}")
        }
    }
}

/**
 * Data Transfer Object for URL statistics.
 */
data class UrlStats(
    val hash: String,
    val target: String,
    val totalClicks: Long,
    val browserStats: Map<String, Int> = emptyMap(),
    val platformStats: Map<String, Int> = emptyMap(),
    val countryStats: Map<String, Int> = emptyMap(),
    val referrerStats: Map<String, Int> = emptyMap()
)

interface GetStatsUseCase {
    fun getStats(hash: String, targetUrl: String): UrlStats
}

class GetStatsUseCaseImpl(
    private val clickRepository: ClickRepositoryService
) : GetStatsUseCase {
    
    private val logger = KotlinLogging.logger {}

    override fun getStats(hash: String, targetUrl: String): UrlStats {
        logger.info { "Getting stats for hash: $hash" }
        
        val clicks = clickRepository.findByHash(hash)
        
        val totalClicks = clicks.size.toLong()
        
        val browserStats = clicks
            .groupBy { it.properties.browser ?: "Unknown" }
            .mapValues { it.value.size }
        
        val platformStats = clicks
            .groupBy { it.properties.platform ?: "Unknown" }
            .mapValues { it.value.size }
        
        val countryStats = clicks
            .groupBy { it.properties.country ?: "Unknown" }
            .mapValues { it.value.size }
        
        val referrerStats = clicks
            .groupBy { it.properties.referrer ?: "Direct" }
            .mapValues { it.value.size }
        
        return UrlStats(
            hash = hash,
            target = targetUrl,
            totalClicks = totalClicks,
            browserStats = browserStats,
            platformStats = platformStats,
            countryStats = countryStats,
            referrerStats = referrerStats
        )
    }
}