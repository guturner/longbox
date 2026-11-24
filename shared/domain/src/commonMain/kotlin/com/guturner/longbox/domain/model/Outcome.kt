package com.guturner.longbox.domain.model

import com.guturner.longbox.domain.barcode.BarcodeError

/** Errors use cases can produce. Adapters translate transport/HTTP failures into these. */
sealed interface DomainError {
    data class InvalidBarcode(val reason: BarcodeError) : DomainError
    data object NotFound : DomainError
    data object Unauthorized : DomainError
    data class RateLimited(val retryAfterSeconds: Long?) : DomainError
    data object Offline : DomainError
    data class Unexpected(val message: String) : DomainError
}

sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: DomainError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

fun <T> Outcome<T>.valueOrNull(): T? = (this as? Outcome.Success)?.value
