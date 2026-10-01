package com.marcow.bible.feature.search

/**
 * Which of the two searches the sheet runs, mirroring `_SearchMode` in `legacy/flutter/lib/main.dart`.
 *
 * The order of the constants is the order of the two segments, because the mode control is built
 * from [com.marcow.bible.core.designsystem.components.AppChoice]s in this order.
 */
enum class SearchMode {
    /** `BibleRepository.search`: a case-insensitive `LIKE` over the whole Bible. */
    TRADITIONAL,

    /** `BibleAiController.search`: the overview and the references, in parallel. */
    AI,
}
