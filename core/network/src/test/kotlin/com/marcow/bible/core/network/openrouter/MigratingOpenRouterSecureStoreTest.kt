package com.marcow.bible.core.network.openrouter

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Phase 4 item 11 and R1's first mitigation: read `FlutterSecureStorage.xml` for the API key so an
 * existing user is not made to press sign in again.
 *
 * There is no Dart test for this, because the Dart has no such migration — it *was* the writer of that
 * file. What is pinned here is the behaviour R1's decision depends on, which is that the fallback is
 * the app's existing signed-out state:
 *
 *  - a key that is in the new store is never looked up in the old file, so a sign-out cannot be undone
 *    by a migration still standing behind it,
 *  - a readable Flutter key is written into the new store, not just returned, so it survives the
 *    process being killed before anything else reads it again,
 *  - an unreadable Flutter file is indistinguishable from a fresh install: no key, no exception, no
 *    leftover state,
 *  - only the API key is carried over, because the other three are a half-finished PKCE exchange.
 */
class MigratingOpenRouterSecureStoreTest {
    @Test
    fun `the flutter api key is carried over and written into the new store`() = runTest {
        val legacy = LegacyStore("sk-legacy")
        val store = migrating(legacy)

        assertEquals("sk-legacy", store.migrating.read(OPENROUTER_API_KEY))

        // Written, not merely returned: the next run reads the new file and finds the key there.
        assertEquals("sk-legacy", store.entries[OPENROUTER_API_KEY])
        assertEquals(listOf(OPENROUTER_API_KEY), legacy.reads)
    }

    @Test
    fun `a key already in the new store wins and the flutter file is left alone`() = runTest {
        val legacy = LegacyStore("sk-legacy")
        val store = migrating(legacy)
        store.entries[OPENROUTER_API_KEY] = "sk-native"

        assertEquals("sk-native", store.migrating.read(OPENROUTER_API_KEY))
        assertEquals(emptyList<String>(), legacy.reads)
    }

    @Test
    fun `signing out does not hand the flutter key back`() = runTest {
        val store = migrating(LegacyStore(null))
        store.migrating.write(OPENROUTER_API_KEY, "sk-signed-in")

        store.migrating.delete(OPENROUTER_API_KEY)

        // The read that follows a sign-out is the one that decides whether signing out works at all.
        // Were the migration still armed, the slot would be empty again and the old file would restore
        // the key, so the user would be signed straight back in.
        assertNull(store.migrating.read(OPENROUTER_API_KEY))
        assertNull(store.entries[OPENROUTER_API_KEY])
    }

    @Test
    fun `an unreadable flutter file reads as no key`() = runTest {
        // The reader collapses "no file", "no such key" and "cannot decrypt" into null, so a fresh
        // install and a device whose Tink keyset does not match take the same path.
        val store = migrating(LegacyStore(null))

        assertNull(store.migrating.read(OPENROUTER_API_KEY))
    }

    @Test
    fun `the flutter file is read once per process`() = runTest {
        val legacy = LegacyStore(null)
        val store = migrating(legacy)

        assertNull(store.migrating.read(OPENROUTER_API_KEY))
        assertNull(store.migrating.read(OPENROUTER_API_KEY))

        // The file cannot change under us, so a second attempt would only repeat a failure the user
        // can do nothing about.
        assertEquals(1, legacy.reads.size)
    }

    @Test
    fun `only the api key is migrated`() = runTest {
        val legacy = LegacyStore("sk-legacy")
        val store = migrating(legacy)

        // A verifier from a previous install cannot complete an exchange this one starts, and a parked
        // code has expired, so all three are left to be written again by a fresh sign-in.
        assertNull(store.migrating.read(OPENROUTER_PKCE_VERIFIER))
        assertNull(store.migrating.read(OPENROUTER_PKCE_METHOD_KEY))
        assertNull(store.migrating.read(OPENROUTER_PENDING_CODE))
        assertEquals(emptyList<String>(), legacy.reads)
    }

    @Test
    fun `writes and deletes reach the new store`() = runTest {
        val store = migrating(LegacyStore(null))
        store.migrating.write(OPENROUTER_API_KEY, "sk-signed-in")
        assertEquals("sk-signed-in", store.entries[OPENROUTER_API_KEY])

        store.migrating.write(OPENROUTER_PKCE_VERIFIER, "verifier")
        assertEquals("verifier", store.entries[OPENROUTER_PKCE_VERIFIER])

        store.migrating.delete(OPENROUTER_PKCE_VERIFIER)
        assertNull(store.entries[OPENROUTER_PKCE_VERIFIER])
    }

    /**
     * The store under test plus the map behind it, so a test can plant a native key and then see
     * whether the carry-over wrote one.
     */
    private class Fixture(private val legacy: FlutterSecureStorageReader) {
        val entries = mutableMapOf<String, String>()
        val migrating: OpenRouterSecureStore = MigratingOpenRouterSecureStore(InMemoryStore(entries), legacy)
    }

    private fun migrating(legacy: LegacyStore): Fixture = Fixture(legacy)
}

/** [FlutterSecureStorageReader] answering one fixed value, recording what it was asked for. */
private class LegacyStore(private val value: String?) : FlutterSecureStorageReader {
    val reads = mutableListOf<String>()

    override suspend fun read(key: String): String? {
        reads += key
        return value
    }
}

/** A plain map standing in for `EncryptedSharedPreferences`. */
private class InMemoryStore(private val entries: MutableMap<String, String>) : OpenRouterSecureStore {
    override suspend fun read(key: String): String? = entries[key]

    override suspend fun write(key: String, value: String) {
        entries[key] = value
    }

    override suspend fun delete(key: String) {
        entries.remove(key)
    }
}
