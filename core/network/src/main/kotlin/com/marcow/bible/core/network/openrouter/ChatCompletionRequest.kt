package com.marcow.bible.core.network.openrouter

import kotlinx.serialization.json.JsonObject

/**
 * One OpenRouter chat completion, ready to go on the wire.
 *
 * [body] is the finished request object rather than a serializable data class because
 * `openrouter_service.dart` assembled the body field by field and the exact field set differs per
 * call path — a search overview sends a different `reasoning` and no `response_format`, while the
 * chat path adds `tools` and streams. Building it by hand is what keeps those differences visible
 * instead of hidden behind nullable fields that are silently dropped.
 */
data class ChatCompletionRequest(val model: String, val body: JsonObject)
