package com.guturner.longbox.data.repository

import com.guturner.longbox.contract.API_V1
import com.guturner.longbox.contract.ComicDto
import com.guturner.longbox.contract.ContractMapper
import com.guturner.longbox.contract.CreatorDto
import com.guturner.longbox.contract.GoogleSignInRequest
import com.guturner.longbox.contract.ReleasesResponse
import com.guturner.longbox.contract.StoryArcDto
import com.guturner.longbox.contract.TokenResponse
import com.guturner.longbox.data.remote.LongboxApi
import com.guturner.longbox.data.session.SessionTransport
import com.guturner.longbox.data.session.StoredTokens
import com.guturner.longbox.domain.model.CalendarDate
import com.guturner.longbox.domain.model.Comic
import com.guturner.longbox.domain.model.ComicId
import com.guturner.longbox.domain.model.Creator
import com.guturner.longbox.domain.model.CreatorId
import com.guturner.longbox.domain.model.DomainError
import com.guturner.longbox.domain.model.Outcome
import com.guturner.longbox.domain.model.StoryArc
import com.guturner.longbox.domain.model.StoryArcId
import com.guturner.longbox.domain.model.map
import com.guturner.longbox.domain.repository.ComicCatalog
import com.guturner.longbox.domain.repository.SessionRepository
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Catalog reads with an offline fallback to the on-device library cache. */
class ApiComicCatalog(
    private val api: LongboxApi,
    private val offline: OfflineFirstCollectionRepository,
) : ComicCatalog {

    override suspend fun comic(id: ComicId): Outcome<Comic> =
        when (val remote = api.get<ComicDto> { url.takeFrom("$API_V1/comics/${id.value}") }) {
            is Outcome.Success -> Outcome.Success(ContractMapper.asComic(remote.value))
            is Outcome.Failure -> offline.cachedComicDto(id.value)?.let { Outcome.Success(ContractMapper.asComic(it)) } ?: remote
        }

    override suspend fun creator(id: CreatorId): Outcome<Creator> =
        when (val remote = api.get<CreatorDto> { url.takeFrom("$API_V1/creators/${id.value}") }) {
            is Outcome.Success -> {
                offline.rememberCreator(remote.value) // so the blurb is there next time you're roaming
                Outcome.Success(ContractMapper.asCreator(remote.value))
            }
            is Outcome.Failure -> offline.cachedCreator(id.value)?.let { Outcome.Success(ContractMapper.asCreator(it)) } ?: remote
        }

    override suspend fun storyArc(id: StoryArcId): Outcome<StoryArc> =
        when (val remote = api.get<StoryArcDto> { url.takeFrom("$API_V1/arcs/${id.value}") }) {
            is Outcome.Success -> {
                offline.rememberArc(remote.value)
                Outcome.Success(ContractMapper.asStoryArc(remote.value))
            }
            is Outcome.Failure -> offline.cachedArc(id.value)?.let { Outcome.Success(ContractMapper.asStoryArc(it)) } ?: remote
        }

    override suspend fun releases(from: CalendarDate, to: CalendarDate): Outcome<List<Comic>> =
        api.get<ReleasesResponse> {
            url.takeFrom("$API_V1/releases")
            parameter("from", from.toString())
            parameter("to", to.toString())
        }.map { response -> response.comics.map(ContractMapper::asComic) }
}

class ApiSessionRepository(
    private val api: LongboxApi,
    private val transport: SessionTransport,
) : SessionRepository {

    private val signedIn = MutableStateFlow(false)
    override val isSignedIn: StateFlow<Boolean> = signedIn.asStateFlow()

    init {
        // Any 401 that survives a refresh attempt means the session is gone: show sign-in again.
        api.onSessionExpired = { signedIn.value = false }
    }

    override suspend fun restore(): Boolean {
        val ok = when (transport) {
            // Roaming (server unreachable) with stored tokens still counts as signed in: the app runs from cache.
            is SessionTransport.Bearer -> transport.store.load() != null &&
                api.send { url.takeFrom("$API_V1/auth/me") }.let { it is Outcome.Success || (it as Outcome.Failure).error == DomainError.Offline }
            SessionTransport.CookieSession -> api.send { url.takeFrom("$API_V1/auth/me") } is Outcome.Success
        }
        signedIn.value = ok
        return ok
    }

    override suspend fun signInWithGoogle(idToken: String): Outcome<Unit> {
        val client = if (transport is SessionTransport.CookieSession) "web" else "native"
        val response = try {
            api.signIn(GoogleSignInRequest(idToken, client))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return Outcome.Failure(DomainError.Offline)
        }
        if (!response.status.isSuccess()) {
            return Outcome.Failure(if (response.status.value == 403) DomainError.Unauthorized else DomainError.Unexpected("Sign-in failed"))
        }
        if (transport is SessionTransport.Bearer) {
            val tokens = response.body<TokenResponse>()
            transport.store.save(StoredTokens(tokens.accessToken, tokens.refreshToken))
        }
        signedIn.value = true
        return Outcome.Success(Unit)
    }

    override suspend fun signOut() {
        val tokens = (transport as? SessionTransport.Bearer)?.store?.load()
        api.logout(tokens)
        (transport as? SessionTransport.Bearer)?.store?.clear()
        signedIn.value = false
    }
}
