// app/src/main/java/com/pulsessh/app/ui/lock/LockViewModel.kt
package com.pulsessh.app.ui.lock

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.lifecycle.viewModelScope
import com.pulsessh.app.core.crypto.PassphraseVault
import com.pulsessh.app.core.mvi.MviViewModel
import com.pulsessh.app.core.mvi.UiEffect
import com.pulsessh.app.core.mvi.UiIntent
import com.pulsessh.app.core.mvi.UiState
import com.pulsessh.app.core.security.AppLockController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

/** Which operation an authenticated [Cipher] from [LockEffect.RequestBiometricAuth] will perform. */
enum class CipherAuthMode { WRAP, UNWRAP }

/**
 * A human-facing failure to show on the unlock gate.
 *
 * Kept out of [LockUiState.error] as a raw `String` on purpose: [LockViewModel] has no
 * `Context` and must stay that way for JVM testability, so it cannot resolve a string
 * resource itself (`CLAUDE.md` requires every user-visible string to live in
 * `res/values/strings.xml`). [Platform] carries text the OS already resolved and localised
 * (BiometricPrompt's own error text), which is reasonable to pass through as-is; [KeyInvalidated]
 * carries no text at all; [LockScreen] resolves it to `R.string.lock_error_key_invalidated`.
 */
sealed interface LockError {
    /** A message already resolved by the platform - BiometricPrompt's own error text. */
    data class Platform(val message: String) : LockError

    /** The KeyStore key was permanently invalidated (the user's enrolled biometrics changed). */
    data object KeyInvalidated : LockError

    /** An unexpected failure with no more specific explanation to give the user. */
    data object Generic : LockError
}

/**
 * State of the unlock gate.
 *
 * @property isUnlocking true while an unlock attempt is in flight; the button is disabled and a
 * progress indicator is shown.
 * @property error the current failure to show, or null when there is nothing to report. It is
 * cleared by [LockIntent.DismissError] and by starting a new attempt.
 */
data class LockUiState(
    val isUnlocking: Boolean = false,
    val error: LockError? = null,
) : UiState

/** Everything the user (or the platform, on the user's behalf) can ask the unlock gate to do. */
sealed interface LockIntent : UiIntent {
    /** Begin an unlock attempt. */
    data object Unlock : LockIntent

    /** Acknowledge and clear the current [LockUiState.error]. */
    data object DismissError : LockIntent

    /** BiometricPrompt authenticated [cipher] successfully; [mode] says what to do with it. */
    data class BiometricAuthSucceeded(val cipher: Cipher, val mode: CipherAuthMode) : LockIntent

    /** BiometricPrompt failed, was cancelled, or errored with a human-readable [message]. */
    data class BiometricAuthFailed(val message: String) : LockIntent
}

/** One-shot events the unlock gate sends to its host. */
sealed interface LockEffect : UiEffect {
    /** The vault is open; the host should navigate to the host list and drop the lock route. */
    data object NavigateToHosts : LockEffect

    /** Ask the host to run BiometricPrompt against [cipher], then report back via a [LockIntent]. */
    data class RequestBiometricAuth(val cipher: Cipher, val mode: CipherAuthMode) : LockEffect
}

/**
 * Drives the unlock gate.
 *
 * Depends only on [PassphraseVault] and [AppLockController] - both plain Kotlin beyond
 * `javax.crypto.Cipher` - so this class runs in ordinary JVM unit tests. The actual
 * `BiometricPrompt` call happens in [LockScreen], which owns the `FragmentActivity` it requires;
 * this class only prepares the [Cipher] to authenticate and reacts to the result.
 */
@HiltViewModel
class LockViewModel
    @Inject
    constructor(
        private val passphraseVault: PassphraseVault,
        private val appLockController: AppLockController,
    ) : MviViewModel<LockUiState, LockIntent, LockEffect>(LockUiState()) {
        override fun onIntent(intent: LockIntent) {
            when (intent) {
                LockIntent.Unlock -> unlock()
                LockIntent.DismissError -> setState { copy(error = null) }
                is LockIntent.BiometricAuthSucceeded -> onBiometricSucceeded(intent.cipher, intent.mode)
                is LockIntent.BiometricAuthFailed -> onBiometricFailed(intent.message)
            }
        }

        private fun unlock() {
            setState { copy(isUnlocking = true, error = null) }
            viewModelScope.launch {
                try {
                    val hasStoredPassphrase = passphraseVault.hasStoredPassphrase.first()
                    val (cipher, mode) =
                        if (hasStoredPassphrase) {
                            passphraseVault.prepareUnwrapCipher() to CipherAuthMode.UNWRAP
                        } else {
                            passphraseVault.prepareWrapCipher() to CipherAuthMode.WRAP
                        }
                    emitEffect(LockEffect.RequestBiometricAuth(cipher, mode))
                } catch (_: KeyPermanentlyInvalidatedException) {
                    // The user's enrolled biometrics changed since the key was created, or the key
                    // vanished entirely (e.g. the user removed and re-added their screen lock,
                    // which deletes auth-bound keys without necessarily invalidating them first) -
                    // either way the old key (and anything wrapped with it) can never be used
                    // again. Wipe both and let the next Unlock attempt take the first-run wrap
                    // path instead.
                    passphraseVault.reset()
                    setState { copy(isUnlocking = false, error = LockError.KeyInvalidated) }
                } catch (_: Exception) {
                    // Anything else - a KeyStore provider quirk, a DataStore read failure - should
                    // surface as a retryable error, not crash the app.
                    setState { copy(isUnlocking = false, error = LockError.Generic) }
                }
            }
        }

        private fun onBiometricSucceeded(
            cipher: Cipher,
            mode: CipherAuthMode,
        ) {
            viewModelScope.launch {
                try {
                    val passphrase =
                        when (mode) {
                            CipherAuthMode.WRAP -> {
                                passphraseVault.wrapAndStore(cipher)
                                null
                            }
                            CipherAuthMode.UNWRAP -> passphraseVault.unwrap(cipher)
                        }
                    passphrase?.fill(0)
                    appLockController.setUnlocked(true)
                    setState { copy(isUnlocking = false) }
                    emitEffect(LockEffect.NavigateToHosts)
                } catch (_: Exception) {
                    // Cipher.doFinal() can throw AEADBadTagException (corrupted/tampered wrapped
                    // bytes) or IllegalStateException (PassphraseVault's "no wrapped passphrase
                    // stored yet", reachable via a TOCTOU race between unlock()'s
                    // hasStoredPassphrase snapshot and this callback firing). Neither has a
                    // narrower common supertype worth naming, and either way the right response is
                    // the same: stop spinning and let the user retry rather than crash.
                    setState { copy(isUnlocking = false, error = LockError.Generic) }
                }
            }
        }

        private fun onBiometricFailed(message: String) {
            setState { copy(isUnlocking = false, error = LockError.Platform(message)) }
        }
    }
