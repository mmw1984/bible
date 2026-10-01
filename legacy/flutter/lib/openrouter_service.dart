import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:app_links/app_links.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;
import 'package:url_launcher/url_launcher.dart';

import 'oauth_callback.dart';
import 'oauth_cleanup.dart';
import 'openrouter_oauth.dart';

class OpenRouterAuth {
  OpenRouterAuth({
    FlutterSecureStorage? storage,
    http.Client? client,
    AppLinks? appLinks,
    bool? isWeb,
    Uri? webLocation,
  }) : _storage = storage ?? const FlutterSecureStorage(),
       _client = client ?? http.Client(),
       _appLinks = appLinks ?? AppLinks(),
       _isWeb = isWeb ?? kIsWeb,
       _webLocation = webLocation ?? Uri.base;

  static const callback = 'bible://openrouter/callback';
  static const _keyName = 'openrouter_api_key';
  static const _verifierName = 'openrouter_pkce_verifier';
  static const _pkceMethodName = 'openrouter_pkce_method';
  static const _pendingCodeName = 'openrouter_pending_code';
  final FlutterSecureStorage _storage;
  final http.Client _client;
  final AppLinks _appLinks;
  final bool _isWeb;
  final Uri _webLocation;
  final Set<String> _handledCodes = {};
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

    if (_isWeb) {
      try {
        await _exchange(_webLocation);
      } catch (_) {
        // Keep the app usable and expose the failure through lastError.
      }
    }

    try {
      final initial = await _appLinks.getInitialLink();
      if (initial != null && (!_isWeb || initial != _webLocation)) {
        await _exchange(initial);
      }
    } catch (error) {
      if (!_isWeb) {
        _lastError = 'OpenRouter callback failed: $error';
        onChanged?.call();
      }
    }

