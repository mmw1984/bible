package com.marcow.bible.feature.aichat.domain

/**
 * `AiMemoryStore` of `legacy/flutter/lib/ai_memory_store_io.dart:6` — the two files the chat's memory
 * lives in, behind a port.
 *
 * The port is the same shape the search feature already took for its own memory block
 * (`AiSearchMemory` in `feature/search/domain/AiSearchUseCase.kt`), and it exists for the same reason:
 * `NATIVE_PLAN.md` §4.6 replaces the files with Room tables, and a screen that reads
 * [AiMemoryStore] does not move when that happens. [BlankAiMemoryStore] is what ships until then, and
 * it returns an empty transcript and an empty memory block — the same state the Flutter build was in
 * on a fresh install that had not yet had a conversation.
 */
interface AiMemoryStore {
    /**
     * The block for the prompt's `Persistent user memory:` line, truncated to [maxCharacters] from
     * the *end* of the document.
     *
     * From the end rather than the start because the file is append-only and its last entries are the
     * ones about the conversation in hand; a truncated head would be the oldest thing the user ever
     * asked. `promptMemory(maxCharacters: 7000)` in the chat and `3500` in the search are the two
     * callers, and they differ because the two prompts are different lengths.
     */
    suspend fun promptMemory(maxCharacters: Int = DEFAULT_MEMORY_CHARACTERS): String

    /**
     * The last [limit] turns, oldest first, as `transcript(limit: 24)` returned them.
     *
     * Oldest first is the order the recent block and the message list both want, and it is the
     * reverse of the order the file is scanned in — the store walks the lines backwards to keep the
     * limit without holding the whole history, then flips.
     */
    suspend fun transcript(limit: Int = DEFAULT_TRANSCRIPT_LIMIT): List<AiMessage>

    /** Appends one turn to both files, queued behind any write still in flight. */
    suspend fun recordMessage(message: AiMessage)

    /** Overwrites the transcript with [messages], which is what a restore and a clear both do. */
    suspend fun replaceTranscript(messages: List<AiMessage>)

    /** Empties both files, leaving the memory document re-seeded with its four headings. */
    suspend fun clear()

    companion object {
        /** `maxCharacters: 7000`, the chat's own default. */
        const val DEFAULT_MEMORY_CHARACTERS = 7000

        /** `limit: 24`, how many turns a restore brings back. */
        const val DEFAULT_TRANSCRIPT_LIMIT = 24
    }
}

/**
 * The store as a fresh install has it: no memory, no history.
 *
 * Deliberately not a file-backed implementation. The Dart store wrote two files under
 * `getApplicationDocumentsDirectory()` with a serialised write queue, and reproducing that in
 * `feature/aichat` would put a second copy of persistence in the wrong module: §4.6 wants these two
 * files to become Room tables, and a file store would have to be deleted rather than migrated. The
 * port is what the chat is written against, so the swap is one Hilt binding.
 *
 * Public rather than internal because `AiChatModule` names it in an `@Binds` signature, which a
 * public module cannot do with an internal type — the same reason `feature/search`'s `BlankAiSearchMemory`
 * is public.
 */
class BlankAiMemoryStore @javax.inject.Inject constructor() : AiMemoryStore {
    override suspend fun promptMemory(maxCharacters: Int): String = ""

    override suspend fun transcript(limit: Int): List<AiMessage> = emptyList()

    override suspend fun recordMessage(message: AiMessage) = Unit

    override suspend fun replaceTranscript(messages: List<AiMessage>) = Unit

    override suspend fun clear() = Unit
}
