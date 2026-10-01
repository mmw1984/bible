import 'package:bible/devotion_content.dart';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Tests for the tier-3 site-page fallback: post discovery on the homepage
/// and article parsing straight from the public pages. Fixtures are real
/// pages saved from devotion.wkphc.org.
void main() {
  String fixture(String name) =>
      File('test/fixtures/$name').readAsStringSync();

  group('extractPostLinksFromPage (homepage)', () {
    final links = extractPostLinksFromPage(fixture('devotion_site_homepage.html'));

    test('finds the newest numeric permalinks in document order', () {
      final urls = links.map((link) => link.url).toList();
      expect(urls.first, 'https://devotion.wkphc.org/25447');
      expect(urls.take(3), containsAll(<String>[
        'https://devotion.wkphc.org/25436',
        'https://devotion.wkphc.org/25432',
      ]));
      // Newest first, no duplicates.
      expect(urls.toSet().length, urls.length);
    });

    test('anchor text carries the full title including devotion date', () {
      final first = links.first;
      expect(first.title, contains('[圖片靈修]'));
      expect(parseDevotionTitleDate(first.title), DateTime(2026, 8, 22));
    });
  });

  group('normalizePostHref', () {
    test('handles absolute, relative, query and trailing slash forms', () {
      expect(
        normalizePostHref('https://devotion.wkphc.org/25447'),
        'https://devotion.wkphc.org/25447',
      );
      expect(
        normalizePostHref('/25447/'),
        'https://devotion.wkphc.org/25447',
      );
      expect(
        normalizePostHref(
            'https://devotion.wkphc.org/25447?utm_source=rss&amp;utm_medium=x'),
        'https://devotion.wkphc.org/25447',
      );
      expect(normalizePostHref('https://devotion.wkphc.org/feed'), isNull);
      expect(normalizePostHref('/wp-content/uploads/a.png'), isNull);
    });
  });

  group('parseArticlePage (whole article page)', () {
    late DevotionPost post;

    setUpAll(() {
      post = parseArticlePage(
        url: 'https://devotion.wkphc.org/25436',
        html: fixture('devotion_site_article.html'),
      )!;
    });

    test('extracts title with devotion date from the entry heading', () {
      expect(post.title, contains('[觀畫靈修]'));
      expect(post.devotionDate, DateTime(2026, 8, 21));
      expect(post.id, 25436);
    });

    test('content container yields video and painting gallery', () {
      expect(post.blocks.first, isA<DevotionVideo>());
      final images = [
        for (final block in post.blocks) ...[
          if (block is DevotionImage) block,
          if (block is DevotionSection)
            ...block.blocks.whereType<DevotionImage>(),
        ],
      ];
      expect(images.length, greaterThanOrEqualTo(7));
      final sections = post.blocks.whereType<DevotionSection>().toList();
      expect(sections.map((s) => s.title), contains('觀畫'));
    });
  });

  group('serializeHtmlElement', () {
    test('round-trips through parse → serialize → parse', () {
      const html = '<div class="dove"><div class="title">詩歌</div>'
          '<p>第一段 <b>粗體</b> 文字</p>'
          '<img src="https://example.com/a.png" />'
          '<iframe src="https://www.youtube.com/embed/abc1234"></iframe></div>';
      final first = parseDevotionBlocksFromElement(parseHtmlDocument(html));
      final serialized =
          serializeHtmlElement(parseHtmlDocument(html).children.first as HtmlElement);
      final second = parseDevotionBlocks(serialized);

      final sectionsOfFirst = first.whereType<DevotionSection>().single;
      final sectionsOfSecond = second.whereType<DevotionSection>().single;
      expect(sectionsOfSecond.title, sectionsOfFirst.title);
      expect(
        sectionsOfSecond.blocks.whereType<DevotionParagraph>().single.text,
        '第一段 粗體 文字',
      );
      expect(sectionsOfSecond.blocks.whereType<DevotionImage>().single.url,
          'https://example.com/a.png');
      expect(
        sectionsOfSecond.blocks.whereType<DevotionVideo>().single.videoId,
        'abc1234',
      );
    });

    test('scraped content survives the offline cache round-trip', () {
      final page = parseArticlePage(
        url: 'https://devotion.wkphc.org/25436',
        html: fixture('devotion_site_article.html'),
      )!;
      final restored =
          decodeDevotionPostsCache(encodeDevotionPostsCache([page]));
      expect(restored, hasLength(1));
      final cached = restored.single;
      expect(cached.title, page.title);
      expect(cached.devotionDate, page.devotionDate);
      expect(
        cached.blocks.whereType<DevotionSection>().map((s) => s.title),
        page.blocks.whereType<DevotionSection>().map((s) => s.title),
      );
    });
  });
}
