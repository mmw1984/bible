import 'dart:convert';

import 'package:flutter/services.dart';

class BibleBook {
  const BibleBook(this.id, this.zh, this.en, this.chapters, this.old);
  final String id, zh, en;
  final int chapters;
  final bool old;
}

class VersePair {
  const VersePair(this.number, this.zh, this.en);
  final int number;
  final String zh, en;
}

class ScriptureHit {
  const ScriptureHit({
    required this.book,
    required this.chapter,
    required this.verse,
  });

  final BibleBook book;
  final int chapter;
  final VersePair verse;
}

class BibleRepository {
  final Map<String, Map<String, List<String>>> _assetCache = {};

  Future<List<VersePair>> chapter(BibleBook book, int chapter) async {
    final result = await Future.wait([
      _translation('cuv', book.id, chapter),
      _translation('web', book.id, chapter),
    ]);
    final count = result[0].length > result[1].length
        ? result[0].length
        : result[1].length;
    return List.generate(
      count,
      (i) => VersePair(
        i + 1,
        i < result[0].length ? result[0][i] : '',
        i < result[1].length ? result[1][i] : '',
      ),
    );
  }

  Future<List<ScriptureHit>> search(String query, {int limit = 80}) async {
    final needle = query.trim().toLowerCase();
    if (needle.isEmpty) return const [];
    final hits = <ScriptureHit>[];
    final books = await Future.wait(
      bibleBooks.map((book) async {
        final translations = await Future.wait([
          _assetBook('cuv', book.id),
          _assetBook('web', book.id),
        ]);
        return (book, translations[0], translations[1]);
      }),
    );
    for (final (book, chinese, english) in books) {
      for (var chapter = 1; chapter <= book.chapters; chapter++) {
        final zh = chinese['$chapter'] ?? const <String>[];
        final en = english['$chapter'] ?? const <String>[];
        final count = zh.length > en.length ? zh.length : en.length;
        for (var index = 0; index < count; index++) {
          final pair = VersePair(
            index + 1,
            index < zh.length ? zh[index] : '',
            index < en.length ? en[index] : '',
          );
          if (pair.zh.toLowerCase().contains(needle) ||
              pair.en.toLowerCase().contains(needle)) {
            hits.add(ScriptureHit(book: book, chapter: chapter, verse: pair));
            if (hits.length >= limit) return hits;
          }
        }
      }
    }
    return hits;
  }

  Future<Map<String, List<String>>> _assetBook(
    String translation,
    String book,
  ) async {
    final key = '$translation/$book';
    final cached = _assetCache[key];
    if (cached != null) return cached;
    final path = 'assets/bible/$translation/$book.json';
    try {
      final payload = await rootBundle.loadString(path);
      final json = jsonDecode(payload) as Map<String, dynamic>;
      final chapters = (json['chapters'] as Map<String, dynamic>).map(
        (number, verses) => MapEntry(number, (verses as List).cast<String>()),
      );
      _assetCache[key] = chapters;
      return chapters;
    } on Object catch (error) {
      throw StateError(
        'Bundled Bible asset is missing or invalid: $path\n$error',
      );
    }
  }

  Future<List<String>> _translation(
    String translation,
    String book,
    int chapter,
  ) async {
    final local = (await _assetBook(translation, book))['$chapter'];
    if (local != null && local.isNotEmpty) return local;
    throw StateError(
      'Bundled Bible chapter is missing: $translation/$book/$chapter',
    );
  }
}

