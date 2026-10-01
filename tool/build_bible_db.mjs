#!/usr/bin/env node
/**
 * Builds the prepackaged scripture database from the bundled CUV/WEB JSON.
 *
 * Why a generated SQLite file instead of Room (NATIVE_PLAN.md §3.1 as amended):
 * Room validates the schema identity hash of a prepackaged database, and that
 * hash can only be produced by running Room itself, which means a build step
 * needs an Android runtime. The scripture is immutable, so it is read straight
 * out of a read-only SQLite file instead, and Room only owns the tables that
 * actually change. See `core/database`'s ScriptureQueries.
 *
 * Usage:
 *   node tool/build_bible_db.mjs            write app/src/main/assets/databases/bible.db
 *   node tool/build_bible_db.mjs --check    regenerate into a temp file and fail if the
 *                                           committed database is out of date
 *
 * Requires Node 22+ for the built-in `node:sqlite` module. No npm dependencies.
 */
import { DatabaseSync } from 'node:sqlite';
import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const TOOL_DIR = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(TOOL_DIR, '..');
const BIBLE_ASSETS = path.join(ROOT, 'legacy/flutter/assets/bible');
const FLUTTER_LIB = path.join(ROOT, 'legacy/flutter/lib');
const OUTPUT = path.join(ROOT, 'app/src/main/assets/databases/bible.db');

/** Expected shape of the bundled data, matching tool/verify_bible.mjs. */
const EXPECTED_BOOKS = 66;
const EXPECTED_CHAPTERS = 1189;

/**
 * Book metadata, in canonical order. Copied from the `bibleBooks` list in
 * `legacy/flutter/lib/bible_data.dart`, which is what the UI shows; the JSON
 * headers disagree with it in places. `verifyAgainstFlutterList()` below fails
 * the build if the two ever drift apart.
 */
const BOOKS = [
  ['GEN', '創世記', 'Genesis', 50, 0], ['EXO', '出埃及記', 'Exodus', 40, 0],
  ['LEV', '利未記', 'Leviticus', 27, 0], ['NUM', '民數記', 'Numbers', 36, 0],
  ['DEU', '申命記', 'Deuteronomy', 34, 0], ['JOS', '約書亞記', 'Joshua', 24, 0],
  ['JDG', '士師記', 'Judges', 21, 0], ['RUT', '路得記', 'Ruth', 4, 0],
  ['1SA', '撒母耳記上', '1 Samuel', 31, 0], ['2SA', '撒母耳記下', '2 Samuel', 24, 0],
  ['1KI', '列王紀上', '1 Kings', 22, 0], ['2KI', '列王紀下', '2 Kings', 25, 0],
  ['1CH', '歷代志上', '1 Chronicles', 29, 0], ['2CH', '歷代志下', '2 Chronicles', 36, 0],
  ['EZR', '以斯拉記', 'Ezra', 10, 0], ['NEH', '尼希米記', 'Nehemiah', 13, 0],
  ['EST', '以斯帖記', 'Esther', 10, 0], ['JOB', '約伯記', 'Job', 42, 0],
  ['PSA', '詩篇', 'Psalms', 150, 0], ['PRO', '箴言', 'Proverbs', 31, 0],
  ['ECC', '傳道書', 'Ecclesiastes', 12, 0], ['SNG', '雅歌', 'Song of Songs', 8, 0],
  ['ISA', '以賽亞書', 'Isaiah', 66, 0], ['JER', '耶利米書', 'Jeremiah', 52, 0],
  ['LAM', '耶利米哀歌', 'Lamentations', 5, 0], ['EZK', '以西結書', 'Ezekiel', 48, 0],
  ['DAN', '但以理書', 'Daniel', 12, 0], ['HOS', '何西阿書', 'Hosea', 14, 0],
  ['JOL', '約珥書', 'Joel', 3, 0], ['AMO', '阿摩司書', 'Amos', 9, 0],
  ['OBA', '俄巴底亞書', 'Obadiah', 1, 0], ['JON', '約拿書', 'Jonah', 4, 0],
  ['MIC', '彌迦書', 'Micah', 7, 0], ['NAM', '那鴻書', 'Nahum', 3, 0],
  ['HAB', '哈巴谷書', 'Habakkuk', 3, 0], ['ZEP', '西番雅書', 'Zephaniah', 3, 0],
  ['HAG', '哈該書', 'Haggai', 2, 0], ['ZEC', '撒迦利亞書', 'Zechariah', 14, 0],
  ['MAL', '瑪拉基書', 'Malachi', 4, 0], ['MAT', '馬太福音', 'Matthew', 28, 1],
  ['MRK', '馬可福音', 'Mark', 16, 1], ['LUK', '路加福音', 'Luke', 24, 1],
  ['JHN', '約翰福音', 'John', 21, 1], ['ACT', '使徒行傳', 'Acts', 28, 1],
  ['ROM', '羅馬書', 'Romans', 16, 1], ['1CO', '哥林多前書', '1 Corinthians', 16, 1],
  ['2CO', '哥林多後書', '2 Corinthians', 13, 1], ['GAL', '加拉太書', 'Galatians', 6, 1],
  ['EPH', '以弗所書', 'Ephesians', 6, 1], ['PHP', '腓立比書', 'Philippians', 4, 1],
  ['COL', '歌羅西書', 'Colossians', 4, 1], ['1TH', '帖撒羅尼迦前書', '1 Thessalonians', 5, 1],
  ['2TH', '帖撒羅尼迦後書', '2 Thessalonians', 3, 1], ['1TI', '提摩太前書', '1 Timothy', 6, 1],
  ['2TI', '提摩太後書', '2 Timothy', 4, 1], ['TIT', '提多書', 'Titus', 3, 1],
  ['PHM', '腓利門書', 'Philemon', 1, 1], ['HEB', '希伯來書', 'Hebrews', 13, 1],
  ['JAS', '雅各書', 'James', 5, 1], ['1PE', '彼得前書', '1 Peter', 5, 1],
  ['2PE', '彼得後書', '2 Peter', 3, 1], ['1JN', '約翰壹書', '1 John', 5, 1],
  ['2JN', '約翰貳書', '2 John', 1, 1], ['3JN', '約翰參書', '3 John', 1, 1],
  ['JUD', '猶大書', 'Jude', 1, 1], ['REV', '啟示錄', 'Revelation', 22, 1],
];

