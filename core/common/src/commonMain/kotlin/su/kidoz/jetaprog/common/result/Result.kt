package su.kidoz.jetaprog.common.result

// Extension functions for working with Kotlin's Result type.

/**
 * Combines two Results, returning a Result of a Pair if both are successful.
 */
public fun <A, B> Result<A>.zip(other: Result<B>): Result<Pair<A, B>> = flatMap { a -> other.map { b -> a to b } }

/**
 * Flat maps the success value to another Result.
 */
public inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> =
    fold(
        onSuccess = { transform(it) },
        onFailure = { Result.failure(it) },
    )

/**
 * Recovers from a failure by providing an alternative Result.
 */
public inline fun <T> Result<T>.recoverWith(recovery: (Throwable) -> Result<T>): Result<T> =
    fold(
        onSuccess = { Result.success(it) },
        onFailure = { recovery(it) },
    )

/**
 * Returns the successful value or throws the failure exception with a custom message.
 */
public fun <T> Result<T>.getOrThrow(message: () -> String): T = getOrElse { throw IllegalStateException(message(), it) }

/**
 * Converts a nullable value to a Result.
 */
public fun <T : Any> T?.toResult(errorMessage: () -> String = { "Value was null" }): Result<T> =
    if (this != null) Result.success(this) else Result.failure(IllegalStateException(errorMessage()))

/**
 * Runs a block and wraps any exception in a Result.
 */
public inline fun <T> resultOf(block: () -> T): Result<T> = runCatching(block)

/**
 * Runs a suspend block and wraps any exception in a Result.
 */
public suspend fun <T> resultOfSuspend(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: Throwable) {
        Result.failure(e)
    }
