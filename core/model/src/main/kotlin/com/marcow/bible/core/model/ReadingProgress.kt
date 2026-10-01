package com.marcow.bible.core.model

/**
 * The stored scroll position for one book, mirroring the `reading_progress` table in
 * `NATIVE_PLAN.md` §3.1.
 *
 * Flutter stores an absolute pixel offset (`legacy/flutter/lib/main.dart:321`); the native reader
 * stores a [scrollRatio] in `0.0..1.0` so the position survives different screen heights and font
 * scales. See `NATIVE_PLAN.md` risk R5 for the one-time conversion of the legacy pixel value.
 */
data class ReadingProgress(
    val bookId: String,
    val chapter: Int,
    val verse: Int?,
    val scrollRatio: Float,
    val mode: ReadingMode,
    val updatedAt: Long,
)
