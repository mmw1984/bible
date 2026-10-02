package com.marcow.bible.core.network.openrouter

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * The RFC 7636 pieces of `OpenRouterAuth.beginSignIn`, kept free of Android so they can be pinned by
 * JVM tests the way `legacy/flutter/test/openrouter_oauth_test.dart` pinned the Dart ones.
 *
 * `NATIVE_PLAN.md` §4.7 spells the method as `plain`; the Flutter build this ports uses `S256`
 * (`openRouterPkceMethod = 'S256'` at `legacy/flutter/lib/openrouter_oauth.dart:5`, sent back on the
 * exchange at `legacy/flutter/lib/openrouter_service.dart:182`). The Flutter behaviour wins here: a
 * challenge derived one way and exchanged with the other is what makes a `plain` sign-in fail
 * against a provider that only honours `S256`.
 */

/** `'S256'`, `openRouterPkceMethod` of `legacy/flutter/lib/openrouter_oauth.dart`. */
const val OPENROUTER_PKCE_METHOD = "S256"

/** `OpenRouterAuth.callback`, the URI OpenRouter redirects back to. */
const val OPENROUTER_CALLBACK_URI = "bible://openrouter/callback"

/**
 * `createOpenRouterPkceChallenge(verifier)`: base64url of the verifier's SHA-256, unpadded.
 *
 * Dart's `base64Url.encode(sha256.convert(utf8.encode(verifier)).bytes).replaceAll('=', '')` and Java's
 * url encoder agree here, because a SHA-256 digest is 32 bytes and 32 leaves a base64 encoding whose
 * padding Dart removed and Java never added.
 */
fun openRouterPkceChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance(SHA_256).digest(verifier.toByteArray(Charsets.UTF_8))
    return base64Url(digest)
}

/**
 * `_randomVerifier()`: 64 bytes from [random], base64url, unpadded — an 86 character verifier.
 *
 * [random] is a parameter so a test can hand in a fixed source; the Flutter test did not need one
 * because it pinned the challenge of a known verifier rather than the randomness itself.
 */
fun openRouterPkceVerifier(random: SecureRandom = SecureRandom()): String {
    val bytes = ByteArray(VERIFIER_BYTES)
    random.nextBytes(bytes)
    return base64Url(bytes)
}

/**
 * `Uri.https('openrouter.ai', '/auth', {…})` of `beginSignIn`.
 *
 * The parameters keep the order Dart's map had, so this reads against `openrouter_service.dart:96`
 * the way the Flutter source did.
 */
fun openRouterAuthorizeUri(challenge: String, callbackUrl: String = OPENROUTER_CALLBACK_URI): String =
    "$OPENROUTER_AUTHORIZE_ORIGIN/auth" +
        "?callback_url=${percentEncode(callbackUrl)}" +
        "&code_challenge=${percentEncode(challenge)}" +
        "&code_challenge_method=$OPENROUTER_PKCE_METHOD"

/**
 * `isOpenRouterCallback(uri, isWeb: false, …)`: is this the callback OpenRouter redirects to?
 *
 * The Dart classifier also accepted a page on the app's own web origin, which is how the `flutter web`
 * build received the same sign-in. An Android install is handed `bible://openrouter/callback` and
 * nothing else, so only the shapes it can actually receive are recognised here.
 */
fun isOpenRouterCallback(uri: String): Boolean {
    val parsed = OpenRouterCallbackUri.parse(uri)
    return parsed != null && (parsed.isAppLink || parsed.isLoopback)
}

/**
 * A parsed callback URI: the scheme, host and path OpenRouter redirects to, plus its query.
 *
 * `android.net.Uri` parses this too, but only on a device, and the query has to be read the way Dart's
 * `queryParameters` read it — `+` is a space, a missing `=` is an empty value — or a code that
 * arrived percent-encoded would not match the one that was sent.
 */
