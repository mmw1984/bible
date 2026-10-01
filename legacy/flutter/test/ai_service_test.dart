import 'dart:async';

import 'package:bible/ai_memory_store.dart';
import 'package:bible/ai_service.dart';
import 'package:bible/openrouter_service.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'initialize stays false until one shared bootstrap future completes',
    () async {
      final memory = _FakeMemoryStore(blockInitialization: true);
      final settings = _FakeSettingsStore({
        'openrouter_model': 'openrouter/free',
      });
      final controller = BibleAiController(
        model: _FakeModel(),
        memory: memory,
        auth: _FakeAuth(),
        settings: settings,
      );

      final first = controller.initialize();
      final second = controller.initialize();

      expect(identical(first, second), isTrue);
      expect(controller.initialized, isFalse);

      memory.finishInitialization();
      await first;

      expect(controller.initialized, isTrue);
      expect(controller.availability, AiAvailability.ready);
    },
  );

  test(
    'scripture questions and explanations share prompt context and memory',
    () async {
      final model = _FakeModel(
        responses: ['answer one [[END]]', 'answer two [[END]]'],
      );
      final memory = _FakeMemoryStore();
      final controller = BibleAiController(
        model: model,
        memory: memory,
        auth: _FakeAuth(),
        settings: _FakeSettingsStore(const {}),
      );
      await controller.initialize();

      await controller.send(
        question: '關於創世記 1:1：天地是甚麼意思？',
        scriptureContext: '創世記 1:1\n中文：起初神創造天地。',
        scriptureReference: '創世記 1:1',
      );
      await controller.send(
        question: '請解釋創世記 1:1',
        scriptureContext: '創世記 1:1\n中文：起初神創造天地。',
        scriptureReference: '創世記 1:1',
        kind: 'explanation',
      );

      expect(model.prompts, hasLength(2));
      expect(model.prompts.first, contains('起初神創造天地'));
      expect(model.prompts.last, contains('請解釋創世記 1:1'));
      expect(controller.messages.map((message) => message.kind), [
        'chat',
        'chat',
        'explanation',
        'explanation',
      ]);
      expect(memory.records.last.kind, 'explanation');
      expect(memory.records.last.scripture, '創世記 1:1');
    },
  );

  test('reasoning keeps analysis but removes provider safety metadata', () {
    expect(
      sanitizeReasoningForDisplay('User Safety: Safe\n先確認問題所指的經文，再比較上下文。'),
      '先確認問題所指的經文，再比較上下文。',
    );
    expect(sanitizeReasoningForDisplay('User Saf'), isEmpty);
    expect(sanitizeReasoningForDisplay('安全是這段經文的重要主題。'), '安全是這段經文的重要主題。');
  });

  test('reasoning streams visibly without exposing safety metadata', () async {
    final model = _ReasoningModel();
    final controller = BibleAiController(
      model: model,
      memory: _FakeMemoryStore(),
      auth: _FakeAuth(),
      settings: _FakeSettingsStore(const {}),
    );
    await controller.initialize();

    final sending = controller.send(question: '請分析');
    await model.reasoningPublished.future;

    expect(controller.messages.last.role, 'assistant');
    expect(controller.messages.last.reasoning, '先看上下文。');
    expect(controller.messages.last.reasoning, isNot(contains('Safety')));

    model.release.complete();
    await sending;
    expect(controller.messages.last.text, '正式回答');
    expect(controller.messages.last.reasoning, '先看上下文。');
  });

  test(
    'provider stop finish reason completes an answer without END marker',
    () async {
      final model = _FinishReasonModel();
      final controller = BibleAiController(
        model: model,
        memory: _FakeMemoryStore(),
        auth: _FakeAuth(),
        settings: _FakeSettingsStore(const {}),
      );
      await controller.initialize();

      final answer = await controller.send(question: '正常回答');

      expect(answer, '正常完成的回答');
      expect(model.prompts, hasLength(1));
      expect(controller.messages.last.incomplete, isFalse);
    },
  );

  test(
    'both scripture actions dispatch complete Android activity payloads',
    () async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      addTearDown(() => debugDefaultTargetPlatformOverride = null);
      const channel = MethodChannel('bible/android');
      final calls = <MethodCall>[];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            calls.add(call);
            return null;
          });
      addTearDown(
        () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(channel, null),
      );
      final controller = BibleAiController(
        model: _FakeModel(),
        memory: _FakeMemoryStore(),
        auth: _FakeAuth(),
        settings: _FakeSettingsStore(const {}),
      );

      await controller.openChatActivity(
        initialQuestion: '關於創世記 1:1：',
        scriptureContext: 'full chapter',
        scriptureReference: '創世記 1:1',
      );
      await controller.openChatActivity(
        initialQuestion: '請解釋創世記 1:1',
        scriptureContext: 'full chapter',
        scriptureReference: '創世記 1:1',
        scriptureAttachment: '創世記 1:1\n起初神創造天地。',
        autoSend: true,
      );

      expect(calls.map((call) => call.method), [
        'openAiChatActivity',
        'openAiChatActivity',
      ]);
      expect((calls.first.arguments as Map)['autoSend'], isFalse);
      expect((calls.last.arguments as Map), {
        'initialQuestion': '請解釋創世記 1:1',
        'scriptureContext': 'full chapter',
        'scriptureReference': '創世記 1:1',
        'scriptureAttachment': '創世記 1:1\n起初神創造天地。',
        'autoSend': true,
      });
    },
  );

  test(
    'missing completion marker continues until END and strips markers',
    () async {
      final model = _FakeModel(
        responses: ['第一段 [[MORE]]', '第二段仍未完成', '最後一段 [[END]]'],
      );
      final controller = BibleAiController(
        model: model,
        memory: _FakeMemoryStore(),
        auth: _FakeAuth(),
        settings: _FakeSettingsStore(const {}),
      );
      await controller.initialize();

      final answer = await controller.send(question: '請完整回答');

      expect(answer, '第一段\n\n第二段仍未完成\n\n最後一段');
      expect(model.prompts, hasLength(3));
      expect(controller.messages.last.incomplete, isFalse);
      expect(controller.messages.last.text, isNot(contains('[[END]]')));
    },
  );

  test(
    'continuation cap preserves partial response and marks it incomplete',
    () async {
      final model = _FakeModel(responses: ['一', '二', '三']);
      final controller = BibleAiController(
        model: model,
        memory: _FakeMemoryStore(),
        auth: _FakeAuth(),
        settings: _FakeSettingsStore(const {}),
      );
      await controller.initialize();

      await controller.send(question: '長回答');

      expect(model.prompts, hasLength(3));
      expect(controller.messages.last.incomplete, isTrue);
    },
  );

  test('a restarted continuation replaces the duplicate opening', () async {
    final model = _FakeModel(
      responses: ['**重點**\n第一段內容，仍未完成。', '**重點**\n重新整理後的完整回答，並提供結論。 [[END]]'],
    );
    final controller = BibleAiController(
      model: model,
      memory: _FakeMemoryStore(),
      auth: _FakeAuth(),
      settings: _FakeSettingsStore(const {}),
    );
    await controller.initialize();

    final answer = await controller.send(question: '請完整回答');

    expect(answer, '**重點**\n重新整理後的完整回答，並提供結論。');
    expect('**重點**'.allMatches(answer), hasLength(1));
    expect(controller.messages.last.incomplete, isFalse);
  });

  test(
    'a stream interrupted after content continues from the partial answer',
    () async {
      final model = _InterruptedModel();
      final controller = BibleAiController(
        model: model,
        memory: _FakeMemoryStore(),
        auth: _FakeAuth(),
        settings: _FakeSettingsStore(const {}),
      );
      await controller.initialize();

      final answer = await controller.send(question: '不要由頭回答');

      expect(answer, '第一段，接續完成。');
      expect(model.prompts, hasLength(2));
      expect(controller.messages.last.incomplete, isFalse);
    },
  );

  test('AI search uses independent overview and scripture requests', () async {
    final model = _FakeModel(
      responses: [
        '呢個係獨立概覽。',
        '{"scriptures":[{"bookId":"JHN","chapter":3,"verseStart":16,"verseEnd":16,"reason":"談及神的愛"}],"suggestedQuestions":["愛有甚麼意思？"]}',
      ],
    );
    final controller = BibleAiController(
      model: model,
      memory: _FakeMemoryStore(),
      auth: _FakeAuth(),
      settings: _FakeSettingsStore(const {}),
    );
    await controller.initialize();
    String? publishedOverview;
    AiSearchReferences? publishedReferences;

    final result = await controller.search(
      '神的愛',
      onOverview: (value) => publishedOverview = value,
      onReferences: (value) => publishedReferences = value,
    );

    expect(model.prompts, hasLength(2));
    expect(model.prompts.first, contains('BIBLE_SEARCH_OVERVIEW'));
    expect(model.prompts.last, contains('BIBLE_SEARCH_REFERENCES_JSON'));
    expect(publishedOverview, '呢個係獨立概覽。');
    expect(publishedReferences?.scriptures.single.bookId, 'JHN');
    expect(result.overview, publishedOverview);
    expect(result.scriptures.single.verseStart, 16);
  });

  test('AI overview removes thinking tags before publishing', () async {
    final model = _FakeModel(
      responses: [
        '<think>先分析關鍵字同相關主題。</think>\n神的愛貫穿救恩。',
        '{"scriptures":[],"suggestedQuestions":[]}',
      ],
    );
    final controller = BibleAiController(
      model: model,
      memory: _FakeMemoryStore(),
      auth: _FakeAuth(),
      settings: _FakeSettingsStore(const {}),
    );
    await controller.initialize();

    final result = await controller.search('神的愛');

    expect(result.overview, '神的愛貫穿救恩。');
    expect(result.overview, isNot(contains('分析')));
  });

  test('web search is optional rather than a required preflight', () async {
    final model = _FakeModel(responses: ['網頁回答 [[END]]']);
    final controller = BibleAiController(
      model: model,
      memory: _FakeMemoryStore(),
      auth: _FakeAuth(),
      settings: _FakeSettingsStore(const {}),
    );
    await controller.initialize();

    await controller.send(question: '最近有甚麼發現？');

    expect(
      model.prompts.single,
      isNot(contains('A required web research pass has already run')),
    );
    expect(model.prompts.single, contains('A web-search tool is available'));
    expect(controller.messages.first.webSearch, isTrue);
    expect(controller.messages.last.webSearch, isTrue);
  });

  test(
    'optional web tool failure retries the answer without preflight',
    () async {
      final model = _OptionalWebSearchModel();
      final controller = BibleAiController(
        model: model,
        memory: _FakeMemoryStore(),
        auth: _FakeAuth(),
        settings: _FakeSettingsStore(const {}),
      );
      await controller.initialize();

      final answer = await controller.send(question: '請解釋恩典');

      expect(model.researchCalls, 0);
      expect(model.webSearchModes, [
        OpenRouterWebSearch.automatic,
        OpenRouterWebSearch.disabled,
      ]);
      expect(answer, '完成回答');
      expect(controller.generationError, isNull);
    },
  );

  test(
    'citation markers and generated source lists never enter the transcript',
    () async {
      final model = _FakeModel(
        responses: [
          '答案內容 [1] citeturn0search0\n\n**來源**\n- https://example.com [[END]]',
        ],
      );
      final controller = BibleAiController(
        model: model,
        memory: _FakeMemoryStore(),
        auth: _FakeAuth(),
        settings: _FakeSettingsStore(const {}),
      );
      await controller.initialize();

      final answer = await controller.send(question: '請回答');

      expect(answer, '答案內容');
      expect(controller.messages.last.text, '答案內容');
      expect(answer, isNot(contains('來源')));
      expect(answer, isNot(contains('example.com')));
      expect(answer, isNot(contains('cite')));
    },
  );
}

