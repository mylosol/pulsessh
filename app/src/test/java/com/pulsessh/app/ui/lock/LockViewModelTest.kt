// app/src/test/java/com/pulsessh/app/ui/lock/LockViewModelTest.kt
package com.pulsessh.app.ui.lock

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.pulsessh.app.core.crypto.FakeMasterKeyGateway
import com.pulsessh.app.core.crypto.PassphraseVault
import com.pulsessh.app.core.security.AppLockController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Behaviour of the unlock gate.
 *
 * [LockViewModel] depends on the real [PassphraseVault], backed by a [FakeMasterKeyGateway] and
 * a temp-file-backed `DataStore`, so these tests exercise the whole unlock loop with only the
 * Android KeyStore boundary faked - matching the roadmap's "unit tests use a fake KeyStore
 * facade" instruction. Nothing here needs Robolectric.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LockViewModelTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * A temp-file-backed `DataStore`, with its internal write actor bound to [testDispatcher]'s
     * scheduler rather than DataStore's own real-thread default. `LockViewModel.unlock` writes
     * to the vault from inside `viewModelScope.launch`, so without this the write actor would run
     * on a genuinely concurrent background thread that `advanceUntilIdle` cannot wait for, making
     * assertions racy against tests that only pass most of the time.
     */
    private fun newDataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(testDispatcher),
            produceFile = { tempFolder.newFile("test.preferences_pb") },
        )

    private fun newViewModel(): Pair<LockViewModel, AppLockController> {
        val vault = PassphraseVault(newDataStore(), FakeMasterKeyGateway(), UnconfinedTestDispatcher())
        val appLockController = AppLockController()
        return LockViewModel(vault, appLockController) to appLockController
    }

    /**
     * A [LockViewModel] whose vault already has a wrapped passphrase stored, as if a previous
     * unlock had already completed - the steady-state case for a returning user. Exercises the
     * UNWRAP branch of `unlock()` and `onBiometricSucceeded` instead of the first-run WRAP branch
     * [newViewModel] covers.
     */
    private suspend fun newSeededViewModel(): Pair<LockViewModel, AppLockController> {
        val vault = PassphraseVault(newDataStore(), FakeMasterKeyGateway(), UnconfinedTestDispatcher())
        vault.wrapAndStore(vault.prepareWrapCipher())
        val appLockController = AppLockController()
        return LockViewModel(vault, appLockController) to appLockController
    }

    @Test
    fun `the gate starts idle and without an error`() =
        runTest {
            val (viewModel, _) = newViewModel()

            assertThat(viewModel.state.value).isEqualTo(LockUiState())
        }

    @Test
    fun `unlock on first run requests a wrap cipher`() =
        runTest {
            val (viewModel, _) = newViewModel()

            viewModel.onIntent(LockIntent.Unlock)
            advanceUntilIdle()

            viewModel.effects.test {
                val effect = awaitItem() as LockEffect.RequestBiometricAuth
                assertThat(effect.mode).isEqualTo(CipherAuthMode.WRAP)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a succeeded wrap unlocks and navigates to hosts`() =
        runTest {
            val (viewModel, appLockController) = newViewModel()
            viewModel.onIntent(LockIntent.Unlock)
            advanceUntilIdle()
            lateinit var requested: LockEffect.RequestBiometricAuth
            viewModel.effects.test {
                requested = awaitItem() as LockEffect.RequestBiometricAuth
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.onIntent(LockIntent.BiometricAuthSucceeded(requested.cipher, requested.mode))
            advanceUntilIdle()

            viewModel.effects.test {
                assertThat(awaitItem()).isEqualTo(LockEffect.NavigateToHosts)
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(appLockController.isUnlocked.value).isTrue()
            assertThat(viewModel.state.value.isUnlocking).isFalse()
        }

    @Test
    fun `unlock on a returning user requests an unwrap cipher`() =
        runTest {
            val (viewModel, _) = newSeededViewModel()

            viewModel.onIntent(LockIntent.Unlock)
            advanceUntilIdle()

            viewModel.effects.test {
                val effect = awaitItem() as LockEffect.RequestBiometricAuth
                assertThat(effect.mode).isEqualTo(CipherAuthMode.UNWRAP)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a succeeded unwrap unlocks and navigates to hosts`() =
        runTest {
            val (viewModel, appLockController) = newSeededViewModel()
            viewModel.onIntent(LockIntent.Unlock)
            advanceUntilIdle()
            lateinit var requested: LockEffect.RequestBiometricAuth
            viewModel.effects.test {
                requested = awaitItem() as LockEffect.RequestBiometricAuth
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(requested.mode).isEqualTo(CipherAuthMode.UNWRAP)

            viewModel.onIntent(LockIntent.BiometricAuthSucceeded(requested.cipher, requested.mode))
            advanceUntilIdle()

            viewModel.effects.test {
                assertThat(awaitItem()).isEqualTo(LockEffect.NavigateToHosts)
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(appLockController.isUnlocked.value).isTrue()
        }

    @Test
    fun `a failed biometric attempt surfaces the error and stays locked`() =
        runTest {
            val (viewModel, appLockController) = newViewModel()

            viewModel.onIntent(LockIntent.BiometricAuthFailed("Fingerprint not recognised"))
            advanceUntilIdle()

            assertThat(viewModel.state.value.error).isEqualTo(LockError.Platform("Fingerprint not recognised"))
            assertThat(viewModel.state.value.isUnlocking).isFalse()
            assertThat(appLockController.isUnlocked.value).isFalse()
        }

    @Test
    fun `a permanently invalidated key resets the vault and surfaces a retryable error`() =
        runTest {
            val fakeGateway = FakeMasterKeyGateway()
            val vault = PassphraseVault(newDataStore(), fakeGateway, UnconfinedTestDispatcher())
            val viewModel = LockViewModel(vault, AppLockController())
            fakeGateway.invalidateNextCipher = true

            viewModel.onIntent(LockIntent.Unlock)
            advanceUntilIdle()

            assertThat(viewModel.state.value.error).isEqualTo(LockError.KeyInvalidated)
            assertThat(viewModel.state.value.isUnlocking).isFalse()
            assertThat(vault.hasStoredPassphrase.first()).isFalse()
        }

    @Test
    fun `a decrypt against a vanished key resets the vault and surfaces a retryable error`() =
        runTest {
            val fakeGateway = FakeMasterKeyGateway()
            val vault = PassphraseVault(newDataStore(), fakeGateway, UnconfinedTestDispatcher())
            vault.wrapAndStore(vault.prepareWrapCipher())
            fakeGateway.deleteKey()
            val viewModel = LockViewModel(vault, AppLockController())

            viewModel.onIntent(LockIntent.Unlock)
            advanceUntilIdle()

            assertThat(viewModel.state.value.error).isEqualTo(LockError.KeyInvalidated)
            assertThat(viewModel.state.value.isUnlocking).isFalse()
            assertThat(vault.hasStoredPassphrase.first()).isFalse()
        }

    @Test
    fun `dismissing an error clears it`() =
        runTest {
            val (viewModel, _) = newViewModel()

            viewModel.onIntent(LockIntent.DismissError)
            advanceUntilIdle()

            assertThat(viewModel.state.value.error).isNull()
        }
}
