package iecxiv.cronmaster
import org.junit.Assert.*
import org.junit.Test

class HealthTest {
    @Test fun successWithOldFailuresIsGreen() {
        val j=obj("logsEnabled" to true,"logError" to obj("hasError" to false,"hasHistoricalFailures" to true,"latestExitCode" to 0,"exitCode" to 1))
        val state=health(j)
        assertEquals(Health.Kind.GOOD,state.kind)
        assertEquals(Health.Status.SUCCEEDED_WITH_HISTORY,state.status)
        assertEquals(0,state.exitCode)
    }
    @Test fun latestFailureIsRed() {
        val j=obj("logsEnabled" to true,"logError" to obj("hasError" to true,"latestExitCode" to 2))
        val state=health(j)
        assertEquals(Health.Kind.BAD,state.kind)
        assertEquals(Health.Status.FAILED,state.status)
        assertEquals(2,state.exitCode)
    }
    @Test fun incompleteLogIsNotGreen() {
        val j=obj("logsEnabled" to true,"logError" to obj("hasError" to false,"latestExitCode" to null))
        val state=health(j)
        assertEquals(Health.Kind.UNKNOWN,state.kind)
        assertEquals(Health.Status.NO_RESULT,state.status)
    }
    @Test fun disabledLogsAreNotFailure() {
        val state=health(obj("logsEnabled" to false))
        assertEquals(Health.Kind.UNKNOWN,state.kind)
        assertEquals(Health.Status.LOGGING_DISABLED,state.status)
    }
    @Test fun missingLoggingFlagRemainsDisabled() {
        val state=health(obj())
        assertEquals(Health.Kind.UNKNOWN,state.kind)
        assertEquals(Health.Status.LOGGING_DISABLED,state.status)
    }
    @Test fun enabledLoggingWithoutDataIsUnknown() {
        val state=health(obj("logsEnabled" to true))
        assertEquals(Health.Kind.UNKNOWN,state.kind)
        assertEquals(Health.Status.NO_DATA,state.status)
    }
    @Test fun successfulExitWithoutHistoryIsGreen() {
        val state=health(obj("logsEnabled" to true,"logError" to obj("latestExitCode" to 0)))
        assertEquals(Health.Kind.GOOD,state.kind)
        assertEquals(Health.Status.SUCCEEDED,state.status)
        assertEquals(0,state.exitCode)
    }
    @Test fun historicalFailuresWithoutRecentResultRemainUnknown() {
        val state=health(obj("logsEnabled" to true,"logError" to obj("hasHistoricalFailures" to true)))
        assertEquals(Health.Kind.UNKNOWN,state.kind)
        assertEquals(Health.Status.NO_RECENT_RESULT_WITH_HISTORY,state.status)
    }
    @Test fun nonzeroExitIsFailureEvenWithoutErrorFlag() {
        val state=health(obj("logsEnabled" to true,"logError" to obj("hasError" to false,"latestExitCode" to 7)))
        assertEquals(Health.Kind.BAD,state.kind)
        assertEquals(Health.Status.FAILED,state.status)
        assertEquals(7,state.exitCode)
    }
    @Test fun failureUsesFallbackExitWhenLatestIsMissing() {
        val state=health(obj("logsEnabled" to true,"logError" to obj("hasError" to true,"exitCode" to 3)))
        assertEquals(Health.Kind.BAD,state.kind)
        assertEquals(Health.Status.FAILED,state.status)
        assertEquals(3,state.exitCode)
    }
    @Test fun failureWithoutExitKeepsUnknownCode() {
        val state=health(obj("logsEnabled" to true,"logError" to obj("hasError" to true)))
        assertEquals(Health.Kind.BAD,state.kind)
        assertEquals(Health.Status.FAILED,state.status)
        assertNull(state.exitCode)
    }
    @Test fun errorFlagStillTakesPrecedenceOverZeroExit() {
        val state=health(obj("logsEnabled" to true,"logError" to obj("hasError" to true,"latestExitCode" to 0,"exitCode" to 9)))
        assertEquals(Health.Kind.BAD,state.kind)
        assertEquals(Health.Status.FAILED,state.status)
        assertEquals(0,state.exitCode)
    }
    @Test fun disabledLoggingIgnoresFailureData() {
        val state=health(obj("logsEnabled" to false,"logError" to obj("hasError" to true,"latestExitCode" to 5)))
        assertEquals(Health.Kind.UNKNOWN,state.kind)
        assertEquals(Health.Status.LOGGING_DISABLED,state.status)
    }
}
