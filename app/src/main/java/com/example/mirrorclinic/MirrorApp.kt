package com.example.mirrorclinic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

enum class Screen(val label: String) {
    Home("Home"),
    Records("Records"),
    Gallery("Gallery")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MirrorApp() {
    // Single source of truth for mirror mode.
    var mirrored by rememberSaveable { mutableStateOf(false) }
    var current by rememberSaveable { mutableStateOf(Screen.Home) }

    Scaffold(
        // ---------------------------------------------------------------
        // THE ENTIRE UI IS FLIPPED HERE, IN ONE LINE.
        //
        // Because this is a Compose graphicsLayer transform, Compose also
        // INVERTS it for pointer hit-testing. So a button that visually
        // moves to the right still responds where you SEE it -- no manual
        // touch remapping, no coordinate math, no Accessibility service.
        // That is exactly why the app-only flip is reliable and the
        // whole-OS flip is not: here Compose owns both the drawing and
        // the input, so the two stay in sync automatically.
        // ---------------------------------------------------------------
        modifier = Modifier.graphicsLayer {
            scaleX = if (mirrored) -1f else 1f
        },
        topBar = {
            TopAppBar(
                title = { Text("Mirror Clinic") },
                actions = {
                    Text("Mirror", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = mirrored,
                        onCheckedChange = { mirrored = it }
                    )
                    Spacer(Modifier.width(12.dp))
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = current == screen,
                        onClick = { current = screen },
                        icon = {
                            Icon(
                                imageVector = when (screen) {
                                    Screen.Home -> Icons.Filled.Home
                                    Screen.Records -> Icons.Filled.Description
                                    Screen.Gallery -> Icons.Filled.PhotoLibrary
                                },
                                contentDescription = screen.label
                            )
                        },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (current) {
                Screen.Home -> HomeScreen()
                Screen.Records -> RecordsScreen()
                Screen.Gallery -> GalleryScreen()
            }
        }
    }
}
