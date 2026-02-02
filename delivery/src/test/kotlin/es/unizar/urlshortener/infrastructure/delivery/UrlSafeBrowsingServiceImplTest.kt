package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import com.sun.net.httpserver.HttpServer

class UrlSafeBrowsingServiceImplTest {

    private lateinit var server: HttpServer
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        port = server.address.port
        server.executor = null
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun `checkUrl returns true when response has no matches`() {
        val path = "/v4/threatMatches:find"
        server.createContext(path) { exchange ->
            val body = "{}"
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }

        val apiUrl = "http://localhost:$port/v4/threatMatches:find"
        // El servicio ya no necesita Validator ni RabbitTemplate
        val service = UrlSafeBrowsingServiceImpl("key", apiUrl)

        val result = service.checkUrl("http://example.com/")
        assertTrue(result)
    }

    @Test
    fun `checkUrl returns false when response contains matches`() {
        val path = "/v4/threatMatches:find"
        server.createContext(path) { exchange ->
            val body = "{ \"matches\": [ { \"threatType\": \"MALWARE\" } ] }"
            exchange.sendResponseHeaders(200, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }

        val apiUrl = "http://localhost:$port/v4/threatMatches:find"
        val service = UrlSafeBrowsingServiceImpl("key", apiUrl)

        val result = service.checkUrl("http://malicious.example/")
        assertFalse(result)
    }
}