/** Same shape as `books` in the database. */
const SCHEMA = `
CREATE TABLE books (
  id       TEXT    NOT NULL PRIMARY KEY,
  ordinal  INTEGER NOT NULL,
  name_zh  TEXT    NOT NULL,
  name_en  TEXT    NOT NULL,
  chapters INTEGER NOT NULL,
  testament INTEGER NOT NULL
);

CREATE TABLE verses (
  book_id  TEXT    NOT NULL REFERENCES books(id),
  chapter  INTEGER NOT NULL,
  verse    INTEGER NOT NULL,
  text_cuv TEXT,
  text_web TEXT,
  PRIMARY KEY (book_id, chapter, verse)
) WITHOUT ROWID;

-- Mutable per-book reading state. Lives in the same file as the immutable
-- scripture so the reader needs a single database, but it is only ever written
-- through ScriptureQueries.upsertProgress().
CREATE TABLE reading_progress (
  book_id      TEXT    NOT NULL PRIMARY KEY REFERENCES books(id),
  chapter      INTEGER NOT NULL,
  verse        INTEGER,
  scroll_ratio REAL    NOT NULL DEFAULT 0,
  mode         TEXT    NOT NULL,
  updated_at   INTEGER NOT NULL
);

-- searchContains() scans both columns with LIKE '%needle%'. The index below
-- does not help a leading wildcard match, but it keeps the ordered scan on
-- canonical order instead of the storage order.
CREATE INDEX verses_scan ON verses (book_id, chapter, verse);
`;

function readTranslation(translation, bookId) {
  const file = path.join(BIBLE_ASSETS, translation, `${bookId}.json`);
  if (!fs.existsSync(file)) {
    throw new Error(`missing bundled scripture: ${file}`);
  }
  return JSON.parse(fs.readFileSync(file, 'utf8'));
}

/**
 * Fails if the BOOKS table above drifts from the Flutter list, so the names
 * the UI shows and the names baked into the database can never disagree.
 */
function verifyAgainstFlutterList() {
  const source = fs.readFileSync(path.join(FLUTTER_LIB, 'bible_data.dart'), 'utf8');
  const pattern = /BibleBook\('([A-Z0-9]+)', '([^']+)', '([^']+)', (\d+), (true|false)\)/g;
  const fromDart = [...source.matchAll(pattern)].map((m) => [
    m[1], m[2], m[3], Number(m[4]), m[5] === 'true' ? 0 : 1,
  ]);
  if (fromDart.length !== BOOKS.length) {
    throw new Error(
      `bible_data.dart lists ${fromDart.length} books but this tool lists ${BOOKS.length}`,
    );
  }
  fromDart.forEach((row, index) => {
    if (BOOKS[index].join('|') !== row.join('|')) {
      throw new Error(
        `book ${index} differs:\n  bible_data.dart: ${row.join(', ')}\n  this tool:  ${BOOKS[index].join(', ')}`,
      );
    }
  });
}

