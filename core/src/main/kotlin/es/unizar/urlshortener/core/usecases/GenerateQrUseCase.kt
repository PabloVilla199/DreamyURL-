@file:Suppress("WildcardImport")

package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.*
import es.unizar.urlshortener.core.sanitizeInput
import es.unizar.urlshortener.core.InternalError
import es.unizar.urlshortener.core.safeCall

/**
 * Use case for generating QR images for given URLs.
 *
 * Keeps domain logic (validation, input sanitization, orchestration) inside core.
 */
interface GenerateQrUseCase {
    /**
     * Generate a PNG QR image for the provided [url] with optional [size].
     * @return PNG bytes.
     */
    fun generate(url: String, size: Int = 200, format: QrFormat = QrFormat.PNG): ByteArray
}

/**
 * Implementation of [GenerateQrUseCase] delegating to [QrCodeService].
 */
class GenerateQrUseCaseImpl(
    private val qrCodeService: QrCodeService,
    private val qrCodeRepository: QrCodeRepositoryService
) : GenerateQrUseCase {

    override fun generate(url: String, size: Int, format: QrFormat): ByteArray {
        // Sanitize basic inputs
        val sanitizedUrl = sanitizeInput(url, "url", 2048)
        val sanitizedSize = when {
            size <= 0 -> throw InternalError("Invalid QR size: $size")
            size > 1000 -> throw InternalError("Requested QR size $size exceeds maximum allowed (1000)")
            else -> size
        }

        val cached = runCatching { 
            qrCodeRepository.find(sanitizedUrl, sanitizedSize, format) 
        }.getOrNull()

        if (cached != null) {
            return cached
        }

        val generatedBytes = safeCall {
            qrCodeService.generateFor(sanitizedUrl, sanitizedSize, format)
        }

        runCatching {
            qrCodeRepository.save(sanitizedUrl, sanitizedSize, format, generatedBytes)
            println(">>> CACHE SAVED: Imagen guardada en Redis correctamente")
        }.onFailure { e ->
            println(">>> ERROR REDIS: No se pudo guardar en caché.")
            e.printStackTrace() 
        }

        return generatedBytes
    }
}