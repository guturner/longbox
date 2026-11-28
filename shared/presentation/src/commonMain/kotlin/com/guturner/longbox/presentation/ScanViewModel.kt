package com.guturner.longbox.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guturner.longbox.domain.barcode.ComicBarcode
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.usecase.CheckOwnershipUseCase
import com.guturner.longbox.domain.usecase.Ownership
import com.guturner.longbox.domain.usecase.ScanComicUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScanMode {
    /** Going through boxes or the weekly pull: every scan is added. */
    ADD,

    /** In a shop: every scan is checked against the (cached) library; adding is a deliberate tap. */
    CHECK,
}

data class OwnershipCard(val title: String, val detail: String, val owned: Boolean, val barcode: ComicBarcode?)

data class DuplicateScan(val displayTitle: String, val copyCount: Int, val mainCode: String, val addOn: String?)

data class ScanUiState(
    val mode: ScanMode = ScanMode.ADD,
    /** Most recent first; shown under the viewfinder so you can see the box going in. */
    val recent: List<ScanFeedback> = emptyList(),
    val sessionCount: Int = 0,
    /** Result of the last CHECK-mode scan. */
    val ownership: OwnershipCard? = null,
    val duplicatePending: DuplicateScan? = null,
)

data class ScanFeedback(val code: String, val message: UiMessage)

/**
 * Turns camera detections into collection entries (ADD) or ownership answers (CHECK).
 * Cameras report the same barcode many times per second, so a code is ignored if it was
 * handled within the last [REPEAT_WINDOW_MS]. Both modes work offline.
 */
