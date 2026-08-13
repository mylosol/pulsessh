package com.pulsessh.app.core.mvi

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Behavioural contract of [MviViewModel].
 *
 * These tests are the reason the base class avoids Android APIs: they run on a plain JVM with no
 * Robolectric, so every ViewModel built on it can be tested the same cheap way.
 */
class MviViewModelTest {
    @Test
    fun `initial state is the one supplied to the constructor`() =
        runTest {
            val viewModel = CounterViewModel(CounterState(count = 7))

            assertThat(viewModel.state.value).isEqualTo(CounterState(count = 7))
        }

    @Test
    fun `setState reduces the current state`() =
        runTest {
            val viewModel = CounterViewModel()

            viewModel.onIntent(CounterIntent.Increment)
            viewModel.onIntent(CounterIntent.Increment)

            assertThat(viewModel.state.value.count).isEqualTo(2)
        }

    @Test
    fun `state flow emits every distinct reduction to a collector`() =
        runTest {
            val viewModel = CounterViewModel()

            viewModel.state.test {
                assertThat(awaitItem().count).isEqualTo(0)

                viewModel.onIntent(CounterIntent.Increment)
                assertThat(awaitItem().count).isEqualTo(1)

                viewModel.onIntent(CounterIntent.Reset)
                assertThat(awaitItem().count).isEqualTo(0)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `effects are received in the order they were emitted`() =
        runTest {
            val viewModel = CounterViewModel()

            viewModel.emit(CounterEffect.Announced(1))
            viewModel.emit(CounterEffect.Announced(2))
            viewModel.emit(CounterEffect.Announced(3))

            viewModel.effects.test {
                assertThat(awaitItem()).isEqualTo(CounterEffect.Announced(1))
                assertThat(awaitItem()).isEqualTo(CounterEffect.Announced(2))
                assertThat(awaitItem()).isEqualTo(CounterEffect.Announced(3))
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * The buffered-channel guarantee: an effect emitted while nobody is listening - the gap
     * between `onStop` and the next `onStart`, or anything emitted before the first composition -
     * is held until a collector arrives, rather than being dropped or blocking the emitter.
     */
    @Test
    fun `an effect emitted before anyone collects is still delivered`() =
        runTest {
            val viewModel = CounterViewModel()

            viewModel.emit(CounterEffect.Announced(42))

            viewModel.effects.test {
                assertThat(awaitItem()).isEqualTo(CounterEffect.Announced(42))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `each effect is delivered exactly once`() =
        runTest {
            val viewModel = CounterViewModel()

            viewModel.emit(CounterEffect.Announced(1))
            viewModel.effects.test {
                assertThat(awaitItem()).isEqualTo(CounterEffect.Announced(1))
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.effects.test {
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
}

/** State of the throwaway ViewModel used to exercise the base class. */
private data class CounterState(val count: Int = 0) : UiState

/** Intents of the throwaway ViewModel used to exercise the base class. */
private sealed interface CounterIntent : UiIntent {
    /** Adds one to the count. */
    data object Increment : CounterIntent

    /** Puts the count back to zero. */
    data object Reset : CounterIntent
}

/** Effects of the throwaway ViewModel used to exercise the base class. */
private sealed interface CounterEffect : UiEffect {
    /**
     * Announces a value.
     *
     * @property value the announced number, used to assert delivery order.
     */
    data class Announced(val value: Int) : CounterEffect
}

/**
 * Minimal [MviViewModel] implementation.
 *
 * It also widens the protected [emitEffect] so a test can push an effect without going through an
 * intent, which is what makes the "emitted before anyone collects" case expressible.
 */
private class CounterViewModel(
    initialState: CounterState = CounterState(),
) : MviViewModel<CounterState, CounterIntent, CounterEffect>(initialState) {
    override fun onIntent(intent: CounterIntent) {
        when (intent) {
            CounterIntent.Increment -> setState { copy(count = count + 1) }
            CounterIntent.Reset -> setState { copy(count = 0) }
        }
    }

    /** Test-only bridge to the protected [emitEffect]. */
    suspend fun emit(effect: CounterEffect) {
        emitEffect(effect)
    }
}
