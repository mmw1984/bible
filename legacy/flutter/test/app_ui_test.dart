import 'dart:ui' as ui;

import 'package:bible/app_theme.dart';
import 'package:bible/app_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

void main() {
  testWidgets('theme glyphs stay legible at toolbar size', (tester) async {
    tester.view.physicalSize = const Size(240, 100);
    tester.view.devicePixelRatio = 2;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      const Directionality(
        textDirection: TextDirection.ltr,
        child: RepaintBoundary(
          child: ColoredBox(
            color: Color(0xFF777777),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                _GlyphSample(
                  background: Color(0xFF111111),
                  foreground: Color(0xFFB1B0AA),
                  glyph: AppGlyph.sun,
                ),
                _GlyphSample(
                  background: Color(0xFFFEFBF4),
                  foreground: Color(0xFF696760),
                  glyph: AppGlyph.moon,
                ),
              ],
            ),
          ),
        ),
      ),
    );

    await expectLater(
      find.byType(RepaintBoundary).first,
      matchesGoldenFile('goldens/theme_glyphs.png'),
    );
  });

  testWidgets(
    'segmented choices keep labels centered and full cells tappable',
    (tester) async {
      tester.view.physicalSize = const Size(390, 200);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      var selected = true;

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Center(
              child: SizedBox(
                width: 360,
                child: StatefulBuilder(
                  builder: (context, setState) => AppSegmented<bool>(
                    choices: const [
                      AppChoice(
                        value: true,
                        label: 'OpenRouter',
                        icon: LucideIcons.cloud,
                      ),
                      AppChoice(
                        value: false,
                        label: '自訂模型',
                        icon: LucideIcons.cpu,
                      ),
                    ],
                    selected: selected,
                    onChanged: (value) => setState(() => selected = value),
                  ),
                ),
              ),
            ),
          ),
        ),
      );

      final segmented = find.byType(AppSegmented<bool>);
      final rect = tester.getRect(segmented);
      final innerWidth = rect.width - 6;
      final firstCenter = rect.left + 3 + innerWidth * .25;
      final secondCenter = rect.left + 3 + innerWidth * .75;
      expect(
        tester.getCenter(find.text('OpenRouter')).dx,
        closeTo(firstCenter, .5),
      );
      expect(tester.getCenter(find.text('自訂模型')).dx, closeTo(secondCenter, .5));

      final routerText = tester.widget<Text>(find.text('OpenRouter'));
      final customText = tester.widget<Text>(find.text('自訂模型'));
      expect(routerText.style?.fontSize, customText.style?.fontSize);
      expect(routerText.style?.fontWeight, FontWeight.w600);
      expect(customText.style?.fontWeight, FontWeight.w500);

      final cloudCenter = tester.getCenter(find.byIcon(LucideIcons.cloud));
      final memoryCenter = tester.getCenter(find.byIcon(LucideIcons.cpu));
      expect(cloudCenter.dx, inExclusiveRange(rect.left, firstCenter));
      expect(memoryCenter.dx, inExclusiveRange(rect.center.dx, secondCenter));

      await tester.tapAt(Offset(secondCenter, rect.bottom - 4));
      await tester.pumpAndSettle();
      expect(selected, isFalse);
      expect(
        tester
            .getSemantics(find.bySemanticsLabel('自訂模型'))
            .flagsCollection
            .isSelected,
        ui.Tristate.isTrue,
      );

      await tester.tapAt(Offset(firstCenter, rect.top + 4));
      await tester.pumpAndSettle();
      expect(selected, isTrue);
      expect(
        tester
            .getSemantics(find.bySemanticsLabel('OpenRouter'))
            .flagsCollection
            .isSelected,
        ui.Tristate.isTrue,
      );
    },
  );

  testWidgets('single-line app input centers its placeholder vertically', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: 320,
              child: AppTextInput(
                key: const ValueKey('centered-input'),
                controller: TextEditingController(),
                hint: '搜尋全本聖經',
              ),
            ),
          ),
        ),
      ),
    );

    final input = find.byKey(const ValueKey('centered-input'));
    final surface = find.descendant(
      of: input,
      matching: find.byType(AnimatedContainer),
    );
    expect(
      tester.getCenter(find.text('搜尋全本聖經')).dy,
      closeTo(tester.getCenter(surface.first).dy, 1),
    );
  });

  testWidgets('control surface is always opaque and unblurred', (tester) async {
    await tester.pumpWidget(
      _surfaceApp(const AppControlSurface(child: Text('Control'))),
    );
    expect(find.byType(BackdropFilter), findsNothing);
    final decoration =
        tester.widget<DecoratedBox>(find.byType(DecoratedBox).first).decoration
            as BoxDecoration;
    expect(decoration.color?.a, 1);
  });

  testWidgets('shared buttons and selected segments use solid surfaces', (
    tester,
  ) async {
    await tester.pumpWidget(
      _surfaceApp(
        SizedBox(
          width: 240,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              AppGlyphButton(
                glyph: AppGlyph.settings,
                label: 'Settings',
                onTap: () {},
                selected: true,
              ),
              const SizedBox(height: 12),
              AppSegmented<bool>(
                choices: const [
                  AppChoice(value: true, label: '中文'),
                  AppChoice(value: false, label: 'English'),
                ],
                selected: true,
                onChanged: (_) {},
              ),
            ],
          ),
        ),
      ),
    );

    expect(find.byType(AppButton), findsOneWidget);
    expect(find.byType(BackdropFilter), findsNothing);
    final decorations = tester
        .widgetList<DecoratedBox>(find.byType(DecoratedBox))
        .map((widget) => widget.decoration)
        .whereType<BoxDecoration>();
    expect(decorations, isNotEmpty);
    expect(decorations.every((decoration) => decoration.color?.a == 1), isTrue);
  });

  testWidgets('selected surface uses an opaque requested color', (
    tester,
  ) async {
    await tester.pumpWidget(
      _surfaceApp(
        const AppControlSurface(
          color: Colors.black,
          selected: true,
          child: SizedBox(width: 48, height: 48),
        ),
      ),
    );

    final decoration =
        tester.widget<DecoratedBox>(find.byType(DecoratedBox).first).decoration
            as BoxDecoration;
    expect(decoration.color, Colors.black);
  });

  testWidgets('app input forwards its custom radius to the solid surface', (
    tester,
  ) async {
    await tester.pumpWidget(
      _surfaceApp(
        SizedBox(
          width: 280,
          child: AppTextInput(
            key: const ValueKey('rounded-input'),
            controller: TextEditingController(),
            hint: 'Question',
            radius: 21,
          ),
        ),
      ),
    );

    final surface = tester.widget<AppControlSurface>(
      find.descendant(
        of: find.byKey(const ValueKey('rounded-input')),
        matching: find.byType(AppControlSurface),
      ),
    );
    expect(surface.radius, 21);
  });
}

Widget _surfaceApp(Widget child) => MaterialApp(
  theme: ThemeData(extensions: const [AppColors.light, AppRadii.fallback]),
  home: Scaffold(body: Center(child: child)),
);

class _GlyphSample extends StatelessWidget {
  const _GlyphSample({
    required this.background,
    required this.foreground,
    required this.glyph,
  });

  final Color background;
  final Color foreground;
  final AppGlyph glyph;

  @override
  Widget build(BuildContext context) => ColoredBox(
    color: background,
    child: SizedBox(
      width: 44,
      height: 44,
      child: Center(child: AppGlyphView(glyph, color: foreground, size: 19)),
    ),
  );
}
