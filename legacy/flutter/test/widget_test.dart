import 'dart:ui' as ui;

import 'package:bible/ai_memory_store.dart';
import 'package:bible/ai_service.dart';
import 'package:bible/app_settings.dart';
import 'package:bible/app_theme.dart';
import 'package:bible/app_ui.dart';
import 'package:bible/bible_data.dart';
import 'package:bible/l10n/app_localizations.dart';
import 'package:bible/main.dart';
import 'package:bible/openrouter_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  testWidgets('reader exposes core controls', (tester) async {
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(_readerApp());
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 600));

    expect(find.text('創世記'), findsOneWidget);
    expect(find.text('中文'), findsOneWidget);
    expect(find.text('英文'), findsOneWidget);
    expect(find.text('雙語'), findsOneWidget);
    expect(find.bySemanticsLabel('選擇書卷'), findsOneWidget);
    expect(find.bySemanticsLabel('搜尋全本聖經'), findsOneWidget);
  });

  testWidgets('reader controls sit above frosted top and bottom edges', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    _phone(tester);
    final settings = AppSettingsController();

    await tester.pumpWidget(_readerApp(settingsController: settings));
    await _pumpReader(tester);

    // Menu + search use the shared glyph button; settings uses a Lucide icon.
    expect(find.byType(AppGlyphButton), findsAtLeastNWidgets(2));
    expect(find.byIcon(Icons.settings), findsNothing);
    expect(
      find.byWidgetPredicate(
        (widget) => widget is Icon && widget.icon == LucideIcons.settings,
      ),
      findsOneWidget,
    );
    // All control surfaces honor the navbar blur setting; the nav pill plus segmented/button surfaces blur together.
    expect(find.byType(BackdropFilter), findsAtLeastNWidgets(1));
    expect(find.byType(AppFrostedEdgeBackdrop), findsNothing);
    final surfaces = tester.widgetList<AppControlSurface>(
      find.byType(AppControlSurface),
    );
    expect(surfaces, isNotEmpty);
  });

  testWidgets('reader status bar stays opaque and disables contrast scrims', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    _phone(tester);
    final settings = AppSettingsController();

    await tester.pumpWidget(_readerApp(settingsController: settings));
    await _pumpReader(tester);

    void expectReaderChrome() {
      final overlay = tester.widget<AnnotatedRegion<SystemUiOverlayStyle>>(
        find.byKey(const ValueKey('reader-system-ui-overlay')),
      );
      expect(overlay.value.systemStatusBarContrastEnforced, isFalse);
      expect(overlay.value.systemNavigationBarContrastEnforced, isFalse);
    }

    expectReaderChrome();

    await settings.setThemeMode(ThemeMode.dark);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    expectReaderChrome();
  });

  testWidgets('settings switch app language and reader book names live', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    _phone(tester);
    final settings = AppSettingsController();
    final controller = _aiController(_RecordingModel());
    await controller.initialize();

    await tester.pumpWidget(
      _readerApp(aiController: controller, settingsController: settings),
    );
    await _pumpReader(tester);
    expect(find.text('創世記'), findsOneWidget);

    await tester.tap(find.bySemanticsLabel('設定'));
    await tester.pumpAndSettle();
    await tester.tap(find.bySemanticsLabel('深色'));
    await tester.pumpAndSettle();
    expect(settings.state.themeMode, ThemeMode.dark);
    expect(
      Theme.of(tester.element(find.text('外觀'))).brightness,
      Brightness.dark,
    );
    await tester.tap(find.bySemanticsLabel('English'));
    await tester.pumpAndSettle();

    expect(settings.state.locale, AppLocale.en);
    expect(find.text('Settings'), findsOneWidget);
    expect(find.text('Appearance'), findsOneWidget);
    await tester.tap(find.bySemanticsLabel('Back'));
    await tester.pumpAndSettle();
    await _pumpReader(tester);
    expect(find.text('Genesis'), findsOneWidget);
    expect(find.bySemanticsLabel('Select book'), findsOneWidget);
  });

  testWidgets('mobile chapter number opens picker and changes chapter', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(_readerApp());
    await _pumpReader(tester);

    await tester.tap(find.byKey(const ValueKey('chapter-anchor')));
    await tester.pumpAndSettle();
    expect(find.text('選擇章節'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('chapter-picker-2')));
    await tester.pumpAndSettle();
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('chapter-anchor')),
        matching: find.text('02'),
      ),
      findsOneWidget,
    );
    expect(find.text('選擇章節'), findsNothing);
  });

  testWidgets('library switches testament and opens the selected book', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    _phone(tester);
    final controller = _aiController(_RecordingModel());
    await controller.initialize();

    await tester.pumpWidget(_readerApp(aiController: controller));
    await _pumpReader(tester);
    await tester.tap(find.bySemanticsLabel('選擇書卷'));
    await tester.pumpAndSettle();

    expect(find.text('舊約 · 39'), findsOneWidget);
    expect(find.text('新約 · 27'), findsOneWidget);
    expect(_visibleLibraryBookCount(tester), 39);

    final newTestament = find.bySemanticsLabel('新約 · 27');
    final newRect = tester.getRect(newTestament);
    await tester.tapAt(Offset(newRect.center.dx, newRect.bottom - 3));
    await tester.pumpAndSettle();
    expect(_visibleLibraryBookCount(tester), 27);

    final oldTestament = find.bySemanticsLabel('舊約 · 39');
    final oldRect = tester.getRect(oldTestament);
    await tester.tapAt(Offset(oldRect.center.dx, oldRect.top + 3));
    await tester.pumpAndSettle();
    expect(_visibleLibraryBookCount(tester), 39);

    await tester.tap(newTestament);
    await tester.pumpAndSettle();
    await tester.tap(find.text('約翰福音'));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('JHN-chinese')), findsOneWidget);
    await tester.tap(find.bySemanticsLabel('選擇書卷'));
    await tester.pumpAndSettle();
    expect(_visibleLibraryBookCount(tester), 27);
    expect(
      tester
          .getSemantics(find.bySemanticsLabel('新約 · 27'))
          .flagsCollection
          .isSelected,
      ui.Tristate.isTrue,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('search empty state has normal explicit typography', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    _phone(tester);
    final controller = _aiController(_RecordingModel());
    await controller.initialize();

    await tester.pumpWidget(_readerApp(aiController: controller));
    await _pumpReader(tester);
    await tester.tap(find.bySemanticsLabel('搜尋全本聖經'));
    await tester.pumpAndSettle();

    final empty = find.text('輸入字詞、人物、事件或主題');
    final text = tester.widget<Text>(empty);
    expect(text.style?.fontSize, 14);
    expect(text.style?.fontWeight, FontWeight.w500);
    expect(text.textAlign, TextAlign.center);
    expect(
      DefaultTextStyle.of(tester.element(empty)).style.decoration,
      isNot(TextDecoration.underline),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('traditional search stays local and navigates to its result', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    _phone(tester);
    final model = _RecordingModel();
    final controller = _aiController(model);
    await controller.initialize();

    await tester.pumpWidget(_readerApp(aiController: controller));
    await _pumpReader(tester);
    await tester.tap(find.bySemanticsLabel('搜尋全本聖經'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));

    await tester.enterText(find.byType(EditableText), '神愛世人');
    await tester.tap(
      find.byWidgetPredicate(
        (widget) => widget is AppGlyphView && widget.glyph == AppGlyph.forward,
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    expect(find.text('傳統全文結果 · 1'), findsOneWidget);
    expect(model.prompts, isEmpty);
    await tester.tap(find.text('神愛世人').last);
    await tester.pumpAndSettle(const Duration(milliseconds: 50));
    expect(find.byKey(const ValueKey('JHN-3')), findsOneWidget);
    final target = find.text('Verse 22');
    expect(target, findsOneWidget);
    final targetRect = tester.getRect(target);
    expect(targetRect.top, greaterThanOrEqualTo(0));
    expect(targetRect.bottom, lessThanOrEqualTo(844));
    expect(find.bySemanticsLabel('返回搜尋前位置'), findsOneWidget);
  });

  testWidgets('search result can restore the exact reader origin', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    _phone(tester);
    final controller = _aiController(_RecordingModel());
    await controller.initialize();

    await tester.pumpWidget(_readerApp(aiController: controller));
    await _pumpReader(tester);
    final originScroll = tester.widget<CustomScrollView>(
      find.byType(CustomScrollView),
    );
    originScroll.controller!.jumpTo(412);
    await tester.pump();

    await _openTraditionalResult(tester);
    expect(find.byKey(const ValueKey('JHN-3')), findsOneWidget);
    expect(find.text('返回搜尋前位置'), findsOneWidget);
    expect(find.bySemanticsLabel('返回搜尋前位置'), findsOneWidget);

    await tester.tap(find.bySemanticsLabel('返回搜尋前位置'));
    await _pumpReader(tester);
    await tester.pump(const Duration(milliseconds: 500));
    expect(_chapterScroll('GEN-1'), findsOneWidget);
    final restored = tester.widget<CustomScrollView>(
      find.byType(CustomScrollView),
    );
    expect(restored.controller!.offset, closeTo(412, .01));
    expect(find.bySemanticsLabel('返回搜尋前位置'), findsNothing);
  });

  testWidgets('system back restores search origin', (tester) async {
    SharedPreferences.setMockInitialValues({});
    _phone(tester);
    final controller = _aiController(_RecordingModel());
    await controller.initialize();

    await tester.pumpWidget(_readerApp(aiController: controller));
    await _pumpReader(tester);
    final originScroll = tester.widget<CustomScrollView>(
      find.byType(CustomScrollView),
    );
    originScroll.controller!.jumpTo(288);
    await tester.pump();

    await _openTraditionalResult(tester);
    await tester.binding.handlePopRoute();
    await _pumpReader(tester);
    await tester.pump(const Duration(milliseconds: 500));

    expect(_chapterScroll('GEN-1'), findsOneWidget);
    final restored = tester.widget<CustomScrollView>(
      find.byType(CustomScrollView),
    );
    expect(restored.controller!.offset, closeTo(288, .01));
  });

  testWidgets('active chapter navigation clears the temporary origin', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    _phone(tester);
    final controller = _aiController(_RecordingModel());
    await controller.initialize();

    await tester.pumpWidget(_readerApp(aiController: controller));
    await _pumpReader(tester);
    await _openTraditionalResult(tester);
    expect(find.bySemanticsLabel('返回搜尋前位置'), findsOneWidget);

    await tester.scrollUntilVisible(
      find.bySemanticsLabel('下一章'),
      420,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.bySemanticsLabel('下一章'));
    await _pumpReader(tester);

    expect(_chapterScroll('JHN-4'), findsOneWidget);
    expect(find.bySemanticsLabel('返回搜尋前位置'), findsNothing);
  });

  testWidgets('reopening keeps search destination without temporary origin', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    _phone(tester);
    final controller = _aiController(_RecordingModel());
    await controller.initialize();

    await tester.pumpWidget(_readerApp(aiController: controller));
    await _pumpReader(tester);
    await _openTraditionalResult(tester);
    final destination = tester.widget<CustomScrollView>(
      _chapterScroll('JHN-3'),
    );
    final destinationOffset = destination.controller!.offset;
    expect(destinationOffset, greaterThan(0));

    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(milliseconds: 200));
    await tester.pumpWidget(_readerApp(aiController: controller));
    await _pumpReader(tester);

    final reopened = tester.widget<CustomScrollView>(_chapterScroll('JHN-3'));
    expect(reopened.controller!.offset, closeTo(destinationOffset, .01));
    expect(find.bySemanticsLabel('返回搜尋前位置'), findsNothing);
  });

  testWidgets('AI overview and scripture results use separate requests', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    _phone(tester);
    final model = _RecordingModel();
    final controller = _aiController(model);
    await controller.initialize();

    await tester.pumpWidget(_readerApp(aiController: controller));
    await _pumpReader(tester);
    await tester.tap(find.bySemanticsLabel('搜尋全本聖經'));
    await tester.pumpAndSettle();
    expect(
      tester.getTopLeft(find.byKey(const ValueKey('search-dialog-surface'))).dy,
      greaterThanOrEqualTo(10),
    );

    await tester.tap(find.bySemanticsLabel('AI 搜尋'));
    await tester.enterText(find.byType(EditableText), '神的愛');
    await tester.tap(
      find.byWidgetPredicate(
        (widget) => widget is AppGlyphView && widget.glyph == AppGlyph.forward,
      ),
    );
    await tester.pumpAndSettle();

    expect(model.prompts, hasLength(2));
    expect(model.prompts.first, contains('BIBLE_SEARCH_OVERVIEW'));
    expect(model.prompts.last, contains('BIBLE_SEARCH_REFERENCES_JSON'));
    expect(find.text('神的愛貫穿救恩。', findRichText: true), findsOneWidget);
    expect(find.text('AI 經文結果 · 1'), findsOneWidget);
  });

  testWidgets('verse actions dispatch ask and explanation payloads', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    _phone(tester);
    final model = _RecordingModel();
    final controller = _aiController(model);
    await controller.initialize();

    await tester.pumpWidget(_readerApp(aiController: controller));
    await _pumpReader(tester);
    await tester.longPress(find.text('Verse 1'));
    await tester.pumpAndSettle();
    expect(find.text('複製經文'), findsOneWidget);
    expect(find.text('問 AI'), findsOneWidget);
    expect(find.text('解釋經文'), findsOneWidget);

    await tester.tap(find.text('問 AI'));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('ai-scripture-attachment')),
      findsOneWidget,
    );
    expect(find.text('創世記 1:1'), findsWidgets);
    expect(tester.testTextInput.isVisible, isTrue);

    await tester.tap(find.bySemanticsLabel('返回'));
    await tester.pumpAndSettle();
    await tester.longPress(find.text('Verse 1'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('解釋經文'));
    await tester.pumpAndSettle();

    expect(find.textContaining('創世記 1:1'), findsWidgets);
    expect(model.prompts, hasLength(1));
    expect(model.prompts.single, contains('請解釋 創世記 1:1'));
    expect(model.prompts.single, contains('AUTHORITATIVE SCRIPTURE'));
    expect(model.prompts.single, contains('Verse 1'));
    expect(tester.takeException(), isNull);
  });

  testWidgets('reader restores the exact saved chapter offset', (tester) async {
    SharedPreferences.setMockInitialValues({
      'reader_book': 'GEN',
      'reader_chapter': 1,
      'reader_mode': 'chinese',
      'reader_scroll_GEN-1': 360.0,
    });
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(_readerApp());
    await _pumpReader(tester);

    final scrollView = tester.widget<CustomScrollView>(
      find.byType(CustomScrollView),
    );
    expect(scrollView.controller!.offset, closeTo(360, 0.01));
  });

  testWidgets('dispose flushes a pending exact offset before reopening', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(_readerApp());
    await _pumpReader(tester);
    final firstScrollView = tester.widget<CustomScrollView>(
      find.byType(CustomScrollView),
    );
    firstScrollView.controller!.jumpTo(412);

    // Dispose before the 180 ms debounce fires. dispose() must flush the live
    // controller offset and prevent the pending timer from replacing it.
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump();
    final preferences = await SharedPreferences.getInstance();
    expect(preferences.getDouble('reader_scroll_GEN-1'), closeTo(412, 0.01));

    await tester.pumpWidget(_readerApp());
    await _pumpReader(tester);
    final reopenedScrollView = tester.widget<CustomScrollView>(
      find.byType(CustomScrollView),
    );
    expect(reopenedScrollView.controller!.offset, closeTo(412, 0.01));
  });
}

