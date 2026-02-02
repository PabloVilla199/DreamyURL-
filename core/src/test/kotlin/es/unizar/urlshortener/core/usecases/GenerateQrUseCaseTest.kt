package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.*
import org.mockito.Mockito.mock
import org.mockito.kotlin.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GenerateQrUseCaseTest {

    @Test
    fun `generate returns png bytes when service succeeds`() {
        val qrService = mock(QrCodeService::class.java)
        val qrRepository = mock(QrCodeRepositoryService::class.java)
        val expected = byteArrayOf(1, 2, 3, 4)
        
        whenever(qrRepository.find(any(), any(), any())).thenReturn(null)
        whenever(qrService.generateFor("http://example.com/", 200, QrFormat.PNG)).thenReturn(expected)
        
        val useCase = GenerateQrUseCaseImpl(qrService, qrRepository)
        val result = useCase.generate("http://example.com/")
        
        assertEquals(expected.toList(), result.toList())
    }

    @Test
    fun `generate returns svg bytes when svg format requested`() {
        val qrService = mock(QrCodeService::class.java)
        val qrRepository = mock(QrCodeRepositoryService::class.java)
        val expected = "<svg>test</svg>".toByteArray()
        
        whenever(qrRepository.find(any(), any(), any())).thenReturn(null)
        whenever(qrService.generateFor("http://example.com/", 200, QrFormat.SVG)).thenReturn(expected)
        
        val useCase = GenerateQrUseCaseImpl(qrService, qrRepository)
        val result = useCase.generate("http://example.com/", 200, QrFormat.SVG)
        
        assertEquals(expected.toList(), result.toList())
    }

    @Test
    fun `generate throws InternalError for size exceeding maximum`() {
        val qrService = mock(QrCodeService::class.java)
        val qrRepository = mock(QrCodeRepositoryService::class.java)
        val useCase = GenerateQrUseCaseImpl(qrService, qrRepository)
        
        assertFailsWith<InternalError> {
            useCase.generate("http://example.com/", 1500)
        }
    }

    @Test
    fun `generate throws InvalidInputException for blank url`() {
        val qrService = mock(QrCodeService::class.java)
        val qrRepository = mock(QrCodeRepositoryService::class.java)
        val useCase = GenerateQrUseCaseImpl(qrService, qrRepository)
        
        assertFailsWith<es.unizar.urlshortener.core.InvalidInputException> {
            useCase.generate("   ")
        }
    }

    @Test
    fun `generate throws InternalError for non positive size`() {
        val qrService = mock(QrCodeService::class.java)
        val qrRepository = mock(QrCodeRepositoryService::class.java)
        val useCase = GenerateQrUseCaseImpl(qrService, qrRepository)
        
        assertFailsWith<InternalError> {
            useCase.generate("http://example.com/", 0)
        }
    }

    @Test
    fun `generate throws InternalError for too large size`() {
        val qrService = mock(QrCodeService::class.java)
        val qrRepository = mock(QrCodeRepositoryService::class.java)
        val useCase = GenerateQrUseCaseImpl(qrService, qrRepository)
        
        assertFailsWith<InternalError> {
            useCase.generate("http://example.com/", 5000)
        }
    }

    @Test
    fun `generate wraps infra errors as InternalError`() {
        val qrService = mock(QrCodeService::class.java)
        val qrRepository = mock(QrCodeRepositoryService::class.java)
        
        whenever(qrRepository.find(any(), any(), any())).thenReturn(null)
        whenever(qrService.generateFor("http://example.com/", 200)).thenThrow(RuntimeException("boom"))
        
        val useCase = GenerateQrUseCaseImpl(qrService, qrRepository)
        
        assertFailsWith<InternalError> {
            useCase.generate("http://example.com/")
        }
    }

    @Test
    fun `generate returns cached value if present (Cache Hit)`() {
        val qrService = mock(QrCodeService::class.java)
        val qrRepository = mock(QrCodeRepositoryService::class.java)
        val cachedBytes = byteArrayOf(9, 9, 9)

        whenever(qrRepository.find("http://example.com/", 200, QrFormat.PNG))
            .thenReturn(cachedBytes)

        val useCase = GenerateQrUseCaseImpl(qrService, qrRepository)
        val result = useCase.generate("http://example.com/")

        assertEquals(cachedBytes.toList(), result.toList())
        
        verify(qrService, never()).generateFor(any(), any(), any())
    }

    @Test
    fun `generate calls service and saves to cache if missing (Cache Miss)`() {
        val qrService = mock(QrCodeService::class.java)
        val qrRepository = mock(QrCodeRepositoryService::class.java)
        val generatedBytes = byteArrayOf(1, 2, 3, 4)

        whenever(qrRepository.find("http://example.com/", 200, QrFormat.PNG))
            .thenReturn(null)
        
        whenever(qrService.generateFor("http://example.com/", 200, QrFormat.PNG))
            .thenReturn(generatedBytes)

        val useCase = GenerateQrUseCaseImpl(qrService, qrRepository)
        val result = useCase.generate("http://example.com/")

        assertEquals(generatedBytes.toList(), result.toList())

        verify(qrService).generateFor("http://example.com/", 200, QrFormat.PNG)
        verify(qrRepository).save("http://example.com/", 200, QrFormat.PNG, generatedBytes)
    }

    @Test
    fun `generate handles repository errors gracefully`() {
        val qrService = mock(QrCodeService::class.java)
        val qrRepository = mock(QrCodeRepositoryService::class.java)
        val generatedBytes = byteArrayOf(1, 1)

        whenever(qrRepository.find(any(), any(), any())).thenThrow(RuntimeException("Redis Error"))
        whenever(qrService.generateFor(any(), any(), any())).thenReturn(generatedBytes)

        val useCase = GenerateQrUseCaseImpl(qrService, qrRepository)
        
        val result = useCase.generate("http://example.com/")

        assertEquals(generatedBytes.toList(), result.toList())
        verify(qrService).generateFor(any(), any(), any())
    }
}