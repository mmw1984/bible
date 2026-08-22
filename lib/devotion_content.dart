import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

import 'app_settings.dart';

// ─────────────────────────────────────────────────────────────────────────────
// Content model
// ─────────────────────────────────────────────────────────────────────────────

sealed class DevotionBlock {
  const DevotionBlock();
}

class DevotionParagraph extends DevotionBlock {
  const DevotionParagraph(this.text);
  final String text;
}

class DevotionHeading extends DevotionBlock {
  const DevotionHeading(this.text);
  final String text;
}

class DevotionQuote extends DevotionBlock {
  const DevotionQuote(this.text);
  final String text;
}

class DevotionImage extends DevotionBlock {
  const DevotionImage(this.url);
  final String url;
}

/// A YouTube embed (`<iframe src="…youtube.com/embed/…">`).
class DevotionVideo extends DevotionBlock {
  const DevotionVideo(this.videoId);
  final String videoId;

  String get watchUrl => 'https://www.youtube.com/watch?v=$videoId';
  String get thumbnailUrl =>
      'https://img.youtube.com/vi/$videoId/hqdefault.jpg';
}

/// Any other `<iframe>` embed (e.g. SoundCloud players inside 靈修默想
/// posts). The native reader cannot render it inline, so it surfaces as a
/// tappable card; the in-app web reader renders it fully.
class DevotionEmbed extends DevotionBlock {
  const DevotionEmbed(this.url);
  final String url;
}

/// A titled section like `<div class="dove"><div class="title">詩歌</div>…`.
class DevotionSection extends DevotionBlock {
  const DevotionSection({required this.title, required this.blocks});
  final String title;
  final List<DevotionBlock> blocks;
}

class DevotionPost {
  const DevotionPost({
    required this.id,
    required this.date,
    required this.devotionDate,
    required this.title,
    required this.link,
    required this.blocks,
    this.contentHtml = '',
  });

  final int id;

  /// Publish time from the API.
  final DateTime date;

  /// Devotion date parsed from the title suffix like “2026年8月21日”.
  /// Falls back to [date] when the title carries no parsable date.
  final DateTime devotionDate;
  final String title;
  final String link;
  final List<DevotionBlock> blocks;

  /// Raw WordPress HTML the [blocks] were parsed from. Kept verbatim so a
  /// successful fetch can be cached and re-parsed offline on next launch.
  final String contentHtml;
}

// ─────────────────────────────────────────────────────────────────────────────
// Small utilities
// ─────────────────────────────────────────────────────────────────────────────

/// Extracts a `YYYY年M月D日` date from titles such as
/// “[觀畫靈修] 亞伯蘭… －2026年8月21日”. Returns `null` when absent.
DateTime? parseDevotionTitleDate(String title) {
  final match = RegExp(r'(\d{4})年(\d{1,2})月(\d{1,2})日').firstMatch(title);
  if (match == null) return null;
  final y = int.tryParse(match.group(1)!);
  final m = int.tryParse(match.group(2)!);
  final d = int.tryParse(match.group(3)!);
  if (y == null || m == null || d == null) return null;
  if (m < 1 || m > 12 || d < 1 || d > 31) return null;
  return DateTime(y, m, d);
}

/// Some WordPress security plugins reject unknown user agents, which would
/// explain the REST endpoint failing on devices; present as a browser.
const _kDevotionHeaders = {
  'User-Agent':
      'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) '
          'Chrome/126.0 Mobile Safari/537.36',
};

/// Percent-encodes characters that WordPress leaves raw inside `<img src>`
/// (e.g. `螢幕截圖-…-下午5.16.35.png`). dart:io request lines choke on raw
/// non-ASCII paths on some devices, while servers expect RFC 3986 encoding.
/// Only actual non-ASCII code units are escaped — every other byte (including
/// an existing `%`) passes through untouched, so already-encoded URLs are
/// returned unchanged instead of being double-encoded (`%`→`%25`).
String normalizeDevotionImageUrl(String url) {
  final trimmed = url.trim();
  return trimmed.replaceAllMapped(
    RegExp(r'[^\x00-\x7F]'),
    (match) => Uri.encodeComponent(match.group(0)!),
  );
}

/// Extracts the video id from a YouTube embed/watch URL, `null` otherwise.
String? parseYouTubeId(String url) {
  final match = RegExp(
    r'youtube\.com/embed/([\w-]{6,})|youtu\.be/([\w-]{6,})|[?&]v=([\w-]{6,})',
    caseSensitive: false,
  ).firstMatch(url);
  if (match == null) return null;
  return match.group(1) ?? match.group(2) ?? match.group(3);
}

String stripHtmlTags(String html) => html.replaceAll(RegExp(r'<[^>]*>'), ' ');