const bibleBooks = <BibleBook>[
  BibleBook('GEN', '創世記', 'Genesis', 50, true),
  BibleBook('EXO', '出埃及記', 'Exodus', 40, true),
  BibleBook('LEV', '利未記', 'Leviticus', 27, true),
  BibleBook('NUM', '民數記', 'Numbers', 36, true),
  BibleBook('DEU', '申命記', 'Deuteronomy', 34, true),
  BibleBook('JOS', '約書亞記', 'Joshua', 24, true),
  BibleBook('JDG', '士師記', 'Judges', 21, true),
  BibleBook('RUT', '路得記', 'Ruth', 4, true),
  BibleBook('1SA', '撒母耳記上', '1 Samuel', 31, true),
  BibleBook('2SA', '撒母耳記下', '2 Samuel', 24, true),
  BibleBook('1KI', '列王紀上', '1 Kings', 22, true),
  BibleBook('2KI', '列王紀下', '2 Kings', 25, true),
  BibleBook('1CH', '歷代志上', '1 Chronicles', 29, true),
  BibleBook('2CH', '歷代志下', '2 Chronicles', 36, true),
  BibleBook('EZR', '以斯拉記', 'Ezra', 10, true),
  BibleBook('NEH', '尼希米記', 'Nehemiah', 13, true),
  BibleBook('EST', '以斯帖記', 'Esther', 10, true),
  BibleBook('JOB', '約伯記', 'Job', 42, true),
  BibleBook('PSA', '詩篇', 'Psalms', 150, true),
  BibleBook('PRO', '箴言', 'Proverbs', 31, true),
  BibleBook('ECC', '傳道書', 'Ecclesiastes', 12, true),
  BibleBook('SNG', '雅歌', 'Song of Songs', 8, true),
  BibleBook('ISA', '以賽亞書', 'Isaiah', 66, true),
  BibleBook('JER', '耶利米書', 'Jeremiah', 52, true),
  BibleBook('LAM', '耶利米哀歌', 'Lamentations', 5, true),
  BibleBook('EZK', '以西結書', 'Ezekiel', 48, true),
  BibleBook('DAN', '但以理書', 'Daniel', 12, true),
  BibleBook('HOS', '何西阿書', 'Hosea', 14, true),
  BibleBook('JOL', '約珥書', 'Joel', 3, true),
  BibleBook('AMO', '阿摩司書', 'Amos', 9, true),
  BibleBook('OBA', '俄巴底亞書', 'Obadiah', 1, true),
  BibleBook('JON', '約拿書', 'Jonah', 4, true),
  BibleBook('MIC', '彌迦書', 'Micah', 7, true),
  BibleBook('NAM', '那鴻書', 'Nahum', 3, true),
  BibleBook('HAB', '哈巴谷書', 'Habakkuk', 3, true),
  BibleBook('ZEP', '西番雅書', 'Zephaniah', 3, true),
  BibleBook('HAG', '哈該書', 'Haggai', 2, true),
  BibleBook('ZEC', '撒迦利亞書', 'Zechariah', 14, true),
  BibleBook('MAL', '瑪拉基書', 'Malachi', 4, true),
  BibleBook('MAT', '馬太福音', 'Matthew', 28, false),
  BibleBook('MRK', '馬可福音', 'Mark', 16, false),
  BibleBook('LUK', '路加福音', 'Luke', 24, false),
  BibleBook('JHN', '約翰福音', 'John', 21, false),
  BibleBook('ACT', '使徒行傳', 'Acts', 28, false),
  BibleBook('ROM', '羅馬書', 'Romans', 16, false),
  BibleBook('1CO', '哥林多前書', '1 Corinthians', 16, false),
  BibleBook('2CO', '哥林多後書', '2 Corinthians', 13, false),
  BibleBook('GAL', '加拉太書', 'Galatians', 6, false),
  BibleBook('EPH', '以弗所書', 'Ephesians', 6, false),
  BibleBook('PHP', '腓立比書', 'Philippians', 4, false),
  BibleBook('COL', '歌羅西書', 'Colossians', 4, false),
  BibleBook('1TH', '帖撒羅尼迦前書', '1 Thessalonians', 5, false),
  BibleBook('2TH', '帖撒羅尼迦後書', '2 Thessalonians', 3, false),
  BibleBook('1TI', '提摩太前書', '1 Timothy', 6, false),
  BibleBook('2TI', '提摩太後書', '2 Timothy', 4, false),
  BibleBook('TIT', '提多書', 'Titus', 3, false),
  BibleBook('PHM', '腓利門書', 'Philemon', 1, false),
  BibleBook('HEB', '希伯來書', 'Hebrews', 13, false),
  BibleBook('JAS', '雅各書', 'James', 5, false),
  BibleBook('1PE', '彼得前書', '1 Peter', 5, false),
  BibleBook('2PE', '彼得後書', '2 Peter', 3, false),
  BibleBook('1JN', '約翰壹書', '1 John', 5, false),
  BibleBook('2JN', '約翰貳書', '2 John', 1, false),
  BibleBook('3JN', '約翰參書', '3 John', 1, false),
  BibleBook('JUD', '猶大書', 'Jude', 1, false),
  BibleBook('REV', '啟示錄', 'Revelation', 22, false),
];
