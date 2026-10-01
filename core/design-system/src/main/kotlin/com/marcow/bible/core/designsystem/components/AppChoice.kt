package com.marcow.bible.core.designsystem.components

import com.marcow.bible.core.designsystem.icons.AppGlyph

/** One option in an [AppSegmented] control, mirroring `AppChoice`. */
data class AppChoice<T>(val value: T, val label: String, val glyph: AppGlyph? = null)
