import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'ai_memory_store.dart';
import 'bible_data.dart';
import 'openrouter_service.dart';

enum AiAvailability {
  checking,
  temporarilyUnavailable,
  unsupported,
  downloadable,
  downloading,
  ready,
}

enum AiProvider { openRouter }

abstract interface class AiSettingsStore {
  Future<String?> read(String key);
  Future<void> write(String key, String value);
}

class SecureAiSettingsStore implements AiSettingsStore {
  const SecureAiSettingsStore([this._storage = const FlutterSecureStorage()]);

  final FlutterSecureStorage _storage;

  @override
  Future<String?> read(String key) => _storage.read(key: key);

  @override
  Future<void> write(String key, String value) =>
      _storage.write(key: key, value: value);
}

class AiMessage {
  const AiMessage({
    required this.role,
    required this.text,
    this.scripture,
    this.kind = 'chat',
    this.reasoning,
    this.incomplete = false,
  });

  final String role;
  final String text;
  final String? scripture;
  final String kind;
  final String? reasoning;
  final bool incomplete;

  factory AiMessage.fromJson(Map<String, dynamic> json) => AiMessage(
    role: json['role'] as String? ?? 'assistant',
    text: json['text'] as String? ?? '',
    scripture: json['scripture'] as String?,
    kind: json['kind'] as String? ?? 'chat',
    reasoning: json['reasoning'] as String?,
    incomplete: json['incomplete'] as bool? ?? false,
  );

  Map<String, dynamic> toJson() => {
    'role': role,
    'text': text,
    'kind': kind,
    'scripture': ?scripture,
    'reasoning': ?reasoning,
    'incomplete': incomplete,
  };
}

abstract interface class BibleAiModel {
  Future<AiAvailability> availability();
  Future<String> generate(String prompt);
  Stream<String> generateStream(String prompt);
  Future<void> downloadModel();
}

class CloudBibleModel implements BibleAiModel {
  CloudBibleModel(this._client, {required this.modelId});

  final OpenRouterClient _client;
  final String Function() modelId;
  void Function(String delta)? onReasoning;

  @override
  Future<AiAvailability> availability() async => await _client.auth.isSignedIn
      ? AiAvailability.ready
      : AiAvailability.downloadable;

  @override
  Future<void> downloadModel() =>
      throw UnsupportedError('Cloud models do not download');

  @override
  Future<String> generate(String prompt) =>
      _client.generate(prompt: prompt, model: modelId());

  @override
  Stream<String> generateStream(String prompt) => _client.generateStream(
    prompt: prompt,
    model: modelId(),
    onReasoning: onReasoning,
  );
}

class AiScriptureReference {
  const AiScriptureReference({
    required this.bookId,
    required this.chapter,
    required this.verseStart,
    required this.verseEnd,
    required this.reason,
  });

  final String bookId;
  final int chapter;
  final int verseStart;
  final int verseEnd;
  final String reason;

  factory AiScriptureReference.fromJson(Map<String, dynamic> json) {
    int number(String key, [int fallback = 1]) =>
        (json[key] as num?)?.toInt() ?? fallback;
    return AiScriptureReference(
      bookId: (json['bookId'] as String? ?? '').toUpperCase(),
      chapter: number('chapter'),
      verseStart: number('verseStart'),
      verseEnd: number('verseEnd', number('verseStart')),
      reason: json['reason'] as String? ?? '',
    );
  }
}

class AiSearchResponse {
  const AiSearchResponse({
    required this.overview,
    required this.scriptures,
    required this.suggestedQuestions,
  });

  final String overview;
  final List<AiScriptureReference> scriptures;
  final List<String> suggestedQuestions;
}

class BibleAiController extends ChangeNotifier {
  BibleAiController({
    BibleAiModel? model,
    AiMemoryStore? memory,
    OpenRouterAuth? auth,
    AiSettingsStore? settings,
  }) : _overrideModel = model,
       _memory = memory ?? AiMemoryStore(),
       _auth = auth ?? OpenRouterAuth(),
       _settings = settings ?? const SecureAiSettingsStore() {
    _cloudModel = CloudBibleModel(
      OpenRouterClient(auth: _auth),
      modelId: () => modelId,
    );
    _auth.onChanged = _authChanged;
  }

