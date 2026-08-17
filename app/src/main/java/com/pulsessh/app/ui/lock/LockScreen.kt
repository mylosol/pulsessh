// app/src/main/java/com/pulsessh/app/ui/lock/LockScreen.kt
package com.pulsessh.app.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsessh.app.R

/**
 * The unlock gate: the first screen of the app and the only one visible while the vault is locked.
 *
 * Stateful entry point. It observes [LockViewModel], forwards user actions as [LockIntent]s,
 * runs the real `BiometricPrompt` when asked to via [LockEffect.RequestBiometricAuth], and
 * translates [LockEffect.NavigateToHosts] into a call on [onUnlocked]. The layout itself lives in
 * the stateless [LockContent] so it can be previewed and tested without Hilt.
 *
 * @param onUnlocked invoked once the vault is open, so the caller can navigate away.
 * @param modifier applied to the root of the screen.
 * @param viewModel the screen's ViewModel; injected by Hilt, overridable in tests.
 */
@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as FragmentActivity

    LaunchedEffect(viewModel, onUnlocked) {
        viewModel.effects.collect { effect ->
            when (effect) {
                LockEffect.NavigateToHosts -> onUnlocked()
                is LockEffect.RequestBiometricAuth -> runBiometricPrompt(activity, viewModel, effect)
            }
        }
    }

    LockContent(
        state = state,
        onUnlockClick = { viewModel.onIntent(LockIntent.Unlock) },
        onDismissErrorClick = { viewModel.onIntent(LockIntent.DismissError) },
        modifier = modifier,
    )
}

/** Checks biometric availability, then runs `BiometricPrompt` against [effect]'s cipher. */
private fun runBiometricPrompt(
    activity: FragmentActivity,
    viewModel: LockViewModel,
    effect: LockEffect.RequestBiometricAuth,
) {
    val canAuthenticate =
        BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
        viewModel.onIntent(LockIntent.BiometricAuthFailed(activity.getString(R.string.lock_error_no_biometric)))
        return
    }

    val promptInfo =
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.lock_biometric_prompt_title))
            .setSubtitle(activity.getString(R.string.lock_biometric_prompt_subtitle))
            .setNegativeButtonText(activity.getString(R.string.lock_biometric_prompt_cancel))
            .build()

    val prompt =
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            LockAuthenticationCallback(activity, viewModel, effect.mode),
        )
    prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(effect.cipher))
}

/** [BiometricPrompt.AuthenticationCallback] that reports back into [LockViewModel] via intents. */
private class LockAuthenticationCallback(
    private val activity: FragmentActivity,
    private val viewModel: LockViewModel,
    private val mode: CipherAuthMode,
) : BiometricPrompt.AuthenticationCallback() {
    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        val authenticatedCipher = result.cryptoObject?.cipher
        if (authenticatedCipher != null) {
            viewModel.onIntent(LockIntent.BiometricAuthSucceeded(authenticatedCipher, mode))
        } else {
            viewModel.onIntent(LockIntent.BiometricAuthFailed(activity.getString(R.string.lock_error_generic)))
        }
    }

    override fun onAuthenticationError(
        errorCode: Int,
        errString: CharSequence,
    ) {
        viewModel.onIntent(LockIntent.BiometricAuthFailed(errString.toString()))
    }

    override fun onAuthenticationFailed() {
        viewModel.onIntent(LockIntent.BiometricAuthFailed(activity.getString(R.string.lock_error_not_recognised)))
    }
}

/**
 * Stateless layout of the unlock gate.
 *
 * @param state what to render.
 * @param onUnlockClick called when the user asks to unlock.
 * @param onDismissErrorClick called when the user dismisses the current error.
 * @param modifier applied to the root of the layout.
 */
@Composable
fun LockContent(
    state: LockUiState,
    onUnlockClick: () -> Unit,
    onDismissErrorClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.lock_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.lock_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (state.isUnlocking) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.lock_status_unlocking),
                style = MaterialTheme.typography.labelLarge,
            )
        } else {
            Button(onClick = onUnlockClick) {
                Text(text = stringResource(R.string.lock_action_unlock))
            }
        }
        val error = state.error
        if (error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            val errorText =
                when (error) {
                    is LockError.Platform -> error.message
                    LockError.KeyInvalidated -> stringResource(R.string.lock_error_key_invalidated)
                    LockError.Generic -> stringResource(R.string.lock_error_generic)
                }
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onDismissErrorClick) {
                Text(text = stringResource(R.string.lock_action_dismiss_error))
            }
        }
    }
}
