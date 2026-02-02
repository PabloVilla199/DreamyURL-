package es.unizar.urlshortener.core

import java.time.Instant
import org.springframework.context.ApplicationEvent

/**
 * Evento que representa un clic en una URL acortada.
 */
class ClickEvent(
    val shortUrlId: String,
    val ip: String?,
    val referrer: String?,
    val browser: String?,
    val platform: String?,
    val timestamp: Instant,
    val country: String?
) : ApplicationEvent(shortUrlId) {
    
    // Override equals, hashCode, toString para mantener comportamiento de data class
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as ClickEvent
        
        if (shortUrlId != other.shortUrlId) return false
        if (ip != other.ip) return false
        if (referrer != other.referrer) return false
        if (browser != other.browser) return false
        if (platform != other.platform) return false
        if (timestamp != other.timestamp) return false
        if (country != other.country) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = shortUrlId.hashCode()
        result = 31 * result + (ip?.hashCode() ?: 0)
        result = 31 * result + (referrer?.hashCode() ?: 0)
        result = 31 * result + (browser?.hashCode() ?: 0)
        result = 31 * result + (platform?.hashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (country?.hashCode() ?: 0)
        return result
    }
    
    override fun toString(): String {
        return "ClickEvent(shortUrlId='$shortUrlId', ip=$ip, referrer=$referrer, " +
               "browser=$browser, platform=$platform, timestamp=$timestamp, country=$country)"
    }
    
    // Función copy para mantener funcionalidad de data class
    fun copy(
        shortUrlId: String = this.shortUrlId,
        ip: String? = this.ip,
        referrer: String? = this.referrer,
        browser: String? = this.browser,
        platform: String? = this.platform,
        timestamp: Instant = this.timestamp,
        country: String? = this.country
    ) = ClickEvent(shortUrlId, ip, referrer, browser, platform, timestamp, country)
}
