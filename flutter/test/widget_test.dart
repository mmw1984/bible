import 'package:bible/main.dart';
import 'package:bible/bible_data.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  testWidgets('reader exposes core controls', (tester) async {
    SharedPreferences.setMockInitialValues({});
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(_readerApp());
    await tester.pump();

    expect(find.text('創世記'), findsOneWidget);
    expect(find.text('中文'), findsOneWidget);
    expect(find.text('英文'), findsOneWidget);
    expect(find.text('雙語'), findsOneWidget);
    expect(find.byIcon(Icons.menu_rounded), findsOneWidget);
    expect(
      find.byIcon(Icons.search_rounded).evaluate().isNotEmpty ||
          find.byIcon(Icons.auto_awesome_rounded).evaluate().isNotEmpty,
      isTrue,
    );
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

Widget _readerApp() => MaterialApp(
  home: BibleHome(
    dark: true,
    onTheme: () {},
    repository: _FakeBibleRepository(),
  ),
);

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
}
