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
 * own `ScriptureRequest` carries the same four pieces of text under the names `reference`, `text`,
 * `chapterContext` and `question`, and [of] takes those four names, so a caller holding one writes
 *
 * ```
 * ScriptureHandoff.of(
 *     reference = request.reference,
 *     text = request.text,
 *     chapterContext = request.chapterContext,
 *     question = request.question,
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

    companion object {
        /**
         * The handoff for a request the reader made, mapping the reader's names onto the chat's.
         *
         * The parameters are named after the reader's `ScriptureRequest` rather than after the fields
         * they land on, so a caller's four arguments read as the four Flutter passed at
         * `legacy/flutter/lib/main.dart:969` — the reference, the selected verse, the chapter, and the
         * question Explain wrote — and the renaming happens once here instead of at every call site.
         * The pieces themselves are passed through byte for byte: `text` becomes [attachment] and
         * `chapterContext` becomes [context], which are the names `ai_chat_page.dart` had, and no
         * trimming or blanking happens here because Flutter trimmed nothing either — [contextAttached]
         * is what judged the chapter.
         *
         * [autoSend] is derived rather than taken, because a caller passing it could pass the one
         * pairing the reader never produced: a question with `autoSend = false` would be attached to
         * the turn and never asked, and 「問 AI」 must open a composer rather than send.
         */
        fun of(reference: String, text: String, chapterContext: String, question: String? = null): ScriptureHandoff =
            ScriptureHandoff(
                reference = reference,
                context = chapterContext,
                attachment = text,
                question = question,
                autoSend = question != null,
            )
    }
}
