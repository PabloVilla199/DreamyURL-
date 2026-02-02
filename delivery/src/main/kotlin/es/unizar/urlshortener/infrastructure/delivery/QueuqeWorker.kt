package es.unizar.urlshortener.infrastructure.delivery

import com.fasterxml.jackson.databind.ObjectMapper
import es.unizar.urlshortener.core.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.IOException
import java.net.ConnectException
import java.time.Duration
import org.springframework.amqp.rabbit.core.RabbitTemplate
import es.unizar.urlshortener.core.UrlValidationJobService
import es.unizar.urlshortener.core.UrlValidationResult
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Implementation of [UrlValidationWorkerService] that processes URL validation jobs
 * from a RabbitMQ queue.
 * * The validation process consists of two steps: reachability check and safety check.
 * - Step 1: Reachability Check
 * - Uses [UrlReachabilityService] to verify if the URL is reachable.
 * - If reachable, advances to Step 2.
 * - If unreachable, sends a [UrlValidationResult] with [UrlSafety.Unreachable] to the result queue.
 * - Step 2: Safety Check
 * - Uses [UrlSafeBrowsingService] to check if the URL is safe.
 * - Publishes the final [UrlValidationResult] (Safe/Unsafe) to the result queue.
 * * NOTE: This worker DOES NOT write to the database directly to ensure horizontal scalability.
 * It delegates persistence to the [UrlValidationResultListener] via the result queue.
 * * @param urlValidationJobService Service for enqueuing next steps (read-only or enqueue ops).
 * @param urlSafeBrowsingService Service for checking URL safety.
 * @param urlReachabilityService Service for checking URL reachability.
 * @param objectMapper Jackson ObjectMapper for JSON serialization/deserialization.
 * @param rabbitTemplate Template for sending messages to the result queue.
 * @param queueName The name of the RabbitMQ queue to listen to (input).
 * @param resultQueueName The name of the RabbitMQ queue to send results to (output).
 * @param maxAttempts Maximum retry attempts for safety check.
 * @param waitDuration Wait duration between retries for safety check.
 * @param rateLimiterService Service for rate limiting Safe Browsing API calls.
 */