String decodeHtmlEntities(String input) {
  var output = input;
  final numeric = RegExp(r'&#x([0-9a-fA-F]+);|&#(\d+);');
  output = output.replaceAllMapped(numeric, (match) {
    final hex = match.group(1);
    final code = hex != null
        ? int.tryParse(hex, radix: 16)
        : int.tryParse(match.group(2) ?? '');
    if (code == null || code <= 0 || code > 0x10FFFF) return '';
    return String.fromCharCode(code);
  });
  const named = {
    '&nbsp;': ' ',
    '&amp;': '&',
    '&lt;': '<',
    '&gt;': '>',
    '&quot;': '"',
    '&#39;': "'",
    '&apos;': "'",
    '&hellip;': '…',
    '&mdash;': '—',
    '&ndash;': '–',
    '&lsquo;': '『',
    '&rsquo;': '』',
    '&ldquo;': '「',
    '&rdquo;': '」',
  };
  named.forEach((entity, replacement) {
    output = output.replaceAll(entity, replacement);
  });
  return output;
}

// ─────────────────────────────────────────────────────────────────────────────
// HTML tree
//
// A tiny tolerant tokenizer + tree builder. Unlike layered regular expressions
// it cannot be confused by nested wrappers, unbalanced markup, iframes inside
// paragraphs, or Word-pasted `<span>` runs — every tag nests by position, and
// stray close tags are ignored instead of corrupting the scan.
// ─────────────────────────────────────────────────────────────────────────────

sealed class HtmlNode {
  const HtmlNode();
}

class HtmlText extends HtmlNode {
  const HtmlText(this.text);
  final String text;
}

class HtmlElement extends HtmlNode {
  /// [children] defaults to a fresh growable list: the tokenizer appends to
  /// it while building the tree.
  HtmlElement(this.tag, {Map<String, String>? attributes})
      : attributes = attributes ?? const {},
        children = <HtmlNode>[];
  final String tag;
  final Map<String, String> attributes;
  final List<HtmlNode> children;

  bool hasClass(String name) =>
      (attributes['class'] ?? '').split(RegExp(r'\s+')).contains(name);

  /// First descendant element with [tag], depth-first.
  HtmlElement? firstByTag(String tag) {
    for (final child in children) {
      if (child is HtmlElement) {
        if (child.tag == tag) return child;
        final found = child.firstByTag(tag);
        if (found != null) return found;
      }
    }
    return null;
  }

  /// Deep text of this subtree, entities decoded, whitespace collapsed.
  String get text {
    final buffer = StringBuffer();
    void visit(HtmlNode node) {
      if (node is HtmlText) {
        buffer.write(node.text);
      } else if (node is HtmlElement) {
        for (final child in node.children) {
          visit(child);
        }
      }
    }

    for (final child in children) {
      visit(child);
    }
    return _collapseWhitespace(decodeHtmlEntities(buffer.toString()));
  }
}

const _voidElements = {
  'area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input', 'link',
  'meta', 'param', 'source', 'track', 'wbr',
};

final RegExp _tagNamePattern = RegExp(r'^[a-zA-Z][a-zA-Z0-9:._-]*');
final RegExp _attributePattern = RegExp(
    "([:a-zA-Z_][-:\\w.]*)(?:\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s\">]+))?");

