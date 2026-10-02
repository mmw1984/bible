package com.marcow.bible.feature.aichat.domain

/**
 * The `recent` block of `_chatPrompt` in `legacy/flutter/lib/ai_service.dart:1066` — the last dozen
 * turns as `role: text` lines, which is how the model is told what was already said.
 *
 * This is the whole of the conversation history the model sees, and it is a block of *text* rather
 * than a message array because that is what the prompt is: `chatPrompt` interpolates it whole under
 * the `RECENT CONVERSATION` heading with no other structure, and the model reads a transcript far
 * better than it follows a role-tagged array. It is also why the roles are spelled out as prefixes
 * here rather than inferred from position — the store restores a transcript whose last turn may be
 * either speaker.
 */
internal fun recentConversationBlock(messages: List<AiMessage>, question: String): String {
    // The turn being answered is already in the list by the time the prompt is built — `send` appends
    // the question before calling `_answerExistingMessage` — and the prompt states it separately at
    // the end. Counting it here would repeat the question verbatim and push a real earlier turn out
    // of the twelve, so a repeat of the exact trailing question is dropped and nothing else is.
    val historyLength = if (
        messages.isNotEmpty() &&
        messages.last().role == AiMessageRole.USER &&
        messages.last().text == question
    ) {
        messages.size - 1
    } else {
        messages.size
    }
    // `take(historyLength).skip(historyLength > historyLimit ? historyLength - historyLimit : 0)`:
    // the window is the *last* [RECENT_TURN_LIMIT] turns, so a long conversation keeps what is
    // relevant to it and not how the conversation started.
    val window = messages
        .take(historyLength)
        .drop((historyLength - RECENT_TURN_LIMIT).coerceAtLeast(0))
    return window.joinToString("\n") { message ->
        "${message.role.promptPrefix}: ${limitText(message.text, RECENT_TEXT_LIMIT)}"
    }
}

/** `const historyLimit = 12`: how many turns of context the prompt carries. */
private const val RECENT_TURN_LIMIT = 12

/**
 * `_limitText(message.text, 4000)`, the per-turn ceiling inside the block.
 *
 * Half of the scripture context's 24000, applied per turn so one very long answer cannot spend the
 * whole context: twelve turns of 4000 is the budget the block was sized against, and the truncation
 * marker tells the model the text is a head rather than the whole turn.
 */
private const val RECENT_TEXT_LIMIT = 4000
