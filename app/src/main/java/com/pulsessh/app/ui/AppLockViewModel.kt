package com.pulsessh.app.ui

import androidx.lifecycle.ViewModel
import com.pulsessh.app.core.security.AppLockController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Thin bridge from [AppLockController]'s process-wide state into the Compose navigation graph.
 *
 * A `ViewModel` rather than a direct [AppLockController] injection because Hilt's Compose
 * integration resolves `@HiltViewModel`-annotated classes into composables through
 * `hiltViewModel()`; this class holds no state of its own beyond forwarding
 * [AppLockController.isUnlocked]. It extends plain `ViewModel` rather than this project's
 * `MviViewModel<S, I, E>` deliberately: there is no state/intent/effect shape here, only a
 * pass-through `StateFlow`, so the usual MVI base would add empty marker types with no benefit.
 */
@HiltViewModel
class AppLockViewModel
    @Inject
    constructor(
        appLockController: AppLockController,
    ) : ViewModel() {
        /** Mirrors [AppLockController.isUnlocked]. */
        val isUnlocked: StateFlow<Boolean> = appLockController.isUnlocked
    }