class _FakeModel implements BibleAiModel {
  _FakeModel({List<String>? responses})
    : _responses = responses ?? const ['answer [[END]]'];

  final List<String> _responses;
  final List<String> prompts = [];
  int _responseIndex = 0;

  @override
  Future<AiAvailability> availability() async => AiAvailability.ready;

  @override
  Future<void> downloadModel() async {}

  @override
  Future<String> generate(String prompt) async {
    prompts.add(prompt);
    return _responses[_responseIndex++];
  }

  @override
  Stream<String> generateStream(String prompt) async* {
    yield await generate(prompt);
  }
}

class _InterruptedModel implements BibleAiModel {
  final List<String> prompts = [];
  int calls = 0;

  @override
  Future<AiAvailability> availability() async => AiAvailability.ready;

  @override
  Future<void> downloadModel() async {}

  @override
  Future<String> generate(String prompt) async =>
      (await generateStream(prompt).join());

  @override
  Stream<String> generateStream(String prompt) async* {
    prompts.add(prompt);
    if (calls++ == 0) {
      yield '第一段';
      throw StateError('connection interrupted');
    }
    yield '，接續完成。 [[END]]';
  }
}

class _OptionalWebSearchModel implements BibleAiModel, WebResearchBibleAiModel {
  int researchCalls = 0;
  final List<OpenRouterWebSearch> webSearchModes = [];

