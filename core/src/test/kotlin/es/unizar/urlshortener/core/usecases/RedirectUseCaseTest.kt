package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.InternalError
import es.unizar.urlshortener.core.InvalidInputException
import es.unizar.urlshortener.core.Redirection
import es.unizar.urlshortener.core.RedirectionNotFound
import es.unizar.urlshortener.core.ShortUrl
import es.unizar.urlshortener.core.ShortUrlRepositoryService
import es.unizar.urlshortener.core.UrlHash
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RedirectUseCaseTest {

    @Test
    fun `redirectTo returns a redirect when the key exists`() {
        val repository = mock<ShortUrlRepositoryService> ()
        val redirection = mock<Redirection>()
        val shortUrl = ShortUrl(UrlHash("key"), redirection)
        whenever(repository.findByKey(UrlHash("key"))).thenReturn(shortUrl)
        val useCase = RedirectUseCaseImpl(repository)

        assertEquals(redirection, useCase.redirectTo("key"))
    }

    @Test
    fun `redirectTo returns a not found when the key does not exist`() {
        val repository = mock<ShortUrlRepositoryService> ()
        whenever(repository.findByKey(UrlHash("key"))).thenReturn(null)
        val useCase = RedirectUseCaseImpl(repository)

        assertFailsWith<RedirectionNotFound> {
            useCase.redirectTo("key")
        }
    }

    @Test
    fun `redirectTo returns a not found when find by key fails`() {
        val repository = mock<ShortUrlRepositoryService> ()
        whenever(repository.findByKey(UrlHash("key"))).thenThrow(RuntimeException())
        val useCase = RedirectUseCaseImpl(repository)

        assertFailsWith<InternalError> {
            useCase.redirectTo("key")
        }
    }

    @Test
    fun `redirectTo throws InvalidInputException for empty key`() {
        val repository = mock<ShortUrlRepositoryService>()
        val useCase = RedirectUseCaseImpl(repository)

        assertFailsWith<InvalidInputException> {
            useCase.redirectTo("")
        }
    }

    @Test
    fun `redirectTo throws InvalidInputException for key exceeding max length`() {
        val repository = mock<ShortUrlRepositoryService>()
        val useCase = RedirectUseCaseImpl(repository)
        val longKey = "a".repeat(200) // Exceeds MAX_KEY_LENGTH (100)

        assertFailsWith<InvalidInputException> {
            useCase.redirectTo(longKey)
        }
    }

    @Test
    fun `redirectTo throws InvalidInputException for blank key`() {
        val repository = mock<ShortUrlRepositoryService>()
        val useCase = RedirectUseCaseImpl(repository)

        assertFailsWith<InvalidInputException> {
            useCase.redirectTo("   ")
        }
    }
}

