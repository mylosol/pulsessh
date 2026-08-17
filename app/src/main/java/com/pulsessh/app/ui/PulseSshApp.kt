package com.pulsessh.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pulsessh.app.ui.hosts.HostsScreen
import com.pulsessh.app.ui.lock.LockScreen
import com.pulsessh.app.ui.theme.PulseSshTheme

/**
 * Root composable: theme, window surface and the navigation graph.
 *
 * The graph starts locked. Every route that can show host data therefore sits behind
 * [PulseSshRoute.Lock], and unlocking pops the lock route off the back stack so that the system
 * back gesture cannot return to a screen that has already served its purpose. Backgrounding the
 * app or turning the screen off flips [AppLockViewModel.isUnlocked] back to false - observed
 * here - which forces navigation straight back to [PulseSshRoute.Lock], regardless of which
 * screen was showing.
 *
 * @param navController hoisted so that tests and future deep-link handling can drive navigation.
 * @param appLockViewModel bridges [com.pulsessh.app.core.security.AppLockController]'s
 * process-wide state into this composable; injected by Hilt, overridable in tests.
 */
@Composable
fun PulseSshApp(
    navController: NavHostController = rememberNavController(),
    appLockViewModel: AppLockViewModel = hiltViewModel(),
) {
    val isUnlocked by appLockViewModel.isUnlocked.collectAsStateWithLifecycle()

    LaunchedEffect(isUnlocked) {
        if (!isUnlocked) {
            navController.navigate(PulseSshRoute.Lock.route) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    PulseSshTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            NavHost(
                navController = navController,
                startDestination = PulseSshRoute.Lock.route,
            ) {
                composable(PulseSshRoute.Lock.route) {
                    LockScreen(
                        onUnlocked = {
                            navController.navigate(PulseSshRoute.Hosts.route) {
                                // The lock screen must not be reachable by pressing back, and a
                                // duplicate unlock must not stack a second copy of the host list.
                                popUpTo(PulseSshRoute.Lock.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(PulseSshRoute.Hosts.route) {
                    HostsScreen()
                }
            }
        }
    }
}

/**
 * Every destination in the application graph.
 *
 * Routes are plain strings rather than the type-safe serializable destinations of Navigation 2.8
 * because the kotlinx-serialization plugin is not applied to this module. Keeping them in one
 * sealed interface still gives a single place to look, compile-time-checked references at call
 * sites, and an exhaustive `when` if navigation ever needs one.
 */
sealed interface PulseSshRoute {
    /** The path registered with the [NavHost]; unique across the graph. */
    val route: String

    /** Unlock gate. Start destination, and the only screen shown while the vault is locked. */
    data object Lock : PulseSshRoute {
        override val route: String = "lock"
    }

    /** The saved host list, and the landing screen after a successful unlock. */
    data object Hosts : PulseSshRoute {
        override val route: String = "hosts"
    }
}