  @override
  Future<AiAvailability> availability() async => AiAvailability.ready;

  @override
  Future<void> downloadModel() async {}

  @override
  Future<String> generate(String prompt) => generateStream(prompt).join();

  @override
  Stream<String> generateStream(String prompt) =>
      generateStreamWithOptions(prompt, const OpenRouterRequestOptions());

  @override
  Stream<String> generateStreamWithOptions(
    String prompt,
    OpenRouterRequestOptions options,
  ) async* {
    webSearchModes.add(options.webSearch);
    if (options.webSearch == OpenRouterWebSearch.automatic) {
      throw StateError('tool not supported by routed model');
    }
    yield '完成回答 [[END]]';
  }

  @override
  Future<WebResearchResponse> research(String prompt) async {
    researchCalls++;
    return const WebResearchResponse(
      summary: 'unused',
      sources: [],
      searchRequests: 1,
    );
  }
}

class _ReasoningModel implements BibleAiModel, ReasoningBibleAiModel {
  final Completer<void> reasoningPublished = Completer<void>();
  final Completer<void> release = Completer<void>();

  @override
  void Function(String delta)? onReasoning;

  @override
  Future<AiAvailability> availability() async => AiAvailability.ready;

  @override
  Future<void> downloadModel() async {}

  @override
  Future<String> generate(String prompt) => generateStream(prompt).join();