  static final BibleAiController instance = BibleAiController();
  static const _android = MethodChannel('bible/android');
  final BibleAiModel? _overrideModel;
  final OpenRouterAuth _auth;
  final AiSettingsStore _settings;
  late final BibleAiModel _cloudModel;
  final AiMemoryStore _memory;
  final List<AiMessage> _messages = [];
  AiAvailability availability = AiAvailability.checking;
  bool initialized = false;
  Future<void>? _initialization;
  bool generating = false;
  bool openRouterSignedIn = false;
  AiProvider provider = AiProvider.openRouter;
  String modelId = 'openrouter/free';

  List<AiMessage> get messages => List.unmodifiable(_messages);
  bool get isSupported =>
      kIsWeb || defaultTargetPlatform == TargetPlatform.android;
  bool get isReady => _overrideModel != null
      ? availability == AiAvailability.ready
      : openRouterSignedIn;
  bool get requiresLogin => _overrideModel == null && !openRouterSignedIn;
  String? get openRouterAuthError => _auth.lastError;
  BibleAiModel get _activeModel => _overrideModel ?? _cloudModel;

  Future<void> initialize() {
    if (initialized) return Future<void>.value();
    return _initialization ??= _performInitialization();
  }

  Future<void> _performInitialization() async {
    try {
      await _memory.initialize();
      if (isSupported) await _auth.initialize();
      openRouterSignedIn = await _auth.isSignedIn;
      provider = AiProvider.openRouter;
      modelId = await _settings.read('openrouter_model') ?? 'openrouter/free';
      final saved = await _memory.transcript();
      _messages
        ..clear()
        ..addAll(saved.map(AiMessage.fromJson));
      if (_overrideModel != null) {
        availability = await _overrideModel.availability();
      }
      await _syncShortcut();
      initialized = true;
      notifyListeners();
    } catch (_) {
      initialized = false;
      _initialization = null;
      rethrow;
    }
  }

  Future<void> beginOpenRouterLogin() => _auth.beginSignIn();

  Future<void> refreshOpenRouterLogin() async {
    await _auth.retryPendingExchange();
    openRouterSignedIn = await _auth.isSignedIn;
    notifyListeners();
  }

  Future<void> reloadSharedSettings() async {
    provider = AiProvider.openRouter;
    modelId = await _settings.read('openrouter_model') ?? 'openrouter/free';
    openRouterSignedIn = await _auth.isSignedIn;
    notifyListeners();
  }

  Future<void> openChatActivity({
    String? initialQuestion,
    String? scriptureContext,
    String? scriptureReference,
    bool autoSend = false,
  }) async {
    if (!isSupported) return;
    await _android.invokeMethod<void>('openAiChatActivity', {
      'initialQuestion': ?initialQuestion,
      'scriptureContext': ?scriptureContext,
      'scriptureReference': ?scriptureReference,
      'autoSend': autoSend,
    });
  }

  Future<void> signOutOpenRouter() async {
    await _auth.signOut();
    openRouterSignedIn = false;
    notifyListeners();
  }

  Future<void> selectProvider(AiProvider value) async {
    if (provider == value) return;
    provider = value;
    notifyListeners();
    await _settings.write('ai_provider', value.name);
  }

  Future<void> setModel(String value) async {
    final trimmed = value.trim();
    if (trimmed.isEmpty) return;
    modelId = trimmed;
    await _settings.write('openrouter_model', trimmed);
    notifyListeners();
  }

  Future<String> send({
    required String question,
    BibleRepository? repository,
    String? scriptureContext,
    String? scriptureReference,
    String kind = 'chat',
  }) async {
    if (!isReady) throw StateError('AI provider is not ready.');
    final user = AiMessage(
      role: 'user',
      text: question,
      scripture: scriptureReference,
      kind: kind,
    );
    _messages.add(user);
    await _memory.recordMessage(
      role: user.role,
      text: user.text,
      scripture: user.scripture,
      kind: kind,
    );
    return _answerExistingMessage(
      question: question,
      repository: repository,
      scriptureContext: scriptureContext,
      scriptureReference: scriptureReference,
      kind: kind,
    );
  }

