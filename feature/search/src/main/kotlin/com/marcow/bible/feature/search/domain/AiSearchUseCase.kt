package com.marcow.bible.feature.search.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import javax.inject.Inject
import javax.inject.Singleton

/** One step of an AI search reaching the sheet, mirroring the four callbacks of `BibleAiController.search`. */
sealed interface AiSearchUpdate {
    /** `onOverview`: the overview prose is ready to show. */
    data class OverviewReady(val overview: String) : AiSearchUpdate

    /** `onOverviewError`: the overview request failed. */
    data object OverviewFailed : AiSearchUpdate

    /**
     * `onReferences` *after* its `.then`: the references arrived **and** resolved to verses.
     *
     * The two steps are published as one because Dart did: the tile list has nothing to show until
     * the verses exist, and the search line kept spinning for `searching_scripture` across both.
     */
    data class ReferencesReady(val hits: List<AiSearchHit>) : AiSearchUpdate

    /** `onReferencesError`, or the `.catchError` after resolution — told apart by [failure]. */
    data class ReferencesFailed(val failure: ReferenceFailure) : AiSearchUpdate
}

/**
 * The port the sheet runs an AI search through.
 *
 * The use case below is the only implementation, but the sheet takes the port rather than the class
 * so its state machine can be exercised against a stub. Two requests landing in a chosen order — the
 * overview failing while the references arrive, the references failing at the chapter lookup — is
 * what the sheet's four panels are for, and none of it can be provoked reliably against a live model.
 */
interface AiSearch {
    /**
     * The four updates of one search, in whatever order they actually land.
     *
     * A [Flow] rather than a single response because the sheet draws each half as it arrives; the
     * Flutter build's `AiSearchResponse` was the value `search()` returned and nothing read it,
     * because `_searchAi` had already been given every piece through its callbacks.
     */
    fun search(query: String, memory: String, aiLanguage: String): Flow<AiSearchUpdate>
}

/**
 * Runs an AI search, mirroring `BibleAiController.search` in `legacy/flutter/lib/ai_service.dart`.
 *
 * Dart ran the two requests through `Future.wait([loadOverview(), loadReferences()])`, so both were
 * in flight at once and each published its own result or its own error as it landed. The two `async`
 * blocks below are that `Future.wait`: two coroutines launched into one [coroutineScope], neither
 * awaited until both are in flight.
 *
 * The per-branch `catch` is what makes the sheet's four states possible. Each request publishes its
 * own failure rather than letting it escape, so an overview that fails cannot hide references that
 * arrived — which is the behaviour the Flutter build's comment ("Each request publishes its own
 * error without hiding a successful peer") is describing, and the reason the sheet has separate
 * `overview_failed` and `references_failed` panels instead of one.
 *
 * Two deliberate differences from the Dart:
 *
 *  - [CancellationException] is re-thrown instead of caught. Dart had no cancellation; here the sheet
 *    cancels the in-flight search when the query changes, and a stale query must not write an error
 *    panel over the new one's results.
 *  - There is no terminal "sweep". Dart needed the `finally` in `_searchAi` to turn a still-spinning
 *    flag into a failure because `Future.wait` could return or throw with a callback unfired. Each
 *    branch below publishes exactly one terminal update on every path that is not a cancellation, so
 *    the sheet cannot be left spinning and the sweep would have nothing to do.
 *
 * The Dart `search()` also recorded the exchange into the AI memory store. That store does not exist
 * yet — Phase 4 brings `memory.md` and `transcript.jsonl` across (`NATIVE_PLAN.md` §5 Phase 4 item
 * 10) — so nothing is recorded here rather than recorded somewhere Phase 4 will have to move.
 */
@Singleton
class AiSearchUseCase @Inject constructor(
    private val searchOverview: SearchOverviewUseCase,
    private val searchReferences: SearchReferencesUseCase,
    private val resolveReferences: ResolveReferencesUseCase,
) : AiSearch {
    /**
     * The three catches are deliberately broad, and each names [CancellationException] first so the
     * one exception that must not be swallowed still propagates.
     *
     * The three collaborators are ports — a request can fail with a socket error, a decode error, a
     * 429 the retry logic gives up on, or a type the port does not declare — and Dart's `catch (_)`
     * was equally blind to all of them. What a failure means for the sheet is the caller's decision,
     * not this file's: an overview failure must not hide a references answer and the other way round,
     * which is why each catch publishes its own update and lets its peer carry on.
     */
    @Suppress("TooGenericExceptionCaught")
    override fun search(query: String, memory: String, aiLanguage: String): Flow<AiSearchUpdate> = channelFlow {
        coroutineScope {
            async {
                try {
                    send(AiSearchUpdate.OverviewReady(searchOverview(query, memory, aiLanguage)))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    send(AiSearchUpdate.OverviewFailed)
                }
            }
            async {
                val references = try {
                    searchReferences(query, memory, aiLanguage)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    send(AiSearchUpdate.ReferencesFailed(ReferenceFailure.REQUEST))
                    return@async
                }
                val hits = try {
                    resolveReferences(references.scriptures)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    send(AiSearchUpdate.ReferencesFailed(ReferenceFailure.VERSES))
                    return@async
                }
                send(AiSearchUpdate.ReferencesReady(hits))
            }
        }
    }
}

/**
 * The persistent-memory block the two prompts interpolate.
 *
 * `_memory.promptMemory(maxCharacters: 3500)` in `legacy/flutter/lib/ai_service.dart` read
 * `filesDir/memory.md`. Phase 4 brings that file across, and it needs the sign-in before it can be
 * trusted (it is the one legacy file that carries what the user told the AI), so until then the block
 * is empty — which is the same state the Flutter build was in on a fresh install with no memory yet.
 */
interface AiSearchMemory {
    /** The block for the prompt's `Persistent user memory:` line, or `''` when there is none. */
    suspend fun promptMemory(): String
}

@Singleton
class BlankAiSearchMemory @Inject constructor() : AiSearchMemory {
    override suspend fun promptMemory(): String = ""
}
