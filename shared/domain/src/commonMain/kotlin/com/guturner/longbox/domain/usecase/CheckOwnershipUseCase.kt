package com.guturner.longbox.domain.usecase

import com.guturner.longbox.domain.barcode.BarcodeError
import com.guturner.longbox.domain.barcode.BarcodeParseResult
import com.guturner.longbox.domain.barcode.ComicBarcode
import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.repository.CollectionRepository
import kotlinx.coroutines.flow.first

/** Answer to "is the comic in my hand already in my longbox?" */
sealed interface Ownership {
    /** Same issue, same cover, same printing. */
    data class Owned(val copies: List<CollectionEntry>) : Ownership

    /** Same series and issue number, but a different cover variant or printing. */
    data class OwnIssueDifferentCover(val copies: List<CollectionEntry>, val barcode: ComicBarcode) : Ownership

    data class NotOwned(val barcode: ComicBarcode) : Ownership

    data class Invalid(val error: BarcodeError) : Ownership
}

/**
 * Works entirely against the locally cached collection (plus queued offline scans), so it runs
 * in a shop with no signal. The barcode alone is enough: UPC = series, supplement = issue/cover/printing.
 */
class CheckOwnershipUseCase(
    private val collectionRepository: CollectionRepository,
) {
    suspend operator fun invoke(mainCode: String, addOn: String?): Ownership {
        val barcode = when (val parsed = ComicBarcode.parse(mainCode, addOn)) {
            is BarcodeParseResult.Invalid -> return Ownership.Invalid(parsed.error)
            is BarcodeParseResult.Valid -> parsed.barcode
        }
        return check(barcode, collectionRepository.observeEntries().first())
    }

    fun check(barcode: ComicBarcode, entries: List<CollectionEntry>): Ownership {
        val exact = entries.filter { entry -> barcode.fullCode in entry.knownCodes() }
        if (exact.isNotEmpty()) return Ownership.Owned(exact)

        val issue = barcode.issueNumberHint
        val sameIssue = if (barcode.supplement == null || issue == null) emptyList() else entries.filter { entry ->
            entry.knownCodes().any { code -> code.length == 17 && code.startsWith(barcode.upc) && code.substring(12, 15).toInt() == issue }
        }
        return if (sameIssue.isNotEmpty()) Ownership.OwnIssueDifferentCover(sameIssue, barcode) else Ownership.NotOwned(barcode)
    }

    /** The code that was scanned for this copy, plus the catalog's codes for the cover it is. */
    private fun CollectionEntry.knownCodes(): Set<String> = buildSet {
        add(scannedCode)
        val comic = comic ?: return@buildSet
        if (ownedCoverName == null) comic.coverA.upc?.let(::add)
        else comic.variantCovers.filter { it.name == ownedCoverName }.mapNotNullTo(this) { it.upc }
    }
}
