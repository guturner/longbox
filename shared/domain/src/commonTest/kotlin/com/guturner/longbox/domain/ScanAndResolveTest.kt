package com.guturner.longbox.domain

import com.guturner.longbox.domain.Fixtures.absoluteBatman
import com.guturner.longbox.domain.Fixtures.comic
import com.guturner.longbox.domain.barcode.BarcodeError
import com.guturner.longbox.domain.barcode.BarcodeParseResult
import com.guturner.longbox.domain.barcode.ComicBarcode
import com.guturner.longbox.domain.model.Cover
import com.guturner.longbox.domain.model.DomainError
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.usecase.ResolveBarcodeUseCase
import com.guturner.longbox.domain.usecase.ScanComicUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanAndResolveTest {

    private val upc = "759606095582"
    private val coverA = "${upc}00111"
    private val coverB = "${upc}00121"

    private val issue = comic(
        id = 9, series = absoluteBatman, number = "1",
        variants = listOf(Cover("Cover B Jim Lee", null, coverB), Cover("Cover C", null, "${upc}00131")),
    )

    private fun barcode(code: String) =
        (ComicBarcode.parse(code) as BarcodeParseResult.Valid).barcode

    @Test
    fun testInvalidScanNeverReachesRepository() = runTest {
        // Given
        val repo = FakeCollectionRepository()

        // When
        val result = ScanComicUseCase(repo)("759606095583", "00111")

        // Then
        val error = assertIs<Outcome.Failure>(result).error
        assertEquals(DomainError.InvalidBarcode(BarcodeError.INVALID_CHECK_DIGIT), error)
        assertTrue(repo.scanned.isEmpty())
    }

    @Test
    fun testValidScanIsQueued() = runTest {
        val repo = FakeCollectionRepository()

        ScanComicUseCase(repo)(upc, "00111")

        assertEquals(listOf(coverA), repo.scanned.map { it.fullCode })
    }

    @Test
    fun testCoverAResolvesWithoutVariantName() = runTest {
        val resolved = ResolveBarcodeUseCase(FakeBarcodeLookup(mapOf(coverA to issue)))(barcode(coverA))

        val scan = assertIs<Outcome.Success<*>>(resolved).value
        assertEquals(issue, (scan as com.guturner.longbox.domain.usecase.ResolvedScan).comic)
        assertNull(scan.ownedCoverName)
    }

    @Test
    fun testUnlistedVariantFallsBackToCoverAAndNamesTheVariant() = runTest {
        // Given: the catalog only indexes the Cover A code
        val lookup = FakeBarcodeLookup(mapOf(coverA to issue))

        // When
        val result = ResolveBarcodeUseCase(lookup)(barcode(coverB))

        // Then
        val scan = assertIs<Outcome.Success<com.guturner.longbox.domain.usecase.ResolvedScan?>>(result).value!!
        assertEquals(listOf(coverB, coverA), lookup.queried)
        assertEquals("Cover B Jim Lee", scan.ownedCoverName)
    }

    @Test
    fun testUnknownCoverAReturnsNullWithoutSecondLookup() = runTest {
        val lookup = FakeBarcodeLookup(emptyMap())

        val result = ResolveBarcodeUseCase(lookup)(barcode(coverA))

        assertNull(assertIs<Outcome.Success<*>>(result).value)
        assertEquals(1, lookup.queried.size)
    }
}