/// Parses [html] into a tree rooted at a `#document` element. Never throws:
/// malformed regions degrade to text or are skipped.
HtmlElement parseHtmlDocument(String html) {
  final document = HtmlElement('#document');
  final stack = <HtmlElement>[document];
  var i = 0;
  while (i < html.length) {
    final lt = html.indexOf('<', i);
    if (lt < 0) {
      stack.last.children.add(HtmlText(html.substring(i)));
      break;
    }
    if (lt > i) stack.last.children.add(HtmlText(html.substring(i, lt)));

    // Comments.
    if (html.startsWith('<!--', lt)) {
      final end = html.indexOf('-->', lt + 4);
      i = end < 0 ? html.length : end + 3;
      continue;
    }
    // CDATA sections (RSS feeds) become plain text.
    if (html.startsWith('<![CDATA[', lt)) {
      final end = html.indexOf(']]>', lt + 9);
      final content = end < 0
          ? html.substring(lt + 9)
          : html.substring(lt + 9, end);
      stack.last.children.add(HtmlText(content));
      i = end < 0 ? html.length : end + 3;
      continue;
    }
    // Doctypes and other `<! … >` constructs.
    if (html.startsWith('<!', lt)) {
      final end = html.indexOf('>', lt + 2);
      i = end < 0 ? html.length : end + 1;
      continue;
    }
    // Closing tags.
    if (html.startsWith('</', lt)) {
      final gt = html.indexOf('>', lt + 2);
      if (gt < 0) break;
      final tag = html.substring(lt + 2, gt).trim().toLowerCase();
      for (var depth = stack.length - 1; depth > 0; depth--) {
        if (stack[depth].tag == tag) {
          stack.length = depth;
          break;
        }
      }
      i = gt + 1;
      continue;
    }

    // Opening tag: scan to its `>` while respecting quoted attribute values.
    final nameMatch = _tagNamePattern.firstMatch(html.substring(lt + 1));
    if (nameMatch == null) {
      // Stray `<` — keep it as literal text.
      stack.last.children.add(const HtmlText('<'));
      i = lt + 1;
      continue;
    }
    var j = lt + 1 + nameMatch.end;
    var quote = '';
    while (j < html.length) {
      final ch = html[j];
      if (quote.isNotEmpty) {
        if (ch == quote) quote = '';
      } else if (ch == '"' || ch == "'") {
        quote = ch;
      } else if (ch == '>') {
        break;
      }
      j++;
    }
    if (j >= html.length) break; // Unterminated tag: discard the tail.
    final rawTag = html.substring(lt + 1, j);
    i = j + 1;

    final tag = nameMatch.group(0)!.toLowerCase();
    final selfClosing = rawTag.trimRight().endsWith('/');
    final attributes = <String, String>{};
    for (final attr in _attributePattern.allMatches(rawTag.substring(nameMatch.end))) {
      final name = attr.group(1)!.toLowerCase();
      var value = attr.group(2) ?? '';
      if (value.length >= 2 &&
          ((value.startsWith('"') && value.endsWith('"')) ||
              (value.startsWith("'") && value.endsWith("'")))) {
        value = value.substring(1, value.length - 1);
      }
      attributes.putIfAbsent(name, () => value);
    }

    final element = HtmlElement(tag, attributes: attributes);
    stack.last.children.add(element);

    // Raw-text elements: swallow everything up to their closing tag.
    if (tag == 'script' || tag == 'style') {
      final close = html.indexOf('</$tag', i);
      if (close < 0) break;
      final gt = html.indexOf('>', close);
      i = gt < 0 ? html.length : gt + 1;
      continue;
    }
    if (!selfClosing && !_voidElements.contains(tag)) {
      stack.add(element);
    }
  }
  return document;
}

String _collapseWhitespace(String input) =>
    input.replaceAll(RegExp(r'\s+'), ' ').trim();

/// Serialises a parsed subtree back to HTML. Fallback-fetched posts store
/// their content container this way so [decodeDevotionPostsCache] can
/// re-parse them offline exactly like API/RSS posts.
String serializeHtmlElement(HtmlElement element) => _nodeToHtml(element);

String _nodeToHtml(HtmlNode node) {
  if (node is HtmlText) return node.text;
  if (node is! HtmlElement) return '';
  final attributes = node.attributes.entries
      .map((entry) =>
          entry.value.isEmpty ? ' ${entry.key}' : ' ${entry.key}="${entry.value}"')
      .join();
  if (_voidElements.contains(node.tag)) return '<${node.tag}$attributes>';
  final inner = node.children.map(_nodeToHtml).join();
  return '<${node.tag}$attributes>$inner</${node.tag}>';
}

// ─────────────────────────────────────────────────────────────────────────────
// Tree → devotion blocks
// ─────────────────────────────────────────────────────────────────────────────

/// Converts WordPress post HTML into renderable blocks.
///
/// Handles every shape the blog publishes:
///  * flat titled sections (`詩歌靈修`: dove / explain / praying-hands / pray),
///  * nested titled sections (`觀畫靈修`: dove > verse > explain …),
///  * plain articles (`靈修默想`) with headings, lists, quotes and galleries,
///  * YouTube iframes wrapped inside paragraphs, linked images, Word-pasted
///    `<span>` runs and CJK image URLs.
List<DevotionBlock> parseDevotionBlocks(String html) {
  if (html.trim().isEmpty) return const [];
  return parseDevotionBlocksFromElement(parseHtmlDocument(html));
}

/// Same conversion starting from an already-parsed subtree — used by the
/// site-page fallback, which locates the content container inside a whole
/// article page before converting it.
List<DevotionBlock> parseDevotionBlocksFromElement(HtmlElement root) {
  final blocks = _convertChildren(root);
  final flattened = _flattenDevotionSections(blocks);
  return _deduplicateDevotionImages(flattened);
}

List<DevotionBlock> _convertChildren(HtmlElement parent) {
  final blocks = <DevotionBlock>[];
  for (final node in parent.children) {
    if (node is HtmlElement) _convertElement(node, blocks);
  }
  return blocks;
}

