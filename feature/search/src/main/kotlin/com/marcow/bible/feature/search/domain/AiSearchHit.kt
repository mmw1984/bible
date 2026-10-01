package com.marcow.bible.feature.search.domain

import com.marcow.bible.core.model.ScriptureHit

/**
 * A reference the model proposed, resolved to the verses it covers, with the reason it gave.
 *
 * The Dart build carried this as the record `(ScriptureHit, String)`; the same pairing survives as
 * a named pair because [reason] is rendered inside the tile and unnamed positional access in a
 * `LazyColumn` item is exactly the kind of thing that gets swapped by accident.
 */
data class AiSearchHit(val hit: ScriptureHit, val reason: String)

/**
 * Why the AI references never arrived, mirroring `_ReferenceFailure` in
 * `legacy/flutter/lib/main.dart`.
 *
 * The two cases are separate because they have separate causes and separate copy: [REQUEST] is the
 * model call or its JSON that failed, [VERSES] is the local lookup of a reference that did arrive
 * failing, which points at the device's database rather than at the network.
 */
enum class ReferenceFailure {
    /** `onReferencesError`: the request, or the parse of what it returned, failed. */
    REQUEST,

    /** The `.catchError` after `_resolveReferences`: the chapter lookup failed. */
    VERSES,
}
