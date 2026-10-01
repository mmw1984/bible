package com.marcow.bible.core.network.openrouter

/**
 * The ways an OpenRouter call can fail, kept apart because the sheet answers each one differently.
 *
 * A login failure is not a search failure: it is the difference between "try again" and "sign in",
 * and the Flutter build drew those as two different panels (`login_to_search` next to
 * `references_failed`). Dart signalled the first case with a bare `StateError` whose message was
 * the sentinel `OPENROUTER_LOGIN_REQUIRED`; here it is a type, so nothing has to string-match it.
 */
sealed class OpenRouterException(message: String) : Exception(message) {
    /**
     * No key, or the provider rejected the one it had — [OpenRouterSession.signOut] has already run
     * in the second case.
     *
     * The message is the Flutter sentinel verbatim, because it is also what an unreadable error
     * string from a failed request would read like in the logs.
     */
    class LoginRequired : OpenRouterException(LOGIN_REQUIRED_MESSAGE)

    /** A non-2xx answer: `OpenRouter request failed (<status>): <message>`. */
    class RequestFailed(message: String) : OpenRouterException(message)

    /** A 2xx answer with no content, the `OpenRouter returned no response.` of `generateStream`. */
    class EmptyResponse : OpenRouterException(EMPTY_RESPONSE_MESSAGE)
}

/** `StateError('OPENROUTER_LOGIN_REQUIRED')`, the sentinel `_send` threw. */
internal const val LOGIN_REQUIRED_MESSAGE = "OPENROUTER_LOGIN_REQUIRED"

/** `StateError('OpenRouter returned no response.')`. */
internal const val EMPTY_RESPONSE_MESSAGE = "OpenRouter returned no response."

/** The last line of `_errorMessage`, for a body that is neither an object nor a string. */
internal const val UNKNOWN_ERROR_MESSAGE = "Unknown OpenRouter error."
