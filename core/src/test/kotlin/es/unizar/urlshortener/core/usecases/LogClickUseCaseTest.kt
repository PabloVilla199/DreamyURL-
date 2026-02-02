package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.ClickProperties
import es.unizar.urlshortener.core.ClickRepositoryService
import es.unizar.urlshortener.core.InvalidInputException
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertFailsWith

class LogClickUseCaseTest {

    @Test
    fun `logClick fails silently`() {
        val repository = mock<ClickRepositoryService> ()
        val clickProperties = mock<ClickProperties>()
        whenever(repository.save(any())).thenThrow(RuntimeException())

        val useCase = LogClickUseCaseImpl(repository)

        useCase.logClick("key", clickProperties)
        verify(repository).save(any())
    }

    @Test
    fun `logClick throws InvalidInputException for empty key`() {
        val repository = mock<ClickRepositoryService>()
        val clickProperties = mock<ClickProperties>()
        val useCase = LogClickUseCaseImpl(repository)

        assertFailsWith<InvalidInputException> {
            useCase.logClick("", clickProperties)
        }
    }

    @Test
    fun `logClick throws InvalidInputException for key exceeding max length`() {
        val repository = mock<ClickRepositoryService>()
        val clickProperties = mock<ClickProperties>()
        val useCase = LogClickUseCaseImpl(repository)
        val longKey = "a".repeat(200) // Exceeds MAX_KEY_LENGTH (100)

        assertFailsWith<InvalidInputException> {
            useCase.logClick(longKey, clickProperties)
        }
    }

    @Test
    fun `logClick throws InvalidInputException for blank key`() {
        val repository = mock<ClickRepositoryService>()
        val clickProperties = mock<ClickProperties>()
        val useCase = LogClickUseCaseImpl(repository)

        assertFailsWith<InvalidInputException> {
            useCase.logClick("   ", clickProperties)
        }
    }
}

