package com.guturner.longbox.domain.usecase

import com.guturner.longbox.domain.barcode.BarcodeParseResult
import com.guturner.longbox.domain.barcode.ComicBarcode
import com.guturner.longbox.domain.library.LibraryFacets
import com.guturner.longbox.domain.library.LibraryQuery
import com.guturner.longbox.domain.model.CollectionEntry
import com.guturner.longbox.domain.model.Comic
import com.guturner.longbox.domain.model.ComicId
import com.guturner.longbox.domain.model.Creator
import com.guturner.longbox.domain.model.CreatorId
import com.guturner.longbox.domain.model.DomainError
import com.guturner.longbox.domain.model.EntryId
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.model.StoryArc
import com.guturner.longbox.domain.model.StoryArcId
import com.guturner.longbox.domain.repository.CollectionRepository
import com.guturner.longbox.domain.repository.ComicCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class LibraryView(
    val entries: List<CollectionEntry>,
    /** Facets come from the whole collection, so the filter menus don't shrink as you filter. */
    val facets: LibraryFacets,
    val totalInCollection: Int,
)

class ObserveLibraryUseCase(
    private val collectionRepository: CollectionRepository,
) {
    operator fun invoke(query: Flow<LibraryQuery>): Flow<LibraryView> =
        combine(collectionRepository.observeEntries(), query) { entries, q ->
            LibraryView(
                entries = entries.filter(q.filter::matches).sortedWith(q.sort.comparator),
                facets = LibraryFacets.from(entries),
                totalInCollection = entries.size,
            )
        }
}

/** Validates the scan locally (instant feedback, no network) before queuing the lookup. */
class ScanComicUseCase(
    private val collectionRepository: CollectionRepository,
) {
    suspend operator fun invoke(mainCode: String, addOn: String?): Outcome<CollectionEntry> =
        when (val parsed = ComicBarcode.parse(mainCode, addOn)) {
            is BarcodeParseResult.Invalid -> Outcome.Failure(DomainError.InvalidBarcode(parsed.error))
            is BarcodeParseResult.Valid -> collectionRepository.addScan(parsed.barcode)
        }
}

class RemoveFromCollectionUseCase(
    private val collectionRepository: CollectionRepository,
) {
    suspend operator fun invoke(id: EntryId): Outcome<Unit> = collectionRepository.remove(id)
}

class RefreshCollectionUseCase(
    private val collectionRepository: CollectionRepository,
) {
    suspend operator fun invoke(): Outcome<Unit> = collectionRepository.refresh()
}

class GetComicDetailUseCase(
    private val catalog: ComicCatalog,
) {
    suspend operator fun invoke(id: ComicId): Outcome<Comic> = catalog.comic(id)
}

class GetCreatorUseCase(
    private val catalog: ComicCatalog,
) {
    suspend operator fun invoke(id: CreatorId): Outcome<Creator> = catalog.creator(id)
}

class GetStoryArcUseCase(
    private val catalog: ComicCatalog,
) {
    suspend operator fun invoke(id: StoryArcId): Outcome<StoryArc> = catalog.storyArc(id)
}
