package com.example.mirrorclinic

import android.app.Activity
import android.content.Context
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MirrorSetupScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val prefs = context.getSharedPreferences(MirrorService.PREFS, Context.MODE_PRIVATE)

    var position by remember {
        mutableStateOf(prefs.getString(MirrorService.KEY_POS, "top") ?: "top")
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* notification permission is optional */ }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val svc = Intent(context, MirrorService::class.java).apply {
                putExtra(MirrorService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(MirrorService.EXTRA_DATA, result.data)
            }
            ContextCompat.startForegroundService(context, svc)
            activity.moveTaskToBack(true)
        }
    }

    fun choosePosition(pos: String) {
        prefs.edit()
            .putString(MirrorService.KEY_POS, pos)
            .putBoolean(MirrorService.KEY_CUSTOM, false)
            .apply()
        position = pos
        // Tell the running service to move the button now.
        context.startService(
            Intent(context, MirrorService::class.java)
                .setAction(MirrorService.ACTION_SET_POSITION)
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Screen Mirror", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Tap Start Mirror, then a floating Mirror button appears over any app. " +
                "Tap it to freeze the current screen and flip it horizontally. You can " +
                "also drag the button anywhere with your finger."
        )

        Text("Button position", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("top", "bottom", "left", "right").forEach { pos ->
                FilterChip(
                    selected = position == pos,
                    onClick = { choosePosition(pos) },
                    label = { Text(pos.replaceFirstChar { it.uppercase() }) }
                )
            }
        }

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

private fun requestOverlay(context: Context) {
    if (!Settings.canDrawOverlays(context)) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.packageName)
        )
        context.startActivity(intent)
    }
}
