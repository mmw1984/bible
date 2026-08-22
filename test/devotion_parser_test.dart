import 'package:bible/devotion_page.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('parses the 8月20日 poetry devotion with video + sections', (
    tester,
  ) async {
    const html = '''
<p><iframe title="YouTube video player" src="https://www.youtube.com/embed/8IwFu8d7aYE?si=uyCeuFdDLRCYo0Uk" width="560" height="315" frameborder="0" allowfullscreen="allowfullscreen"></iframe><br />
歡迎大家收聽靈食傳說這個靈修頻道。</p>
<div class="dove">
<div class="title">詩歌</div>
<p>讓我們聆聽詩歌。</p>
<p style="text-align: center;"><strong>《頌主奇恩》</strong></p>
</div>
<div class="explain">
<div class="title">分享</div>
<p>這首詩歌帶我們走進一幅極其豐富的大自然畫卷。</p>
<p>詩篇 19:1 說：</p>
<blockquote>諸天述說神的榮耀。</blockquote>
</div>
<div class="pray">
<div class="title">結束祈禱</div>
<p>親愛的天父，感謝祢。</p>
</div>
''';
    final blocks = parseDevotionBlocks(html);

    final videos = blocks.whereType<DevotionVideo>().toList();
    expect(videos, hasLength(1), reason: 'blocks=$blocks');
    expect(videos.first.videoId, '8IwFu8d7aYE');

    final sections = blocks.whereType<DevotionSection>().toList();
    expect(
      sections.map((s) => s.title).join(','),
      '詩歌,分享,結束祈禱',
      reason: 'blocks=${blocks.map((b) => b.runtimeType).toList()}',
    );
    expect(sections[0].blocks.whereType<DevotionParagraph>(), isNotEmpty);
    expect(sections[1].blocks.whereType<DevotionQuote>(), hasLength(1));
    // The section titles must not leak through as stray headings.
    expect(blocks.whereType<DevotionHeading>(), isEmpty);
  });

  test('plain paragraphs without sections still parse', () {
    const html = '<p>第一段</p><p>第二段</p><figure><img src="https://x/y.jpg" /></figure>';
    final blocks = parseDevotionBlocks(html);
    expect(blocks.whereType<DevotionParagraph>(), hasLength(2));
    expect(blocks.whereType<DevotionImage>(), hasLength(1));
  });

  test('parses nested titled sections (dove > verse > explain) and flattens them', () {
    // Mirrors the real 觀畫靈修 markup where 安靜 wraps 經文 which wraps 觀畫.
    // After flattening, all logical sections should be top-level siblings.
    const html = '''
<p>弟兄姊妹，歡迎收聽2026年8月21日的靈修默想。</p>
<div class="dove">
<div class="title">安靜</div>
<p>讓我們先找一個安靜的空間。</p>
<div class="verse">
<div class="title">經文：創世記12:10-20(和修版)</div>
<p>10 那地遭遇饑荒。</p>
<div class="explain">
<div class="title">觀畫</div>
<p><img src="https://devotion.wkphc.org/wp-content/uploads/2026/08/painting.jpg" alt="" /></p>
<p>弟兄姊妹，今日讓我們觀賞這幅畫作。</p>
</div>
<p>19 為甚麼說『她是我的妹妹』？</p>
</div>
</div>
<div class="pray">
<div class="title">結束祈禱</div>
<p>親愛的天父，感謝祢。</p>
</div>
''';
    final blocks = parseDevotionBlocks(html);

    final topLevel = blocks.whereType<DevotionSection>().toList();
    expect(
      topLevel.map((s) => s.title).join(','),
      '安靜,經文：創世記12:10-20(和修版),觀畫,結束祈禱',
      reason: 'container sections 安靜/經文 should be flattened to siblings',
    );
    // 安靜 keeps its intro paragraph
    expect(topLevel[0].blocks.whereType<DevotionParagraph>(), isNotEmpty);
    // 經文 keeps its verses (including the trailing paragraph after 觀畫)
    final verseSection =
        topLevel.firstWhere((s) => s.title.startsWith('經文'));
    expect(verseSection.blocks.whereType<DevotionParagraph>(), hasLength(2));
    // 觀畫 is now a top-level sibling with its image
    final paintingSection = topLevel.firstWhere((s) => s.title == '觀畫');
    expect(paintingSection.blocks.whereType<DevotionImage>(), hasLength(1));
  });

  test('deduplicates repeated WordPress image sizes', () {
    const html = '''
<div class="explain">
<div class="title">觀畫</div>
<p><img src="https://devotion.wkphc.org/wp-content/uploads/2026/08/painting-1024x768.jpg" /></p>
<p><img src="https://devotion.wkphc.org/wp-content/uploads/2026/08/painting-300x200.jpg" /></p>
<p><img src="https://devotion.wkphc.org/wp-content/uploads/2026/08/painting.jpg" /></p>
<p><img src="https://devotion.wkphc.org/wp-content/uploads/2026/08/detail-2026-08-20-1.jpg" /></p>
</div>
''';
    final blocks = parseDevotionBlocks(html);
    final section = blocks.whereType<DevotionSection>().single;
    // Three URLs with same base should dedup to 1, plus the detail image = 2
    expect(section.blocks.whereType<DevotionImage>(), hasLength(2));
  });

  test('title date parsing', () {
    expect(
      parseDevotionTitleDate('[觀畫靈修] 亞伯蘭與撒萊在埃及 －2026年8月21日'),
      DateTime(2026, 8, 21),
    );
    expect(parseDevotionTitleDate('[詩歌靈修] 頌主奇恩'), isNull);
  });

  test('parses every RSS item and sorts by title date', () {
    const xml = '''
<rss><channel>
<item>
<title>[觀畫靈修] 亞伯蘭與撒萊在埃及 －2026年8月21日</title>
<link>https://devotion.wkphc.org/25436</link>
<pubDate>Wed, 20 Aug 2026 09:42:24 +0000</pubDate>
<content:encoded><![CDATA[<p>觀畫內文</p><iframe src="https://www.youtube.com/embed/9rJm0Nq6TB0"></iframe>]]></content:encoded>
</item>
<item>
<title>[詩歌靈修] 頌主奇恩 －2026年8月20日</title>
<link>https://devotion.wkphc.org/25432</link>
<pubDate>Wed, 19 Aug 2026 11:37:55 +0000</pubDate>
<content:encoded><![CDATA[<p>詩歌內文</p>]]></content:encoded>
</item>
</channel></rss>
''';
    final posts = parseDevotionRssItems(xml);
    expect(posts, hasLength(2));
    // Newest title date first.
    expect(posts.first.title, contains('亞伯蘭'));
    expect(posts.last.title, contains('頌主奇恩'));
    expect(
      posts.first.blocks.whereType<DevotionVideo>().single.videoId,
      '9rJm0Nq6TB0',
    );
    // Raw HTML kept for cache round-trips.
    expect(posts.first.contentHtml, contains('觀畫內文'));
  });

  test('cache encode/decode round-trip re-parses blocks', () {
    const html = '''
<p><iframe src="https://www.youtube.com/embed/8IwFu8d7aYE"></iframe></p>
<div class="dove"><div class="title">安靜</div><p>歡迎收聽。</p></div>
''';
    final original = DevotionPost(
      id: 25432,
      date: DateTime.utc(2026, 8, 19, 11, 37),
      devotionDate: DateTime(2026, 8, 20),
      title: '[詩歌靈修] 頌主奇恩 －2026年8月20日',
      link: 'https://devotion.wkphc.org/25432',
      contentHtml: html,
      blocks: parseDevotionBlocks(html),
    );

    final restored = decodeDevotionPostsCache(
      encodeDevotionPostsCache([original]),
    );

    expect(restored, hasLength(1));
    final post = restored.single;
    expect(post.id, 25432);
    expect(post.title, original.title);
    expect(post.devotionDate, DateTime(2026, 8, 20));
    expect(post.link, original.link);
    expect(post.blocks.whereType<DevotionVideo>(), hasLength(1));
    expect(post.blocks.whereType<DevotionSection>().single.title, '安靜');
    // Re-encoding the restored post yields identical cache bytes.
    expect(encodeDevotionPostsCache(restored), encodeDevotionPostsCache([original]));
  });

  test('image URLs with raw CJK are percent-encoded at parse time', () {
    const html =
        '<p><img src="https://devotion.wkphc.org/wp-content/uploads/2026/08/'
        '螢幕截圖-2026-08-20-下午5.16.35-1024x714.png" /></p>';
    final blocks = parseDevotionBlocks(html);
    final url = blocks.whereType<DevotionImage>().single.url;
    expect(url.contains(RegExp(r'[^\x00-\x7F]')), isFalse);
    expect(url, contains('%E8%9E%A2')); // 螢
    // Idempotent: already-encoded URLs stay untouched.
    expect(normalizeDevotionImageUrl(url), url);
  });

  test('SoundCloud iframes are kept as tappable embeds, not dropped', () {
    const html =
        '<p><iframe width="100%" height="166" scrolling="no" frameborder="no" '
        'allow="autoplay; encrypted-media" '
        'src="https://w.soundcloud.com/player/?url=https%3A//api.soundcloud.com'
        '%2Ftracks%2F2378777798&amp;color=%23ff5500&amp;auto_play=false">'
        '</iframe></p>';
    final embed = parseDevotionBlocks(html).whereType<DevotionEmbed>().single;
    expect(embed.url, startsWith('https://w.soundcloud.com/player/'));
    // Entities inside the src are decoded; nothing is lost.
    expect(embed.url.contains('&amp;'), isFalse);
  });

  test('lazy-loaded images fall back to data-src placeholders', () {
    // WP-Smush on the live site parks a 1×1 SVG in `src` and moves the real
    // image into `data-src`; dropping those images made the 觀畫靈修 gallery
    // render with a single painting when reading site pages directly.
    const html = '<p><img decoding="async" class="alignnone wp-image-1 lazyload" '
        'data-src="https://example.com/gallery/painting-1024x714.png" '
        'width="600" height="420" '
        'src="data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMSIgaGVpZ2h0PSIxIj48L3N2Zz4+" '
        'style="--smush-placeholder-width: 600px;" /></p>';
    final url = parseDevotionBlocks(html).whereType<DevotionImage>().single.url;
    expect(url, 'https://example.com/gallery/painting-1024x714.png');
  });

  test('eager images still win over their lazy duplicates', () {
    const html = '<p><img decoding="async" class="lazyload" '
        'data-src="https://example.com/real.jpg" '
        'src="https://example.com/eager.jpg" /></p>';
    final url = parseDevotionBlocks(html).whereType<DevotionImage>().single.url;
    expect(url, 'https://example.com/eager.jpg');
  });
}
