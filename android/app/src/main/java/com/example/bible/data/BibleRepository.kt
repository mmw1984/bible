package com.example.bible.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class BibleBook(
  val id: String,
  val chinese: String,
  val english: String,
  val chapters: Int,
  val isOldTestament: Boolean,
)

data class VersePair(val number: Int, val chinese: String, val english: String)

class BibleRepository(private val context: Context) {
  suspend fun chapter(book: BibleBook, chapter: Int): List<VersePair> = withContext(Dispatchers.IO) {
    val chinese = loadTranslation("cuv", book, chapter)
    val english = loadTranslation("web", book, chapter)
    val count = maxOf(chinese.size, english.size)
    List(count) { index -> VersePair(index + 1, chinese.getOrElse(index) { "" }, english.getOrElse(index) { "" }) }
  }

  private fun loadTranslation(translation: String, book: BibleBook, chapter: Int): List<String> {
    val cache = File(context.filesDir, "bible/$translation/${book.id}-$chapter.json")
    try {
      val connection = URL("https://bible-api.com/data/$translation/${book.id}/$chapter").openConnection() as HttpURLConnection
      connection.connectTimeout = 8_000
      connection.readTimeout = 12_000
      connection.setRequestProperty("Accept", "application/json")
      if (connection.responseCode in 200..299) {
        val payload = connection.inputStream.bufferedReader().use { it.readText() }
        cache.parentFile?.mkdirs()
        cache.writeText(payload)
        return parseApiChapter(payload)
      }
    } catch (_: Exception) {
      // Cache and bundled assets below are the intentional offline path.
    }
    if (cache.exists()) runCatching { return parseApiChapter(cache.readText()) }
    val bundled = "bible/$translation/${book.id}.json"
    return runCatching {
      context.assets.open(bundled).bufferedReader().use { reader ->
        val chapters = JSONObject(reader.readText()).getJSONObject("chapters")
        val verses = chapters.getJSONArray(chapter.toString())
        List(verses.length()) { verses.getString(it) }
      }
    }.getOrDefault(emptyList())
  }

  private fun parseApiChapter(payload: String): List<String> {
    val verses = JSONObject(payload).getJSONArray("verses")
    return List(verses.length()) { verses.getJSONObject(it).getString("text").trim() }
  }
}

val BibleBooks = listOf(
  BibleBook("GEN", "創世記", "Genesis", 50, true), BibleBook("EXO", "出埃及記", "Exodus", 40, true),
  BibleBook("LEV", "利未記", "Leviticus", 27, true), BibleBook("NUM", "民數記", "Numbers", 36, true),
  BibleBook("DEU", "申命記", "Deuteronomy", 34, true), BibleBook("JOS", "約書亞記", "Joshua", 24, true),
  BibleBook("JDG", "士師記", "Judges", 21, true), BibleBook("RUT", "路得記", "Ruth", 4, true),
  BibleBook("1SA", "撒母耳記上", "1 Samuel", 31, true), BibleBook("2SA", "撒母耳記下", "2 Samuel", 24, true),
  BibleBook("1KI", "列王紀上", "1 Kings", 22, true), BibleBook("2KI", "列王紀下", "2 Kings", 25, true),
  BibleBook("1CH", "歷代志上", "1 Chronicles", 29, true), BibleBook("2CH", "歷代志下", "2 Chronicles", 36, true),
  BibleBook("EZR", "以斯拉記", "Ezra", 10, true), BibleBook("NEH", "尼希米記", "Nehemiah", 13, true),
  BibleBook("EST", "以斯帖記", "Esther", 10, true), BibleBook("JOB", "約伯記", "Job", 42, true),
  BibleBook("PSA", "詩篇", "Psalms", 150, true), BibleBook("PRO", "箴言", "Proverbs", 31, true),
  BibleBook("ECC", "傳道書", "Ecclesiastes", 12, true), BibleBook("SNG", "雅歌", "Song of Songs", 8, true),
  BibleBook("ISA", "以賽亞書", "Isaiah", 66, true), BibleBook("JER", "耶利米書", "Jeremiah", 52, true),
  BibleBook("LAM", "耶利米哀歌", "Lamentations", 5, true), BibleBook("EZK", "以西結書", "Ezekiel", 48, true),
  BibleBook("DAN", "但以理書", "Daniel", 12, true), BibleBook("HOS", "何西阿書", "Hosea", 14, true),
  BibleBook("JOL", "約珥書", "Joel", 3, true), BibleBook("AMO", "阿摩司書", "Amos", 9, true),
  BibleBook("OBA", "俄巴底亞書", "Obadiah", 1, true), BibleBook("JON", "約拿書", "Jonah", 4, true),
  BibleBook("MIC", "彌迦書", "Micah", 7, true), BibleBook("NAM", "那鴻書", "Nahum", 3, true),
  BibleBook("HAB", "哈巴谷書", "Habakkuk", 3, true), BibleBook("ZEP", "西番雅書", "Zephaniah", 3, true),
  BibleBook("HAG", "哈該書", "Haggai", 2, true), BibleBook("ZEC", "撒迦利亞書", "Zechariah", 14, true),
  BibleBook("MAL", "瑪拉基書", "Malachi", 4, true), BibleBook("MAT", "馬太福音", "Matthew", 28, false),
  BibleBook("MRK", "馬可福音", "Mark", 16, false), BibleBook("LUK", "路加福音", "Luke", 24, false),
  BibleBook("JHN", "約翰福音", "John", 21, false), BibleBook("ACT", "使徒行傳", "Acts", 28, false),
  BibleBook("ROM", "羅馬書", "Romans", 16, false), BibleBook("1CO", "哥林多前書", "1 Corinthians", 16, false),
  BibleBook("2CO", "哥林多後書", "2 Corinthians", 13, false), BibleBook("GAL", "加拉太書", "Galatians", 6, false),
  BibleBook("EPH", "以弗所書", "Ephesians", 6, false), BibleBook("PHP", "腓立比書", "Philippians", 4, false),
  BibleBook("COL", "歌羅西書", "Colossians", 4, false), BibleBook("1TH", "帖撒羅尼迦前書", "1 Thessalonians", 5, false),
  BibleBook("2TH", "帖撒羅尼迦後書", "2 Thessalonians", 3, false), BibleBook("1TI", "提摩太前書", "1 Timothy", 6, false),
  BibleBook("2TI", "提摩太後書", "2 Timothy", 4, false), BibleBook("TIT", "提多書", "Titus", 3, false),
  BibleBook("PHM", "腓利門書", "Philemon", 1, false), BibleBook("HEB", "希伯來書", "Hebrews", 13, false),
  BibleBook("JAS", "雅各書", "James", 5, false), BibleBook("1PE", "彼得前書", "1 Peter", 5, false),
  BibleBook("2PE", "彼得後書", "2 Peter", 3, false), BibleBook("1JN", "約翰壹書", "1 John", 5, false),
  BibleBook("2JN", "約翰貳書", "2 John", 1, false), BibleBook("3JN", "約翰參書", "3 John", 1, false),
  BibleBook("JUD", "猶大書", "Jude", 1, false), BibleBook("REV", "啟示錄", "Revelation", 22, false),
)
