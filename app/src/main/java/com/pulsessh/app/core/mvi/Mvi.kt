package com.pulsessh.app.core.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * Marker for the immutable snapshot a screen renders from.
 *
 * Implementations must be immutable value types (a `data class` of `val`s) so that Compose can
 * skip recomposition by equality and so that state can be compared in tests without cloning.
 */
interface UiState

/**
 * Marker for something the user (or the system, on the user's behalf) asks a screen to do.
 *
 * Intents are the only way into a [MviViewModel]; screens never call arbitrary methods on it.
 * Implementations are normally a `sealed interface` of `data object`s and `data class`es.
 */
interface UiIntent

/**
 * Marker for a one-shot side effect: navigation, a snackbar, a system dialog, a vibration.
 *
 * Effects are explicitly *not* part of [UiState] because they must fire exactly once. Putting
 * "navigate now" into state forces every consumer to remember to clear the flag afterwards, which
 * is the classic source of double navigation after a configuration change.
 */
interface UiEffect

/**
 * Base class for every ViewModel in PulseSSH.
 *
 * The contract is deliberately small: a screen observes [state], reacts to [effects], and sends
 * [UiIntent]s through [onIntent]. Subclasses mutate state only through [setState] and emit
 * one-shot events only through [emitEffect], which keeps every state transition auditable.
 *
 * Apart from extending [ViewModel] (a plain JVM class with no Android framework dependency at
 * runtime) this class touches no Android APIs, so it can be exercised in ordinary JVM unit tests
 * with `kotlinx-coroutines-test` and without Robolectric.
 *
 * @param S the state type this ViewModel publishes.
 * @param I the intent type this ViewModel accepts.
 * @param E the effect type this ViewModel emits.
 * @param initialState the state published before any intent is handled.
 */
abstract class MviViewModel<S : UiState, I : UiIntent, E : UiEffect>(initialState: S) : ViewModel() {
    /** Mutable backing store for [state]; private so that reduction is funnelled through [setState]. */
    private val _state = MutableStateFlow(initialState)

    /**
     * The current screen state, hot and conflated.
     *
     * A new collector immediately receives the latest value, which is what makes the screen
     * survive recomposition and configuration changes without replaying work.
     */
    val state: StateFlow<S> = _state.asStateFlow()

    /**
     * Buffered mailbox for one-shot effects.
     *
     * [Channel.BUFFERED] is chosen over [Channel.RENDEZVOUS] on purpose: a ViewModel frequently
     * emits an effect while no collector is attached (for example during the gap between
     * `onStop` and the next `onStart`, or before the first composition). A rendezvous channel
     * would suspend the emitter until someone collected; a conflated channel would silently drop
     * all but the newest effect. A buffered channel keeps every effect, in order, until the next
     * collector arrives.
     */
    private val _effects = Channel<E>(Channel.BUFFERED)

    /**
     * Stream of one-shot effects.
     *
     * Backed by a channel, so each effect is delivered to exactly one collector and is consumed
     * when read. Collect it from a lifecycle-aware scope in the UI layer.
     */
    val effects: Flow<E> = _effects.receiveAsFlow()

    /**
     * Handles a user intent.
     *
     * Implementations should be non-blocking: validate, call [setState] for the synchronous part,
     * and launch coroutines for anything slow.
     *
     * @param intent the intent to handle.
     */
    abstract fun onIntent(intent: I)

    /**
     * Atomically replaces the current state with the result of [reducer].
     *
     * [reducer] runs on the current state as receiver, so the usual body is `copy(...)`. It may be
     * invoked more than once under contention (it delegates to `MutableStateFlow.update`), so it
     * must be a pure function of its receiver and must not perform side effects.
     *
     * @param reducer produces the next state from the current one.
     */
    protected fun setState(reducer: S.() -> S) {
        _state.update(reducer)
    }

    /**
     * Sends a one-shot [effect] to [effects].
     *
     * Suspends only if the channel's buffer is full, which in practice means a screen has stopped
     * consuming effects entirely. Call it from a coroutine owned by this ViewModel.
     *
     * @param effect the effect to deliver.
     */
    protected suspend fun emitEffect(effect: E) {
        _effects.send(effect)
    }
}
