package iecxiv.cronmaster

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsInformationCard() {
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.4f))) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.Top) {
            Icon(Icons.Default.Info,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=2.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_personalization_note),style=MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.task_status_information),style=MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.session_information),style=MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.launcher_hint),style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
internal fun SettingsVersionFooter() {
    val context=LocalContext.current
    val uriHandler=LocalUriHandler.current
    val versionName=remember(context) {
        try {context.packageManager.getPackageInfo(context.packageName,0).versionName?.takeIf {it.isNotBlank()}}
        catch(_: Exception) {null}
    }
    val repository="https://github.com/iecxiv/cronmaster-android"
    Column(Modifier.fillMaxWidth().padding(top=8.dp,bottom=16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(if(versionName!=null) stringResource(R.string.version_label,versionName) else stringResource(R.string.version_unavailable),
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(repository,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,
            modifier=Modifier.clip(RoundedCornerShape(6.dp)).clickable {try {uriHandler.openUri(repository)} catch(_: Exception) {}}.padding(horizontal=8.dp,vertical=4.dp))
    }
}
