#!/usr/bin/env node
// Offline builder for the pre-packaged Room database (NATIVE_PLAN.md §3.1 step 2).
//
// Reads the frozen Flutter assets under `legacy/flutter/assets/bible/{cuv,web}/*.json`
// (132 files: 66 books x 2 translations) and writes a single SQLite file to
// `app/src/main/assets/databases/bible.db`, which `BibleDbProvider` opens with
// `Room.databaseBuilder(...).createFromAsset("databases/bible.db")`.
//
// The database is scripture content only, so it is fully derived from the assets:
// this script is deterministic and safe to re-run. It is intentionally NOT wired
// into Gradle, because CI must never regenerate the shipped asset (a rebuild could
// silently change installed-app data). Re-run it by hand and commit the result.
//
// Usage:
//   node tool/build_bible_db.mjs [--check]
//
// `--check` rebuilds into a temp file and compares it byte-for-byte with the
// committed asset, so CI (or a reviewer) can prove the asset is in sync without
// writing to the tree.

import { DatabaseSync } from "node:sqlite";
import { readFileSync, readdirSync, mkdirSync, mkdtempSync, rmSync, renameSync, statSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { tmpdir } from "node:os";

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const assetRoot = join(root, "legacy", "flutter", "assets", "bible");
const outPath = join(root, "app", "src", "main", "assets", "databases", "bible.db");

const EXPECTED_BOOKS = 66;
const EXPECTED_CHAPTERS = 1189;
const EXPECTED_TESTAMENTS = { old: 39, new: 27 };
const SCHEMA_VERSION = 1;

// `bibleBooks` from `legacy/flutter/lib/bible_data.dart`, which is the canonical
// ordering/naming for the whole app (both the Flutter and native readers index
// into it). Ordinal is 1-based to match the `ordinal` column in NATIVE_PLAN §3.1.
const BOOKS = [
  ["GEN", "創世記", "Genesis", 50, 0],
  ["EXO", "出埃及記", "Exodus", 40, 0],
  ["LEV", "利未記", "Leviticus", 27, 0],
  ["NUM", "民數記", "Numbers", 36, 0],
  ["DEU", "申命記", "Deuteronomy", 34, 0],
  ["JOS", "約書亞記", "Joshua", 24, 0],
  ["JDG", "士師記", "Judges", 21, 0],
  ["RUT", "路得記", "Ruth", 4, 0],
  ["1SA", "撒母耳記上", "1 Samuel", 31, 0],
  ["2SA", "撒母耳記下", "2 Samuel", 24, 0],
  ["1KI", "列王紀上", "1 Kings", 22, 0],
  ["2KI", "列王紀下", "2 Kings", 25, 0],
  ["1CH", "歷代志上", "1 Chronicles", 29, 0],
  ["2CH", "歷代志下", "2 Chronicles", 36, 0],
  ["EZR", "以斯拉記", "Ezra", 10, 0],
  ["NEH", "尼希米記", "Nehemiah", 13, 0],
  ["EST", "以斯帖記", "Esther", 10, 0],
  ["JOB", "約伯記", "Job", 42, 0],
  ["PSA", "詩篇", "Psalms", 150, 0],
  ["PRO", "箴言", "Proverbs", 31, 0],
  ["ECC", "傳道書", "Ecclesiastes", 12, 0],
  ["SNG", "雅歌", "Song of Songs", 8, 0],
  ["ISA", "以賽亞書", "Isaiah", 66, 0],
  ["JER", "耶利米書", "Jeremiah", 52, 0],
  ["LAM", "耶利米哀歌", "Lamentations", 5, 0],
  ["EZK", "以西結書", "Ezekiel", 48, 0],
  ["DAN", "但以理書", "Daniel", 12, 0],
  ["HOS", "何西阿書", "Hosea", 14, 0],
  ["JOL", "約珥書", "Joel", 3, 0],
  ["AMO", "阿摩司書", "Amos", 9, 0],
  ["OBA", "俄巴底亞書", "Obadiah", 1, 0],
  ["JON", "約拿書", "Jonah", 4, 0],
  ["MIC", "彌迦書", "Micah", 7, 0],
  ["NAM", "那鴻書", "Nahum", 3, 0],
  ["HAB", "哈巴谷書", "Habakkuk", 3, 0],
  ["ZEP", "西番雅書", "Zephaniah", 3, 0],
  ["HAG", "哈該書", "Haggai", 2, 0],
  ["ZEC", "撒迦利亞書", "Zechariah", 14, 0],
  ["MAL", "瑪拉基書", "Malachi", 4, 0],
  ["MAT", "馬太福音", "Matthew", 28, 1],
  ["MRK", "馬可福音", "Mark", 16, 1],
  ["LUK", "路加福音", "Luke", 24, 1],
  ["JHN", "約翰福音", "John", 21, 1],
  ["ACT", "使徒行傳", "Acts", 28, 1],
  ["ROM", "羅馬書", "Romans", 16, 1],
  ["1CO", "哥林多前書", "1 Corinthians", 16, 1],
  ["2CO", "哥林多後書", "2 Corinthians", 13, 1],
  ["GAL", "加拉太書", "Galatians", 6, 1],
  ["EPH", "以弗所書", "Ephesians", 6, 1],
  ["PHP", "腓立比書", "Philippians", 4, 1],
  ["COL", "歌羅西書", "Colossians", 4, 1],
  ["1TH", "帖撒羅尼迦前書", "1 Thessalonians", 5, 1],
  ["2TH", "帖撒羅尼迦後書", "2 Thessalonians", 3, 1],
  ["1TI", "提摩太前書", "1 Timothy", 6, 1],
  ["2TI", "提摩太後書", "2 Timothy", 4, 1],
  ["TIT", "提多書", "Titus", 3, 1],
  ["PHM", "腓利門書", "Philemon", 1, 1],
  ["HEB", "希伯來書", "Hebrews", 13, 1],
  ["JAS", "雅各書", "James", 5, 1],
  ["1PE", "彼得前書", "1 Peter", 5, 1],
  ["2PE", "彼得後書", "2 Peter", 3, 1],
  ["1JN", "約翰壹書", "1 John", 5, 1],
  ["2JN", "約翰貳書", "2 John", 1, 1],
  ["3JN", "約翰參書", "3 John", 1, 1],
  ["JUD", "猶大書", "Jude", 1, 1],
  ["REV", "啟示錄", "Revelation", 22, 1],
].map(([id, nameZh, nameEn, chapters, testament]) => ({
  id,
  nameZh,
  nameEn,
  chapters,
  testament,
}));

// Schema is written verbatim from NATIVE_PLAN §3.1, plus `devotion_cache` (§3.6, so the
// shipped DB already has the table the devotion cache import needs).
//
// `room_master_table` is deliberately NOT created. Room's identity hash is produced by the
// KSP processor from the generated entity code, which cannot run in this offline script, and
// a hand-written value would be wrong on the first schema change. When the table is absent,
// `RoomOpenHelper` falls back to `onValidateSchema` (a full TableInfo comparison of every
// entity table) and then writes the correct hash itself. So this schema must match the
// `@Entity` declarations in `core/database` exactly: column names/affinities/notNull/primary
// key positions, default values, foreign keys (onUpdate/onDelete) and declared indices.
const SCHEMA_SQL = `
CREATE TABLE books (
  id TEXT NOT NULL PRIMARY KEY,
  ordinal INTEGER NOT NULL,
  name_zh TEXT NOT NULL,
  name_en TEXT NOT NULL,
  chapters INTEGER NOT NULL,
  testament INTEGER NOT NULL
);

CREATE TABLE verses (
  book_id TEXT NOT NULL,
  chapter INTEGER NOT NULL,
  verse INTEGER NOT NULL,
  text_cuv TEXT,
  text_web TEXT,
  PRIMARY KEY (book_id, chapter, verse),
  FOREIGN KEY (book_id) REFERENCES books (id) ON UPDATE NO ACTION ON DELETE CASCADE
) WITHOUT ROWID;

CREATE TABLE reading_progress (
  book_id TEXT NOT NULL PRIMARY KEY,
  chapter INTEGER NOT NULL,
  verse INTEGER,
  scroll_ratio REAL NOT NULL DEFAULT 0,
  mode TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  FOREIGN KEY (book_id) REFERENCES books (id) ON UPDATE NO ACTION ON DELETE CASCADE
);

CREATE TABLE devotion_cache (
  id TEXT NOT NULL PRIMARY KEY,
  raw_post TEXT NOT NULL,
  fetched_at INTEGER NOT NULL
);
`;

function readBook(translation, id) {
  const path = join(assetRoot, translation, `${id}.json`);
  const payload = JSON.parse(readFileSync(path, "utf8"));
  if (payload.id !== id) {
    throw new Error(`${translation}/${id}.json declares id "${payload.id}"`);
  }
  return payload.chapters;
}

function build(targetPath) {
  rmSync(targetPath, { force: true });
  mkdirSync(dirname(targetPath), { recursive: true });
  const db = new DatabaseSync(targetPath);
  db.exec("PRAGMA journal_mode = DELETE;");
  db.exec(SCHEMA_SQL);
  // Room reads `user_version` to decide whether a copied file is current. It must equal
  // `BibleDb.VERSION`, otherwise `PrePackagedCopyOpenHelper` treats the asset as an old
  // database and runs a migration that does not exist.
  db.exec(`PRAGMA user_version = ${SCHEMA_VERSION};`);

  const insertBook = db.prepare(
    "INSERT INTO books (id, ordinal, name_zh, name_en, chapters, testament) VALUES (?, ?, ?, ?, ?, ?)",
  );
  const insertVerse = db.prepare(
    "INSERT INTO verses (book_id, chapter, verse, text_cuv, text_web) VALUES (?, ?, ?, ?, ?)",
  );

  let totalChapters = 0;
  let totalVerses = 0;
  db.exec("BEGIN");
  try {
    BOOKS.forEach((book, index) => {
      const cuv = readBook("cuv", book.id);
      const web = readBook("web", book.id);

      const chapterNumbers = Object.keys(cuv)
        .map(Number)
        .sort((a, b) => a - b);
      if (chapterNumbers.length !== book.chapters) {
        throw new Error(
          `${book.id}: manifest says ${book.chapters} chapters, cuv asset has ${chapterNumbers.length}`,
        );
      }
      if (Object.keys(web).length !== book.chapters) {
        throw new Error(
          `${book.id}: manifest says ${book.chapters} chapters, web asset has ${Object.keys(web).length}`,
        );
      }

      insertBook.run(book.id, index + 1, book.nameZh, book.nameEn, book.chapters, book.testament);

      for (const chapter of chapterNumbers) {
        const zh = cuv[String(chapter)];
        const en = web[String(chapter)] ?? [];
        // The Flutter reader pairs verses by index and takes `max(cuv, web)`
        // (legacy/flutter/lib/bible_data.dart `BibleRepository.chapter`), so a verse
        // row has to exist for every index either translation has. A missing index
        // inside a translation becomes NULL rather than dropping the row, which is
        // what the schema in NATIVE_PLAN §3.1 asks for.
        const count = Math.max(zh.length, en.length);
        for (let i = 0; i < count; i += 1) {
          const textCuv = i < zh.length ? zh[i] : null;
          const textWeb = i < en.length ? en[i] : null;
          if (textCuv == null && textWeb == null) {
            throw new Error(`${book.id} ${chapter}:${i + 1} is empty in both translations`);
          }
          insertVerse.run(book.id, chapter, i + 1, textCuv, textWeb);
          totalVerses += 1;
        }
        totalChapters += 1;
      }
    });
    db.exec("COMMIT");
  } catch (error) {
    db.exec("ROLLBACK");
    throw error;
  }

  const bookCount = db.prepare("SELECT COUNT(*) AS n FROM books").get().n;
  const chapterRows = db
    .prepare("SELECT COUNT(DISTINCT book_id || ':' || chapter) AS n FROM verses")
    .get().n;
  const oldBooks = db.prepare("SELECT COUNT(*) AS n FROM books WHERE testament = 0").get().n;
  const newBooks = db.prepare("SELECT COUNT(*) AS n FROM books WHERE testament = 1").get().n;

  if (bookCount !== EXPECTED_BOOKS) throw new Error(`books: ${bookCount} != ${EXPECTED_BOOKS}`);
  if (chapterRows !== EXPECTED_CHAPTERS) throw new Error(`chapters: ${chapterRows} != ${EXPECTED_CHAPTERS}`);
  if (oldBooks !== EXPECTED_TESTAMENTS.old) throw new Error(`old testament: ${oldBooks}`);
  if (newBooks !== EXPECTED_TESTAMENTS.new) throw new Error(`new testament: ${newBooks}`);

  db.close();

  return { bookCount, chapterRows, oldBooks, newBooks, totalVerses };
}

if (process.argv.includes("--check")) {
  const scratch = mkdtempSync(join(tmpdir(), "bible-db-"));
  try {
    const staged = join(scratch, "bible.db");
    build(staged);
    const committed = readFileSync(outPath);
    const rebuilt = readFileSync(staged);
    if (!committed.equals(rebuilt)) {
      console.error(
        `stale asset: ${outPath} differs from a rebuild of legacy/flutter/assets/bible. ` +
          "Run `node tool/build_bible_db.mjs` and commit the result.",
      );
      process.exit(1);
    }
    console.log(`bible.db is in sync (${committed.length} bytes)`);
  } finally {
    rmSync(scratch, { recursive: true, force: true });
  }
} else {
  const stats = build(outPath);
  console.log(
    `wrote ${outPath} (${statSync(outPath).size} bytes): ` +
      `${stats.bookCount} books, ${stats.chapterRows} chapters, ${stats.totalVerses} verses`,
  );
}