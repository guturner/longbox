package com.guturner.longbox.presentation

import com.guturner.longbox.domain.model.DomainError

/** User-facing message. Kept as plain text so every platform can render it the same way. */
data class UiMessage(val text: String, val isError: Boolean = true)

fun DomainError.toUiMessage(): UiMessage = when (this) {
    is DomainError.InvalidBarcode -> UiMessage(reason.message)
    DomainError.NotFound -> UiMessage("Not found in the catalog yet.")
    DomainError.Unauthorized -> UiMessage("Your session expired. Please sign in again.")
    is DomainError.RateLimited -> UiMessage(
        "The comic catalog is busy" + (retryAfterSeconds?.let { "; retrying in about ${(it + 59) / 60} min." } ?: "."),
    )
    DomainError.Offline -> UiMessage("Can't reach the server. Check your connection.")
    is DomainError.Unexpected -> UiMessage("Something went wrong. Please try again.")
}