Future<void> _pumpReader(WidgetTester tester) async {
  // Asset loading and the two post-load layout frames are asynchronous. Wait
  // until the chapter has a real extent, then allow restoration to jump.
  var lastExtent = -1.0;
  for (var frame = 0; frame < 30; frame++) {
    await tester.pump(const Duration(milliseconds: 100));
    final views = find.byType(CustomScrollView);
    if (views.evaluate().isNotEmpty &&
        find.text('Verse 1').evaluate().isNotEmpty) {
      final view = tester.widget<CustomScrollView>(views);
      if (view.controller?.hasClients == true &&
          view.controller!.position.maxScrollExtent > 0) {
        lastExtent = view.controller!.position.maxScrollExtent;
        await tester.pump();
        await tester.pump();
        return;
      }
    }
  }
  fail('Reader chapter did not finish layout (extent: $lastExtent).');
}

Future<void> _openTraditionalResult(WidgetTester tester) async {
  await tester.tap(
    find.byWidgetPredicate(
      (widget) => widget is AppGlyphView && widget.glyph == AppGlyph.search,
    ),
  );
  await tester.pump(const Duration(milliseconds: 500));
  final input = find.byType(EditableText);
  expect(input, findsOneWidget);
  await tester.enterText(input, '神愛世人');
  await tester.tap(
    find.byWidgetPredicate(
      (widget) => widget is AppGlyphView && widget.glyph == AppGlyph.forward,
    ),
  );
  await tester.pump(const Duration(milliseconds: 500));
  await tester.tap(find.text('神愛世人').last);
  await _pumpReader(tester);
  await tester.pump(const Duration(milliseconds: 400));
}

