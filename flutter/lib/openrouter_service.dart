import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:app_links/app_links.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:url_launcher/url_launcher.dart';

import 'oauth_callback.dart';
import 'oauth_cleanup.dart';

class OpenRouterAuth {
  OpenRouterAuth({
    FlutterSecureStorage? storage,
    http.Client? client,
    AppLinks? appLinks,
  }) : _storage = storage ?? const FlutterSecureStorage(),
       _client = client ?? http.Client(),
       _appLinks = appLinks ?? AppLinks();

  static const callback = 'bible://openrouter/callback';
  static const _keyName = 'openrouter_api_key';
  static const _verifierName = 'openrouter_pkce_verifier';
  static const _pendingCodeName = 'openrouter_pending_code';
  final FlutterSecureStorage _storage;
  final http.Client _client;
  final AppLinks _appLinks;
  void Function()? onChanged;
  StreamSubscription<Uri>? _linkSubscription;
  Future<void>? _exchangeInProgress;
  Future<void>? _tokenExchangeInProgress;
  String? _lastError;

  String? get lastError => _lastError;

  Future<void> initialize() async {
    _linkSubscription ??= _appLinks.uriLinkStream.listen(
      (uri) {
        _exchange(uri).onError((_, _) {});
      },
      onError: (Object error) {
        _lastError = 'OpenRouter callback failed: $error';
        onChanged?.call();
      },
    );
    final initial = await _appLinks.getInitialLink();
    if (initial != null) {
      try {
        await _exchange(initial);
      } catch (_) {
        // Keep the app usable and expose the failure through lastError.
      }
    }
    await retryPendingExchange();
  }

  Future<bool> get isSignedIn async => (await apiKey)?.isNotEmpty == true;
  Future<String?> get apiKey => _storage.read(key: _keyName);

  Future<void> beginSignIn() async {
    final callbackSession = await startOAuthCallback();
    await _storage.delete(key: _pendingCodeName);
    final verifier = _randomVerifier();
    await _storage.write(key: _verifierName, value: verifier);
    _lastError = null;
    onChanged?.call();
    final uri = Uri.https('openrouter.ai', '/auth', {
      'callback_url': callbackSession.callbackUrl,
      'code_challenge': verifier,
      'code_challenge_method': 'plain',
    });
    final opened = await launchUrl(
      uri,
      mode: kIsWeb
          ? LaunchMode.platformDefault
          : LaunchMode.externalApplication,
      webOnlyWindowName: kIsWeb ? '_self' : null,
    );
    if (!opened) {
      await callbackSession.close();
      throw StateError('Could not open OpenRouter sign in.');
    }
    unawaited(
      callbackSession.result.then(_exchange).catchError((Object error) {
        _lastError = 'OpenRouter callback failed: $error';
        onChanged?.call();
      }),
    );
  }

  Future<void> signOut() async {
    await _storage.delete(key: _keyName);
    await _storage.delete(key: _verifierName);
    await _storage.delete(key: _pendingCodeName);
    _lastError = null;
    onChanged?.call();
  }

  Future<void> _exchange(Uri uri) {
    final appLink = uri.scheme == 'bible' && uri.host == 'openrouter';
    final loopback =
        uri.scheme == 'http' &&
        (uri.host == 'localhost' || uri.host == '127.0.0.1') &&
        uri.path == '/callback';
    final webCallback =
        kIsWeb &&
        uri.queryParameters['oauth'] == 'openrouter' &&
        uri.queryParameters.containsKey('code');
    if (!appLink && !loopback && !webCallback) {
      return Future<void>.value();
    }
    return _exchangeInProgress ??= _handleLink(uri).whenComplete(() {
      _exchangeInProgress = null;
    });
  }

  Future<void> _handleLink(Uri uri) async {
    final code = uri.queryParameters['code'];
    if (code == null || code.isEmpty) {
      _lastError = 'OpenRouter callback did not contain an authorization code.';
      onChanged?.call();
      return;
    }
    await _storage.write(key: _pendingCodeName, value: code);
    await retryPendingExchange();
  }

  Future<void> retryPendingExchange() =>
      _tokenExchangeInProgress ??= _performPendingExchange().whenComplete(() {
        _tokenExchangeInProgress = null;
      });

  Future<void> _performPendingExchange() async {
    final code = await _storage.read(key: _pendingCodeName);
    final verifier = await _storage.read(key: _verifierName);
    if (code == null) return;
    if (verifier == null || verifier.isEmpty) {
      _lastError = 'OpenRouter PKCE verifier is missing. Please sign in again.';
      onChanged?.call();
      return;
    }
    try {
      final response = await _client.post(
        Uri.parse('https://openrouter.ai/api/v1/auth/keys'),
        headers: const {'Content-Type': 'application/json'},
        body: jsonEncode({
          'code': code,
          'code_verifier': verifier,
          'code_challenge_method': 'plain',
        }),
      );
      if (response.statusCode < 200 || response.statusCode >= 300) {
        final detail = response.body.trim();
        throw StateError(
          'OpenRouter OAuth exchange failed (${response.statusCode})'
          '${detail.isEmpty ? '.' : ': ${detail.substring(0, min(240, detail.length))}'}',
        );
      }
      final payload = jsonDecode(response.body) as Map<String, dynamic>;
      final key = payload['key'] as String?;
      if (key == null || key.isEmpty) {
        throw StateError('OpenRouter returned no API key.');
      }
      await _storage.write(key: _keyName, value: key);
      await _storage.delete(key: _verifierName);
      await _storage.delete(key: _pendingCodeName);
      _lastError = null;
      clearOAuthCallbackUrl();
    } catch (error) {
      _lastError = error.toString();
      rethrow;
    } finally {
      onChanged?.call();
    }
  }

