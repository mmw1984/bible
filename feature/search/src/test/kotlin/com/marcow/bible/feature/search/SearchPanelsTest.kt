package com.marcow.bible.feature.search

import com.marcow.bible.core.designsystem.R
import com.marcow.bible.feature.search.domain.ReferenceFailure
import com.marcow.bible.feature.search.ui.referencesFailureString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Which of the two `ai_scripture_results` failure messages each reference failure is drawn with.
 *
 * [com.marcow.bible.feature.search.SearchRowsTest] already pins that the two failures are drawn as
 * two different rows. This pins what makes them worth being two: the copy each one reaches for.
 * Swapping the pair compiles, passes every row test, and sends the user to check the wrong thing —
 * `ReferenceFailure.VERSES` is this device's own Bible database failing to resolve a reference the
 * model got right, so telling them the *AI* search could not be completed points them at a network
 * that was never involved.
 */
class SearchPanelsTest {
    @Test
    fun `a reference the local Bible could not answer is a failure to load them`() {
        // "Scripture results could not be loaded.", not "AI scripture search could not be completed."
        assertEquals(R.string.verse_results_failed, referencesFailureString(ReferenceFailure.VERSES))
    }

    @Test
    fun `a request that was never answered is a failure of the AI search`() {
        // The other way round, so this is the half a swap would break.
        assertEquals(R.string.references_failed, referencesFailureString(ReferenceFailure.REQUEST))
    }

    @Test
    fun `the two failures never share a message`() {
        // The reason the panel takes an enum at all: with one message, a failure of the local
        // database would be indistinguishable from a failure of the network.
        assertNotEquals(
            referencesFailureString(ReferenceFailure.VERSES),
            referencesFailureString(ReferenceFailure.REQUEST),
        )
    }

    @Test
    fun `every reference failure has a message of its own`() {
        // Exhaustiveness is the compiler's job, but a value resolving to no resource at all is the
        // one outcome the `when` itself cannot rule out, and it would draw an empty panel.
        ReferenceFailure.entries.forEach { failure ->
            assertNotEquals(0, referencesFailureString(failure), "$failure has no message")
        }
    }
}
