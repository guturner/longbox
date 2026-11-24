package com.guturner.longbox.domain

import com.guturner.longbox.domain.barcode.BarcodeError
import com.guturner.longbox.domain.barcode.BarcodeParseResult
import com.guturner.longbox.domain.barcode.ComicBarcode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComicBarcodeTest {

    // UPC from Metron's API docs example: 75960609558200111
    private val upc = "759606095582"

    @Test
    fun testParsesUpcWithSeparateAddOn() {
        // Given / When
        val result = ComicBarcode.parse(upc, "00111")

        // Then
        val barcode = assertIs<BarcodeParseResult.Valid>(result).barcode
        assertEquals("75960609558200111", barcode.fullCode)
        assertEquals(1, barcode.issueNumberHint)
        assertEquals(1, barcode.coverVariant)
        assertEquals(1, barcode.printing)
        assertTrue(barcode.isCoverA)
    }

    @Test
    fun testParsesSingle17DigitString() {
        val barcode = assertIs<BarcodeParseResult.Valid>(ComicBarcode.parse("75960609558215023")).barcode

        assertEquals("15023", barcode.supplement)
        assertEquals(150, barcode.issueNumberHint)
        assertEquals(2, barcode.coverVariant)
        assertEquals(3, barcode.printing)
        assertFalse(barcode.isCoverA)
    }

    @Test
    fun testAcceptsEan13WithLeadingZero() {
        val barcode = assertIs<BarcodeParseResult.Valid>(ComicBarcode.parse("0$upc", "00111")).barcode

        assertEquals(upc, barcode.upc)
    }

    @Test
    fun testVariantNormalisesToCoverAKeepingPrinting() {
        val variant = assertIs<BarcodeParseResult.Valid>(ComicBarcode.parse(upc, "00132")).barcode

        assertEquals("00112", variant.asCoverA().supplement)
    }

    @Test
    fun testRejectsBadCheckDigit() {
        val result = ComicBarcode.parse("759606095583", "00111")

        assertEquals(BarcodeError.INVALID_CHECK_DIGIT, assertIs<BarcodeParseResult.Invalid>(result).error)
    }

    @Test
    fun testRejectsWrongLength() {
        val result = ComicBarcode.parse("12345")

        assertEquals(BarcodeError.INVALID_LENGTH, assertIs<BarcodeParseResult.Invalid>(result).error)
    }

    @Test
    fun testTwoDigitPeriodicalAddOnIsIgnored() {
        val barcode = assertIs<BarcodeParseResult.Valid>(ComicBarcode.parse(upc, "07")).barcode

        assertNull(barcode.supplement)
        assertTrue(barcode.isCoverA)
    }

    @Test
    fun testRejectsNonNumeric() {
        val result = ComicBarcode.parse("75960609558X", null)

        assertEquals(BarcodeError.NON_NUMERIC, assertIs<BarcodeParseResult.Invalid>(result).error)
    }
}
