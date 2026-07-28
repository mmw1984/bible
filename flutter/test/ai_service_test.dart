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
        'ai_provider': 'openRouter',
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
      expect(controller.provider, AiProvider.openRouter);

      memory.finishInitialization();
      await first;

      expect(controller.initialized, isTrue);
      expect(controller.provider, AiProvider.openRouter);
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