void _convertElement(HtmlElement element, List<DevotionBlock> blocks) {
  switch (element.tag) {
    case 'div':
      final titleChild = _titledChild(element);
      if (titleChild != null) {
        final body = <HtmlNode>[
          for (final child in element.children)
            if (child != titleChild) child,
        ];
        blocks.add(
          DevotionSection(
            title: titleChild.text,
            blocks: _convertNodesOf(body),
          ),
        );
      } else {
        _convertContainer(element, blocks);
      }
    case 'p':
      _convertRichContent(element, blocks, DevotionParagraph.new);
    case 'h1' || 'h2' || 'h3' || 'h4' || 'h5' || 'h6':
      _convertRichContent(element, blocks, DevotionHeading.new);
    case 'blockquote':
      _convertRichContent(element, blocks, DevotionQuote.new);
    case 'li':
      _convertRichContent(
        element,
        blocks,
        (text) => DevotionParagraph('• $text'),
      );
    case 'ul' || 'ol':
      blocks.addAll(_convertChildren(element));
    case 'figure':
      _extractMedia(element, blocks);
    case 'img':
      _addImage(element, blocks);
    case 'iframe':
      _addVideo(element, blocks);
    case 'script' || 'style' || 'head':
      break;
    default:
      _convertContainer(element, blocks);
  }
}

/// The first direct child styled as a section title, if any.
HtmlElement? _titledChild(HtmlElement element) {
  for (final child in element.children) {
    if (child is HtmlElement && child.hasClass('title')) return child;
  }
  return null;
}

List<DevotionBlock> _convertNodesOf(List<HtmlNode> nodes) {
  final blocks = <DevotionBlock>[];
  for (final node in nodes) {
    if (node is HtmlElement) _convertElement(node, blocks);
  }
  return blocks;
}

/// Transparent wrapper: promote children; keep orphan text as a paragraph so
/// unlabeled wrappers never swallow content.
void _convertContainer(HtmlElement element, List<DevotionBlock> blocks) {
  final converted = _convertChildren(element);
  if (converted.isNotEmpty) {
    blocks.addAll(converted);
    return;
  }
  final text = element.text;
  if (text.isNotEmpty) blocks.add(DevotionParagraph(text));
}

/// Paragraph-like nodes: text accumulates into one block while embedded
/// media (images, YouTube iframes) is emitted in document order.
void _convertRichContent(
  HtmlElement element,
  List<DevotionBlock> blocks,
  DevotionBlock Function(String text) build,
) {
  final buffer = StringBuffer();
  void flush() {
    final text = _collapseWhitespace(buffer.toString());
    buffer.clear();
    if (text.isNotEmpty) blocks.add(build(text));
  }

  void visit(HtmlNode node) {
    if (node is HtmlText) {
      buffer.write(decodeHtmlEntities(node.text));
      return;
    }
    if (node is! HtmlElement) return;
    switch (node.tag) {
      case 'br':
        buffer.write(' ');
      case 'img':
        flush();
        _addImage(node, blocks);
      case 'iframe':
        flush();
        _addVideo(node, blocks);
      case 'script' || 'style':
        break;
      default:
        for (final child in node.children) {
          visit(child);
        }
    }
  }

  for (final child in element.children) {
    visit(child);
  }
  flush();
}

/// Figures and other containers that exist purely to hold media.
void _extractMedia(HtmlElement element, List<DevotionBlock> blocks) {
  void visit(HtmlNode node) {
    if (node is! HtmlElement) return;
    switch (node.tag) {
      case 'img':
        _addImage(node, blocks);
      case 'iframe':
        _addVideo(node, blocks);
      default:
        for (final child in node.children) {
          visit(child);
        }
    }
  }

  for (final child in element.children) {
    visit(child);
  }
}

void _addImage(HtmlElement img, List<DevotionBlock> blocks) {
  var src = img.attributes['src']?.trim() ?? '';
  // Lazy-loading plugins park a placeholder in `src` (typically a 1×1 SVG)
  // and keep the real image in `data-src`. Only trust `src` when it points
  // somewhere real.
  if (src.isEmpty || src.startsWith('data:')) {
    src = img.attributes['data-src']?.trim() ?? '';
  }
  if (src.isEmpty || src.startsWith('data:')) return;
  blocks.add(
    DevotionImage(normalizeDevotionImageUrl(decodeHtmlEntities(src))),
  );
}

void _addVideo(HtmlElement frame, List<DevotionBlock> blocks) {
  final src = frame.attributes['src']?.trim() ?? '';
  if (src.isEmpty) return;
  final decoded = decodeHtmlEntities(src);
  final id = parseYouTubeId(decoded);
  if (id != null) {
    blocks.add(DevotionVideo(id));
  } else {
    // SoundCloud and other players: keep the embed instead of dropping it.
    blocks.add(DevotionEmbed(normalizeDevotionImageUrl(decoded)));
  }
}

