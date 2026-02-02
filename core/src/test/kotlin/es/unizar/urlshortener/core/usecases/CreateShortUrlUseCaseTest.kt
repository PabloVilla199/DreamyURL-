package es.unizar.urlshortener.core.usecases

import es.unizar.urlshortener.core.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class CreateShortUrlUseCaseTest {

    private val shortUrlRepository: ShortUrlRepositoryService = mock()
    private val validatorService: ValidatorService = mock()
    private val hashService: HashService = mock()
    private val urlValidationJobService: UrlValidationJobService = mock()

    private lateinit var useCase: CreateShortUrlUseCaseImpl

    @BeforeEach
    fun setUp() {
        useCase = CreateShortUrlUseCaseImpl(
            shortUrlRepository,
            validatorService,
            hashService,
            urlValidationJobService
        )
    }

    private val targetUrl = "http://example.com/"
    private val jobId = "job-123"
    private val properties = ShortUrlProperties(
        ip = IpAddress("127.0.0.1"),
        safety = UrlSafety.Unknown
    )

    @Nested
    inner class CreateMethodTests {
        
        @Test
        fun `create enqueues validation and returns message when URL is valid`() {
            whenever(validatorService.isValid(targetUrl)).thenReturn(true)
            whenever(urlValidationJobService.enqueueValidation(any())).thenReturn(true)

            val result = useCase.create(targetUrl, properties)

            assertNotNull(result.id)
            assertEquals(targetUrl, result.url.value)
            assertEquals(ValidationStep.CHECK_REACHABILITY, result.step)
            verify(urlValidationJobService).enqueueValidation(any())
        }

        @Test
        fun `create throws InvalidUrlException when URL is invalid`() {
            whenever(validatorService.isValid(targetUrl)).thenReturn(false)

            assertFailsWith<InvalidUrlException> {
                useCase.create(targetUrl, properties)
            }
            verify(urlValidationJobService, never()).enqueueValidation(any())
        }

        @Test
        fun `create throws InvalidInputException when URL is blank`() {
            assertFailsWith<InvalidInputException> {
                useCase.create("   ", properties)
            }
        }

        @Test
        fun `create throws MessagueQueueException when enqueue returns false`() {
            whenever(validatorService.isValid(targetUrl)).thenReturn(true)
            whenever(urlValidationJobService.enqueueValidation(any())).thenReturn(false)

            assertFailsWith<MessagueQueueException> {
                useCase.create(targetUrl, properties)
            }
        }

        @Test
        fun `create throws MessagueQueueException when enqueue throws exception`() {
            whenever(validatorService.isValid(targetUrl)).thenReturn(true)
            whenever(urlValidationJobService.enqueueValidation(any())).thenThrow(RuntimeException("Broker down"))

            assertFailsWith<MessagueQueueException> {
                useCase.create(targetUrl, properties)
            }
        }
    }

    @Nested
    inner class FinalizeIfSafeMethodTests {

        private fun mockJob(status: UrlSafety): UrlValidationJob {
            return UrlValidationJob(
                id = jobId,
                url = Url(targetUrl),
                status = status,
                createdAt = OffsetDateTime.now()
            )
        }

        @Test
        fun `finalizeIfSafe returns job as is when status is Pending`() {
            whenever(urlValidationJobService.findJob(jobId)).thenReturn(mockJob(UrlSafety.Pending))

            val result = useCase.finalizeIfSafe(jobId)

            assertEquals(UrlSafety.Pending, result.status)
            verify(shortUrlRepository, never()).save(any())
        }

        @Test
        fun `finalizeIfSafe throws SafeBrowsingException when job not found`() {
            whenever(urlValidationJobService.findJob(jobId)).thenReturn(null)

            assertFailsWith<SafeBrowsingException> {
                useCase.finalizeIfSafe(jobId)
            }
        }

        @Test
        fun `finalizeIfSafe throws UrlNotSafeException when status is Unsafe`() {
            whenever(urlValidationJobService.findJob(jobId)).thenReturn(mockJob(UrlSafety.Unsafe))

            assertFailsWith<UrlNotSafeException> {
                useCase.finalizeIfSafe(jobId)
            }
        }

        @Test
        fun `finalizeIfSafe throws UrlNotReachableException when status is Unreachable`() {
            whenever(urlValidationJobService.findJob(jobId)).thenReturn(mockJob(UrlSafety.Unreachable))

            assertFailsWith<UrlNotReachableException> {
                useCase.finalizeIfSafe(jobId)
            }
        }

        @Test
        fun `finalizeIfSafe throws SafeBrowsingException when status is Unknown`() {
            whenever(urlValidationJobService.findJob(jobId)).thenReturn(mockJob(UrlSafety.Unknown))

            assertFailsWith<SafeBrowsingException> {
                useCase.finalizeIfSafe(jobId)
            }
        }

        @Test
        fun `finalizeIfSafe saves ShortUrl and returns job when status is Safe`() {
            val safeJob = mockJob(UrlSafety.Safe)
            val hash = "hashedUrl"
            
            whenever(urlValidationJobService.findJob(jobId)).thenReturn(safeJob)
            whenever(hashService.hashUrl(targetUrl)).thenReturn(hash)
            whenever(shortUrlRepository.findByKey(UrlHash(hash))).thenReturn(null) 

            val result = useCase.finalizeIfSafe(jobId)

            assertEquals(UrlSafety.Safe, result.status)
            
            verify(shortUrlRepository).save(argThat { 
                this.hash.value == hash && 
                this.properties.safety == UrlSafety.Safe && 
                this.properties.accessible 
            })
        }

        @Test
        fun `finalizeIfSafe returns job without saving if ShortUrl already exists (Idempotency)`() {
            val safeJob = mockJob(UrlSafety.Safe)
            val hash = "hashedUrl"
            val existingShortUrl = ShortUrl(UrlHash(hash), Redirection(Url(targetUrl)))

            whenever(urlValidationJobService.findJob(jobId)).thenReturn(safeJob)
            whenever(hashService.hashUrl(targetUrl)).thenReturn(hash)
            whenever(shortUrlRepository.findByKey(UrlHash(hash))).thenReturn(existingShortUrl)

            val result = useCase.finalizeIfSafe(jobId)

            assertEquals(UrlSafety.Safe, result.status)
            verify(shortUrlRepository, never()).save(any())
        }

        @Test
        fun `finalizeIfSafe throws InternalError when repository fails during save`() {
            val safeJob = mockJob(UrlSafety.Safe)
            whenever(urlValidationJobService.findJob(jobId)).thenReturn(safeJob)
            whenever(hashService.hashUrl(targetUrl)).thenReturn("hash")
            whenever(shortUrlRepository.findByKey(any())).thenReturn(null)
            
            whenever(shortUrlRepository.save(any())).thenThrow(RuntimeException("DB Error"))

            assertFailsWith<InternalError> {
                useCase.finalizeIfSafe(jobId)
            }
        }
    }
}
