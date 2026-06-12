package su.kidoz.jetaprog.common.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import su.kidoz.jetaprog.common.Disposable

/**
 * Base class for MVI ViewModels.
 *
 * Provides a structured way to handle user intents, update state, and emit side effects.
 * Uses StateFlow for state and Channel for effects to ensure proper handling.
 *
 * @param I The type of intents this ViewModel handles
 * @param S The type of state this ViewModel manages
 * @param E The type of effects this ViewModel can emit
 * @param initialState The initial state of the ViewModel
 */
public abstract class MviViewModel<I : Intent, S : State, E : Effect>(
    initialState: S,
) : Disposable {
    /**
     * The coroutine scope for this ViewModel.
     * Uses SupervisorJob so child coroutine failures don't cancel the scope.
     */
    protected val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(initialState)

    /**
     * The current state as a StateFlow for observation.
     */
    public val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)

    /**
     * Effects as a Flow that should be collected to handle side effects.
     * Each effect is delivered exactly once.
     */
    public val effects: Flow<E> = _effects.receiveAsFlow()

    /**
     * The current state value.
     */
    protected val currentState: S get() = _state.value

    /**
     * Dispatches an intent to be handled by this ViewModel.
     * @param intent The intent to handle
     */
    public fun dispatch(intent: I) {
        viewModelScope.launch {
            handleIntent(intent)
        }
    }

    /**
     * Handles an intent. Override this to implement intent handling logic.
     * @param intent The intent to handle
     */
    protected abstract suspend fun handleIntent(intent: I)

    /**
     * Updates the state using a reducer function.
     * @param reducer A function that takes the current state and returns the new state
     */
    protected fun updateState(reducer: S.() -> S) {
        _state.update(reducer)
    }

    /**
     * Emits a side effect.
     * @param effect The effect to emit
     */
    protected suspend fun emitEffect(effect: E) {
        _effects.send(effect)
    }

    /**
     * Disposes this ViewModel, cancelling all coroutines.
     */
    override fun dispose() {
        viewModelScope.cancel()
        _effects.close()
    }
}
