package es.unizar.urlshortener.core

import java.time.Instant

data class ClickInfo(
    val shortUrlId: String,
    val ip: String?, 
    val country: String?, 
    val referrer: String?, 
    val browser: String?, 
    val platform: String?, 
    val timestamp: Instant
)