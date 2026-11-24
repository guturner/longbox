package com.guturner.longbox.domain.barcode

/**
 * A direct-market comic barcode: a 12-digit UPC-A that identifies the *series*, plus a
 * 5-digit supplement (EAN-5 add-on) that identifies the *issue, cover variant and printing*.
 *
 * Supplement layout (Diamond Comic Distributors UPC guide; see docs/DATA-SOURCES.md):
 *   digits 1-3 = issue number, digit 4 = cover variant (1 = Cover A), digit 5 = printing.
 *
 * Older (pre-~1993) books may only carry a 2-digit add-on or none at all; those still parse,
 * but [supplement] is null and lookups fall back to a series-level match.
 */
data class ComicBarcode(
    val upc: String,
    val supplement: String?,
) {

    /** The full 17-digit code Metron stores in its `upc` field (or 12 digits when no add-on). */
    val fullCode: String get() = upc + (supplement ?: "")

    val issueNumberHint: Int? get() = supplement?.take(3)?.toIntOrNull()

    /** 1 = Cover A / regular edition, 2 = first variant, and so on. */
    val coverVariant: Int? get() = supplement?.get(3)?.digitToIntOrNull()

    val printing: Int? get() = supplement?.get(4)?.digitToIntOrNull()

    val isCoverA: Boolean get() = coverVariant == null || coverVariant == 1

    /** Same issue and printing, but Cover A. Used as a lookup fallback for variant covers. */
    fun asCoverA(): ComicBarcode =
        if (supplement == null || isCoverA) this
        else copy(supplement = supplement.substring(0, 3) + "1" + supplement.substring(4))

    override fun toString(): String = if (supplement == null) upc else "$upc $supplement"

    companion object {

        /**
         * Parses what a scanner returns. Accepts:
         *  - main symbol and add-on separately (ZXing reports the add-on as metadata),
         *  - a single 17-digit string (12 + 5), or
         *  - an EAN-13 with a leading 0, which is the same number as a UPC-A.
         */
        fun parse(main: String, addOn: String? = null): BarcodeParseResult {
            val digits = main.filter { !it.isWhitespace() && it != '-' }
            val extra = addOn?.filter { !it.isWhitespace() }?.takeIf { it.isNotEmpty() }

            if (digits.any { !it.isDigit() } || extra?.any { !it.isDigit() } == true) {
                return BarcodeParseResult.Invalid(BarcodeError.NON_NUMERIC)
            }

            val (upc, supplement) = when {
                digits.length == 17 && extra == null -> digits.substring(0, 12) to digits.substring(12)
                digits.length == 13 && digits.startsWith('0') -> digits.substring(1) to extra
                digits.length == 12 -> digits to extra
                else -> return BarcodeParseResult.Invalid(BarcodeError.INVALID_LENGTH)
            }

            if (!hasValidCheckDigit(upc)) {
                return BarcodeParseResult.Invalid(BarcodeError.INVALID_CHECK_DIGIT)
            }

            return when (supplement?.length) {
                null -> BarcodeParseResult.Valid(ComicBarcode(upc, null))
                5 -> BarcodeParseResult.Valid(ComicBarcode(upc, supplement))
                // A 2-digit add-on is a periodical issue code, not a comic supplement.
                2 -> BarcodeParseResult.Valid(ComicBarcode(upc, null))
                else -> BarcodeParseResult.Invalid(BarcodeError.INVALID_SUPPLEMENT)
            }
        }

        /** GS1 mod-10 check digit for UPC-A: odd positions x3, even positions x1. */
        fun hasValidCheckDigit(upc: String): Boolean {
            if (upc.length != 12 || upc.any { !it.isDigit() }) return false
            val body = upc.take(11).map { it.digitToInt() }
            val sum = body.withIndex().sumOf { (index, digit) -> if (index % 2 == 0) digit * 3 else digit }
            val expected = (10 - sum % 10) % 10
            return upc.last().digitToInt() == expected
        }
    }
}

enum class BarcodeError(val message: String) {
    NON_NUMERIC("That barcode contains non-numeric characters."),
    INVALID_LENGTH("That doesn't look like a UPC. Comic barcodes have 12 digits plus a 5-digit add-on."),
    INVALID_CHECK_DIGIT("The barcode didn't read cleanly (check digit mismatch). Try again."),
    INVALID_SUPPLEMENT("The small add-on barcode should be 5 digits."),
}

sealed interface BarcodeParseResult {
    data class Valid(val barcode: ComicBarcode) : BarcodeParseResult
    data class Invalid(val error: BarcodeError) : BarcodeParseResult
}