@Service
@Suppress("LongParameterList")
class UrlValidationWorkerServiceImpl(
    private val urlValidationJobService: UrlValidationJobService,
    private val urlSafeBrowsingService: UrlSafeBrowsingService,
    private val urlReachabilityService: UrlReachabilityService,
    private val objectMapper: ObjectMapper,
    private val rateLimiterService: RateLimiterService,
    private val rabbitTemplate: RabbitTemplate,
    @param:Value("\${safebrowsing.rabbitQueue}") private val queueName: String,
    @param:Value("\${safebrowsing.resultQueue}") private val resultQueueName: String,
    @param:Value("\${resilience4j.retry.safebrowsing.max-attempts}") private val maxAttempts: Int,
    @param:Value("\${resilience4j.retry.safebrowsing.wait-duration}") private val waitDuration: String
) : UrlValidationWorkerService {

    /**
     * Lazy initialization of the Resilience4j Retry mechanism.
     * Configured to retry on network exceptions (IOException, ConnectException).
     */
    private val retry: Retry by lazy {
        val duration = Duration.parse("PT${waitDuration.uppercase()}")
        Retry.of(
            "safebrowsing",
            RetryConfig.custom<Any>()
                .maxAttempts(maxAttempts)
                .waitDuration(duration)
                .retryExceptions(IOException::class.java, ConnectException::class.java)
                .build()
        )
    }

    /**
     * Listens to the configured RabbitMQ queue and processes incoming messages.
     *
     * @param payload The JSON payload of the [UrlValidationMessage].
     */
    @RabbitListener(queues = ["\${safebrowsing.rabbitQueue}"])
    fun handleMessage(payload: String) {
        try {
            val message = objectMapper.readValue(payload, UrlValidationMessage::class.java)
            processMessage(message)
        } catch (e: Exception) {
            logger.error(e) { "Error parsing validation message: $payload" }
        }
    }

    /**
     * Processes a validation message based on its current step.
     *
     * @param message The domain message containing URL and step info.
     * @return true if processing initiated successfully.
     */
    override fun processMessage(message: UrlValidationMessage): Boolean {
        logger.info { "Processing job ${message.id}, Step: ${message.step}" }
        
        return when (message.step) {
            ValidationStep.CHECK_REACHABILITY -> processReachabilityStep(message)
            ValidationStep.CHECK_SAFETY -> processSafetyStep(message)
        }
    }

    /**
     * Executes Step 1: Reachability Check.
     * If reachable, advances the pipeline to Step 2 (Safety Check).
     * If not reachable, sends a failure result to the result queue.
     */
    private fun processReachabilityStep(message: UrlValidationMessage): Boolean {
        return try {
            val result = urlReachabilityService.checkReachability(message.url.toString())
            
            if (result.isReachable) {
                logger.info { "URL ${message.url} is reachable. Proceeding to Safety Check." }
                val nextStepMessage = message.copy(step = ValidationStep.CHECK_SAFETY)
                urlValidationJobService.enqueueValidation(nextStepMessage)
            } else {
                logger.warn { "URL ${message.url} is NOT reachable. Job ${message.id} terminated." }
                sendResult(message.id, UrlSafety.Unreachable)
            }
            true
        } catch (e: Exception) {
            logger.error(e) { "Error in Reachability Step for job ${message.id}" }
            sendResult(message.id, UrlSafety.Error)
            false
        }
    }

    /**
     * Executes Step 2: Safety Check.
     * Uses Rate Limiter and Retry mechanisms.
     * Sends the final result (Safe/Unsafe) to the result queue.
     */
    private fun processSafetyStep(message: UrlValidationMessage): Boolean {
        // Check Rate Limiter
        if (!rateLimiterService.tryConsume()) {
            logger.info { "Rate limit hit. Re-queuing job ${message.id}" }
            Thread.sleep(1000)
            urlValidationJobService.enqueueValidation(message) 
            return false
        }

        // Execute logic with Retry
        return try {
            retry.executeSupplier {
                val isSafe = urlSafeBrowsingService.checkUrl(message.url.toString())
                val status = if (isSafe) UrlSafety.Safe else UrlSafety.Unsafe
                
                logger.info { "Safety check finished for ${message.id}. Result: $status" }
                sendResult(message.id, status)
                true // return for executeSupplier
            }
        } catch (e: Exception) {
            logger.error(e) { "Error processing safety check for ${message.id}" }
            // Optionally send Error status if retries exhausted
            false
        }
    }

    /**
     * Helper function to serialize and send the validation result to the result queue.
     * * @param jobId The identifier of the job.
     * @param status The determined safety status.
     */
    private fun sendResult(jobId: String, status: UrlSafety) {
        try {
            val result = UrlValidationResult(jobId, status)
            val json = objectMapper.writeValueAsString(result)
            rabbitTemplate.convertAndSend(resultQueueName, json)
        } catch (e: Exception) {
            logger.error(e) { "Failed to send result for job $jobId" }
        }
    }
}

/**
 * Listener for the "Result Queue" (Return Queue).
 *
 * This component acts as the sink for validation results coming from the workers.
 * It is responsible for the write operations to the database. By centralizing 
 * the database updates here, we prevent the "Database Connection Exhaustion" 
 * problem that would occur if multiple workers tried to update the DB simultaneously.
 *
 * @param urlValidationJobService Service for managing URL validation jobs (DB access).
 * @param objectMapper Jackson ObjectMapper for JSON deserialization.
 */
@Component
class UrlValidationResultListener(
    private val urlValidationJobService: UrlValidationJobService,
    private val objectMapper: ObjectMapper
) {

    /**
     * Listens to the configured RabbitMQ result queue and updates the job status.
     *
     * @param payload The JSON payload of the [UrlValidationResult].
     */
    @RabbitListener(queues = ["\${safebrowsing.resultQueue}"])
    @Suppress("TooGenericExceptionCaught")
    fun handleResult(payload: String) {
        try {
            val result = objectMapper.readValue(payload, UrlValidationResult::class.java)
            logger.info { "Result received for Job ${result.jobId}: ${result.status}. Updating DB." }
            
            // Persist the result to the database
            urlValidationJobService.updateJobStatus(result.jobId, result.status)
            
        } catch (e: Exception) {
            logger.error(e) { "Error processing validation result: $payload" }
        }
    }
}
