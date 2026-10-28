package iecxiv.cronmaster

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException

@Composable
internal fun CronmasterBrandIcon(bitmap: Bitmap?, modifier: Modifier = Modifier) {
    if (bitmap == null) {
        Image(painterResource(R.drawable.ic_launcher), null, modifier, contentScale = ContentScale.Fit)
    } else {
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        Image(image, null, modifier, contentScale = ContentScale.Crop)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CronmasterAppBar(
    appearance: AppAppearanceViewModel,
    section: String,
    onOpenSettings: () -> Unit,
    onRefresh: (() -> Unit)?,
    refreshEnabled: Boolean
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onOpenSettings, modifier = Modifier.semantics { contentDescription = "Abrir ajustes" }) {
                CronmasterBrandIcon(appearance.iconBitmap, Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
            }
        },
        title = {
            Column {
                Text(appearance.displayName, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(section, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        },
        actions = {
            if (onRefresh != null) IconButton(onClick = onRefresh, enabled = refreshEnabled) {
                Icon(Icons.Default.Refresh, "Actualizar datos")
            }
            if (!appearance.hideSettingsIcon) IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, "Abrir ajustes")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Suppress("DEPRECATION")
@Composable
internal fun ApplyAppearanceToTask(appearance: AppAppearanceViewModel) {
    val activity = LocalContext.current.findActivity()
    val name = appearance.displayName
    val bitmap = appearance.iconBitmap
    LaunchedEffect(activity, name, bitmap) {
        try {
            activity?.title = name
            activity?.setTaskDescription(ActivityManager.TaskDescription(name, bitmap))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Header and shortcut customization remain available. */ }
    }
}