  Future<String> _answerExistingMessage({
    required String question,
    required BibleRepository? repository,
    required String? scriptureContext,
    required String? scriptureReference,
    required String kind,
  }) async {
    generating = true;
    notifyListeners();
    int? visibleIndex;
    try {
      final prompt = await _chatPrompt(question, scriptureContext);
      var streamed = await _streamModelAnswer(
        prompt,
        scriptureReference: scriptureReference,
        kind: kind,
      );
      var answer = streamed.answer;
      visibleIndex = streamed.visibleIndex;
      final toolResults = <String>[];
      for (var toolCall = 0; toolCall < 3; toolCall++) {
        final tool = _jsonObject(answer);
        if (tool?['tool'] != 'get_scripture' || repository == null) break;
        _removeProvisional(visibleIndex);
        visibleIndex = null;
        final result = await _runScriptureTool(tool!, repository);
        toolResults.add(result);
        streamed = await _streamModelAnswer(
          '$prompt\n\nThe app executed get_scripture. Use this authoritative result '
          'to answer the user. If another passage is essential, you may issue '
          'one more get_scripture JSON request.\n\n${toolResults.join('\n\n')}',
          scriptureReference: scriptureReference,
          kind: kind,
        );
        answer = streamed.answer;
        visibleIndex = streamed.visibleIndex;
      }
      if (_jsonObject(answer)?['tool'] == 'get_scripture') {
        _removeProvisional(visibleIndex);
        visibleIndex = null;
        final availableResults = toolResults.isEmpty
            ? 'The scripture tool is unavailable. Answer only from the '
                  'authoritative chapter reference already supplied.'
            : toolResults.join('\n\n');
        streamed = await _streamModelAnswer(
          '$prompt\n\nUse the authoritative tool results below and answer now. '
          'Do not request another tool.\n\n$availableResults',
          scriptureReference: scriptureReference,
          kind: kind,
        );
        answer = streamed.answer;
        visibleIndex = streamed.visibleIndex;
      }
      for (
        var continuation = 0;
        continuation < 2 && !streamed.complete && _jsonObject(answer) == null;
        continuation++
      ) {
        streamed = await _streamModelAnswer(
          '''
$prompt

Response already written:
$answer

Continue directly after the last complete thought without repeating existing
text. End with [[END]] when the answer is complete. If another segment is still
needed, end this segment with [[MORE]].
''',
          scriptureReference: scriptureReference,
          kind: kind,
          initialAnswer: answer,
          visibleIndex: visibleIndex,
        );
        answer = streamed.answer;
        visibleIndex = streamed.visibleIndex;
      }
      final streamedReasoning =
          visibleIndex != null &&
              visibleIndex >= 0 &&
              visibleIndex < _messages.length
          ? _messages[visibleIndex].reasoning
          : null;
      final assistant = AiMessage(
        role: 'assistant',
        text: answer.trim(),
        scripture: scriptureReference,
        kind: kind,
        reasoning: streamedReasoning,
        incomplete: !streamed.complete && _jsonObject(answer) == null,
      );
      if (visibleIndex case final index?
          when index >= 0 && index < _messages.length) {
        _messages[index] = assistant;
      } else {
        _messages.add(assistant);
      }
      notifyListeners();
      await _memory.recordMessage(
        role: assistant.role,
        text: assistant.text,
        scripture: assistant.scripture,
        kind: kind,
      );
      return answer;
    } catch (_) {
      _removeProvisional(visibleIndex);
      rethrow;
    } finally {
      generating = false;
      notifyListeners();
    }
  }

  Future<({String answer, int? visibleIndex, bool complete})>
  _streamModelAnswer(
    String prompt, {
    required String? scriptureReference,
    required String kind,
    String initialAnswer = '',
    int? visibleIndex,
  }) async {
    var rawSegment = '';
    var reasoning =
        visibleIndex != null &&
            visibleIndex >= 0 &&
            visibleIndex < _messages.length
        ? _messages[visibleIndex].reasoning ?? ''
        : '';
    final cloud = _activeModel is CloudBibleModel
        ? _activeModel as CloudBibleModel
        : null;
    void updateProvisional() {
      if (initialAnswer.isEmpty && rawSegment.trimLeft().startsWith('{')) {
        return;
      }
      final answer = _appendWithoutDuplicate(
        initialAnswer,
        _cleanModelOutput(rawSegment),
      );
      final provisional = AiMessage(
        role: 'assistant',
        text: answer,
        scripture: scriptureReference,
        kind: kind,
        reasoning: reasoning.isEmpty ? null : reasoning,
      );
      if (visibleIndex == null) {
        _messages.add(provisional);
        visibleIndex = _messages.length - 1;
      } else {
        _messages[visibleIndex!] = provisional;
      }
      notifyListeners();
    }

    cloud?.onReasoning = (delta) {
      reasoning += delta;
      updateProvisional();
    };
    try {
      await for (final delta in _activeModel.generateStream(prompt)) {
        rawSegment += delta;
        // A leading JSON object is an app-side tool request. Keep it out of the
        // conversation while it is incomplete so users never see protocol text.
        updateProvisional();
      }
    } finally {
      cloud?.onReasoning = null;
    }
    if (rawSegment.trim().isEmpty) {
      throw StateError('AI model returned no response.');
    }
    final answer = _appendWithoutDuplicate(
      initialAnswer,
      _cleanModelOutput(rawSegment),
    );
    return (
      answer: answer.trim(),
      visibleIndex: visibleIndex,
      complete: rawSegment.contains('[[END]]'),
    );
  }

