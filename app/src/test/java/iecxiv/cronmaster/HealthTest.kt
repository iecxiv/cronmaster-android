package iecxiv.cronmaster
import org.junit.Assert.*
import org.junit.Test

class HealthTest {
    @Test fun successWithOldFailuresIsGreen() {
        val j=obj("logsEnabled" to true,"logError" to obj("hasError" to false,"hasHistoricalFailures" to true,"latestExitCode" to 0,"exitCode" to 1))
        assertEquals(Health.Kind.GOOD,health(j).kind)
    }
    @Test fun latestFailureIsRed() {
        val j=obj("logsEnabled" to true,"logError" to obj("hasError" to true,"latestExitCode" to 2))
        assertEquals(Health.Kind.BAD,health(j).kind)
    }
    @Test fun incompleteLogIsNotGreen() {
        val j=obj("logsEnabled" to true,"logError" to obj("hasError" to false,"latestExitCode" to null))
        assertEquals(Health.Kind.UNKNOWN,health(j).kind)
    }
    @Test fun disabledLogsAreNotFailure() { assertEquals(Health.Kind.UNKNOWN,health(obj("logsEnabled" to false)).kind) }
}