  String _randomVerifier() {
    final random = Random.secure();
    return base64Url
        .encode(List<int>.generate(64, (_) => random.nextInt(256)))
        .replaceAll('=', '');
  }
}

class OpenRouterClient {
  OpenRouterClient({required this.auth, http.Client? client})
    : _client = client ?? http.Client();

  final OpenRouterAuth auth;
  final http.Client _client;

  Future<String> generate({
    required String prompt,
    required String model,
  }) async =>
      (await generateStream(prompt: prompt, model: model).join()).trim();

  Stream<String> generateStream({
    required String prompt,
    required String model,
    void Function(String delta)? onReasoning,
  }) async* {
    final key = await auth.apiKey;
    if (key == null || key.isEmpty) {
      throw StateError('OPENROUTER_LOGIN_REQUIRED');
    }
    final structuredSearch =
        prompt.contains('"overview"') &&
        prompt.contains('"scriptures"') &&
        prompt.contains('Return ONLY valid JSON');
    final body = <String, dynamic>{
      'model': model,
      'messages': [
        {'role': 'user', 'content': prompt},
      ],
      'temperature': 0.2,
      'max_tokens': structuredSearch ? 2400 : 900,
      'stream': true,
      if (!structuredSearch) 'reasoning': {'enabled': true, 'exclude': false},
      if (structuredSearch) 'reasoning': {'max_tokens': 32, 'exclude': true},
      if (structuredSearch)
        'response_format': {
          'type': 'json_schema',
          'json_schema': {
            'name': 'bible_search',
            'strict': true,
            'schema': {
              'type': 'object',
              'additionalProperties': false,
              'required': ['overview', 'scriptures', 'suggestedQuestions'],
              'properties': {
                'overview': {'type': 'string'},
                'scriptures': {
                  'type': 'array',
                  'maxItems': 16,
                  'items': {
                    'type': 'object',
                    'additionalProperties': false,
                    'required': [
                      'bookId',
                      'chapter',
                      'verseStart',
                      'verseEnd',
                      'reason',
                    ],
                    'properties': {
                      'bookId': {'type': 'string'},
                      'chapter': {'type': 'integer'},
                      'verseStart': {'type': 'integer'},
                      'verseEnd': {'type': 'integer'},
                      'reason': {'type': 'string'},
                    },
                  },
                },
                'suggestedQuestions': {
                  'type': 'array',
                  'maxItems': 3,
                  'items': {'type': 'string'},
                },
              },
            },
          },
        },
    };
    final request =
        http.Request(
            'POST',
            Uri.parse('https://openrouter.ai/api/v1/chat/completions'),
          )
          ..headers.addAll({
            'Authorization': 'Bearer $key',
            'Content-Type': 'application/json',
            'X-OpenRouter-Title': 'Bible',
          })
          ..body = jsonEncode(body);
    final response = await _client.send(request);
    if (response.statusCode == 401 || response.statusCode == 403) {
      await response.stream.drain<void>();
      await auth.signOut();
      throw StateError('OPENROUTER_LOGIN_REQUIRED');
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      await response.stream.drain<void>();
      throw StateError('OpenRouter request failed (${response.statusCode}).');
    }

    var receivedContent = false;
    await for (final line
        in response.stream
            .transform(utf8.decoder)
            .transform(const LineSplitter())) {
      if (!line.startsWith('data:')) continue;
      final data = line.substring(5).trim();
      if (data.isEmpty || data == '[DONE]') continue;
      final decoded = jsonDecode(data);
      if (decoded is! Map<String, dynamic>) continue;
      final choices = decoded['choices'] as List?;
      final firstChoice = choices?.firstOrNull;
      final delta = firstChoice is Map ? firstChoice['delta'] : null;
      if (delta is Map) {
        final reasoning = _reasoningText(delta);
        if (reasoning.isNotEmpty) onReasoning?.call(reasoning);
      }
      final rawContent = delta is Map ? delta['content'] : null;
      final content = switch (rawContent) {
        String value => value,
        List parts =>
          parts
              .whereType<Map>()
              .map((part) => part['text'])
              .whereType<String>()
              .join(),
        _ => '',
      };
      if (content.isNotEmpty) {
        receivedContent = true;
        yield content;
      }
    }
    if (!receivedContent) {
      throw StateError('OpenRouter returned no response.');
    }
  }

  String _reasoningText(Map<dynamic, dynamic> delta) {
    final direct = delta['reasoning'] ?? delta['reasoning_content'];
    if (direct is String && direct.isNotEmpty) return direct;
    final details = delta['reasoning_details'];
    if (details is! List) return '';
    return details
        .whereType<Map>()
        .map((detail) {
          for (final key in const ['text', 'summary', 'data']) {
            final value = detail[key];
            if (value is String && value.isNotEmpty) return value;
          }
          return '';
        })
        .where((text) => text.isNotEmpty)
        .join();
  }
}