  String _cleanModelOutput(String source) => source
      .replaceAll('[[END]]', '')
      .replaceAll('[[MORE]]', '')
      .replaceFirst(RegExp(r'\[\[(?:E(?:N(?:D)?)?|M(?:O(?:R(?:E)?)?)?)?$'), '')
      .trim();

  String _appendWithoutDuplicate(String existing, String addition) {
    final left = existing.trimRight();
    final right = addition.trimLeft();
    if (left.isEmpty) return right;
    if (right.isEmpty || left.endsWith(right)) return left;
    final maxOverlap = left.length < right.length ? left.length : right.length;
    for (var overlap = maxOverlap; overlap >= 12; overlap--) {
      if (left.endsWith(right.substring(0, overlap))) {
        return '$left${right.substring(overlap)}';
      }
    }
    return '$left\n\n$right';
  }

  void _removeProvisional(int? index) {
    if (index != null && index >= 0 && index < _messages.length) {
      _messages.removeAt(index);
      notifyListeners();
    }
  }

  Future<AiSearchResponse> search(String query) async {
    if (!isReady) throw StateError('AI provider is not ready.');
    generating = true;
    notifyListeners();
    try {
      final trimmedQuery = query.trim();
      if (trimmedQuery.isEmpty) {
        throw ArgumentError.value(query, 'query', 'Search query is empty.');
      }
      final user = AiMessage(role: 'user', text: trimmedQuery, kind: 'search');
      _messages.add(user);
      await _memory.recordMessage(
        role: user.role,
        text: user.text,
        kind: user.kind,
      );
      final memory = await _memory.promptMemory(maxCharacters: 3500);
      final raw = await _activeModel.generate('''
You are the search engine inside a Bible reading app. Search by meaning and
return a short, careful AI Overview. Never invent or quote verse text. The app
will resolve references from its local Bible database.

Return ONLY valid JSON in exactly this shape:
{"overview":"Traditional Chinese summary","scriptures":[{"bookId":"JHN","chapter":3,"verseStart":16,"verseEnd":17,"reason":"why relevant"}],"suggestedQuestions":["follow-up"]}
Use canonical 3-letter book IDs. Return up to 16 relevant scripture references
when the query is broad, and up to 8 for a narrow query. Return 3 questions.
Every reason must accurately describe the referenced verses. For example,
MAT 4:1-11 is Jesus' temptation; the Samaritan woman is JHN 4. Be concise.

Persistent user memory:
$memory

Search query: $trimmedQuery
''');
      final json = _jsonObject(raw);
      if (json == null ||
          json['overview'] is! String ||
          json['scriptures'] is! List ||
          json['suggestedQuestions'] is! List) {
        throw const FormatException('AI search returned invalid JSON.');
      }
      final response = AiSearchResponse(
        overview: (json['overview'] as String).trim(),
        scriptures: (json['scriptures'] as List)
            .whereType<Map>()
            .map(
              (item) =>
                  AiScriptureReference.fromJson(item.cast<String, dynamic>()),
            )
            .where((item) => bibleBooks.any((book) => book.id == item.bookId))
            .where((item) {
              final book = bibleBooks.firstWhere(
                (book) => book.id == item.bookId,
              );
              return item.chapter >= 1 &&
                  item.chapter <= book.chapters &&
                  item.verseStart >= 1 &&
                  item.verseEnd >= item.verseStart;
            })
            .toList(),
        suggestedQuestions: (json['suggestedQuestions'] as List)
            .whereType<String>()
            .take(3)
            .toList(),
      );
      final assistant = AiMessage(
        role: 'assistant',
        text: response.overview,
        scripture: response.scriptures
            .map(
              (r) => '${r.bookId} ${r.chapter}:${r.verseStart}-${r.verseEnd}',
            )
            .join(', '),
        kind: 'search',
      );
      _messages.add(assistant);
      await _memory.recordMessage(
        role: assistant.role,
        text: assistant.text,
        scripture: assistant.scripture,
        kind: assistant.kind,
      );
      return response;
    } finally {
      generating = false;
      notifyListeners();
    }
  }