function build(target) {
  verifyAgainstFlutterList();
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.rmSync(target, { force: true });

  const db = new DatabaseSync(target);
  db.exec('PRAGMA journal_mode = OFF');
  db.exec('PRAGMA synchronous = OFF');
  db.exec(SCHEMA);

  const insertBook = db.prepare(
    'INSERT INTO books (id, ordinal, name_zh, name_en, chapters, testament) VALUES (?, ?, ?, ?, ?, ?)',
  );
  const insertVerse = db.prepare(
    'INSERT INTO verses (book_id, chapter, verse, text_cuv, text_web) VALUES (?, ?, ?, ?, ?)',
  );

  let chapterCount = 0;
  let verseCount = 0;
  let nullCuv = 0;
  let nullWeb = 0;

  db.exec('BEGIN');
  BOOKS.forEach(([id, nameZh, nameEn, chapters, testament], index) => {
    const chinese = readTranslation('cuv', id).chapters;
    const english = readTranslation('web', id).chapters;
    insertBook.run(id, index + 1, nameZh, nameEn, chapters, testament);

    const chineseChapters = Object.keys(chinese);
    const englishChapters = Object.keys(english);
    if (chineseChapters.length !== chapters || englishChapters.length !== chapters) {
      throw new Error(
        `${id}: declared ${chapters} chapters but found ` +
          `${chineseChapters.length} (cuv) and ${englishChapters.length} (web)`,
      );
    }
    chapterCount += chapters;

    for (let chapter = 1; chapter <= chapters; chapter += 1) {
      const zh = chinese[String(chapter)];
      const en = english[String(chapter)];
      if (!zh || !en) {
        throw new Error(`${id} ${chapter}: missing chapter in one of the translations`);
      }
      // max(cuv, web) alignment, verbatim from BibleRepository.chapter().
      const count = Math.max(zh.length, en.length);
      for (let index = 0; index < count; index += 1) {
        const textCuv = index < zh.length ? zh[index] : null;
        const textWeb = index < en.length ? en[index] : null;
        if (textCuv === null) nullCuv += 1;
        if (textWeb === null) nullWeb += 1;
        insertVerse.run(id, chapter, index + 1, textCuv, textWeb);
      }
      verseCount += count;
    }
  });
  db.exec('COMMIT');

  if (BOOKS.length !== EXPECTED_BOOKS || chapterCount !== EXPECTED_CHAPTERS) {
    throw new Error(
      `built ${BOOKS.length} books / ${chapterCount} chapters, expected ${EXPECTED_BOOKS} / ${EXPECTED_CHAPTERS}`,
    );
  }

  db.exec('PRAGMA user_version = 1');
  db.exec('VACUUM');
  db.close();

  return { books: BOOKS.length, chapters: chapterCount, verses: verseCount, nullCuv, nullWeb };
}

/** Content digest of the immutable tables, independent of page layout. */
function digest(target) {
  const db = new DatabaseSync(target, { readOnly: true });
  const hash = createHash('sha256');
  const books = db.prepare('SELECT * FROM books ORDER BY ordinal').all();
  const verses = db.prepare('SELECT * FROM verses ORDER BY book_id, chapter, verse').all();
  for (const row of books) {
    hash.update(`book:${row.id}:${row.ordinal}:${row.name_zh}:${row.name_en}:${row.chapters}:${row.testament}\n`);
  }
  for (const row of verses) {
    hash.update(`verse:${row.book_id}:${row.chapter}:${row.verse}:${row.text_cuv}:${row.text_web}\n`);
  }
  db.close();
  return hash.digest('hex');
}

function main() {
  const check = process.argv.includes('--check');
  if (!check) {
    const stats = build(OUTPUT);
    const bytes = fs.statSync(OUTPUT).size;
    console.log(
      `wrote ${path.relative(ROOT, OUTPUT)}: ` +
        `${stats.books} books, ${stats.chapters} chapters, ${stats.verses} verse rows, ` +
        `${(bytes / 1024 / 1024).toFixed(1)} MB ` +
        `(cuv gaps ${stats.nullCuv}, web gaps ${stats.nullWeb})`,
    );
    return;
  }

  if (!fs.existsSync(OUTPUT)) {
    throw new Error(`${path.relative(ROOT, OUTPUT)} is missing; run this tool without --check`);
  }
  const temp = path.join(ROOT, 'tool/out/bible.check.db');
  build(temp);
  const expected = digest(temp);
  const actual = digest(OUTPUT);
  fs.rmSync(temp, { force: true });
  if (expected !== actual) {
    throw new Error(
      'the committed bible.db does not match legacy/flutter/assets/bible; ' +
        'run `node tool/build_bible_db.mjs` and commit the result',
    );
  }
  console.log('bible.db is up to date with legacy/flutter/assets/bible');
}

try {
  main();
} catch (error) {
  console.error(`build_bible_db: ${error.message}`);
  process.exit(1);
}