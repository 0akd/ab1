package com.arjun.absolutra.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arjun.absolutra.ui.screens.AuthScreen
import com.arjun.absolutra.ui.screens.BrowserScreen
import com.arjun.absolutra.ui.screens.NotesScreen
import com.arjun.absolutra.ui.screens.CanvasScreen
import com.arjun.absolutra.ui.screens.CaptureSettingsScreen
import com.arjun.absolutra.ui.screens.SettingsScreen
import com.arjun.absolutra.ui.screens.TodoScreen
import kotlinx.coroutines.launch

enum class AppScreen(val title: String) {
    Todo("Tasks"),
    Canvas("Canvas Boards"),
    CaptureSettings("Screenshot Config"),
    Auth("Login"),
    Notes("Notes"),
    Browser("Browser"),
    Settings("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(AppScreen.Todo) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))

                AppScreen.entries.forEach { screen ->
                    NavigationDrawerItem(
                        label = { Text(screen.title) },
                        selected = currentScreen == screen,
                        onClick = {
                            currentScreen = screen
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentScreen.title) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Sidebar")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { innerPadding ->
            when (currentScreen) {
                AppScreen.Todo -> TodoScreen(modifier = Modifier.padding(innerPadding))
                AppScreen.Canvas -> CanvasScreen(modifier = Modifier.padding(innerPadding))
                AppScreen.CaptureSettings -> CaptureSettingsScreen(modifier = Modifier.padding(innerPadding))
                AppScreen.Auth -> AuthScreen(modifier = Modifier.padding(innerPadding))
                AppScreen.Notes -> NotesScreen(modifier = Modifier.padding(innerPadding))
                AppScreen.Browser -> BrowserScreen(modifier = Modifier.padding(innerPadding))
                AppScreen.Settings -> SettingsScreen(modifier = Modifier.padding(innerPadding))
            }
        }
    }
}
