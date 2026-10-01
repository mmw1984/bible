package com.marcow.bible.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.marcow.bible.core.datastore.proto.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * A [DataStore] that keeps the message in memory.
 *
 * Proto DataStore's real implementation owns a file and a background actor, neither of which
 * exists in a JVM unit test. This stands in for it so [SettingsRepository] can be tested
 * directly; [roundTrip] goes through the same [Serializer] the file-backed store uses, so a test
 * can still prove the message survives real serialization.
 *
 * It is a `testFixtures` dependency rather than test-only code because the feature modules own the
 * screens that write settings, and their tests need a store to write them into.
 */
class InMemorySettingsDataStore(initial: Settings = Settings.getDefaultInstance()) : DataStore<Settings> {
    private val state = MutableStateFlow(initial)

    private val serializer = object : Serializer<Settings> {
        override val defaultValue: Settings = Settings.getDefaultInstance()

        override suspend fun readFrom(input: InputStream): Settings = Settings.parseFrom(input)

        override suspend fun writeTo(t: Settings, output: OutputStream) = t.writeTo(output)
    }

    override val data: Flow<Settings> = state

    override suspend fun updateData(transform: suspend (t: Settings) -> Settings): Settings {
        val next = transform(state.value)
        state.value = next
        return next
    }

    /** Serializes and parses the current message back, proving the wire format round-trips. */
    fun roundTrip(): Settings = runBlocking {
        val bytes = ByteArrayOutputStream()
        serializer.writeTo(state.value, bytes)
        serializer.readFrom(ByteArrayInputStream(bytes.toByteArray()))
    }
}