Finder _chapterScroll(String chapterKey) => find.byWidgetPredicate(
  (widget) =>
      widget is CustomScrollView && widget.key == ValueKey<String>(chapterKey),
);

void _phone(WidgetTester tester) {
  const channel = MethodChannel('bible/android');
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
      .setMockMethodCallHandler(channel, (_) async => null);
  tester.view.physicalSize = const Size(390, 844);
  tester.view.devicePixelRatio = 1;
  addTearDown(() {
    tester.view.reset();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, null);
  });
}

Widget _readerApp({
  BibleAiController? aiController,
  AppSettingsController? settingsController,
}) {
  final settings = settingsController ?? AppSettingsController();
  ThemeData theme(Brightness brightness, AppColors colors) => ThemeData(
    brightness: brightness,
    extensions: [colors, AppRadii.fallback],
  );
  return AnimatedBuilder(
    animation: settings,
    builder: (context, _) => MaterialApp(
      locale: settings.state.locale.locale,
      supportedLocales: AppLocalizations.supportedLocales,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      theme: theme(Brightness.light, AppColors.light),
      darkTheme: theme(Brightness.dark, AppColors.dark),
      themeMode: settings.state.themeMode,
      builder: (context, child) => AppSettingsScope(
        controller: settings,
        child: Material(
          type: MaterialType.transparency,
          child: child ?? const SizedBox.shrink(),
        ),
      ),
      home: BibleHome(
        settingsController: settings,
        repository: _FakeBibleRepository(),
        aiController: aiController,
      ),
    ),
  );
}

