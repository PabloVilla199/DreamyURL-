package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.RateLimiterService
import es.unizar.urlshortener.core.RateLimitStatus
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.OffsetDateTime




/**
 * Implementation of [RateLimiterService] using Bucket4j.
 * Implements a token bucket algorithm to limit the rate of
 * Google Safe Browsing API calls.
 * 
 * The bucket is configured with a maximum capacity, refill tokens,
 * and refill interval, all provided via application properties.
 * 
 * @param perSecondCapacity Maximum number of tokens in the bucket.
 * @param perSecondRefillTokens Number of tokens to add during each refill.
 * @param perSecondRefillSeconds Time period (in seconds) for each refill.
 */
@Service
class RateLimiterServiceImpl(
    @Value("\${safebrowsing.ratelimit.perSecondCapacity}") private val perSecondCapacity: Long,
    @Value("\${safebrowsing.ratelimit.perSecondRefillTokens}") private val perSecondRefillTokens: Long,
    @Value("\${safebrowsing.ratelimit.perSecondRefillSeconds}") private val perSecondRefillSeconds: Long
) : RateLimiterService {

    private val bucket: Bucket = Bucket.builder()
        .addLimit(
            Bandwidth.classic(
                perSecondCapacity, 
                Refill.intervally(perSecondRefillTokens, Duration.ofSeconds(perSecondRefillSeconds))
            )
        )
        .build()

    override fun tryConsume(): Boolean {
        return bucket.tryConsume(1)
    }

    override fun getStatus(): RateLimitStatus {
        val remainingTokens = bucket.availableTokens
        val probe = bucket.estimateAbilityToConsume(1)
        val nanosToWait = probe.nanosToWaitForRefill

        val resetTime =
            if (nanosToWait > 0)
                OffsetDateTime.now().plusNanos(nanosToWait)
            else
                OffsetDateTime.now()

        return RateLimitStatus(
            remainingTokens = remainingTokens,
            resetTime = resetTime,
            isLimitExceeded = remainingTokens == 0L
        )
    }
}
