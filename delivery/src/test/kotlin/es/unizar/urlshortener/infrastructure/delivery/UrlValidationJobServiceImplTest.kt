package es.unizar.urlshortener.infrastructure.delivery

import com.fasterxml.jackson.databind.ObjectMapper
import es.unizar.urlshortener.core.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any 
import org.springframework.amqp.rabbit.core.RabbitTemplate

class UrlValidationJobServiceImplTest {

    @Test
    fun `enqueueValidation publishes to rabbit and stores job`() {
        val validator = mock<ValidatorService>()
        whenever(validator.isValid("http://enqueue.example/")).thenReturn(true)
        val rabbit = mock<RabbitTemplate>()
        val objectMapper = ObjectMapper()
            .registerModule(com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        
        val service = UrlValidationJobServiceImpl("test-queue", rabbit, objectMapper, validator)

        val message = UrlValidationMessage(url = Url("http://enqueue.example/"))
        val ok = service.enqueueValidation(message)

        assertTrue(ok)
        val job = service.findJob(message.id)
        assertNotNull(job)
        assertEquals(UrlSafety.Pending, job!!.status)
    }

    @Test
    fun `updateJobStatus updates the status of an existing job`() {
        val validator = mock<ValidatorService>()
        val rabbit = mock<RabbitTemplate>()
        val objectMapper = ObjectMapper().registerModule(com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
        val service = UrlValidationJobServiceImpl("test-queue", rabbit, objectMapper, validator)

        whenever(validator.isValid(any())).thenReturn(true)
        val msg = UrlValidationMessage(url = Url("http://example.com"))
        service.enqueueValidation(msg)

        val result = service.updateJobStatus(msg.id, UrlSafety.Safe)

        assertTrue(result)
        val updatedJob = service.findJob(msg.id)
        assertEquals(UrlSafety.Safe, updatedJob?.status)
        assertNotNull(updatedJob?.updatedAt)
    }

    @Test
    fun `updateJobStatus returns false for unknown job`() {
        val service = UrlValidationJobServiceImpl("q", mock(), mock(), mock())
        val result = service.updateJobStatus("unknown-id", UrlSafety.Safe)
        assertFalse(result)
    }
}