class ScanViewModel(
    private val scanComic: ScanComicUseCase,
    private val checkOwnership: CheckOwnershipUseCase,
    private val nowMillis: () -> Long,
    initialMode: ScanMode = ScanMode.ADD,
) : ViewModel() {

    private val mutableState = MutableStateFlow(ScanUiState(mode = initialMode))
    val state: StateFlow<ScanUiState> = mutableState.asStateFlow()

    private val lastHandled = mutableMapOf<String, Long>()
    private var inFlight: String? = null
    /** Codes successfully processed in this scanner session; silently rejected if scanned again. */
    private val sessionCodes = mutableSetOf<String>()

    fun setMode(mode: ScanMode) = mutableState.update { it.copy(mode = mode, ownership = null) }

    /** Returns true if the detection was accepted (the UI uses this to vibrate / beep). */
    fun onDetected(mainCode: String, addOn: String?): Boolean {
        val key = mainCode + (addOn ?: "")
        // Session de-dup: in ADD mode, block re-scanning a code that was already successfully
        // added this session (prevents duplicate pending entries while offline). CHECK mode
        // always re-queries so the user can confirm ownership after adding.
        if (mutableState.value.mode == ScanMode.ADD && key in sessionCodes) return false
        val now = nowMillis()
        val last = lastHandled[key]
        if (inFlight == key || (last != null && now - last < REPEAT_WINDOW_MS)) return false
        inFlight = key
        lastHandled[key] = now

        viewModelScope.launch {
            when (mutableState.value.mode) {
                ScanMode.ADD -> add(key, mainCode, addOn)
                ScanMode.CHECK -> check(key, mainCode, addOn)
            }
            inFlight = null
        }
        return true
    }

    /** "I'm buying it": add the comic from the last CHECK result. Queued if away from home. */
    fun addCheckedComic() {
        val barcode = mutableState.value.ownership?.barcode ?: return
        viewModelScope.launch {
            add(barcode.fullCode, barcode.upc, barcode.supplement)
            mutableState.update { it.copy(ownership = it.ownership?.copy(title = "Added to your library", owned = true, barcode = null)) }
        }
    }

    fun confirmAddCopy() {
        val pending = mutableState.value.duplicatePending ?: return
        mutableState.update { it.copy(duplicatePending = null) }
        viewModelScope.launch {
            val key = pending.mainCode + (pending.addOn ?: "")
            val feedback = when (val result = scanComic(pending.mainCode, pending.addOn)) {
                is Outcome.Success -> {
                    sessionCodes.add(key)
                    mutableState.update { it.copy(sessionCount = it.sessionCount + 1) }
                    val where = if (result.value.isQueuedOnDevice) "saved on this device until you're home" else "looking it up…"
                    ScanFeedback(key, UiMessage("Added another copy — $where", isError = false))
                }
                is Outcome.Failure -> ScanFeedback(key, result.error.toUiMessage())
            }
            mutableState.update { it.copy(recent = (listOf(feedback) + it.recent).take(MAX_RECENT)) }
        }
    }

    fun dismissDuplicate() = mutableState.update { it.copy(duplicatePending = null) }

    private suspend fun add(key: String, mainCode: String, addOn: String?) {
        val ownership = checkOwnership(mainCode, addOn)
        if (ownership is Ownership.Owned) {
            val comic = ownership.copies.firstNotNullOfOrNull { it.comic }
            val copyCount = ownership.copies.firstOrNull()?.copyCount ?: 1
            mutableState.update {
                it.copy(duplicatePending = DuplicateScan(
                    displayTitle = comic?.displayTitle ?: formatCode(key),
                    copyCount = copyCount,
                    mainCode = mainCode,
                    addOn = addOn,
                ))
            }
            return
        }
        val feedback = when (val result = scanComic(mainCode, addOn)) {
            is Outcome.Success -> {
                sessionCodes.add(key)
                mutableState.update { it.copy(sessionCount = it.sessionCount + 1) }
                val where = if (result.value.isQueuedOnDevice) "saved on this device until you're home" else "looking it up…"
                ScanFeedback(key, UiMessage("Added ${formatCode(key)} — $where", isError = false))
            }
            is Outcome.Failure -> {
                lastHandled.remove(key) // let the user retry immediately
                ScanFeedback(key, result.error.toUiMessage())
            }
        }
        mutableState.update { it.copy(recent = (listOf(feedback) + it.recent).take(MAX_RECENT)) }
    }

    private suspend fun check(key: String, mainCode: String, addOn: String?) {
        val card = when (val result = checkOwnership(mainCode, addOn)) {
            is Ownership.Owned -> {
                val comic = result.copies.firstNotNullOfOrNull { it.comic }
                OwnershipCard(
                    title = "You own this",
                    detail = (comic?.displayTitle ?: formatCode(key)) +
                        if ((result.copies.firstOrNull()?.copyCount ?: 1) > 1) " · ${result.copies.first().copyCount} copies" else "",
                    owned = true,
                    barcode = null,
                )
            }
            is Ownership.OwnIssueDifferentCover -> {
                val comic = result.copies.firstNotNullOfOrNull { it.comic }
                val issue = comic?.displayTitle ?: "issue #${result.barcode.issueNumberHint}"
                OwnershipCard(
                    title = "You own a different cover",
                    detail = "You have $issue, but not this cover/printing " +
                        "(cover ${result.barcode.coverVariant}, printing ${result.barcode.printing}).",
                    owned = false,
                    barcode = result.barcode,
                )
            }
            is Ownership.NotOwned -> OwnershipCard(
                title = "Not in your library",
                detail = result.barcode.issueNumberHint?.let { "Issue #$it · cover ${result.barcode.coverVariant}" } ?: formatCode(key),
                owned = false,
                barcode = result.barcode,
            )
            is Ownership.Invalid -> {
                lastHandled.remove(key)
                OwnershipCard("Couldn't read that", result.error.message, owned = false, barcode = null)
            }
        }
        mutableState.update { it.copy(ownership = card) }
    }

    private fun formatCode(code: String) =
        if (code.length == 17) "${code.take(12)} ${code.drop(12)}" else code

    private companion object {
        const val REPEAT_WINDOW_MS = 4_000L
        const val MAX_RECENT = 20
    }
}
