package com.pulsessh.app.ui.lock

import androidx.lifecycle.viewModelScope
import com.pulsessh.app.core.mvi.MviViewModel
import com.pulsessh.app.core.mvi.UiEffect
import com.pulsessh.app.core.mvi.UiIntent
import com.pulsessh.app.core.mvi.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State of the unlock gate.
 *
 * @property isUnlocking true while an unlock attempt is in flight; the button is disabled and a
 * progress indicator is shown.
 * @property error a human-readable failure message, or null when there is nothing to report. It is
 * cleared by [LockIntent.DismissError] and by starting a new attempt.
 */
data class LockUiState(
    val isUnlocking: Boolean = false,
    val error: String? = null,
) : UiState

/** Everything the user can ask the unlock gate to do. */
sealed interface LockIntent : UiIntent {
    /** Begin an unlock attempt. */
    data object Unlock : LockIntent

    /** Acknowledge and clear the current [LockUiState.error]. */
    data object DismissError : LockIntent
}

/** One-shot events the unlock gate sends to its host. */
sealed interface LockEffect : UiEffect {
    /** The vault is open; the host should navigate to the host list and drop the lock route. */
    data object NavigateToHosts : LockEffect
}

/**
 * Drives the unlock gate.
 *
 * Phase 0 placeholder: [LockIntent.Unlock] succeeds immediately so that the shell is navigable
 * end to end and so that the navigation wiring, the MVI plumbing and the effect delivery can all
 * be tested before any security code exists.
 *
 * TODO(Module A): replace [unlock] with the real flow - `BiometricPrompt` with a
 * `CryptoObject` bound to the hardware-backed key, PIN fallback, attempt throttling and unwrapping
 * of the database key. Until then this class must not be shipped in a release build, and it
 * deliberately references no biometric API so that nothing here has to be unpicked later.
 */
@HiltViewModel
class LockViewModel
    @Inject
    constructor() : MviViewModel<LockUiState, LockIntent, LockEffect>(LockUiState()) {
        /**
         * Handles a [LockIntent].
         *
         * @param intent the intent to handle.
         */
        override fun onIntent(intent: LockIntent) {
            when (intent) {
                LockIntent.Unlock -> unlock()
                LockIntent.DismissError -> setState { copy(error = null) }
            }
        }

        /**
         * Placeholder unlock: flips into the in-flight state, then immediately reports success.
         *
         * The intermediate state is set even though nothing suspends, so that the screen's loading
         * path is exercised and so that swapping in the real authentication later changes only the
         * body of the coroutine.
         */
        private fun unlock() {
            setState { copy(isUnlocking = true, error = null) }
            viewModelScope.launch {
                emitEffect(LockEffect.NavigateToHosts)
                setState { copy(isUnlocking = false) }
            }
        }
    }