  @override
  Stream<String> generateStream(String prompt) async* {
    onReasoning?.call('User Safety: Safe\n先看上下文。');
    await Future<void>.delayed(const Duration(milliseconds: 70));
    reasoningPublished.complete();
    await release.future;
    yield '正式回答 [[END]]';
  }
}

class _FinishReasonModel implements BibleAiModel, FinishReasonBibleAiModel {
  final List<String> prompts = [];

  @override
  void Function(String reason)? onFinishReason;

  @override
  Future<AiAvailability> availability() async => AiAvailability.ready;

  @override
  Future<void> downloadModel() async {}

  @override
  Future<String> generate(String prompt) => generateStream(prompt).join();

  @override
  Stream<String> generateStream(String prompt) async* {
    prompts.add(prompt);
    yield '正常完成的回答';
    onFinishReason?.call('stop');
  }
}

class _FakeAuth extends OpenRouterAuth {
  @override
  Future<void> initialize() async {}

  @override
  Future<bool> get isSignedIn async => false;
}

class _FakeSettingsStore implements AiSettingsStore {
  _FakeSettingsStore(Map<String, String> values) : _values = Map.of(values);

  final Map<String, String> _values;

  @override
  Future<String?> read(String key) async => _values[key];

  @override
  Future<void> write(String key, String value) async => _values[key] = value;
}

class _MemoryRecord {
  const _MemoryRecord(this.kind, this.scripture);

  final String kind;
  final String? scripture;
}

class _FakeMemoryStore extends AiMemoryStore {
  _FakeMemoryStore({bool blockInitialization = false})
    : _initialization = blockInitialization ? Completer<void>() : null;

  final Completer<void>? _initialization;
  final List<_MemoryRecord> records = [];

  void finishInitialization() => _initialization?.complete();

  @override
  Future<void> initialize() => _initialization?.future ?? Future<void>.value();

  @override
  Future<String> promptMemory({int maxCharacters = 7000}) async =>
      '# Test memory';

  @override
  Future<List<Map<String, dynamic>>> transcript({int limit = 24}) async => [];

  @override
  Future<void> recordMessage({
    required String role,
    required String text,
    String? scripture,
    String kind = 'chat',
  }) async {
    records.add(_MemoryRecord(kind, scripture));
  }
}
