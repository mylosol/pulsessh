package com.pulsessh.app.ui.lock

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Behaviour of the placeholder unlock gate.
 *
 * [LockViewModel] launches on `viewModelScope`, which dispatches on `Dispatchers.Main`. Replacing
 * Main with a [StandardTestDispatcher] is what keeps this a plain JVM test: `runTest` picks up the
 * scheduler installed here, so virtual time drives the ViewModel's coroutines and no Android
 * framework or Robolectric is involved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LockViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    /** Installs the test dispatcher as `Dispatchers.Main` before each test. */
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    /** Restores the real main dispatcher so tests cannot leak into one another. */
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the gate starts idle and without an error`() =
        runTest {
            val viewModel = LockViewModel()

            assertThat(viewModel.state.value).isEqualTo(LockUiState())
        }

    @Test
    fun `unlock emits NavigateToHosts and settles back to not unlocking`() =
        runTest {
            val viewModel = LockViewModel()

            viewModel.onIntent(LockIntent.Unlock)
            advanceUntilIdle()

            viewModel.effects.test {
                assertThat(awaitItem()).isEqualTo(LockEffect.NavigateToHosts)
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(viewModel.state.value.isUnlocking).isFalse()
            assertThat(viewModel.state.value.error).isNull()
        }

    @Test
    fun `dismissing an error clears it`() =
        runTest {
            val viewModel = LockViewModel()

            viewModel.onIntent(LockIntent.DismissError)
            advanceUntilIdle()

            assertThat(viewModel.state.value.error).isNull()
        }
}
