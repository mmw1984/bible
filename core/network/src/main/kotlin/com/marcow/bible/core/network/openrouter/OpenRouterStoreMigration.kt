package com.marcow.bible.core.network.openrouter

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An attempt to read one key out of the file `flutter_secure_storage` left behind, which
 * `NATIVE_PLAN.md` §6 R1 asks for and describes as deliberately best-effort.
 *
 * R1's finding is that the file is an `EncryptedSharedPreferences` whose entries are encrypted with
 * Tink, and that reading it couples to that package's own keyset and encryptor configuration. So this
 * is a port with a nullable answer rather than a migration: [read] returns the value, or null, and
 * null is a single outcome covering "there is no such file", "the file has no such key" and "the file
 * exists and could not be decrypted". A caller cannot tell those apart and does not need to — every
 * one of them means the same thing, which is that whoever used this install has to sign in again.
 *
 * It is a port because [MigratingOpenRouterSecureStoreTest] has to run on the JVM, where neither
 * `EncryptedSharedPreferences` nor a Tink keyset exists. The same reason [OpenRouterSecureStore] is
 * one.
 */
interface FlutterSecureStorageReader {
    /** The value Flutter stored under [key], or null when it cannot be read. */
    suspend fun read(key: String): String?
}

/**
 * Reads `FlutterSecureStorage.xml` with the same `EncryptedSharedPreferences` configuration the Flutter
 * build used, and swallows every failure.
 *
 * The configuration is the bet R1 describes. `flutter_secure_storage` on Android writes an
 * `EncryptedSharedPreferences` under the file name `FlutterSecureStorage`, with an AES256-SIV key
 * scheme, an AES256-GCM value scheme and a master key under the platform default alias — so opening
 * that file the same way is the whole of the attempt, and there is no format to write a parser for.
 * Where the bet fails it fails by throwing, and that is caught rather than reported: an unreadable
 * file is the documented acceptable outcome, not an error the user can act on.
 *
 * The master key is looked up by the same alias the Flutter build used, which is what makes the
 * attempt possible at all — a keyset under a *different* alias would be unreadable no matter how the
 * file was opened, and building a new one under this alias cannot decrypt what the old one encrypted.
 */
@Singleton
class EncryptedFlutterSecureStorageReader @Inject constructor(@ApplicationContext private val context: Context) :
    FlutterSecureStorageReader {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        try {
            preferences()?.getString(key, null)?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            // A keyset that is not there, a value encrypted with another one, or a file this version
            // cannot open at all: all of them are "no key", which is what the caller does about.
            null
        }
    }

    /**
     * The Flutter file, or null when this install never had one.
     *
     * The emptiness check is on the raw preferences rather than on a decrypted value, so a fresh
     * install does not build a master key or a Tink keyset at all. Only a device that really did run
     * the Flutter build pays for the decrypt attempt, and only once per process.
     */
    private fun preferences(): SharedPreferences? =
        context.getSharedPreferences(FLUTTER_PREFERENCES_FILE, Context.MODE_PRIVATE)
            .takeIf { it.all.isNotEmpty() }
            ?.let { open() }

    private fun open(): SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FLUTTER_PREFERENCES_FILE,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private companion object {
        /** `FlutterSecureStorage.xml`: the file name `flutter_secure_storage` opens on Android. */
        const val FLUTTER_PREFERENCES_FILE = "FlutterSecureStorage"
    }
}

/**
 * [EncryptedOpenRouterSecureStore] with the Flutter file's key carried over once, which is Phase 4
 * item 11 and the first of R1's three mitigations: implement the reader, and fall back to asking for a
 * new sign-in when it does not work.
 *
 * Only [OPENROUTER_API_KEY] is carried over. The other three are the PKCE verifier, its method and a
 * parked authorization code, and all three are worthless without the key they were half of — a
 * verifier from a previous install cannot complete an exchange this one starts, and a code parked
 * before the upgrade has already expired. Migrating them would be three writes into the new store for
 * entries nothing reads.
 *
 * The attempt happens on the *read* of the key rather than at construction, which is what puts it
 * before [OpenRouterAuthManager.initialize]'s own first read without either class knowing about the
 * other. It is tried once per process, and finding a key in this store counts as having tried: the
 * file does not change under us, and a second attempt would only repeat a failure the user cannot do
 * anything about.
 *
 * A failed attempt is silent, and that is the decision rather than an omission. R1 accepts that
 * "the user presses sign in again" as the worst case, and the app is already in exactly that state —
 * [OpenRouterSession.signedIn] is false and the settings row offers the button. A message would have
 * to be raised on every launch for every install that never used the Flutter build, including fresh
 * installs where there is no file at all and nothing went wrong. Whether to say something is a
 * question for the settings screen, which is where a "signed out" reason would be shown and which this
 * pass does not touch.
 *
 * Built by [OpenRouterModule] rather than by `@Inject`, because its [store] parameter is
 * [OpenRouterSecureStore] — the same type this class is bound *as*. Injecting it would ask the graph
 * for the thing it is in the middle of constructing.
 */
class MigratingOpenRouterSecureStore(
    private val store: OpenRouterSecureStore,
    private val legacy: FlutterSecureStorageReader,
) : OpenRouterSecureStore {
    private val migrationLock = Mutex()
    private var attempted = false

    /**
     * The [OPENROUTER_API_KEY] read, which is the only one the Flutter file is ever consulted for.
     *
     * A key already in this store ends the question as much as an empty slot does, and it has to: a
     * sign-out deletes the key and the next read would otherwise find the slot empty and hand the
     * Flutter key straight back, leaving [OpenRouterAuthManager.signOut] unable to sign anybody out.
     */
    override suspend fun read(key: String): String? {
        if (key != OPENROUTER_API_KEY) return store.read(key)
        return migrationLock.withLock { readApiKey(key) }
    }

    override suspend fun write(key: String, value: String) = store.write(key, value)

    override suspend fun delete(key: String) = store.delete(key)

    private suspend fun readApiKey(key: String): String? {
        val stored = store.read(key)
        if (stored != null) {
            attempted = true
            return stored
        }
        if (attempted) return null
        attempted = true
        val carried = legacy.read(key) ?: return null
        // Written before it is returned, so a read that succeeded and an app that was killed straight
        // afterwards still leaves the key where the next run will find it.
        store.write(key, carried)
        return carried
    }
}
