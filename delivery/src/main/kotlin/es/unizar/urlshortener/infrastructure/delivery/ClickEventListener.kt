package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.ClickEvent
import es.unizar.urlshortener.core.GeoService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * Event listener for processing click events asynchronously.
 */
@Component
class ClickEventListener(
    private val geoService: GeoService
) {
    private val logger = LoggerFactory.getLogger(ClickEventListener::class.java)

    @Async("geoTaskExecutor")
    @EventListener
    fun handleClickEvent(event: ClickEvent) {
        logger.info("Processing click event for shortUrlId: ${event.shortUrlId}, IP: ${event.ip}")
        try {
            geoService.processClick(event)
            logger.info("Successfully processed click event for shortUrlId: ${event.shortUrlId}")
        } catch (ex: Exception) {
            logger.warn("Failed to process click event for shortUrlId: ${event.shortUrlId}", ex)
        }
    }
}