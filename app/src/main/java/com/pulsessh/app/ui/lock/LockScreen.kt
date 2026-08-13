package com.pulsessh.app.ui.lock

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsessh.app.R

/**
 * The unlock gate: the first screen of the app and the only one visible while the vault is locked.
 *
 * Stateful entry point. It observes [LockViewModel], forwards user actions as [LockIntent]s and
 * translates [LockEffect]s into calls on [onUnlocked]; the layout itself lives in the stateless
 * [LockContent] so it can be previewed and tested without Hilt.
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

    LaunchedEffect(viewModel, onUnlocked) {
        viewModel.effects.collect { effect ->
            when (effect) {
                LockEffect.NavigateToHosts -> onUnlocked()
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
        if (state.error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = state.error,
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
