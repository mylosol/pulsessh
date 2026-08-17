package com.pulsessh.app.core.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the vault is unlocked, application-wide.
 *
 * Deliberately holds no reference to any live session or socket: backgrounding the app or
 * turning the screen off re-locks the vault, but the connection pool - once it exists - is
 * untouched. See `docs/architecture.md` section 7.2.
 *
 * Registered as a [DefaultLifecycleObserver] on `ProcessLifecycleOwner` by
 * [com.pulsessh.app.PulseSshApplication] so that backgrounding the whole app re-locks it; screen
 * off is handled separately by [ScreenOffReceiver], since it is not a lifecycle event.
 */
@Singleton
class AppLockController
    @Inject
    constructor() : DefaultLifecycleObserver {
        private val _isUnlocked = MutableStateFlow(false)

        /** True once a successful biometric unlock has completed; false whenever the vault re-locks. */
        val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

        /** Sets the unlock state. Called by [com.pulsessh.app.ui.lock.LockViewModel] on success. */
        fun setUnlocked(unlocked: Boolean) {
            _isUnlocked.value = unlocked
        }

        /** Re-locks when the app goes to the background. */
        override fun onStop(owner: LifecycleOwner) {
            _isUnlocked.value = false
        }
    }