int _visibleLibraryBookCount(WidgetTester tester) {
  final list = tester.widget<ListView>(find.byType(ListView));
  return (list.childrenDelegate.estimatedChildCount! + 1) ~/ 2;
}

class _FakeBibleRepository extends BibleRepository {
  @override
  Future<List<VersePair>> chapter(BibleBook book, int chapter) async =>
      List.generate(
        40,
        (index) => VersePair(
          index + 1,
          'Verse ${index + 1}',
          'English verse ${index + 1}',
        ),
      );

  @override
  Future<List<ScriptureHit>> search(String query, {int limit = 80}) async => [
    ScriptureHit(
      book: bibleBooks.firstWhere((book) => book.id == 'JHN'),
      chapter: 3,
      verse: const VersePair(22, '神愛世人', 'For God so loved the world'),
    ),
  ];
}

BibleAiController _aiController(_RecordingModel model) => BibleAiController(
  model: model,
  memory: _FakeMemoryStore(),
  auth: _FakeAuth(),
  settings: _FakeSettingsStore(),
);

class _RecordingModel implements BibleAiModel {
  final List<String> prompts = [];

  @override
  Future<AiAvailability> availability() async => AiAvailability.ready;

  @override
  Future<void> downloadModel() async {}

  @override
  Future<String> generate(String prompt) async {
    prompts.add(prompt);
    if (prompt.contains('BIBLE_SEARCH_OVERVIEW')) return '神的愛貫穿救恩。';
    if (prompt.contains('BIBLE_SEARCH_REFERENCES_JSON')) {
      return '{"scriptures":[{"bookId":"JHN","chapter":3,"verseStart":16,"verseEnd":16,"reason":"神愛世人"}],"suggestedQuestions":["如何理解神的愛？"]}';
    }
    return 'answer [[END]]';
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
  final Map<String, String> values = {};

  @override
  Future<String?> read(String key) async => values[key];

  @override
  Future<void> write(String key, String value) async => values[key] = value;
}

class _FakeMemoryStore extends AiMemoryStore {
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
}
