import 'package:bible/app_markdown.dart';
import 'package:bible/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('renders completed and streaming strong Markdown spans', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: ColoredBox(
          color: Colors.black,
          child: Column(
            children: [
              AppMarkdown(
                data: '**completed**',
                foreground: AppColors.dark.ink,
                secondary: AppColors.dark.muted,
                border: AppColors.dark.line,
              ),
              AppMarkdown(
                data: '**still streaming',
                foreground: AppColors.dark.ink,
                secondary: AppColors.dark.muted,
                border: AppColors.dark.line,
              ),
              AppMarkdown(
                data: '**trailing space **',
                foreground: AppColors.dark.ink,
                secondary: AppColors.dark.muted,
                border: AppColors.dark.line,
              ),
              AppMarkdown(
                data: '** 你好',
                foreground: AppColors.dark.ink,
                secondary: AppColors.dark.muted,
                border: AppColors.dark.line,
              ),
              AppMarkdown(
                data: '** text** / **text ** / ** text **',
                foreground: AppColors.dark.ink,
                secondary: AppColors.dark.muted,
                border: AppColors.dark.line,
              ),
            ],
          ),
        ),
      ),
    );

    final richText = tester.widgetList<RichText>(find.byType(RichText));
    expect(
      richText.every((widget) => widget.selectionRegistrar != null),
      isTrue,
    );
    final strongSpans = richText
        .map((widget) => widget.text)
        .whereType<TextSpan>()
        .expand((span) => span.children ?? const <InlineSpan>[])
        .whereType<TextSpan>()
        .where((span) => span.style?.fontWeight == FontWeight.w700)
        .map((span) => span.text)
        .toList();
    expect(
      strongSpans,
      containsAll([
        'completed',
        'still streaming',
        'trailing space ',
        ' 你好',
        ' text',
        'text ',
        ' text ',
      ]),
    );
  });

  testWidgets('renders Markdown tables in a horizontally scrollable surface', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: SizedBox(
          width: 320,
          child: AppMarkdown(
            data: '''
| 書卷 | 章節 | 主題 | 說明 |
| --- | :---: | --- | ---: |
| 約翰福音 | 3:16 | **愛** | 神愛世人 |
| 詩篇 | 23:1 | 牧者 | 我必不致缺乏 |
''',
            foreground: AppColors.light.ink,
            secondary: AppColors.light.muted,
            border: AppColors.light.line,
          ),
        ),
      ),
    );

    expect(find.byType(Table), findsOneWidget);
    expect(
      find.byWidgetPredicate(
        (widget) =>
            widget is SingleChildScrollView &&
            widget.scrollDirection == Axis.horizontal,
      ),
      findsOneWidget,
    );
    final table = tester.widget<Table>(find.byType(Table));
    expect(table.children, hasLength(3));
    expect(table.children.first.children, hasLength(4));
    final cellText = tester.widgetList<RichText>(find.byType(RichText));
    expect(
      cellText.every((widget) => widget.selectionRegistrar != null),
      isTrue,
    );
  });
}
