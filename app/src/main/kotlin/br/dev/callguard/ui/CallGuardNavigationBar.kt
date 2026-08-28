package br.dev.callguard.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/** Barra de abas compartilhada pelas tres telas. */
@Composable
fun CallGuardNavigationBar(
    currentScreen: CallGuardScreen,
    onScreenSelected: (CallGuardScreen) -> Unit,
) {
    NavigationBar {
        CallGuardScreen.entries.filter { it.inNavBar }.forEach { screen ->
            NavigationBarItem(
                // O diagnostico nao tem aba propria; enquanto ele esta aberto, Proteção
                // continua marcada, que e de onde se chega ate ele.
                selected = screen == currentScreen ||
                    (currentScreen == CallGuardScreen.DIAGNOSTICS &&
                        screen == CallGuardScreen.HOME),
                onClick = { onScreenSelected(screen) },
                icon = { Icon(screen.icon, contentDescription = null) },
                label = { Text(screen.label) },
            )
        }
    }
}

private val CallGuardScreen.icon: ImageVector
    get() = when (this) {
        CallGuardScreen.HOME -> Icons.Default.Lock
        CallGuardScreen.BLOCKED_CALLS -> Icons.AutoMirrored.Filled.List
        CallGuardScreen.RULES -> Icons.Default.Build
        CallGuardScreen.ANONYMOUS_CALL -> Icons.Default.Call
        CallGuardScreen.LOGS -> Icons.Default.Settings
        CallGuardScreen.DIAGNOSTICS -> Icons.Default.Info
    }
