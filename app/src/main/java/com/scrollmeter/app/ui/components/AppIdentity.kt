package com.scrollmeter.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scrollmeter.app.apps.AppInfo
import com.scrollmeter.app.apps.AppInfoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Label and icon of [packageName], loaded off the main thread; null while loading or for [skip]. */
@Composable
fun rememberAppInfo(provider: AppInfoProvider, packageName: String, size: Dp, skip: Boolean = false): State<AppInfo?> {
    val iconPx = with(LocalDensity.current) { size.roundToPx() }
    return produceState<AppInfo?>(initialValue = null, packageName, iconPx, skip) {
        value = if (skip) null else withContext(Dispatchers.IO) { provider.load(packageName, iconPx) }
    }
}

/** The app's icon, or its initial in a circle when the icon is unknown (spec §15). */
@Composable
fun AppIcon(info: AppInfo?, label: String, size: Dp = 36.dp) {
    val icon = info?.icon
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(label.take(1).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}
