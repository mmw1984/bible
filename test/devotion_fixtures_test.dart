import 'dart:io';

import 'package:bible/devotion_content.dart';
import 'package:flutter_test/flutter_test.dart';

/// Regression tests against real WordPress markup saved from
/// devotion.wkphc.org (see test/fixtures). These cover every post shape the
/// blog publishes, including the 2026-08-20/21 posts that previously rendered
/// title-only when the parser failed.
void main() {
  String fixture(String name) =>
      File('test/fixtures/$name').readAsStringSync();

  List<DevotionSection> sectionsOf(List<DevotionBlock> blocks) =>
      blocks.whereType<DevotionSection>().toList();

  group('觀畫靈修 (nested dove > verse > explain, 2026-08-21)', () {
    final blocks = parseDevotionBlocks(fixture('devotion_guanhua.html'));

    test('renders video, all five sections and full bodies', () {
      expect(blocks.first, isA<DevotionVideo>());
      expect(
        sectionsOf(blocks).map((s) => s.title),
        ['安靜', '經文：創世記12:10-20(和修版)', '觀畫', '思想／祈禱', '結束祈禱'],
      );
    });

    test('安靜 keeps its intro and 經文 keeps three verse paragraphs', () {
      final sections = sectionsOf(blocks);
      expect(sections[0].blocks.whereType<DevotionParagraph>(), hasLength(1));
      expect(sections[1].blocks.whereType<DevotionParagraph>(), hasLength(3));
    });

    test('觀畫 carries the painting gallery interleaved with commentary', () {
      final painting = sectionsOf(blocks).firstWhere((s) => s.title == '觀畫');
      expect(painting.blocks.whereType<DevotionImage>().length, greaterThanOrEqualTo(7));
      final texts = painting.blocks.whereType<DevotionParagraph>().map((p) => p.text);
      expect(texts, contains(contains('穆齊奧利')));
      expect(texts, contains(contains('神並沒有向亞伯蘭失信')));
    });

    test('CJK screenshot URLs are percent-encoded', () {
      final urls = [
        for (final block in blocks) ...[
          if (block is DevotionImage) block.url,
          if (block is DevotionSection)
            for (final child in block.blocks)
              if (child is DevotionImage) child.url,
        ],
      ];
      expect(urls, isNotEmpty);
      for (final url in urls) {
        expect(url.contains(RegExp(r'[^\x00-\x7F]')), isFalse, reason: url);
      }
    });
  });

  group('詩歌靈修 (flat sections, 2026-08-20)', () {
    final blocks = parseDevotionBlocks(fixture('devotion_shige.html'));

    test('renders video, four sections and song lyrics', () {
      expect(blocks.first, isA<DevotionVideo>());
      expect(
        sectionsOf(blocks).map((s) => s.title).toList(),
        ['詩歌', '分享', '思想／祈禱', '結束祈禱'],
      );
      final lyrics = sectionsOf(blocks).first.blocks
          .whereType<DevotionParagraph>()
          .map((p) => p.text)
          .join('\n');
      expect(lyrics, contains('蝶舞於花間'));
      expect(lyrics, contains('副歌'));
    });

    test('no phantom empty paragraphs', () {
      void assertNoEmpty(List<DevotionBlock> blocks) {
        for (final block in blocks) {
          if (block is DevotionParagraph) expect(block.text, isNotEmpty);
          if (block is DevotionSection) assertNoEmpty(block.blocks);
        }
      }

      assertNoEmpty(blocks);
    });

    test('list items become bullets inside 思想／祈禱', () {
      final section =
          sectionsOf(blocks).firstWhere((s) => s.title == '思想／祈禱');
      final bullets =
          section.blocks.whereType<DevotionParagraph>().map((p) => p.text);
      expect(bullets.where((t) => t.startsWith('• ')), hasLength(2));
    });
  });

  group('靈修默想 (daily article with SoundCloud player)', () {
    final blocks = parseDevotionBlocks(fixture('devotion_moxiang.html'));

    test('keeps the four titled sections', () {
      expect(
        sectionsOf(blocks).map((s) => s.title).toList(),
        ['王上十二9～11（和修版）', '淺釋', '默想／祈禱', '禱文'],
      );
    });

    test('SoundCloud embed attribution survives as text', () {
      final texts = blocks.whereType<DevotionParagraph>().map((p) => p.text);
      expect(texts, contains('永光e電園'));
    });

    test('non-YouTube iframes are ignored rather than crashing', () {
      expect(blocks.whereType<DevotionVideo>(), isEmpty);
    });
  });

  group('傳道書 series (long-form article)', () {
    final blocks = parseDevotionBlocks(fixture('devotion_chuandao.html'));

    test('keeps every titled section in order', () {
      expect(
        sectionsOf(blocks).map((s) => s.title).toList(),
        [
          '聆聽聖言:《傳道書 12:8-14》',
          '回歸起點︰虛空的虛空，凡事虛空',
          '傳道者的角色︰誠實追問的智慧導師',
          '刺棍與釘子︰智慧言語的刺痛與穩固',
          '書本的局限︰知識不能取代敬畏',
          '最終的結論︰敬畏神，謹守誡命，這是人所當盡的本分',
          '一切隱藏的事︰活在神的眼光之下',
          '思考課題',
          '默想生命',
          '禱文',
        ],
      );
    });

    test('download link paragraph is preserved', () {
      final texts = blocks.whereType<DevotionParagraph>().map((p) => p.text);
      expect(texts.any((t) => t.contains('PDF 文字版下載')), isTrue);
    });
  });

  group('圖片靈修 (image-led post)', () {
    final blocks = parseDevotionBlocks(fixture('devotion_tupian.html'));

    test('renders video, map image, prose and two sections', () {
      expect(blocks.first, isA<DevotionVideo>());
      expect(blocks.at(1), isA<DevotionImage>());
      expect(blocks.whereType<DevotionParagraph>().length, 5);
      expect(
        sectionsOf(blocks).map((s) => s.title).toList(),
        ['思想／祈禱', '結束祈禱'],
      );
    });
  });

  group('cache round-trip on real markup', () {
    test('re-parses identical blocks after encode/decode', () {
      final html = fixture('devotion_guanhua.html');
      final original = DevotionPost(
        id: 25436,
        date: DateTime.utc(2026, 8, 20, 17, 42),
        devotionDate: DateTime(2026, 8, 21),
        title: '[觀畫靈修] 亞伯蘭與撒萊在埃及 －2026年8月21日',
        link: 'https://devotion.wkphc.org/25436',
        contentHtml: html,
        blocks: parseDevotionBlocks(html),
      );

      final restored =
          decodeDevotionPostsCache(encodeDevotionPostsCache([original]));

      expect(restored.single.blocks.length, original.blocks.length);
      expect(
        restored.single.blocks.map((b) => b.runtimeType).toList(),
        original.blocks.map((b) => b.runtimeType).toList(),
      );
      final painting = restored.single.blocks
          .whereType<DevotionSection>()
          .firstWhere((s) => s.title == '觀畫');
      expect(painting.blocks.whereType<DevotionImage>(), isNotEmpty);
    });
  });

  group('malformed markup tolerance', () {
    test('unbalanced divs never throw or duplicate sections', () {
      const html = '''
<div class="dove"><div class="title">詩歌</div><p>第一段</p>
<div class="explain"><div class="title">分享</div><p>第二段</p></div>
'''; // both wrappers left open
      final blocks = parseDevotionBlocks(html);
      // The unterminated 詩歌 wrapper legitimately nests 分享; both must
      // exist exactly once somewhere in the tree.
      final titles = <String>[];
      void collect(List<DevotionBlock> blocks) {
        for (final block in blocks) {
          if (block is DevotionSection) {
            titles.add(block.title);
            collect(block.blocks);
          }
        }
      }

      collect(blocks);
      expect(titles.where((t) => t == '詩歌'), hasLength(1));
      expect(titles.where((t) => t == '分享'), hasLength(1));
    });

    test('stray close tags are ignored', () {
      const html = '<p>文字</p></div></div><p>更多文字</p>';
      expect(parseDevotionBlocks(html).whereType<DevotionParagraph>(), hasLength(2));
    });

    test('truncated document yields what parsed so far', () {
      const html = '<div class="pray"><div class="title">結束祈禱</div><p>阿們';
      final blocks = parseDevotionBlocks(html);
      expect(sectionsOf(blocks).single.title, '結束祈禱');
    });

    test('empty input yields no blocks', () {
      expect(parseDevotionBlocks(''), isEmpty);
      expect(parseDevotionBlocks('   \n '), isEmpty);
    });
  });
}

extension _At<T> on List<T> {
  T? at(int index) => index >= 0 && index < length ? this[index] : null;
}
