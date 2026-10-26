package iecxiv.cronmaster
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CancellationTest {
    @Test(expected=CancellationException::class)
    fun cancellationIsRethrown(): Unit=runBlocking {
        safely<Unit> { throw CancellationException("normal lifecycle cancellation") }
        Unit
    }
    @Test fun networkErrorsAreResults(): Unit=runBlocking {
        assertTrue(safely<Unit> { throw IOException("offline") }.isFailure)
    }
}