  Future<void> clearConversation() async {
    _messages.clear();
    await _memory.clear();
    notifyListeners();
  }

  Future<String?> regenerateLast({
    BibleRepository? repository,
    String? scriptureContext,
    String? scriptureReference,
  }) async {
    if (generating) return null;
    final userIndex = _messages.lastIndexWhere(
      (message) => message.role == 'user',
    );
    if (userIndex < 0) return null;
    final previous = _messages[userIndex];
    if (userIndex + 1 < _messages.length) {
      _messages.removeRange(userIndex + 1, _messages.length);
    }
    await _memory.replaceTranscript(_messages.map((item) => item.toJson()));
    notifyListeners();
    return _answerExistingMessage(
      question: previous.text,
      repository: repository,
      scriptureContext: scriptureContext,
      scriptureReference: scriptureReference ?? previous.scripture,
      kind: previous.kind,
    );
  }

  Future<String> _chatPrompt(String question, String? scriptureContext) async {
    final memory = await _memory.promptMemory();
    final historyLength =
        _messages.isNotEmpty &&
            _messages.last.role == 'user' &&
            _messages.last.text == question
        ? _messages.length - 1
        : _messages.length;
    final recent = _messages
        .take(historyLength)
        .skip(historyLength > 12 ? historyLength - 12 : 0)
        .map((message) => '${message.role}: ${message.text}')
        .join('\n');
    return '''
You are the Bible assistant in a Bible reading app. Reply in
Traditional Chinese unless the user asks otherwise. Be clear about uncertainty,
use the supplied scripture as authoritative, and keep continuity with memory.
All entrances belong to one continuing conversation.

Never invent, paraphrase as a quotation, or silently correct scripture text.
When quoting a verse, reproduce it verbatim from the authoritative full-chapter
reference or an app get_scripture result. If the required passage is not in
either source, request it with the tool before quoting it.

If another passage is essential, reply ONLY with this JSON tool request:
{"tool":"get_scripture","bookId":"JHN","chapter":3,"verseStart":16,"verseEnd":17,"language":"bilingual"}

Persistent memory:
$memory

Recent conversation:
$recent

Authoritative full-chapter reference:
${scriptureContext ?? '(none supplied)'}

Current user message: $question

For every user-facing answer, end with [[END]] when complete. If the response
must continue in another segment, end with [[MORE]]. Do not use these markers
inside a JSON tool request.
''';
  }

  Future<String> _runScriptureTool(
    Map<String, dynamic> request,
    BibleRepository repository,
  ) async {
    final id = (request['bookId'] as String? ?? '').toUpperCase();
    final book = bibleBooks.where((item) => item.id == id).firstOrNull;
    if (book == null) return 'Tool error: unknown bookId $id';
    final chapter = (request['chapter'] as num?)?.toInt() ?? 1;
    final start = (request['verseStart'] as num?)?.toInt() ?? 1;
    final end = (request['verseEnd'] as num?)?.toInt() ?? start;
    if (chapter < 1 || chapter > book.chapters) {
      return 'Tool error: ${book.id} has no chapter $chapter';
    }
    if (start < 1 || end < start || end - start > 100) {
      return 'Tool error: invalid verse range $start-$end';
    }
    final verses = await repository.chapter(book, chapter);
    final selected = verses.where(
      (verse) => verse.number >= start && verse.number <= end,
    );
    return selected
        .map(
          (verse) =>
              '${book.zh} $chapter:${verse.number}\n中文：${verse.zh}\nEnglish: ${verse.en}',
        )
        .join('\n\n');
  }

  Map<String, dynamic>? _jsonObject(String source) {
    final start = source.indexOf('{');
    final end = source.lastIndexOf('}');
    if (start < 0 || end <= start) return null;
    try {
      return (jsonDecode(source.substring(start, end + 1)) as Map)
          .cast<String, dynamic>();
    } catch (_) {
      return null;
    }
  }

  Future<void> _syncShortcut() async {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return;
    try {
      await _android.invokeMethod<void>('setAiShortcutEnabled', isSupported);
    } on MissingPluginException {
      // The optional shortcut is absent in tests and older Android shells.
    } on PlatformException {
      // Older shells simply omit the optional launcher shortcut.
    }
  }

  Future<void> _authChanged() async {
    openRouterSignedIn = await _auth.isSignedIn;
    notifyListeners();
  }
}
