package com.marcow.bible.core.network.devotion

/**
 * A tier of the devotion fetch did not produce anything usable.
 *
 * `fetchDevotionPosts` in `legacy/flutter/lib/devotion_content.dart` signalled every one of these
 * with `http.ClientException` and then moved on to the next tier, so the message is a diagnosis for
 * the log rather than something the reader is shown: the UI reports one generic "could not be
 * loaded" and keeps the detail under it, which is what the Flutter page did with `errorDetail`.
 */
class DevotionFetchException(message: String, cause: Throwable? = null) : Exception(message, cause)