    try {
      await retryPendingExchange();
    } catch (_) {
      // A failed exchange remains pending and can be retried without blocking AI.
    }
  }

  Future<bool> get isSignedIn async => (await apiKey)?.isNotEmpty == true;
  Future<String?> get apiKey => _storage.read(key: _keyName);

  Future<void> beginSignIn() async {
    final callbackSession = await startOAuthCallback();
    await _storage.delete(key: _pendingCodeName);
    final verifier = _randomVerifier();
    await _storage.write(key: _verifierName, value: verifier);
    await _storage.write(key: _pkceMethodName, value: openRouterPkceMethod);
    _lastError = null;
    onChanged?.call();
    final uri = Uri.https('openrouter.ai', '/auth', {
      'callback_url': callbackSession.callbackUrl,
      'code_challenge': createOpenRouterPkceChallenge(verifier),
      'code_challenge_method': openRouterPkceMethod,
    });
    final opened = await launchUrl(
      uri,
      mode: _isWeb
          ? LaunchMode.platformDefault
          : LaunchMode.externalApplication,
      webOnlyWindowName: _isWeb ? '_self' : null,
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
    await _storage.delete(key: _pkceMethodName);
    await _storage.delete(key: _pendingCodeName);
    _lastError = null;
    onChanged?.call();
  }

  Future<void> _exchange(Uri uri) {
    if (!isOpenRouterCallback(uri, isWeb: _isWeb, webLocation: _webLocation)) {
      return Future<void>.value();
    }
    return _exchangeInProgress ??= _handleLink(uri).whenComplete(() {
      _exchangeInProgress = null;
    });
  }

  Future<void> _handleLink(Uri uri) async {
    final callbackError =
        uri.queryParameters['error_description'] ??
        uri.queryParameters['error'];
    if (callbackError != null && callbackError.isNotEmpty) {
      _lastError = 'OpenRouter login was not completed: $callbackError';
      clearOAuthCallbackUrl();
      onChanged?.call();
      return;
    }
    final code = uri.queryParameters['code'];
    if (code == null || code.isEmpty) {
      _lastError = 'OpenRouter callback did not contain an authorization code.';
      clearOAuthCallbackUrl();
      onChanged?.call();
      return;
    }
    if (!_handledCodes.add(code)) return;
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
    final pkceMethod = await _storage.read(key: _pkceMethodName) ?? 'plain';
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
          'code_challenge_method': pkceMethod,
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
      await _storage.delete(key: _pkceMethodName);
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
    OpenRouterRequestOptions options = const OpenRouterRequestOptions(),
  }) async => (await generateStream(
    prompt: prompt,
    model: model,
    options: options,
  ).join()).trim();

  Future<WebResearchResponse> research({
    required String prompt,
    required String model,
  }) async {
    final response = await _send(
      prompt: prompt,
      model: model,
      stream: false,
      options: const OpenRouterRequestOptions(
        webSearch: OpenRouterWebSearch.forced,
        maxToolCalls: 1,
        maxWebSearchUses: 1,
        maxWebResults: 6,
        maxTotalWebResults: 6,
        webSearchContextSize: 'low',
        reasoning: false,
        maxTokens: 8192,
      ),
    );
    final payload = jsonDecode(await response.stream.bytesToString());
    if (payload is! Map<String, dynamic>) {
      throw StateError('OpenRouter returned an invalid research response.');
    }
    final choices = payload['choices'] as List?;
    final firstChoice = choices?.firstOrNull;
    final message = firstChoice is Map ? firstChoice['message'] : null;
    final content = message is Map ? _contentText(message['content']) : '';
    final annotations = message is Map ? message['annotations'] : null;
    final sources = _researchSources(annotations);
    final usage = payload['usage'];
    final serverToolUse = usage is Map ? usage['server_tool_use'] : null;
    final rawRequests = serverToolUse is Map
        ? serverToolUse['web_search_requests']
        : null;
    final searchRequests = rawRequests is num ? rawRequests.toInt() : 0;
    if (searchRequests < 1 && sources.isEmpty) {
      throw StateError('OpenRouter web search did not run.');
    }
    if (content.trim().isEmpty && sources.isEmpty) {
      throw StateError('OpenRouter web search returned no evidence.');
    }
    return WebResearchResponse(
      summary: content.trim(),
      sources: sources,
      searchRequests: searchRequests < 1 ? 1 : searchRequests,
    );
  }

  Stream<String> generateStream({
    required String prompt,
    required String model,
    void Function(String delta)? onReasoning,
    void Function(String reason)? onFinishReason,
    OpenRouterRequestOptions options = const OpenRouterRequestOptions(),
  }) async* {
    final response = await _send(
      prompt: prompt,
      model: model,
      stream: true,
      options: options,
    );
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
      final eventError = decoded['error'];
      if (eventError != null) {
        throw StateError(_errorMessage(eventError));
      }
      final choices = decoded['choices'] as List?;
      final firstChoice = choices?.firstOrNull;
      final finishReason = firstChoice is Map
          ? firstChoice['finish_reason']
          : null;
      if (finishReason is String && finishReason.isNotEmpty) {
        onFinishReason?.call(finishReason);
      }
      final delta = firstChoice is Map ? firstChoice['delta'] : null;
      if (delta is Map) {
        final reasoning = _reasoningText(delta);
        if (reasoning.isNotEmpty) onReasoning?.call(reasoning);
      }
      final content = delta is Map ? _contentText(delta['content']) : '';
      if (content.isNotEmpty) {
        receivedContent = true;
        yield content;
      }
    }
    if (!receivedContent) {
      throw StateError('OpenRouter returned no response.');
    }
  }

  Future<http.StreamedResponse> _send({
    required String prompt,
    required String model,
    required bool stream,
    required OpenRouterRequestOptions options,
  }) async {
    final key = await auth.apiKey;
    if (key == null || key.isEmpty) {
      throw StateError('OPENROUTER_LOGIN_REQUIRED');
    }
    final structuredReferences = prompt.contains(
      'BIBLE_SEARCH_REFERENCES_JSON',
    );
    final shortSearchOverview = prompt.contains('BIBLE_SEARCH_OVERVIEW');
    final structuredSearch =
        structuredReferences ||
        (prompt.contains('"overview"') &&
            prompt.contains('"scriptures"') &&
            prompt.contains('Return ONLY valid JSON'));
    final maxTokens = structuredSearch
        ? 2400
        : shortSearchOverview
        ? 1200
        : options.maxTokens;
    final body = <String, dynamic>{
      'model': model,
      'messages': [
        {'role': 'user', 'content': prompt},
      ],
      'temperature': 0.2,
      'max_tokens': ?maxTokens,
      'stream': stream,
      if (!structuredSearch && !shortSearchOverview && options.reasoning)
        'reasoning': {
          'max_tokens': options.reasoningMaxTokens,
          'exclude': false,
        },
      if (shortSearchOverview) 'reasoning': {'enabled': false, 'exclude': true},
      if (structuredSearch) 'reasoning': {'max_tokens': 32, 'exclude': true},
      if (options.webSearch != OpenRouterWebSearch.disabled) ...{
        'tools': [
          {
            'type': 'openrouter:web_search',
            'parameters': {
              'engine': 'auto',
              'max_results': options.maxWebResults,
              'max_uses': options.maxWebSearchUses,
              'max_total_results': options.maxTotalWebResults,
              'search_context_size': options.webSearchContextSize,
            },
          },
        ],
        'tool_choice': options.webSearch == OpenRouterWebSearch.forced
            ? {'type': 'openrouter:web_search'}
            : 'auto',
        'max_tool_calls': options.maxToolCalls,
      },
      if (structuredSearch)
        'response_format': {
          'type': 'json_schema',
          'json_schema': {
            'name': 'bible_search',
            'strict': true,
            'schema': {
              'type': 'object',
              'additionalProperties': false,
              'required': [
                if (!structuredReferences) 'overview',
                'scriptures',
                'suggestedQuestions',
              ],
              'properties': {
                if (!structuredReferences) 'overview': {'type': 'string'},
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
      final rawBody = await response.stream.bytesToString();
      Object? payload;
      try {
        payload = jsonDecode(rawBody);
      } catch (_) {
        payload = rawBody;
      }
      throw StateError(
        'OpenRouter request failed (${response.statusCode}): '
        '${_errorMessage(payload)}',
      );
    }

    return response;
  }

  String _contentText(Object? rawContent) => switch (rawContent) {
    String value => value,
    List parts =>
      parts
          .whereType<Map>()
          .map((part) => part['text'])
          .whereType<String>()
          .join(),
    _ => '',
  };

  List<WebResearchSource> _researchSources(Object? annotations) {
    if (annotations is! List) return const [];
    final sources = <WebResearchSource>[];
    final seenUrls = <String>{};
    final hostCounts = <String, int>{};
    for (final annotation in annotations.whereType<Map>()) {
      final citation = annotation['url_citation'];
      final data = citation is Map ? citation : annotation;
      final url = data['url'];
      final uri = url is String ? Uri.tryParse(url) : null;
      if (uri == null || !uri.hasScheme || uri.host.isEmpty) continue;
      final normalized = _normalizeResearchUri(uri);
      final normalizedUrl = normalized.toString();
      final host = normalized.host.toLowerCase();
      if (!seenUrls.add(normalizedUrl) || (hostCounts[host] ?? 0) >= 2) {
        continue;
      }
      hostCounts[host] = (hostCounts[host] ?? 0) + 1;
      final title = data['title'];
      final content = data['content'];
      sources.add(
        WebResearchSource(
          url: normalizedUrl,
          title: title is String && title.trim().isNotEmpty
              ? title.trim()
              : host,
          excerpt: content is String ? content.trim() : '',
        ),
      );
    }
    return sources;
  }

  Uri _normalizeResearchUri(Uri uri) {
    const trackingKeys = {
      'fbclid',
      'gclid',
      'mc_cid',
      'mc_eid',
      'ref',
      'source',
    };
    final query = <String, String>{};
    for (final entry in uri.queryParameters.entries) {
      final key = entry.key.toLowerCase();
      if (key.startsWith('utm_') || trackingKeys.contains(key)) continue;
      query[entry.key] = entry.value;
    }
    final normalized = Uri(
      scheme: uri.scheme.toLowerCase(),
      userInfo: uri.userInfo,
      host: uri.host.toLowerCase(),
      port: uri.hasPort ? uri.port : null,
      path: uri.path,
      queryParameters: query.isEmpty ? null : query,
    );
    return normalized;
  }

  String _reasoningText(Map<dynamic, dynamic> delta) {
    for (final key in const ['reasoning', 'reasoning_content', 'analysis']) {
      final text = _reasoningValue(delta[key]);
      if (text.isNotEmpty) return text;
    }
    final details = _reasoningValue(delta['reasoning_details']);
    if (details.isNotEmpty) return details;
    final content = delta['content'];
    if (content is! List) return '';
    return content
        .whereType<Map>()
        .where((part) {
          final type = part['type']?.toString().toLowerCase() ?? '';
          return type.contains('reasoning') || type.contains('analysis');
        })
        .map(_reasoningValue)
        .join();
  }

  String _reasoningValue(Object? value) => switch (value) {
    String text => text,
    List items => items.map(_reasoningValue).join(),
    Map data => () {
      for (final key in const [
        'text',
        'summary',
        'content',
        'reasoning',
        'data',
      ]) {
        final text = _reasoningValue(data[key]);
        if (text.isNotEmpty) return text;
      }
      return '';
    }(),
    _ => '',
  };

  String _errorMessage(Object? value) {
    if (value is Map) {
      final nested = value['error'];
      if (nested != null && !identical(nested, value)) {
        return _errorMessage(nested);
      }
      final message = value['message'];
      if (message is String && message.trim().isNotEmpty) {
        return message.trim();
      }
    }
    if (value is String && value.trim().isNotEmpty) return value.trim();
    return 'Unknown OpenRouter error.';
  }
}

enum OpenRouterWebSearch { disabled, forced, automatic }

class OpenRouterRequestOptions {
  const OpenRouterRequestOptions({
    this.webSearch = OpenRouterWebSearch.disabled,
    this.maxToolCalls = 0,
    this.maxWebSearchUses = 0,
    this.maxWebResults = 0,
    this.maxTotalWebResults = 0,
    this.webSearchContextSize = 'low',
    this.reasoning = true,
    this.maxTokens,
    this.reasoningMaxTokens = 8192,
  });

  final OpenRouterWebSearch webSearch;
  final int maxToolCalls;
  final int maxWebSearchUses;
  final int maxWebResults;
  final int maxTotalWebResults;
  final String webSearchContextSize;
  final bool reasoning;
  final int? maxTokens;
  final int reasoningMaxTokens;
}

class WebResearchSource {
  const WebResearchSource({
    required this.url,
    required this.title,
    required this.excerpt,
  });

  final String url;
  final String title;
  final String excerpt;
}

class WebResearchResponse {
  const WebResearchResponse({
    required this.summary,
    required this.sources,
    required this.searchRequests,
  });

  final String summary;
  final List<WebResearchSource> sources;
  final int searchRequests;

  String get compactEvidence {
    final evidence = sources
        .map((source) {
          final excerpt = source.excerpt.length <= 1800
              ? source.excerpt
              : source.excerpt.substring(0, 1800);
          return '[${source.title}] ${excerpt.trim()}';
        })
        .where((item) => item.trim().isNotEmpty)
        .join('\n\n');
    return evidence.isEmpty ? summary : '$summary\n\n$evidence';
  }
}
