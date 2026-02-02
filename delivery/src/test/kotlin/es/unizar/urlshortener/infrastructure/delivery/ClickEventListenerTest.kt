package es.unizar.urlshortener.infrastructure.delivery

import org.mockito.kotlin.any
import es.unizar.urlshortener.core.ClickEvent
import es.unizar.urlshortener.core.GeoService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class ClickEventListenerTest {
    private val geoService: GeoService = mock()

    private val listener = ClickEventListener(geoService)

    @Test
    fun `handleClickEvent should call geoService processClick`() {
        val event = ClickEvent(
            shortUrlId = "xyz789",
            ip = "8.8.8.8",
            timestamp = Instant.now(),
            referrer = null,
            browser = null,
            platform = null,
            country = "US"
        )

        listener.handleClickEvent(event)

        verify(geoService).processClick(event)
    }

    @Test
    fun `handleClickEvent logs error when processing fails`() {
        val event = ClickEvent(
            shortUrlId = "fail", ip = "1.1.1.1", timestamp = Instant.now(),
            referrer = null, browser = null, platform = null, country = null
        )

        whenever(geoService.processClick(any())).thenThrow(RuntimeException("Geo Error"))

        listener.handleClickEvent(event)

        verify(geoService).processClick(any())
    }
}