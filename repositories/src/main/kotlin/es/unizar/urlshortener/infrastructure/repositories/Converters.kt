@file:Suppress("WildcardImport")

package es.unizar.urlshortener.infrastructure.repositories

import es.unizar.urlshortener.core.*
import java.time.OffsetDateTime

/**
 * Extension method to convert a [ClickEntity] into a domain [Click].
 */
fun ClickEntity.toDomain(): Click = Click(
    hash = UrlHash(this.hash),
    properties = ClickProperties(
        ip = this.ip?.let { IpAddress(it) },
        referrer = this.referrer,
        browser = this.browser,
        platform = this.platform,
        country = this.country
    ),
    created = this.created
)

/**
 * Extension method to convert a domain [Click] into a [ClickEntity].
 */
fun Click.toEntity() = ClickEntity(
    id = null,  // Auto-generated
    hash = this.hash.value,
    created = this.created,
    ip = this.properties.ip?.value,
    referrer = this.properties.referrer,
    browser = this.properties.browser,
    platform = this.properties.platform,
    country = this.properties.country
)

/**
 * Extension method to convert a [ShortUrlEntity] into a domain [ShortUrl].
 */
fun ShortUrlEntity.toDomain() = ShortUrl(
    hash = UrlHash(hash),
    redirection = Redirection(
        target = Url(target),
        type = when (mode) {
            HttpStatusCodes.PERMANENT_REDIRECT -> RedirectionType.Permanent
            else -> RedirectionType.Temporary
        }
    ),
    created = created,
    properties = ShortUrlProperties(
        sponsor = sponsor?.let { Sponsor(it) },
        owner = owner?.let { Owner(it) },
        safety = when (safe) {
            true -> UrlSafety.Safe
            false -> UrlSafety.Unsafe
        },
        ip = ip?.let { IpAddress(it) },
        country = country?.let { CountryCode(it) },
        
        accessible = accessible,
        validatedAt = validatedAt,
        validationResult = if (validationStatusCode != null) {
            ValidationResult(
                statusCode = validationStatusCode,
                responseTime = validationResponseTime,
                contentType = validationContentType
            )
        } else null
    )
)

/**
 * Extension method to convert a domain [ShortUrl] into a [ShortUrlEntity].
 */
fun ShortUrl.toEntity() = ShortUrlEntity(
    hash = hash.value,
    target = redirection.target.value,
    mode = redirection.statusCode,
    created = created,
    owner = properties.owner?.value,
    sponsor = properties.sponsor?.value,
    safe = properties.safety == UrlSafety.Safe,
    ip = properties.ip?.value,
    country = properties.country?.value,
    
    accessible = properties.accessible,
    validatedAt = properties.validatedAt,
    validationStatusCode = properties.validationResult?.statusCode,
    validationResponseTime = properties.validationResult?.responseTime,
    validationContentType = properties.validationResult?.contentType
)