// SPDX-License-Identifier: GPL-3.0-only
package com.verisonder.sonderrotate

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner

private const val SHIZUKU_REQUEST = 1001

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SonderRotateTheme { HomeScreen() } }
    }
}

/**
 * Material You proper: on Android 12 and up the colours come from the user's wallpaper, so the app
 * matches the phone rather than imposing a palette of its own. Older versions get a fixed scheme,
 * since there is nothing to derive one from.
 */
@Composable
private fun SonderRotateTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    // Both the permission and Shizuku are granted in other apps, so the only reliable moment to
    // re-read them is when this screen comes back to the foreground.
    var refresh by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val canWrite = remember(refresh) { OrientationController.canWrite(context) }
    val auto = remember(refresh) { OrientationController.isAutoRotate(context) }
    val current = remember(refresh) { OrientationController.current(context) }
    var forceApps by remember(refresh) { mutableStateOf(Preferences.forceApps(context)) }
    val shizukuReady = remember(refresh, forceApps) { ShizukuShell.hasPermission() }
    val shizukuPresent = remember(refresh, forceApps) { ShizukuShell.isAvailable() }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(stringResource(R.string.intro), style = MaterialTheme.typography.bodyLarge)

            val state = stringResource(
                when {
                    auto -> R.string.orientation_auto
                    current == Orientation.LANDSCAPE -> R.string.orientation_landscape
                    else -> R.string.orientation_portrait
                }
            )
            Text(
                stringResource(R.string.current_state, state),
                style = MaterialTheme.typography.titleMedium
            )
            if (!auto && canWrite) {
                TextButton(onClick = {
                    OrientationController.releaseToAuto(context)
                    refresh++
                }) { Text(stringResource(R.string.set_auto)) }
            }

            InfoCard(
                title = stringResource(R.string.permission_title),
                body = stringResource(R.string.permission_body),
                status = stringResource(
                    if (canWrite) R.string.permission_granted else R.string.permission_missing
                ),
                onClick = if (canWrite) null else {
                    {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                Uri.parse("package:" + context.packageName)
                            )
                        )
                    }
                }
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.force_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = forceApps, onCheckedChange = {
                            forceApps = it
                            Preferences.setForceApps(context, it)
                            if (it && !ShizukuShell.hasPermission()) {
                                ShizukuShell.requestPermission(SHIZUKU_REQUEST)
                            }
                            if (!it) ShizukuShell.setIgnoreOrientationRequest(false)
                            refresh++
                        })
                    }
                    Text(
                        stringResource(R.string.force_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (forceApps) {
                        Text(
                            stringResource(
                                when {
                                    !shizukuPresent -> R.string.shizuku_missing
                                    !shizukuReady -> R.string.shizuku_needs_permission
                                    else -> R.string.shizuku_ready
                                }
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            InfoCard(
                title = stringResource(R.string.add_tile_title),
                body = stringResource(R.string.add_tile_body),
                status = null,
                onClick = null
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, status: String?, onClick: (() -> Unit)?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (status != null) {
                Text(
                    status,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (onClick != null) {
                TextButton(onClick = onClick) { Text(stringResource(R.string.permission_title)) }
            }
        }
    }
}