/// Flattens the deeply-nested WordPress wrappers so that logical sections
/// appear as siblings: `安靜` → `經文…` → `觀畫` etc. Without this the 觀畫靈修
/// renders three levels deep and looks broken.
List<DevotionBlock> _flattenDevotionSections(List<DevotionBlock> blocks) {
  final result = <DevotionBlock>[];
  for (final block in blocks) {
    if (block is DevotionSection) {
      final flattenedChildren = _flattenDevotionSections(block.blocks);
      if (_isContainerSection(block.title)) {
        final childSections =
            flattenedChildren.whereType<DevotionSection>().toList();
        final childNonSections =
            flattenedChildren.where((b) => b is! DevotionSection).toList();
        if (childSections.isNotEmpty) {
          result.add(
            DevotionSection(title: block.title, blocks: childNonSections),
          );
          result.addAll(childSections);
          continue;
        }
      }
      result.add(DevotionSection(title: block.title, blocks: flattenedChildren));
    } else {
      result.add(block);
    }
  }
  return result;
}

bool _isContainerSection(String title) {
  final t = title.trim();
  if (t == '安靜' || t == '安静') return true;
  if (t.startsWith('經文') || t.startsWith('经文')) return true;
  return false;
}

/// Removes duplicate image URLs (WordPress often emits the same painting at
/// multiple sizes: `…-1024x768.jpg`, `…-300x200.jpg`). Keeps first occurrence.
List<DevotionBlock> _deduplicateDevotionImages(List<DevotionBlock> blocks) {
  final seen = <String>{};
  String normalize(String url) {
    // Strip WordPress size suffix `-1024x768` before extension and query.
    final qIndex = url.indexOf('?');
    final base = qIndex >= 0 ? url.substring(0, qIndex) : url;
    final query = qIndex >= 0 ? url.substring(qIndex) : '';
    final normalizedBase =
        base.replaceAll(RegExp(r'-\d+x\d+(?=\.\w+$)'), '');
    return '$normalizedBase$query';
  }

  List<DevotionBlock> dedup(List<DevotionBlock> input) {
    final out = <DevotionBlock>[];
    for (final block in input) {
      if (block is DevotionImage) {
        final key = normalize(block.url);
        if (seen.contains(key)) continue;
        seen.add(key);
        out.add(block);
      } else if (block is DevotionSection) {
        out.add(
          DevotionSection(title: block.title, blocks: dedup(block.blocks)),
        );
      } else {
        out.add(block);
      }
    }
    return out;
  }

  return dedup(blocks);
}

// ─────────────────────────────────────────────────────────────────────────────
// Fetching
// ─────────────────────────────────────────────────────────────────────────────

const devotionOrigin = 'https://devotion.wkphc.org';
const _kDevotionApi = '$devotionOrigin/wp-json/wp/v2/posts';
const _kDevotionRss = '$devotionOrigin/feed';

Future<List<DevotionPost>> fetchDevotionPosts({http.Client? client}) async {
  final shared = client ?? http.Client();
  try {
    try {
      return await _fetchFromRestApi(shared);
    } catch (_) {
      // The REST API can be disabled or blocked by security plugins while the
      // classic RSS feed stays open — degrade to it so today's devotion still
      // loads (the site's feed exposes only the single latest post).
      return await _fetchFromRssFeed(shared);
    }
  } catch (_) {
    // Even the feed can be unreachable (CORS on web builds, aggressive
    // firewalls or plugin quirks on mobile). Last resort: read exactly what
    // any browser reads — the public pages themselves. As long as the site
    // renders somewhere, the newest devotions land in the app.
    return _fetchFromSitePages(shared);
  } finally {
    if (client == null) shared.close();
  }
}

