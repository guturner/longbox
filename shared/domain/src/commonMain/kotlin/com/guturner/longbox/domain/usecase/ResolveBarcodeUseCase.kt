package com.guturner.longbox.domain.usecase

import com.guturner.longbox.domain.barcode.ComicBarcode
import com.guturner.longbox.domain.model.Comic
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.repository.BarcodeLookup

data class ResolvedScan(
    val comic: Comic,
    /** Cover the physical copy has; null when it is Cover A or unknown. */
    val ownedCoverName: String?,
)

/**
 * Server-side rule for turning a barcode into a catalog issue:
 *  1. exact match on UPC + supplement (works for Cover A and for variants the catalog lists),
 *  2. otherwise retry as Cover A (variant digit -> 1) and pick the matching variant cover.
 */
class ResolveBarcodeUseCase(
    private val lookup: BarcodeLookup,
) {
    suspend operator fun invoke(barcode: ComicBarcode): Outcome<ResolvedScan?> {
        val exact = lookup.findByCode(barcode.fullCode)
        if (exact is Outcome.Failure) return exact
        (exact as Outcome.Success).value?.let { return Outcome.Success(resolved(it, barcode)) }

        if (barcode.isCoverA) return Outcome.Success(null)

        return when (val fallback = lookup.findByCode(barcode.asCoverA().fullCode)) {
            is Outcome.Failure -> fallback
            is Outcome.Success -> Outcome.Success(fallback.value?.let { resolved(it, barcode) })
        }
    }

    private fun resolved(comic: Comic, barcode: ComicBarcode): ResolvedScan {
        if (barcode.isCoverA) return ResolvedScan(comic, ownedCoverName = null)
        val byUpc = comic.variantCovers.firstOrNull { it.upc == barcode.fullCode }
        // Fall back to catalog order: variant digit 2 is usually the first listed variant.
        val byPosition = barcode.coverVariant?.let { comic.variantCovers.getOrNull(it - 2) }
        val name = (byUpc ?: byPosition)?.name ?: "Variant ${barcode.coverVariant}"
        return ResolvedScan(comic, ownedCoverName = name)
    }
}
