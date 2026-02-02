package es.unizar.urlshortener.infrastructure.delivery

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import es.unizar.urlshortener.core.UrlSafety
import es.unizar.urlshortener.core.UrlValidationJobService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

/**
 * Unit tests for [UrlValidationResultListener].
 *
 * These tests verify the processing of the "Result Queue" (`nqq-result`).
 * **Key Responsibilities**:
 * - Deserialization: Handles polymorphic JSON for [UrlSafety] (e.g., `{"type": "Safe"}`).
 * - Persistence: Calls [UrlValidationJobService.updateJobStatus] to write the final state to DB.
 *
 * This component acts as the "sink" for the asynchronous architecture.
 */
class UrlValidationResultListenerTest {

    private lateinit var urlValidationJobService: UrlValidationJobService
    private lateinit var objectMapper: ObjectMapper
    private lateinit var listener: UrlValidationResultListener

    @BeforeEach
    fun setup() {
        urlValidationJobService = mock()
        
        // We use a real ObjectMapper to verify Jackson configuration (annotations in Domain.kt)
        // works correctly for sealed classes.
        objectMapper = ObjectMapper().registerKotlinModule()
        
        listener = UrlValidationResultListener(
            urlValidationJobService,
            objectMapper
        )
    }

    /**
     * Verifies that a message with `type: Safe` is correctly deserialized to [UrlSafety.Safe]
     * and persisted to the database.
     */
    @Test
    fun `handleResult deserializes Safe message and updates DB`() {
        // Prepare a JSON payload representing a Safe result
        // Note: Includes "type":"Safe" due to @JsonTypeInfo configuration in Domain
        val payload = """
            {
                "jobId": "job-123",
                "status": {
                    "type": "Safe"
                }
            }
        """.trimIndent()

        listener.handleResult(payload)

        // Verify the service is called. We use 'argThat' because Jackson creates a new instance
        // of the UrlSafety object, so reference equality check would fail.
        verify(urlValidationJobService).updateJobStatus(eq("job-123"), argThat { status -> status is UrlSafety.Safe })
    }

    /**
     * Verifies that a message with `type: Unsafe` is correctly deserialized to [UrlSafety.Unsafe].
     */
    @Test
    fun `handleResult deserializes Unsafe message and updates DB`() {
        val payload = """
            {
                "jobId": "job-456",
                "status": {
                    "type": "Unsafe"
                }
            }
        """.trimIndent()

        listener.handleResult(payload)

        verify(urlValidationJobService).updateJobStatus(eq("job-456"), argThat { status -> status is UrlSafety.Unsafe })
    }

    /**
     * Verifies that a message with `type: Unreachable` is correctly deserialized.
     */
    @Test
    fun `handleResult deserializes Unreachable message and updates DB`() {
        val payload = """
            {
                "jobId": "job-789",
                "status": {
                    "type": "Unreachable"
                }
            }
        """.trimIndent()

        listener.handleResult(payload)

        verify(urlValidationJobService).updateJobStatus(
            eq("job-789"),
            argThat { status -> status is UrlSafety.Unreachable }
        )
    }

    /**
     * Verifies error handling for malformed messages.
     */
    @Test
    fun `handleResult ignores invalid JSON`() {
        val invalidJson = "{ invalid json }"
        
        listener.handleResult(invalidJson)
        
        // Verify no DB interactions happen on error to prevent data corruption
        verifyNoInteractions(urlValidationJobService)
    }
}
