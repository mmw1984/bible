package com.marcow.bible.feature.aichat.domain

/**
 * The `memory.md` document of `legacy/flutter/lib/ai_memory_store_io.dart:12`, as the two pure
 * functions that produce and bound it.
 *
 * `AiMemoryStore` owns *where* the memory is kept and this owns what it says. That split is the point:
 * §4.6 replaces the file with two Room tables, and when it does the truncation rule and the entry
 * format are still exactly these, because the block they produce is prompt text the model reads and
 * has to keep being the same. A store that formatted the entries itself would take the prompt's memory
 * block with it.
 */

/**
 * The document `initialize()` wrote when the file did not exist: a title and four empty sections.
 *
 * The sections are not decoration — [AiMessageKind.memoryHeading] files each turn under one of them,
 * and a document that started without them would put a turn's heading at the top of the file with no
 * section to sit under. The seeded preference line is the Flutter default that shipped in every
 * install's `memory.md`, and it is kept verbatim so a migrated memory file reads the same.
 */
internal const val MEMORY_DOCUMENT_SEED = "# Bible AI Memory\n\n" +
    "## User preferences\n" +
    "- Scripture reference language: Chinese and English\n\n" +
    "## Important events and conversation\n\n" +
    "## Explained scripture\n\n" +
    "## Search history and conclusions\n\n"

/**
 * `promptMemory(maxCharacters:)`: the document, or its tail with a different title when it is too
 * long for the prompt.
 *
 * The title changes rather than being kept, and that is the whole behaviour: the tail of an
 * append-only document is what is about the conversation in hand, and a prompt that says 「Bible AI
 * Memory」 over a fragment of it would be claiming a completeness the text does not have. The tail
 * itself is the *last* [maxCharacters] characters, not the first — the head is the oldest thing the
 * user ever asked, which is the least useful part to spend a context window on.
 */
internal fun memoryBlock(content: String, maxCharacters: Int): String =
    if (content.length <= maxCharacters) {
        content
    } else {
        TRUNCATED_MEMORY_TITLE + "\n\n" + content.substring(content.length - maxCharacters)
    }

/**
 * The `###` entry `recordMessage` appended for one turn, given the ISO-8601 timestamp it was filed
 * under.
 *
 * Multi-line content is indented by two spaces so it stays inside its bullet: `memory.md` is a
 * document the model is shown, and an unindented continuation line would render as a new paragraph
 * that looks like a separate thought. The scripture reference is a second bullet rather than part of
 * the role line, and is omitted entirely when the turn had no passage attached, which is what the
 * `?scripture` in the entry was for.
 */
internal fun memoryEntry(message: AiMessage, timestamp: String): String = buildString {
    append("\n### ").append(timestamp).append(" - ").append(message.kind.memoryHeading).append("\n")
    append("- Role: ").append(message.role.promptPrefix)
    message.scripture?.let { append("\n- Scripture: ").append(it) }
    append("\n- Content: ").append(message.text.replace("\n", "\n  ")).append("\n")
}

/**
 * `# Bible AI Memory (latest entries)`, the title of a truncated memory block.
 *
 * Distinct from [MEMORY_DOCUMENT_SEED]'s title on purpose: it is the model's cue that what follows is
 * a tail, so a memory that has grown past the ceiling is not read as the whole of it.
 */
private const val TRUNCATED_MEMORY_TITLE = "# Bible AI Memory (latest entries)"
