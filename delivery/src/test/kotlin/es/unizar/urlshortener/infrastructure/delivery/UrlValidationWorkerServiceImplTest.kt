package es.unizar.urlshortener.infrastructure.delivery

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import es.unizar.urlshortener.core.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.amqp.rabbit.core.RabbitTemplate

/**
 * Unit tests for [UrlValidationWorkerServiceImpl].
 *
 * These tests verify the business logic of the asynchronous validation worker.
 * **Key Architectural Decision**:
 * - This worker processes messages from the "Input Queue" (`nqq-check`).
 * - It performs heavy lifting (network checks) but **does not write to the DB**.
 * - Instead, it publishes the final verdict to the "Result Queue" (`nqq-result`) via [RabbitTemplate].
 *
 * @see UrlValidationWorkerServiceImpl
 */
class UrlValidationWorkerServiceImplTest {

    private lateinit var urlValidationJobService: UrlValidationJobService
    private lateinit var urlSafeBrowsingService: UrlSafeBrowsingService
    private lateinit var urlReachabilityService: UrlReachabilityService
    private lateinit var objectMapper: ObjectMapper
    private lateinit var workerService: UrlValidationWorkerServiceImpl
    private lateinit var rateLimiterService: RateLimiterService
    
    // Mock for the RabbitTemplate used to send results back to the result queue
    private lateinit var rabbitTemplate: RabbitTemplate

    @BeforeEach
    fun setup() {
        // Mock dependencies to isolate worker logic
        urlValidationJobService = mock()
        urlSafeBrowsingService = mock()
        urlReachabilityService = mock()
        rateLimiterService = mock()
        rabbitTemplate = mock()

        // Configure real ObjectMapper to ensure JSON serialization works as expected
        objectMapper = ObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        
        // Bypass rate limiter for unit tests
        whenever(rateLimiterService.tryConsume()).thenReturn(true)
        val maxAttempts = 3
        val waitDuration = "2S"
        
        workerService = UrlValidationWorkerServiceImpl(
            urlValidationJobService,
            urlSafeBrowsingService,
            urlReachabilityService,
            objectMapper,
            rateLimiterService,
            rabbitTemplate,
            "test-queue",
            "result-queue", // Simulates the infrastructure config
            maxAttempts,
            waitDuration
        )
    }

    /**
     * Verifies that if Step 1 (Reachability) passes, the job is re-enqueued for Step 2.
     * The worker should NOT send a result yet, nor update the DB.
     */
    @Test
    fun `processMessage Step 1 Reachable enqueues Step 2`() {
        val url = Url("https://reachable.com")
        val message = UrlValidationMessage(url = url, step = ValidationStep.CHECK_REACHABILITY)

        // Mock Reachability OK
        whenever(urlReachabilityService.checkReachability(url.value)).thenReturn(ReachabilityCheckResult(true, 200))

        workerService.processMessage(message)

        verify(urlReachabilityService).checkReachability(url.value)
        
        // Verify it enqueues the next step (SAFETY) back into the input flow
        verify(urlValidationJobService).enqueueValidation(argThat { step == ValidationStep.CHECK_SAFETY })
    }

    /**
     * Verifies that if Step 1 (Reachability) fails, the worker sends a failure result.
     * **Important**: Verifies that `convertAndSend` is called on [RabbitTemplate] instead of a DB update.
     */
    @Test
    fun `processMessage Step 1 Unreachable updates Status Unreachable`() {
        val url = Url("https://unreachable.com")
        val message = UrlValidationMessage(url = url, step = ValidationStep.CHECK_REACHABILITY)

        // Mock Reachability Fail
        whenever(urlReachabilityService.checkReachability(url.value)).thenReturn(ReachabilityCheckResult(false, 404))

        workerService.processMessage(message)

        verify(urlReachabilityService).checkReachability(url.value)
        
        // Verify result is sent to the Result Queue
        verify(rabbitTemplate).convertAndSend(eq("result-queue"), any<String>())

        // Ensure it DOES NOT enqueue next step or call DB directly
        verify(urlValidationJobService, never()).enqueueValidation(any())
        verify(urlValidationJobService, never()).updateJobStatus(any(), any())
    }

    /**
     * Verifies that if Step 2 (Safety) passes, a SAFE result is sent to the queue.
     */
    @Test
    fun `processMessage Step 2 Safe updates Status Safe`() {
        val url = Url("https://safe.com")
        val message = UrlValidationMessage(url = url, step = ValidationStep.CHECK_SAFETY)

        whenever(urlSafeBrowsingService.checkUrl(url.value)).thenReturn(true)

        workerService.processMessage(message)

        verify(urlSafeBrowsingService).checkUrl(url.value)
        
        // Verify SAFE result is sent to the Result Queue
        verify(rabbitTemplate).convertAndSend(eq("result-queue"), any<String>())
    }

    /**
     * Verifies that if Step 2 (Safety) fails, an UNSAFE result is sent to the queue.
     */
    @Test
    fun `processMessage Step 2 Unsafe updates Status Unsafe`() {
        val url = Url("https://unsafe.com")
        val message = UrlValidationMessage(url = url, step = ValidationStep.CHECK_SAFETY)

        whenever(urlSafeBrowsingService.checkUrl(url.value)).thenReturn(false)

        workerService.processMessage(message)

        verify(urlSafeBrowsingService).checkUrl(url.value)
        
        // Verify UNSAFE result is sent to the Result Queue
        verify(rabbitTemplate).convertAndSend(eq("result-queue"), any<String>())
    }

    /**
     * Robustness test: ensures the worker doesn't crash or trigger logic on malformed JSON.
     */
    @Test
    fun `handleMessage ignores invalid JSON`() {
        val invalidJson = "{ invalid json }"
        workerService.handleMessage(invalidJson)
        verifyNoInteractions(urlSafeBrowsingService)
    }
}
