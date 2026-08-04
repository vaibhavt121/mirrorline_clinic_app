package com.example.mirrorclinic

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.mirrorclinic.ui.theme.MirrorClinicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MirrorClinicTheme {
                Surface(Modifier.fillMaxSize()) {
                    MirrorSetupScreen()
                }
            }
        }
    }
}

@Composable
fun MirrorSetupScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored; notification is optional */ }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val svc = Intent(context, MirrorService::class.java).apply {
                putExtra(MirrorService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(MirrorService.EXTRA_DATA, result.data)
            }
            ContextCompat.startForegroundService(context, svc)
            // Send our app to the background so the Mirror button floats over other apps.
            activity.moveTaskToBack(true)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Screen Mirror", style = MaterialTheme.typography.headlineMedium)
        Text(
            "How it works: tap Start Mirror, then a small \"Mirror\" button sits in the " +
                "top-right corner over any app. Tap it to freeze the current screen and " +
                "flip it horizontally. In the flipped view you can toggle the mirror on/off " +
                "or close it. It's a snapshot of that moment, not a live feed."
        )

        Button(
            onClick = { requestOverlay(context) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("1. Allow display over other apps") }

        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= 33) {
                    notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
                if (!Settings.canDrawOverlays(context)) {
                    requestOverlay(context)
                    return@Button
                }
                val mpm = context.getSystemService(MediaProjectionManager::class.java)
                    ?: return@Button
                projectionLauncher.launch(mpm.createScreenCaptureIntent())
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("2. Start Mirror") }
    }
}

private fun requestOverlay(context: android.content.Context) {
    if (!Settings.canDrawOverlays(context)) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.packageName)
        )
        context.startActivity(intent)
    }
}
