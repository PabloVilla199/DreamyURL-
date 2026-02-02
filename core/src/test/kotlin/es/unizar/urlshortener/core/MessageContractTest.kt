package es.unizar.urlshortener.core

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class MessageContractTest {

    @Test
    fun `UrlValidationMessage toString contains id and url`() {
        val message = UrlValidationMessage(url = Url("http://example.com/"))
        val repr = message.toString()

        assertTrue(repr.contains("id="))
        assertTrue(repr.contains("example.com"))
    }
}