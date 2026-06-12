package su.kidoz.jetaprog.common.mvi

/**
 * Marker interface for MVI intents (user actions).
 * Intents represent all possible actions that can be performed by the user or system.
 */
public interface Intent

/**
 * Marker interface for MVI states (UI state).
 * States represent the current state of the UI and should be immutable.
 */
public interface State

/**
 * Marker interface for MVI effects (side effects).
 * Effects represent one-time events that should be consumed only once,
 * such as navigation events, showing toasts, etc.
 */
public interface Effect