Future<List<DevotionPost>> _fetchFromRestApi(http.Client client) async {
  const fields = 'id,date_gmt,date,title,content,link';
  // Primary: pretty-permalink route. Fallback: `index.php?rest_route=…`,
  // which survives security plugins / permalink setups that block /wp-json.
  final uris = [
    Uri.parse(_kDevotionApi).replace(
      queryParameters: {'per_page': '20', '_fields': fields},
    ),
    Uri.parse('$devotionOrigin/index.php').replace(
      queryParameters: {'rest_route': '/wp/v2/posts', 'per_page': '20', '_fields': fields},
    ),
  ];
  Object? lastError;
  for (final uri in uris) {
    try {
      final response = await client
          .get(uri, headers: _kDevotionHeaders)
          .timeout(const Duration(seconds: 20));
      if (response.statusCode != 200) {
        throw http.ClientException('devotion api ${response.statusCode}');
      }
      return _decodeRestPosts(utf8.decode(response.bodyBytes));
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError ?? http.ClientException('devotion api unreachable');
}

List<DevotionPost> _decodeRestPosts(String body) {
  final decoded = jsonDecode(body) as List<dynamic>;
  final posts = <DevotionPost>[];
  for (final item in decoded) {
    if (item is! Map<String, dynamic>) continue;
    final id = (item['id'] as num?)?.toInt() ?? 0;
    final dateRaw = item['date_gmt'] as String? ?? item['date'] as String?;
    final publishDate =
        DateTime.tryParse(dateRaw ?? '')?.toLocal() ?? DateTime.now();
    final titleHtml = (item['title'] as Map<String, dynamic>?)?['rendered'] as String? ?? '';
    final rawTitle = decodeHtmlEntities(stripHtmlTags(titleHtml)).trim();
    final devotionDate = parseDevotionTitleDate(rawTitle) ?? publishDate;
    final link = item['link'] as String? ?? devotionOrigin;
    final contentHtml =
        (item['content'] as Map<String, dynamic>?)?['rendered'] as String? ?? '';
    posts.add(
      DevotionPost(
        id: id,
        date: publishDate,
        devotionDate: devotionDate,
        title: rawTitle,
        link: link,
        contentHtml: contentHtml,
        blocks: parseDevotionBlocks(contentHtml),
      ),
    );
  }
  if (posts.isEmpty) {
    throw http.ClientException('devotion api returned no posts');
  }
  // The blog's publish order does not match the devotion calendar date
  // embedded in each title (e.g. a 7月22日 devotion can be published on
  // 8月21日). Sort by the title date so “today” is actually today.
  posts.sort((a, b) => b.devotionDate.compareTo(a.devotionDate));
  return posts;
}

Future<List<DevotionPost>> _fetchFromRssFeed(http.Client client) async {
  final response = await client
      .get(Uri.parse(_kDevotionRss), headers: _kDevotionHeaders)
      .timeout(const Duration(seconds: 20));
  if (response.statusCode != 200) {
    throw http.ClientException('devotion rss ${response.statusCode}');
  }
  final xml = utf8.decode(response.bodyBytes);
  final posts = parseDevotionRssItems(xml);
  if (posts.isEmpty) {
    throw http.ClientException('devotion rss had no items');
  }
  posts.sort((a, b) => b.devotionDate.compareTo(a.devotionDate));
  return posts;
}

/// Parses every `<item>` of the WordPress RSS feed into posts.
List<DevotionPost> parseDevotionRssItems(String xml) {
  final posts = <DevotionPost>[];
  for (final match in RegExp(
    r'<item>([\s\S]*?)</item>',
    caseSensitive: false,
  ).allMatches(xml)) {
    final post = _devotionPostFromRssItem(match.group(1)!);
    if (post != null) posts.add(post);
  }
  return posts;
}

/// Parses the first `<item>` of the WordPress RSS feed into a post.
DevotionPost? parseDevotionRssItem(String xml) {
  final itemMatch =
      RegExp(r'<item>([\s\S]*?)</item>', caseSensitive: false).firstMatch(xml);
  if (itemMatch == null) return null;
  return _devotionPostFromRssItem(itemMatch.group(1)!);
}

DevotionPost? _devotionPostFromRssItem(String item) {
  String tag(String name) {
    final match = RegExp(
      '<$name[^>]*>([\\s\\S]*?)</$name>',
      caseSensitive: false,
    ).firstMatch(item);
    if (match == null) return '';
    var value = match.group(1) ?? '';
    final cdata = RegExp(r'^<!\[CDATA\[([\s\S]*)\]\]>').firstMatch(value);
    if (cdata != null) value = cdata.group(1)!;
    return value.trim();
  }

  final rawTitle = decodeHtmlEntities(stripHtmlTags(tag('title'))).trim();
  final contentHtml = tag('content:encoded');
  final fallbackHtml = tag('description');
  final body = contentHtml.isNotEmpty ? contentHtml : fallbackHtml;
  final link = decodeHtmlEntities(tag('link')).trim();
  final publishDate = parseRfc822Date(tag('pubDate'));

  return DevotionPost(
    id: 0,
    date: publishDate,
    devotionDate: parseDevotionTitleDate(rawTitle) ?? publishDate,
    title: rawTitle,
    link: link.isEmpty ? devotionOrigin : link,
    contentHtml: body,
    blocks: parseDevotionBlocks(body),
  );
}

/// Minimal RFC-822 date parser ("Fri, 21 Aug 2026 09:46:05 +0000").
/// `dart:io`'s [HttpDate] is unavailable on web and throws on drift, so this
/// stays tolerant and never throws.
DateTime parseRfc822Date(String raw) {
  const months = {
    'jan': 1, 'feb': 2, 'mar': 3, 'apr': 4, 'may': 5, 'jun': 6,
    'jul': 7, 'aug': 8, 'sep': 9, 'oct': 10, 'nov': 11, 'dec': 12,
  };
  final match = RegExp(
    r'(\d{1,2})\s+([A-Za-z]{3})[a-z]*\s+(\d{2,4})'
    r'(?:\s+(\d{1,2}):(\d{2})(?::(\d{2}))?)?',
  ).firstMatch(raw.trim());
  if (match == null) return DateTime.now();
  final day = int.tryParse(match.group(1)!) ?? 1;
  final month = months[match.group(2)!.toLowerCase()] ?? 1;
  var year = int.tryParse(match.group(3)!) ?? DateTime.now().year;
  if (year < 100) year += 2000;
  final hour = int.tryParse(match.group(4) ?? '') ?? 0;
  final minute = int.tryParse(match.group(5) ?? '') ?? 0;
  final second = int.tryParse(match.group(6) ?? '') ?? 0;
  // Ignore the zone offset and treat as local — the title date drives order.
  return DateTime(year, month, day, hour, minute, second);
}

// ─────────────────────────────────────────────────────────────────────────────
// Site-page fallback (tier 3)
//
// Reads the public pages themselves: post permalinks are discovered on the
// homepage list, then each article page's content container is parsed with
// the same engine used for API/RSS HTML.
// ─────────────────────────────────────────────────────────────────────────────

/// A recent-post permalink discovered on the site homepage. [title] is the
/// anchor text, which the blog fills with the full post title including its
/// devotion date ("[圖片靈修] 耶利哥 – 2026年8月22日").
class ScrapedPostLink {
  const ScrapedPostLink({required this.url, required this.title});

  final String url;
  final String title;
}

final RegExp _kAbsolutePostUrl =
    RegExp(r'^https?://devotion\.wkphc\.org/(\d{3,})/?$');
final RegExp _kRelativePostUrl = RegExp(r'^/(\d{3,})/?$');

/// Normalises a homepage href into a canonical permalink, `null` when it
/// points somewhere else (pages, categories, uploads…).
String? normalizePostHref(String rawHref) {
  var href = decodeHtmlEntities(rawHref).trim();
  final hash = href.indexOf('#');
  if (hash >= 0) href = href.substring(0, hash);
  final query = href.indexOf('?');
  if (query >= 0) href = href.substring(0, query);
  final isPost =
      _kAbsolutePostUrl.hasMatch(href) || _kRelativePostUrl.hasMatch(href);
  if (!isPost) return null;
  if (href.endsWith('/')) href = href.substring(0, href.length - 1);
  return '$devotionOrigin/${href.substring(href.lastIndexOf('/') + 1)}';
}

/// Extracts every post permalink (+ anchor-text title) from a homepage,
/// document order preserved so "newest first" survives without dates.
List<ScrapedPostLink> extractPostLinksFromPage(String html) {
  final seen = <String>{};
  final links = <ScrapedPostLink>[];
  void visit(HtmlNode node) {
    if (node is! HtmlElement) return;
    if (node.tag == 'a') {
      final url = normalizePostHref(node.attributes['href'] ?? '');
      if (url != null && seen.add(url)) {
        links.add(
          ScrapedPostLink(url: url, title: node.text.trim()),
        );
      }
      return; // Anchors never nest.
    }
    for (final child in node.children) {
      visit(child);
    }
  }

  visit(parseHtmlDocument(html));
  return links;
}

/// Container classes WordPress themes use for the post body, most likely
/// first. The live theme ships `entry-content`.
const _kContentClassNames = [
  'entry-content',
  'post-content',
  'article-content',
  'the-content',
];

HtmlElement _findContentRoot(HtmlElement document) {
  HtmlElement? found;
  bool matches(HtmlElement element) {
    final classes =
        (element.attributes['class'] ?? '').split(RegExp(r'\s+'));
    return classes.any(_kContentClassNames.contains);
  }

  void visit(HtmlElement element) {
    if (found != null || matches(element)) {
      found ??= element;
      return;
    }
    for (final child in element.children) {
      if (child is HtmlElement) visit(child);
      if (found != null) return;
    }
  }

  visit(document);
  return found ??
      document.firstByTag('article') ??
      document.firstByTag('body') ??
      document;
}

/// Best-effort page title: the entry heading first, then `<title>` minus the
/// known site suffix. The date regex only needs the title to contain a date,
/// so imperfect fallbacks stay harmless.
String? extractArticleTitle(HtmlElement document) {
  final heading = document.firstByTag('h1');
  final headingText = heading?.text.trim() ?? '';
  if (headingText.isNotEmpty) return headingText;
  final titleTag = document.firstByTag('title');
  final titleText = titleTag?.text.trim() ?? '';
  if (titleText.isEmpty) return null;
  return titleText
      .replaceFirst(RegExp(r'\s*[-–—]\s*靈修默想\s*$'), '')
      .trim();
}

/// Builds a [DevotionPost] from a whole article page. Returns `null` when no
/// usable content container exists — the caller skips such pages.
DevotionPost? parseArticlePage({
  required String url,
  required String html,
  String? titleHint,
}) {
  final document = parseHtmlDocument(html);
  final content = _findContentRoot(document);
  if (content.children.isEmpty) return null;
  final title = (titleHint != null && titleHint.isNotEmpty)
      ? titleHint
      : extractArticleTitle(document) ?? '';
  if (title.isEmpty) return null;
  final id = int.tryParse(
        Uri.tryParse(url)?.pathSegments.lastWhere(
              (segment) => segment.isNotEmpty,
              orElse: () => '',
            ) ??
            '',
      ) ??
      0;
  // Page markup carries no machine-readable publish time; the title date is
  // what orders posts, and [date] only feeds the cache.
  final fetchedAt = DateTime.now();
  return DevotionPost(
    id: id,
    date: fetchedAt,
    devotionDate: parseDevotionTitleDate(title) ?? fetchedAt,
    title: title,
    link: url,
    contentHtml: serializeHtmlElement(content),
    blocks: parseDevotionBlocksFromElement(content),
  );
}

Future<List<DevotionPost>> _fetchFromSitePages(http.Client client) async {
  final homeResponse = await client
      .get(Uri.parse(devotionOrigin), headers: _kDevotionHeaders)
      .timeout(const Duration(seconds: 20));
  if (homeResponse.statusCode != 200) {
    throw http.ClientException('devotion homepage ${homeResponse.statusCode}');
  }
  final links = extractPostLinksFromPage(utf8.decode(homeResponse.bodyBytes));
  if (links.isEmpty) {
    throw http.ClientException('devotion site listed no posts');
  }
  final posts = <DevotionPost>[];
  for (final link in links.take(12)) {
    try {
      final response = await client
          .get(Uri.parse(link.url), headers: _kDevotionHeaders)
          .timeout(const Duration(seconds: 20));
      if (response.statusCode != 200) continue;
      final post = parseArticlePage(
        url: link.url,
        html: utf8.decode(response.bodyBytes),
        titleHint: link.title,
      );
      if (post != null) posts.add(post);
    } catch (_) {
      // One unreachable article shouldn't sink the rest.
    }
  }
  if (posts.isEmpty) {
    throw http.ClientException('devotion site pages produced no posts');
  }
  posts.sort((a, b) => b.devotionDate.compareTo(a.devotionDate));
  return posts;
}

// ─────────────────────────────────────────────────────────────────────────────
// Offline cache
// ─────────────────────────────────────────────────────────────────────────────

/// SharedPreferences key holding the last successfully fetched devotions.
const _kDevotionCacheKey = 'devotion_cache_v1';

/// Serialises raw post data (not parsed blocks) so the cache survives parser
/// improvements — old caches re-parse with current logic on next launch.
String encodeDevotionPostsCache(List<DevotionPost> posts) => jsonEncode([
      for (final post in posts)
        {
          'id': post.id,
          'date': post.date.toIso8601String(),
          'title': post.title,
          'link': post.link,
          'content': post.contentHtml,
        },
    ]);

List<DevotionPost> decodeDevotionPostsCache(String raw) {
  final decoded = jsonDecode(raw) as List<dynamic>;
  final posts = <DevotionPost>[];
  for (final item in decoded) {
    if (item is! Map<String, dynamic>) continue;
    final contentHtml = item['content'] as String? ?? '';
    if (contentHtml.isEmpty) continue;
    final title = (item['title'] as String? ?? '').trim();
    final date =
        DateTime.tryParse(item['date'] as String? ?? '') ?? DateTime.now();
    posts.add(
      DevotionPost(
        id: (item['id'] as num?)?.toInt() ?? 0,
        date: date,
        devotionDate: parseDevotionTitleDate(title) ?? date,
        title: title,
        link: item['link'] as String? ?? devotionOrigin,
        contentHtml: contentHtml,
        blocks: parseDevotionBlocks(contentHtml),
      ),
    );
  }
  if (posts.isNotEmpty) {
    posts.sort((a, b) => b.devotionDate.compareTo(a.devotionDate));
  }
  return posts;
}

Future<List<DevotionPost>> readDevotionCache() async {
  try {
    final preferences = await SharedPreferences.getInstance();
    final raw = preferences.getString(_kDevotionCacheKey);
    if (raw == null || raw.isEmpty) return const [];
    return decodeDevotionPostsCache(raw);
  } catch (_) {
    return const [];
  }
}

Future<void> writeDevotionCache(List<DevotionPost> fetched) async {
  try {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString(
      _kDevotionCacheKey,
      encodeDevotionPostsCache(fetched),
    );
  } catch (_) {
    // Cache is best-effort; never break loading over it.
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Date formatting
// ─────────────────────────────────────────────────────────────────────────────

const _englishMonths = [
  'Jan',
  'Feb',
  'Mar',
  'Apr',
  'May',
  'Jun',
  'Jul',
  'Aug',
  'Sep',
  'Oct',
  'Nov',
  'Dec',
];

String formatDevotionDate(DateTime date, AppLocale locale) {
  if (locale == AppLocale.en) {
    return '${_englishMonths[date.month - 1]} ${date.day}, ${date.year}';
  }
  return '${date.year}年${date.month}月${date.day}日';
}

String formatDevotionDateShort(DateTime date, AppLocale locale) {
  if (locale == AppLocale.en) {
    return '${_englishMonths[date.month - 1]} ${date.day}';
  }
  return '${date.month}月${date.day}日';
}
