package com.guturner.longbox.contract

import kotlinx.serialization.Serializable

/** Versioned base path, matching the /api/v1 convention used in queuehaven. */
const val API_V1 = "/api/v1"

/** Custom header every state-changing browser request must carry (CSRF defence, see docs/SECURITY.md). */
const val CSRF_HEADER = "X-Longbox-Client"

@Serializable
data class PublisherDto(val id: Long, val name: String)

@Serializable
data class SeriesDto(
    val id: Long,
    val name: String,
    val sortName: String,
    val volume: Int,
    val yearBegan: Int,
)

@Serializable
data class CreatorRefDto(val id: Long, val name: String)

@Serializable
data class CreditDto(val creator: CreatorRefDto, val roles: List<String>)

@Serializable
data class StoryArcRefDto(val id: Long, val name: String)

@Serializable
data class CoverDto(val name: String, val imageUrl: String? = null, val upc: String? = null)

@Serializable
data class ComicDto(
    val id: Long,
    val publisher: PublisherDto,
    val series: SeriesDto,
    val number: String,
    val title: String? = null,
    val coverDate: String? = null,
    val storeDate: String? = null,
    val summary: String? = null,
    val coverA: CoverDto,
    val variantCovers: List<CoverDto> = emptyList(),
    val credits: List<CreditDto> = emptyList(),
    val storyArcs: List<StoryArcRefDto> = emptyList(),
    val upc: String? = null,
)

@Serializable
data class ContainerDto(
    val id: String,
    val name: String,
    val kind: String,
    val entryCount: Int,
    val createdAtEpochMillis: Long,
)

@Serializable
data class ContainerListResponse(val containers: List<ContainerDto>)

@Serializable
data class CreateContainerRequest(val name: String, val kind: String, val clientMutationId: String? = null)

@Serializable
data class RenameContainerRequest(val name: String)

@Serializable
data class MoveToContainerRequest(val entryId: String)

@Serializable
data class CollectionEntryDto(
    val id: String,
    val scannedCode: String,
    val status: String,              // PENDING | IN_STACK | LOOKUP_FAILED | IN_BOX | DELETED (legacy: RESOLVED, NOT_FOUND)
    val comic: ComicDto? = null,
    val ownedCoverName: String? = null,
    val addedAtEpochMillis: Long,
    val copyCount: Int = 1,
    val containerId: String? = null,
    val containerName: String? = null,
)

@Serializable
data class CollectionResponse(val entries: List<CollectionEntryDto>)

/**
 * [clientMutationId] is generated on the device when the scan is queued. Replaying the offline
 * outbox after a dropped connection re-sends it, and the server returns the existing entry
 * instead of adding a duplicate (idempotent POST).
 */
@Serializable
data class ScanRequest(
    val code: String,
    val addOn: String? = null,
    val clientMutationId: String? = null,
    val scannedAtEpochMillis: Long? = null,
    /** If set, the server assigns the entry to this container atomically in the same transaction. */
    val targetContainerId: String? = null,
)

@Serializable
data class CreatorDto(
    val id: Long,
    val name: String,
    val bio: String? = null,
    val imageUrl: String? = null,
    val birth: String? = null,
    val death: String? = null,
)

@Serializable
data class StoryArcDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    val imageUrl: String? = null,
)

@Serializable
data class ReleasesResponse(val from: String, val to: String, val comics: List<ComicDto>)

/** Google ID token exchanged for Longbox tokens. [client] is "web" (cookies) or "native" (bearer). */
@Serializable
data class GoogleSignInRequest(val idToken: String, val client: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

/** Native clients only. Web clients receive the same tokens as HttpOnly cookies instead. */
@Serializable
data class TokenResponse(
    val accessToken: String,
    val accessTokenExpiresInSeconds: Long,
    val refreshToken: String,
)

@Serializable
data class MeResponse(val email: String, val displayName: String?)

@Serializable
data class ErrorDto(val code: String, val message: String, val retryAfterSeconds: Long? = null)
