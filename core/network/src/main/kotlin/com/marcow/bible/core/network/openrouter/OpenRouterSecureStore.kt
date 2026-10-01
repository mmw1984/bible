package com.marcow.bible.core.network.openrouter

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the four OpenRouter OAuth secrets live, replacing `FlutterSecureStorage`.
 *
 * This is a port for the same reason [OpenRouterSession] is one: `OpenRouterAuthManager` has to be
 * testable on the JVM, and neither `EncryptedSharedPreferences` nor `FlutterSecureStorage` can be
 * built there. The four key names are the ones `NATIVE_PLAN.md` §4.7 lists and
 * `legacy/flutter/lib/openrouter_service.dart:29`–`32` used, kept identical so the parity notes and
 * the migration story read the same.
 */
interface OpenRouterSecureStore {
    /** `_storage.read(key: …)`, so a missing or empty value is null. */
    suspend fun read(key: String): String?

    /** `_storage.write(key: …, value: …)`. */
    suspend fun write(key: String, value: String)

    /** `_storage.delete(key: …)`. */
    suspend fun delete(key: String)
}

/** The key `OpenRouterAuth` read for the API key the requests authenticate with. */
const val OPENROUTER_API_KEY = "openrouter_api_key"

/** The key holding the PKCE verifier until the code comes back. */
const val OPENROUTER_PKCE_VERIFIER = "openrouter_pkce_verifier"

/**
 * The key holding the method the challenge was derived with, sent back on the exchange.
 *
 * Named `_KEY` rather than just `OPENROUTER_PKCE_METHOD` because that name is already the method
 * *value* — `openRouterPkceMethod` of `legacy/flutter/lib/openrouter_oauth.dart`, `S256`. The two
 * live in the same package, and `OpenRouterPkce.kt` owns the value while this file owns the key it
 * is filed under, exactly as `_pkceMethodName` held the key in Dart while the constant held the value.
 */
const val OPENROUTER_PKCE_METHOD_KEY = "openrouter_pkce_method"

/** The key an authorization code is parked under so a killed app can still finish signing in. */
const val OPENROUTER_PENDING_CODE = "openrouter_pending_code"

/**
 * The store on `EncryptedSharedPreferences`, which is what `flutter_secure_storage` used on Android
 * too, so the Flutter file is readable with the same configuration — see
 * [EncryptedFlutterSecureStorageReader] for the attempt and [MigratingOpenRouterSecureStore] for the
 * carry-over. R1 accepts the attempt failing, and then the user signs in once more.
 *
 * The file is named after this app rather than after the Flutter package: sharing a name would put
 * entries written by a different encryptor configuration in the same preference file, and
 * `EncryptedSharedPreferences` fails to open such a file rather than reading what it can.
 */
@Singleton
class EncryptedOpenRouterSecureStore @Inject constructor(@ApplicationContext context: Context) :
    OpenRouterSecureStore {
    private val preferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFERENCES_FILE,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override suspend fun read(key: String): String? = preferences.getString(key, null)?.takeIf { it.isNotEmpty() }

    override suspend fun write(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override suspend fun delete(key: String) {
        preferences.edit().remove(key).apply()
    }

    private companion object {
        const val PREFERENCES_FILE = "openrouter_secure"
    }
}
