import 'dart:async';

import 'package:bible/ai_chat_page.dart';
import 'package:bible/ai_memory_store.dart';
import 'package:bible/ai_service.dart';
import 'package:bible/app_settings.dart';
import 'package:bible/app_theme.dart';
import 'package:bible/app_ui.dart';
import 'package:bible/openrouter_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

void main() {
  testWidgets(
    'short Chinese user message stays on one line on the real chat page',
    (tester) async {
      _phone(tester);
      final controller = _controller();
      await controller.initialize();
      await controller.send(question: '你好');

      await tester.pumpWidget(_testApp(AiChatPage(aiController: controller)));
      await _pumpPage(tester);

      final size = tester.getSize(
        find.byKey(const ValueKey('ai-message-0-user')),
      );
      expect(size.width, greaterThan(45));
      expect(size.width, lessThan(80));
      expect(size.height, lessThanOrEqualTo(50));
      final textSize = tester.getSize(find.text('你好'));
      expect(textSize.width, greaterThan(textSize.height));
      expect(textSize.height, lessThan(24));
    },
  );

  testWidgets('assistant Markdown uses the app typeface', (tester) async {
    _phone(tester);
    final controller = _controller(response: '**重點**：起初神創造天地。 [[END]]');
    await controller.initialize();
    await controller.send(question: '請解釋');

    await tester.pumpWidget(_testApp(AiChatPage(aiController: controller)));
    await _pumpPage(tester);

    final richText = find
        .byType(RichText)
        .evaluate()
        .map((element) => element.widget as RichText)
        .firstWhere((widget) => widget.text.toPlainText().contains('重點'));
    expect(richText.text.style?.fontFamily, 'OpenRunde');
    final assistant = tester.widget<Container>(
      find
          .descendant(
            of: find.byKey(const ValueKey('ai-message-1-assistant')),
            matching: find.byType(Container),
          )
          .first,
    );
    expect(assistant.decoration, isA<BoxDecoration>());
  });

  testWidgets('streaming assistant content renders tolerant Markdown live', (
    tester,
  ) async {
    _phone(tester);
    final model = _StreamingModel();
    final controller = BibleAiController(
      model: model,
      memory: _FakeMemoryStore(),
      auth: _FakeAuth(false),
      settings: _FakeSettingsStore(const {}),
    );
    await controller.initialize();
    await tester.pumpWidget(_testApp(AiChatPage(aiController: controller)));
    await _pumpPage(tester);
    expect(tester.testTextInput.isVisible, isTrue);

    final sending = controller.send(question: '即時輸出');
    await model.firstChunk.future;
    await tester.pump(const Duration(milliseconds: 80));

    final streamingText = find
        .byType(RichText)
        .evaluate()
        .map((element) => element.widget as RichText)
        .firstWhere((widget) => widget.text.toPlainText().contains('串流粗體'));
    final root = streamingText.text as TextSpan;
    final strong = root.children!.whereType<TextSpan>().firstWhere(
      (span) => span.text?.contains('串流粗體') == true,
    );
    expect(strong.style?.fontWeight, FontWeight.w700);

    model.release.complete();
    await sending;
    await tester.pump();
    expect(controller.messages.last.text, '**串流粗體**');
  });

  testWidgets('reasoning streams with animation and hides safety metadata', (
    tester,
  ) async {
    _phone(tester);
    final model = _UiReasoningModel();
    final controller = BibleAiController(
      model: model,
      memory: _FakeMemoryStore(),
      auth: _FakeAuth(false),
      settings: _FakeSettingsStore(const {}),
    );
    await controller.initialize();
    await tester.pumpWidget(_testApp(AiChatPage(aiController: controller)));
    await _pumpPage(tester);

    final sending = controller.send(question: '請分析');
    await model.reasoningPublished.future;
    try {
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.text('正在思考'), findsOneWidget);
      expect(find.text('先看上下文。', findRichText: true), findsOneWidget);
      expect(
        find.textContaining('User Safety', findRichText: true),
        findsNothing,
      );
      expect(find.byKey(const ValueKey('thinking-dot-0')), findsOneWidget);
      final firstOpacity = tester.widget<Opacity>(
        find.byKey(const ValueKey('thinking-dot-0')),
      );
      await tester.pump(const Duration(milliseconds: 180));
      final laterOpacity = tester.widget<Opacity>(
        find.byKey(const ValueKey('thinking-dot-0')),
      );
      expect(laterOpacity.opacity, isNot(firstOpacity.opacity));
    } finally {
      if (!model.release.isCompleted) model.release.complete();
      await sending;
    }
    await tester.pump(const Duration(milliseconds: 250));
    expect(find.text('思考內容'), findsOneWidget);
    expect(find.text('先看上下文。', findRichText: true), findsOneWidget);

    await tester.tap(find.bySemanticsLabel('收起思考內容'));
    await tester.pump(const Duration(milliseconds: 250));
    expect(find.text('先看上下文。', findRichText: true), findsNothing);
  });

  testWidgets('reasoning remains visible when answer text arrives first', (
    tester,
  ) async {
    _phone(tester);
    final model = _LateReasoningModel();
    final controller = BibleAiController(
      model: model,
      memory: _FakeMemoryStore(),
      auth: _FakeAuth(false),
      settings: _FakeSettingsStore(const {}),
    );
    await controller.initialize();
    await tester.pumpWidget(_testApp(AiChatPage(aiController: controller)));
    await _pumpPage(tester);

    final sending = controller.send(question: '請先回答再思考');
    await model.reasoningPublished.future;
    try {
      await tester.pump(const Duration(milliseconds: 100));

      expect(find.text('初步回答。', findRichText: true), findsOneWidget);
      expect(find.text('補充推理。', findRichText: true), findsOneWidget);
      expect(find.text('正在思考'), findsOneWidget);
      expect(
        tester.getRect(find.text('補充推理。', findRichText: true)).height,
        greaterThan(0),
      );
      expect(
        find.textContaining('User Safety', findRichText: true),
        findsNothing,
      );
    } finally {
      if (!model.release.isCompleted) model.release.complete();
      await sending;
    }
  });

  testWidgets('latest reply button returns a scrolled conversation to bottom', (
    tester,
  ) async {
    _phone(tester, size: const Size(390, 640));
    final controller = _controller();
    await controller.initialize();
    for (var index = 0; index < 9; index++) {
      await controller.send(question: '問題 $index');
    }

    await tester.pumpWidget(_testApp(AiChatPage(aiController: controller)));
    await _pumpPage(tester);
    final list = tester.widget<ListView>(
      find.byKey(const ValueKey('ai-message-list')),
    );
    expect(list.controller!.position.maxScrollExtent, greaterThan(0));

    list.controller!.jumpTo(list.controller!.position.maxScrollExtent);
    await tester.pump();
    expect(find.bySemanticsLabel('前往最新回覆'), findsOneWidget);

    await tester.tap(find.bySemanticsLabel('前往最新回覆'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 320));
    expect(list.controller!.offset, closeTo(0, 1));
    expect(find.bySemanticsLabel('前往最新回覆'), findsNothing);
  });

  testWidgets('OpenRouter connection status is a compact settings row', (
    tester,
  ) async {
    _phone(tester);
    final controller = _controller(signedIn: true);
    await controller.initialize();

    await tester.pumpWidget(_testApp(AiSettingsPage(aiController: controller)));
    await _pumpPage(tester);

    expect(find.text('已安全連接'), findsOneWidget);
    expect(
      tester
          .getSize(find.byKey(const ValueKey('openrouter-connection-status')))
          .height,
      42,
    );
  });

  testWidgets('web research stays implicit and sources stay hidden', (
    tester,
  ) async {
    _phone(tester);
    final controller = _controller();
    await controller.initialize();

    await tester.pumpWidget(_testApp(AiChatPage(aiController: controller)));
    await _pumpPage(tester);

    expect(find.byKey(const ValueKey('web-search-attachment')), findsNothing);
    expect(find.byKey(const ValueKey('web-search-toggle')), findsNothing);
    expect(find.byIcon(LucideIcons.earth), findsNothing);

    await tester.enterText(find.byType(EditableText), '最近有甚麼發現？');
    await tester.tap(find.bySemanticsLabel('送出'));
    await tester.pumpAndSettle();

    expect(controller.messages.first.webSearch, isTrue);
    expect(controller.messages.last.webSearch, isTrue);
    expect(find.text('已搜尋網頁'), findsNothing);
    expect(find.byKey(const ValueKey('web-search-attachment')), findsNothing);
  });

  testWidgets('settings navigation and clear confirmation complete normally', (
    tester,
  ) async {
    _phone(tester);
    final memory = _FakeMemoryStore();
    final controller = _controller(memory: memory);
    await controller.initialize();
    await controller.send(question: '保留到確認清除');

    await tester.pumpWidget(_testApp(AiChatPage(aiController: controller)));
    await _pumpPage(tester);

    expect(find.byIcon(LucideIcons.settings), findsOneWidget);
    await tester.tap(find.bySemanticsLabel('設定'));
    await _pumpPage(tester);
    expect(find.text('設定'), findsOneWidget);
    expect(find.byType(EditableText), findsOneWidget);
    expect(tester.takeException(), isNull);

    await tester.tap(find.byIcon(LucideIcons.arrowLeft));
    await _pumpPage(tester);
    await tester.tap(find.bySemanticsLabel('設定'));
    await _pumpPage(tester);
    // The layout section added above the danger zone pushes it off-screen;
    // drag the settings list until the tile clears the bottom edge.
    final tile = find.widgetWithText(ListTile, '清除對話');
    final settingsList = find
        .descendant(
          of: find.byType(AiSettingsPage),
          matching: find.byType(Scrollable),
        )
        .first;
    for (var i = 0; i < 6 && tester.getRect(tile).bottom > 780; i++) {
      await tester.drag(settingsList, const Offset(0, -220));
      await tester.pump(const Duration(milliseconds: 250));
    }
    expect(tester.getRect(tile).bottom, lessThanOrEqualTo(844));
    await tester.tap(tile);
    await _pumpPage(tester);
    expect(find.text('清除對話？'), findsOneWidget);

    await tester.tap(find.text('清除'));
    await _pumpPage(tester);
    expect(controller.messages, isEmpty);
    expect(memory.cleared, isTrue);
    expect(tester.takeException(), isNull);
  });

  testWidgets('AI pages remain stable on a narrow large-text viewport', (
    tester,
  ) async {
    _phone(tester, size: const Size(320, 640), textScaleFactor: 1.5);
    final controller = _controller(signedIn: true);
    await controller.initialize();

    await tester.pumpWidget(_testApp(AiChatPage(aiController: controller)));
    await _pumpPage(tester);
    expect(find.text('Bible AI'), findsNothing);
    expect(tester.takeException(), isNull);

    await tester.tap(find.bySemanticsLabel('設定'));
    await _pumpPage(tester);
    expect(find.text('設定'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('AI controls are solid with frosted top and bottom docks', (
    tester,
  ) async {
    _phone(tester);
    final controller = _controller(signedIn: true);
    final settings = _appSettings(locale: AppLocale.en);
    await controller.initialize();

    await tester.pumpWidget(
      _testApp(
        AppSettingsScope(
          controller: settings,
          child: AiChatPage(
            aiController: controller,
            settingsController: settings,
          ),
        ),
      ),
    );
    await _pumpPage(tester);

    // All control surfaces now honor the navbar blur setting (blur → translucent + BackdropFilter).
    expect(find.byType(BackdropFilter), findsAtLeastNWidgets(1));
    expect(find.byType(AppFrostedEdgeBackdrop), findsNothing);
    final sendDecoration = tester
        .widgetList<DecoratedBox>(
          find.descendant(
            of: find.byKey(const ValueKey('ai-send-control')),
            matching: find.byType(DecoratedBox),
          ),
        )
        .map((box) => box.decoration as BoxDecoration)
        .firstWhere((decoration) => decoration.color != null);
    expect(sendDecoration.color?.a, lessThan(1));

    await tester.tap(find.bySemanticsLabel('設定'));
    await _pumpPage(tester);
    expect(find.text('中文'), findsOneWidget);
    expect(find.text('English'), findsOneWidget);
    expect(find.text('Liquid Glass'), findsNothing);
    expect(find.byType(Switch), findsNothing);
  });

  testWidgets('composer follows asymmetric device corners and IME inset', (
    tester,
  ) async {
    _phone(tester, size: const Size(390, 844));
    tester.view.viewInsets = const FakeViewPadding(bottom: 280);
    addTearDown(() => tester.view.resetViewInsets());
    final controller = _controller(signedIn: true);
    await controller.initialize();

    await tester.pumpWidget(
      _testApp(
        AiChatPage(aiController: controller),
        radii: const AppRadii(
          compact: 8,
          control: 12,
          surface: 18,
          screen: 44,
          topLeft: 44,
          topRight: 40,
          bottomLeft: 12,
          bottomRight: 44,
        ),
      ),
    );
    await _pumpPage(tester);

    final composer = tester.getRect(
      find.byKey(const ValueKey('ai-composer-input')),
    );
    final send = tester.getRect(find.byKey(const ValueKey('ai-send-button')));
    expect(composer.left, closeTo(10, 1));
    expect(390 - send.right, greaterThanOrEqualTo(10));
    expect(send.bottom, lessThanOrEqualTo(844 - 280 - 10));
    final sendSurface = tester.widget<AppControlSurface>(
      find.byKey(const ValueKey('ai-send-control')),
    );
    final input = tester.widget<AppTextInput>(
      find.byKey(const ValueKey('ai-composer-input')),
    );
    expect(sendSurface.radius, closeTo(14.96, .01));
    expect(input.radius, closeTo(12, .01));
  });
}

Future<void> _pumpPage(WidgetTester tester) async {
  for (var frame = 0; frame < 8; frame++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
  expect(tester.takeException(), isNull);
}

void _phone(
  WidgetTester tester, {
  Size size = const Size(390, 844),
  double textScaleFactor = 1,
}) {
  const android = MethodChannel('bible/android');
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(android, (_) async => null);
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  tester.platformDispatcher.textScaleFactorTestValue = textScaleFactor;
  addTearDown(() {
    tester.view.reset();
    tester.platformDispatcher.clearTextScaleFactorTestValue();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(android, null);
  });
}

BibleAiController _controller({
  AiAvailability availability = AiAvailability.ready,
  bool signedIn = false,
  String response = 'answer [[END]]',
  AiMemoryStore? memory,
}) => BibleAiController(
  model: _FakeModel(availability, response),
  memory: memory ?? _FakeMemoryStore(),
  auth: _FakeAuth(signedIn),
  settings: _FakeSettingsStore(const {}),
);

AppSettingsController _appSettings({AppLocale locale = AppLocale.zhHant}) {
  final controller = AppSettingsController();
  controller.state = AppSettingsState(
    themeMode: ThemeMode.light,
    locale: locale,
  );
  controller.initialized = true;
  return controller;
}

Widget _testApp(Widget child, {AppRadii radii = AppRadii.fallback}) =>
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        fontFamily: 'OpenRunde',
        scaffoldBackgroundColor: AppColors.light.canvas,
        extensions: [AppColors.light, radii],
      ),
      builder: (context, child) => Material(
        type: MaterialType.transparency,
        child: child ?? const SizedBox.shrink(),
      ),
      home: ColoredBox(color: AppColors.light.canvas, child: child),
    );

class _FakeModel implements BibleAiModel {
  const _FakeModel(this.status, this.response);

  final AiAvailability status;
  final String response;

  @override
  Future<AiAvailability> availability() async => status;

  @override
  Future<void> downloadModel() async {}

  @override
  Future<String> generate(String prompt) async => response;

  @override
  Stream<String> generateStream(String prompt) async* {
    yield response;
  }
}

class _StreamingModel implements BibleAiModel {
  final Completer<void> firstChunk = Completer<void>();
  final Completer<void> release = Completer<void>();

  @override
  Future<AiAvailability> availability() async => AiAvailability.ready;

  @override
  Future<void> downloadModel() async {}

  @override
  Future<String> generate(String prompt) => generateStream(prompt).join();

  @override
  Stream<String> generateStream(String prompt) async* {
    yield '**串流粗體';
    firstChunk.complete();
    await release.future;
    yield '** [[END]]';
  }
}

class _UiReasoningModel implements BibleAiModel, ReasoningBibleAiModel {
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
    reasoningPublished.complete();
    await release.future;
    yield '正式回答 [[END]]';
  }
}

class _LateReasoningModel implements BibleAiModel, ReasoningBibleAiModel {
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
    yield '初步回答。';
    onReasoning?.call('User Safety: Safe\n補充推理。');
    reasoningPublished.complete();
    await release.future;
    yield '完成 [[END]]';
  }
}

class _FakeAuth extends OpenRouterAuth {
  _FakeAuth(this.signedIn);

  final bool signedIn;

  @override
  Future<void> initialize() async {}

  @override
  Future<bool> get isSignedIn async => signedIn;
}

class _FakeSettingsStore implements AiSettingsStore {
  _FakeSettingsStore(Map<String, String> values) : values = Map.of(values);

  final Map<String, String> values;

  @override
  Future<String?> read(String key) async => values[key];

  @override
  Future<void> write(String key, String value) async => values[key] = value;
}

class _FakeMemoryStore extends AiMemoryStore {
  bool cleared = false;

  @override
  Future<void> initialize() async {}

  @override
  Future<String> promptMemory({int maxCharacters = 7000}) async => '';

  @override
  Future<List<Map<String, dynamic>>> transcript({int limit = 24}) async => [];

  @override
  Future<void> recordMessage({
    required String role,
    required String text,
    String? scripture,
    String kind = 'chat',
  }) async {}

  @override
  Future<void> clear() async {
    cleared = true;
  }
}
