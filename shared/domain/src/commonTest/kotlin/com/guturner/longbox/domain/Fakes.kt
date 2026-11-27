package com.guturner.longbox.domain

import com.guturner.longbox.domain.barcode.ComicBarcode
import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.Comic
import com.guturner.longbox.domain.model.EntryId
import com.guturner.longbox.domain.model.EntryState
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.repository.BarcodeLookup
import com.guturner.longbox.domain.repository.CollectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeCollectionRepository(initial: List<CollectionEntry> = emptyList()) : CollectionRepository {
    val entries = MutableStateFlow(initial)
    val scanned = mutableListOf<ComicBarcode>()

    override fun observeEntries(): Flow<List<CollectionEntry>> = entries
    override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun addScan(barcode: ComicBarcode): Outcome<CollectionEntry> {
        scanned += barcode
        val entry = CollectionEntry(EntryId("s${scanned.size}"), barcode.fullCode, EntryState.PENDING, null, null, 0)
        entries.value = entries.value + entry
        return Outcome.Success(entry)
    }

    override suspend fun remove(id: EntryId): Outcome<Unit> {
        entries.value = entries.value.filterNot { it.id == id }
        return Outcome.Success(Unit)
    }
}

class FakeBarcodeLookup(private val byCode: Map<String, Comic>) : BarcodeLookup {
    val queried = mutableListOf<String>()

    override suspend fun findByCode(code: String): Outcome<Comic?> {
        queried += code
        return Outcome.Success(byCode[code])
    }
}
