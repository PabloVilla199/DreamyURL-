package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.QrCodeService
import es.unizar.urlshortener.core.QrFormat
import es.unizar.urlshortener.core.InternalError
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.io.ByteArrayOutputStream
import com.google.common.hash.Hashing
import es.unizar.urlshortener.core.HashService
import es.unizar.urlshortener.core.ValidatorService
import es.unizar.urlshortener.core.UrlReachabilityService
import es.unizar.urlshortener.core.UrlSafeBrowsingService
import org.apache.commons.validator.routines.UrlValidator
import java.nio.charset.StandardCharsets
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.URI
import java.net.IDN
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import es.unizar.urlshortener.core.ClickRepositoryService
import org.springframework.data.redis.core.StringRedisTemplate
import es.unizar.urlshortener.core.Click
import es.unizar.urlshortener.core.ReachabilityCheckResult
import org.springframework.stereotype.Service
import es.unizar.urlshortener.core.ShortUrl
import es.unizar.urlshortener.core.ShortUrlRepositoryService
import es.unizar.urlshortener.core.UrlHash
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.client.j2se.MatrixToImageConfig
import java.io.StringWriter
import org.springframework.amqp.rabbit.core.RabbitTemplate
import es.unizar.urlshortener.core.UrlValidationMessage
import es.unizar.urlshortener.core.Url
import es.unizar.urlshortener.core.UrlValidationJob
import es.unizar.urlshortener.core.UrlSafety
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.resilience4j.retry.annotation.Retry
import es.unizar.urlshortener.core.UrlValidationJobService

import java.net.http.HttpTimeoutException
import java.net.UnknownHostException
import java.io.IOException

/**
 * Infrastructure implementations of core domain service ports.
 * * This file contains **Adapter** implementations that provide concrete functionality
 * for the core domain services. These implementations use external libraries and
 * utilities to fulfill the contracts defined by the core domain ports.
 */

/**
 * Apache Commons-based implementation of the [ValidatorService] port.
 * * This adapter provides URL validation functionality using the Apache Commons
 * Validator library.
 */
class ValidatorServiceImpl : ValidatorService {
    override fun isValid(url: String) = urlValidator.isValid(url)

    companion object {
        val urlValidator = UrlValidator(arrayOf("http", "https"))
    }
}

/**
 * Google Guava-based implementation of the [HashService] port.
 * * This adapter provides hash generation functionality using Google's Guava
 * library (Murmur3 32-bit).
 */
class HashServiceImpl : HashService {
    override fun hashUrl(url: String) = Hashing.murmur3_32_fixed().hashString(url, StandardCharsets.UTF_8).toString()
}

/**
 * HTTP-based implementation of [UrlReachabilityService].
 *
 * Checks if a URL is reachable (returns 200-299) using a HEAD -> GET fallback strategy.
 * Includes Resilience4j retry annotation and Redis caching.
 *
 * @param timeoutMillis Connection timeout in milliseconds.
 * @param cacheEnabled Whether to use Redis cache.
 * @param cacheTtlMinutes TTL for cached results.
 */
