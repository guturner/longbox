package com.guturner.longbox.domain

import com.guturner.longbox.domain.Fixtures.absoluteBatman
import com.guturner.longbox.domain.Fixtures.comic
import com.guturner.longbox.domain.barcode.BarcodeError
import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.Cover
import com.guturner.longbox.domain.model.EntryId
import com.guturner.longbox.domain.model.LookupStatus
import com.guturner.longbox.domain.usecase.CheckOwnershipUseCase
import com.guturner.longbox.domain.usecase.Ownership
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CheckOwnershipTest {

    private val upc = "759606095582"
    private val issue1 = comic(9, absoluteBatman, "1", variants = listOf(Cover("Cover B", null, "${upc}00121")))
        .copy(coverA = Cover("Cover A", null, "${upc}00111"))

    private fun owned(code: String, comic: com.guturner.longbox.domain.model.Comic?, cover: String? = null, status: LookupStatus = LookupStatus.RESOLVED) =
        CollectionEntry(EntryId(code), code, status, comic, cover, 0)

    @Test
    fun testExactCopyIsOwned() = runTest {
        // Given
        val useCase = CheckOwnershipUseCase(FakeCollectionRepository(listOf(owned("${upc}00111", issue1))))

        // When / Then
        assertIs<Ownership.Owned>(useCase(upc, "00111"))
    }

    @Test
    fun testOtherCoverOfOwnedIssueIsFlagged() = runTest {
        val useCase = CheckOwnershipUseCase(FakeCollectionRepository(listOf(owned("${upc}00111", issue1))))

        val result = useCase(upc, "00121")

        assertIs<Ownership.OwnIssueDifferentCover>(result)
    }

    @Test
    fun testQueuedOfflineScanCountsAsOwned() = runTest {
        // A comic bought while roaming is pending (no catalog data yet) but must still show as owned.
        val useCase = CheckOwnershipUseCase(FakeCollectionRepository(listOf(owned("${upc}00211", null, status = LookupStatus.PENDING))))

        assertIs<Ownership.Owned>(useCase(upc, "00211"))
        assertIs<Ownership.NotOwned>(useCase(upc, "00311"))
    }

    @Test
    fun testInvalidBarcode() = runTest {
        val result = CheckOwnershipUseCase(FakeCollectionRepository())("759606095583", "00111")

        assertEquals(BarcodeError.INVALID_CHECK_DIGIT, assertIs<Ownership.Invalid>(result).error)
    }
}
