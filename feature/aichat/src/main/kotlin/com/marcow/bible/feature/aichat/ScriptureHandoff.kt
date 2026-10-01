package com.marcow.bible.feature.aichat

/**
 * What the reader hands the chat when a verse action opens it, mirroring the five arguments
 * `_openAiChat` took at `legacy/flutter/lib/main.dart:969` and `_AiChatPageState` read back in
 * `initState` at `legacy/flutter/lib/ai_chat_page.dart:80`.
 *
 * The chat is the one destination the reader can arrive at already holding a passage, and it is the
 * only one that arrives with a question attached — the reader long-pressed a verse, chose 「解釋經文」
 * and the question was written for them. So the handoff is a value rather than four call arguments:
 * it can be built at the tap, carried through navigation, and applied whenever the chat is ready,
 * which is what `deliverLaunchPayload` did in Flutter when the payload outlived the page that
 * created it.
 *
 * It is declared here rather than imported from the reader because the dependency runs the other
 * way: `feature/reader` knows about the chat, the chat does not know about the reader. The reader's
 * own [com.marcow.bible.feature.reader.ScriptureRequest] carries the same four pieces of text under
 * the names `reference`, `text`, `chapterContext` and `question`, and a caller holding one maps it
 * across in a single expression:
 *
 * ```
 * ScriptureHandoff(
 *     reference = request.reference,
 *     attachment = request.text,
 *     context = request.chapterContext,
 *     question = request.question,
 *     autoSend = request.question != null,
 * )
 * ```
 *
 * [autoSend] is not read off the action itself but off whether there is a question, which is the same
 * thing: `VerseAction.carriesQuestion` is true only for Explain, and Explain was the only action
 * Flutter passed `autoSend: true` for. 「問 AI」 attached the same passage and opened an empty
 * composer.
 */
data class ScriptureHandoff(
    /** `scriptureReference`: `"Genesis 1:1"`, filed on the turn and shown as the chip's heading. */
    val reference: String? = null,
    /** `scriptureContext`: the whole chapter, which is what the prompt's authoritative block gets. */
    val context: String? = null,
    /** `scriptureAttachment`: the selected verse, which is the text the chip shows. */
    val attachment: String? = null,
    /** `initialQuestion`: the question 「解釋經文」 wrote for the reader, or null for 「問 AI」. */
    val question: String? = null,
    /** `autoSend`: whether [question] is asked on the reader's behalf the moment the chat is ready. */
    val autoSend: Boolean = false,
) {
    /**
     * `bool contextAttached`: is there a chapter for the chip and the prompt at all?
     *
     * Flutter judged this once, on `launchScriptureContext?.trim().isNotEmpty == true`, and both the
     * chip and the two arguments to `send` were then gated on it — so a handoff carrying a blank
     * context shows no chip *and* asks with no chapter, rather than showing one passage and
     * answering from another.
     */
    val contextAttached: Boolean
        get() = !context.isNullOrBlank()

    /** The question as it will be asked: trimmed, or null when there is nothing to ask. */
    val trimmedQuestion: String?
        get() = question?.trim()?.takeIf { it.isNotEmpty() }
}
