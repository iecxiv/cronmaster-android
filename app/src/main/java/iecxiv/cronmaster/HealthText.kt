package iecxiv.cronmaster

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

val Health.label: String
    @Composable get() = when(status) {
        Health.Status.LOGGING_DISABLED -> stringResource(R.string.task_health_logging_disabled)
        Health.Status.NO_DATA -> stringResource(R.string.task_health_no_data)
        Health.Status.FAILED -> stringResource(R.string.task_health_failed_exit,exitCode?.toString() ?: "?")
        Health.Status.SUCCEEDED -> stringResource(R.string.task_health_succeeded)
        Health.Status.SUCCEEDED_WITH_HISTORY -> stringResource(R.string.task_health_succeeded_history)
        Health.Status.NO_RECENT_RESULT_WITH_HISTORY -> stringResource(R.string.task_health_no_recent_result_history)
        Health.Status.NO_RESULT -> stringResource(R.string.task_health_no_result)
    }