data class OpenRouterCallbackUri(
    val scheme: String,
    val host: String,
    val path: String,
    val query: Map<String, String>,
) {
    /** `uri.scheme == 'bible' && uri.host == 'openrouter' && uri.path == '/callback'`. */
    val isAppLink: Boolean
        get() = scheme == "bible" && host == "openrouter" && path.equals("/callback", ignoreCase = true)

    /** The loopback leg of the same sign-in, which a desktop or `flutter run` build receives. */
    val isLoopback: Boolean
        get() = scheme == "http" && (host == "localhost" || host == "127.0.0.1") &&
            path.equals("/callback", ignoreCase = true)

    /** `uri.queryParameters['error_description'] ?? uri.queryParameters['error']`, empties dropped. */
    val error: String?
        get() = query["error_description"]?.takeIf { it.isNotEmpty() }
            ?: query["error"]?.takeIf { it.isNotEmpty() }

    /** `uri.queryParameters['code']`, which `_handleLink` rejected when null or empty. */
    val code: String?
        get() = query["code"]?.takeIf { it.isNotEmpty() }

    companion object {
        /** Parses [uri], or null when it is not a `scheme://host/path` URI at all. */
        fun parse(uri: String): OpenRouterCallbackUri? {
            val schemeEnd = uri.indexOf(SCHEME_SEPARATOR)
            if (schemeEnd <= 0) return null
            val rest = uri.substring(schemeEnd + SCHEME_SEPARATOR.length)
            val pathStart = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
            if (pathStart < 0) return null
            val host = rest.substring(0, pathStart).substringAfter('@').substringBefore(':').lowercase()
            if (host.isEmpty()) return null
            val remainder = rest.substring(pathStart)
            val path = remainder.substringBefore('?').substringBefore('#')
            val rawQuery = remainder.substringAfter('?', missingDelimiterValue = "")
            return OpenRouterCallbackUri(
                scheme = uri.substring(0, schemeEnd).lowercase(),
                host = host,
                path = path,
                query = parseQuery(rawQuery.substringBefore('#')),
            )
        }

        /** `Uri.splitQueryString`, including its rule that `+` decodes to a space. */
        private fun parseQuery(raw: String): Map<String, String> {
            if (raw.isEmpty()) return emptyMap()
            return raw.split('&').mapNotNull { pair ->
                if (pair.isEmpty()) return@mapNotNull null
                val name = pair.substringBefore('=')
                percentDecode(name) to percentDecode(pair.substringAfter('=', missingDelimiterValue = ""))
            }.toMap()
        }
    }
}

/** `Uri.https('openrouter.ai', …)`'s origin. */
private const val OPENROUTER_AUTHORIZE_ORIGIN = "https://openrouter.ai"

private const val SHA_256 = "SHA-256"

private const val SCHEME_SEPARATOR = "://"

private const val VERIFIER_BYTES = 64

/** `Base64.urlEncoder.withoutPadding()`, which is Dart's `base64Url.encode(…).replaceAll('=', '')`. */
private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

/** Dart's `_uriEncode`: every byte that is not unreserved becomes `%XX`. */
private fun percentEncode(value: String): String {
    val encoded = StringBuilder()
    value.toByteArray(Charsets.UTF_8).forEach { byte ->
        val code = byte.toInt() and 0xFF
        if (isUnreserved(code)) {
            encoded.append(code.toChar())
        } else {
            encoded.append('%').append(HEX[code shr 4]).append(HEX[code and 0x0F])
        }
    }
    return encoded.toString()
}

private fun percentDecode(value: String): String {
    val bytes = ArrayList<Byte>(value.length)
    var index = 0
    while (index < value.length) {
        val char = value[index]
        val escape = char == '%' && index + 2 < value.length
        val hex = if (escape) value.substring(index + 1, index + 3).toIntOrNull(16) else null
        if (hex != null) {
            bytes.add(hex.toByte())
            index += 3
        } else if (char == '+') {
            bytes.add(' '.code.toByte())
            index++
        } else {
            bytes.addAll(char.toString().toByteArray(Charsets.UTF_8).asList())
            index++
        }
    }
    return String(bytes.toByteArray(), Charsets.UTF_8)
}

private fun isUnreserved(code: Int): Boolean = code in 'a'.code..'z'.code ||
    code in 'A'.code..'Z'.code ||
    code in '0'.code..'9'.code ||
    code == '-'.code ||
    code == '.'.code ||
    code == '_'.code ||
    code == '~'.code

private const val HEX = "0123456789ABCDEF"