open class UrlReachabilityServiceImpl(
    @param:Value("\${reachability.timeoutMillis:5000}") private val timeoutMillis: Long,
    @param:Value("\${reachability.cache.enabled:true}") private val cacheEnabled: Boolean,
    @param:Value("\${reachability.cache.ttl-minutes:10}") private val cacheTtlMinutes: Long,
    private val redisTemplate: StringRedisTemplate, 
    private val objectMapper: ObjectMapper
) : UrlReachabilityService {

    private val logger = KotlinLogging.logger {}

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(timeoutMillis))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    @Retry(name = "reachability")
    override fun checkReachability(url: String): ReachabilityCheckResult {
        if (cacheEnabled) {
            getFromCache(url)?.let { 
                logger.debug { "Cache hit for $url" }
                return it 
            }
        }

        val result = performHttpCheckWithFallback(url)

        if (cacheEnabled) {
            saveToCache(url, result)
        }

        return result
    }

    private fun performHttpCheckWithFallback(url: String): ReachabilityCheckResult {
        val headResult = performRequest(url, "HEAD")
        
        return when {
            headResult.isReachable && headResult.statusCode == 200 -> headResult
            headResult.statusCode == 405 || headResult.statusCode == 501 -> performRequest(url, "GET")
            else -> headResult
        }
    }

    private fun performRequest(url: String, method: String): ReachabilityCheckResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMillis))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .header("User-Agent", "UrlShortener-Bot/1.0")
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            val responseTime = System.currentTimeMillis() - startTime

            when (response.statusCode()) {
                in 200..299 -> ReachabilityCheckResult(
                    isReachable = true,
                    statusCode = response.statusCode(),
                    responseTimeMs = responseTime,
                    contentType = response.headers().firstValue("Content-Type").orElse(null)
                )
                in 300..399 -> ReachabilityCheckResult(
                    isReachable = true, 
                    statusCode = response.statusCode(),
                    responseTimeMs = responseTime
                )
                else -> ReachabilityCheckResult(
                    isReachable = false,
                    statusCode = response.statusCode(),
                    responseTimeMs = responseTime,
                    errorType = "HTTP_${response.statusCode()}"
                )
            }
        } catch (e: HttpTimeoutException) {
            ReachabilityCheckResult(
                isReachable = false,
                errorType = "TIMEOUT",
                responseTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: UnknownHostException) {
            ReachabilityCheckResult(
                isReachable = false,
                errorType = "DNS_ERROR",
                responseTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: IOException) {
            // Check if cause is UnknownHostException (common in some JDK versions)
            if (e.cause is UnknownHostException) {
                ReachabilityCheckResult(
                    isReachable = false,
                    errorType = "DNS_ERROR",
                    responseTimeMs = System.currentTimeMillis() - startTime
                )
            } else {
                logger.warn { "Network error checking $url: ${e.message}" }
                ReachabilityCheckResult(
                    isReachable = false,
                    errorType = "NETWORK_ERROR",
                    responseTimeMs = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            logger.warn { "Unexpected error checking $url: ${e.message}" }
            ReachabilityCheckResult(
                isReachable = false,
                errorType = "NETWORK_ERROR",
                responseTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    private fun getFromCache(url: String): ReachabilityCheckResult? {
        val cacheKey = "reachability:${hashUrl(url)}"
        val json = redisTemplate.opsForValue().get(cacheKey) ?: return null
        return try {
            objectMapper.readValue(json, ReachabilityCheckResult::class.java)
        } catch (e: Exception) { 
            // Fix: Delete invalid cache entry to satisfy test expectations and prevent stuck bad data
            logger.warn { "Failed to deserialize cached result for $url, deleting key." }
            redisTemplate.delete(cacheKey)
            null 
        }
    }

    private fun saveToCache(url: String, result: ReachabilityCheckResult) {
        val cacheKey = "reachability:${hashUrl(url)}"
        val json = objectMapper.writeValueAsString(result)
        redisTemplate.opsForValue().set(cacheKey, json, Duration.ofMinutes(cacheTtlMinutes))
    }
    
    private fun hashUrl(url: String) = java.util.Base64.getEncoder().encodeToString(url.toByteArray())
}

/**
 * ZXing-based implementation of [QrCodeService].
 */
class QrCodeServiceImpl : QrCodeService {

    override fun generateFor(url: String, size: Int, format: QrFormat): ByteArray = try {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to StandardCharsets.UTF_8.name(),
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, size, size, hints)

        when (format) {
            is QrFormat.PNG -> generatePng(bitMatrix, size)
            is QrFormat.JPEG -> generateJpeg(bitMatrix, size)
            is QrFormat.SVG -> generateSvg(bitMatrix, size)
        }
    } catch (e: Exception) {
        throw InternalError("Failed to generate QR code", e)
    }

    private fun generatePng(bitMatrix: com.google.zxing.common.BitMatrix, size: Int): ByteArray {
        return ByteArrayOutputStream().use { baos ->
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos, MatrixToImageConfig())
            baos.toByteArray()
        }
    }

    private fun generateJpeg(bitMatrix: com.google.zxing.common.BitMatrix, size: Int): ByteArray {
        return ByteArrayOutputStream().use { baos ->
            val image = MatrixToImageWriter.toBufferedImage(bitMatrix)
            ImageIO.write(image, "JPEG", baos)
            baos.toByteArray()
        }
    }

    private fun generateSvg(bitMatrix: com.google.zxing.common.BitMatrix, size: Int): ByteArray {
        val writer = StringWriter()
        writer.write("""<?xml version="1.0" encoding="UTF-8"?>""")
        writer.write("""<svg xmlns="http://www.w3.org/2000/svg" version="1.1" width="$size" height="$size">""")
        writer.write("""<rect width="100%" height="100%" fill="white"/>""")
        
        for (x in 0 until size) {
            for (y in 0 until size) {
                if (bitMatrix.get(x, y)) {
                    writer.write("""<rect x="$x" y="$y" width="1" height="1" fill="black"/>""")
                }
            }
        }
        
        writer.write("</svg>")
        return writer.toString().toByteArray(Charsets.UTF_8)
    }
}

/**
 * HTTP-based implementation of [UrlSafeBrowsingService].
 *
 * Checks if a URL is Safe return UrlSafety.Safe or UrlSafety.Unsafe using
 * Google Safe Browsing API.
 *  
 * @param apiKey The API key for Google Safe Browsing.
 * @param apiUrl The base URL for the Safe Browsing API.
 * 
 */
@Service
class UrlSafeBrowsingServiceImpl(
    @param:Value("\${safebrowsing.apiKey}") private val apiKey: String,
    @param:Value("\${safebrowsing.apiUrl}") private val apiUrl: String,
) : UrlSafeBrowsingService {

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    override fun checkUrl(url: String): Boolean {
        return try {
            val responseBody = callSafeBrowsingApi(url)
            interpretSafeBrowsingResponse(responseBody) 
        } catch (e: Exception) {
            false 
        }    
    }

    private fun callSafeBrowsingApi(url: String): String {
        val requestJson = """
            {
                "client": {
                    "clientId": "DREAMY_URL",
                    "clientVersion": "1.0"
                },
                "threatInfo": {
                    "threatTypes": ["MALWARE","SOCIAL_ENGINEERING","UNWANTED_SOFTWARE","POTENTIALLY_HARMFUL_APPLICATION"],
                    "platformTypes": ["ANY_PLATFORM"],
                    "threatEntryTypes": ["URL"],
                    "threatEntries": [
                        {"url": "$url"}
                    ]
                }
            }
        """.trimIndent()

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$apiUrl?key=$apiKey"))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestJson))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        return response.body()
    }

    private fun interpretSafeBrowsingResponse(responseBody: String): Boolean {
        return !responseBody.contains("matches") 
    }
}

/**
 *  Implementation of [UrlValidationJobService].
 *
 * Acts as the **Orchestrator** for asynchronous validation.
 * Responsibilities:
 * - Persist Job state (Jobs Map).
 * - Publish messages to RabbitMQ.
 * - Canonicalize URLs before processing.
 * 
 * This service works in conjunction with the QueueWorker to perform
 * the asynchronous URL validation pipeline, including
 * reachability checks and safety checks using the 
 * Google Safe Browsing API.
 *  
 * @param queueName The RabbitMQ queue name for URL validation messages.
 * @param rabbitTemplate The RabbitMQ template for publishing messages.
 * @param objectMapper The Jackson ObjectMapper for JSON serialization.
 * @param validatorService The ValidatorService for URL validation.
 * 
 */ 
@Service
class UrlValidationJobServiceImpl(
    @param:Value("\${safebrowsing.rabbitQueue}") private val queueName: String,
    private val rabbitTemplate: RabbitTemplate,
    private val objectMapper: ObjectMapper,
    private val validatorService: ValidatorService
) : UrlValidationJobService {

    private val jobs = mutableMapOf<String, UrlValidationJob>()

    override fun enqueueValidation(message: UrlValidationMessage): Boolean {
        return try {
            val canonical = canonicalizeUrl(message.url.toString())
            val urlObj = Url(canonical)
            
            val job = UrlValidationJob(
                id = message.id,
                url = urlObj,
                status = UrlSafety.Pending,
                createdAt = message.createdAt,
                retries = message.retries
            )
            jobs[job.id] = job

            val payload = objectMapper.writeValueAsString(message.copy(url = urlObj))
            rabbitTemplate.convertAndSend(queueName, payload)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun updateJobStatus(jobId: String, status: UrlSafety): Boolean {
        val job = jobs[jobId] ?: return false
        jobs[jobId] = job.copy(status = status, updatedAt = java.time.OffsetDateTime.now())
        return true
    }

    override fun findJob(jobId: String): UrlValidationJob? {
        return jobs[jobId]
    }

    private fun canonicalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (!validatorService.isValid(trimmed)) {
            throw IllegalArgumentException("El esquema de la URL no es válido")
        }

        val uri = URI(trimmed)
        val host = IDN.toASCII(uri.host.lowercase())
        val path = if (uri.path.isNullOrEmpty()) "/" else uri.path

        return URI(
            uri.scheme.lowercase(),
            uri.userInfo,
            host,
            uri.port,
            path,
            uri.query,
            null
        ).toString()
    }
}