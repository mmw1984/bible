package com.example.bible.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bible.R
import com.example.bible.data.BibleBook
import com.example.bible.data.BibleBooks
import com.example.bible.data.BibleRepository
import com.example.bible.data.VersePair
import kotlinx.coroutines.launch

private enum class ReadingMode { Chinese, English, Bilingual }

private val Ink = Color(0xFFF1EFE9)
private val Muted = Color(0xFF8C8B86)
private val Faint = Color(0xFF5E5E5A)
private val Canvas = Color(0xFF090909)
private val Surface = Color(0xFF111111)
private val Line = Color(0xFF292927)

@Composable
fun MainScreen(onItemClick: (androidx.navigation3.runtime.NavKey) -> Unit = {}) {
  val context = LocalContext.current
  val repository = remember { BibleRepository(context) }
  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  var bookIndex by remember { mutableIntStateOf(0) }
  var chapter by remember { mutableIntStateOf(1) }
  var mode by remember { mutableStateOf(ReadingMode.Chinese) }
  var verses by remember { mutableStateOf<List<VersePair>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var dark by remember { mutableStateOf(true) }
  val book = BibleBooks[bookIndex]

  LaunchedEffect(bookIndex, chapter) {
    loading = true
    verses = repository.chapter(book, chapter)
    loading = false
  }

  val chooseBook: (Int) -> Unit = { index ->
    bookIndex = index
    chapter = 1
    mode = ReadingMode.Chinese
    scope.launch { drawerState.close() }
  }

  ModalNavigationDrawer(
    drawerState = drawerState,
    gesturesEnabled = drawerState.isOpen,
    drawerContent = {
      ModalDrawerSheet(drawerContainerColor = Canvas, drawerContentColor = Ink, modifier = Modifier.width(360.dp)) {
        BookDrawer(bookIndex, chooseBook) { scope.launch { drawerState.close() } }
      }
    },
  ) {
    BoxWithConstraints(Modifier.fillMaxSize().background(if (dark) Canvas else Color(0xFFF5F2EA))) {
      val wide = maxWidth >= 840.dp
      val showReflection = maxWidth >= 1180.dp
      Row(Modifier.fillMaxSize()) {
        if (wide) BookSidebar(book, chapter) { scope.launch { drawerState.open() } }
        BibleReader(
          book = book,
          chapter = chapter,
          mode = mode,
          verses = verses,
          loading = loading,
          wide = wide,
          dark = dark,
          onMenu = { scope.launch { drawerState.open() } },
          onTheme = { dark = !dark },
          onMode = { mode = it },
          onPrevious = {
            if (chapter > 1) chapter-- else if (bookIndex > 0) { bookIndex--; chapter = BibleBooks[bookIndex].chapters }
          },
          onNext = {
            if (chapter < book.chapters) chapter++ else if (bookIndex < BibleBooks.lastIndex) { bookIndex++; chapter = 1 }
          },
          modifier = Modifier.weight(1f),
        )
        if (showReflection) ReflectionPane()
      }
    }
  }
}

@Composable
private fun BibleReader(
  book: BibleBook,
  chapter: Int,
  mode: ReadingMode,
  verses: List<VersePair>,
  loading: Boolean,
  wide: Boolean,
  dark: Boolean,
  onMenu: () -> Unit,
  onTheme: () -> Unit,
  onMode: (ReadingMode) -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val exposure = FontFamily(Font(R.font.exposure_variable))
  val openRunde = FontFamily(Font(R.font.openrunde_regular))
  val contentColor = if (dark) Ink else Color(0xFF191918)
  Column(modifier.fillMaxHeight().padding(WindowInsets.safeDrawing.asPaddingValues())) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
      if (!wide) OutlinedIcon(Icons.Rounded.Menu, "選擇書卷", onMenu)
      Spacer(Modifier.weight(1f))
      OutlinedIcon(Icons.Rounded.Search, "搜尋", {})
      Spacer(Modifier.width(8.dp))
      OutlinedIcon(if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, "切換外觀", onTheme)
    }
    AnimatedContent(
      targetState = Triple(book, chapter, mode),
      transitionSpec = { (fadeIn() + slideInVertically { it / 12 }) togetherWith (fadeOut() + slideOutVertically { -it / 12 }) },
      label = "chapter",
      modifier = Modifier.weight(1f),
    ) { (activeBook, activeChapter, activeMode) ->
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = if (wide) 48.dp else 20.dp, vertical = 46.dp),
      ) {
        item {
          val title = when (activeMode) {
            ReadingMode.Chinese -> activeBook.chinese
            ReadingMode.English -> activeBook.english
            ReadingMode.Bilingual -> "${activeBook.chinese} / ${activeBook.english}"
          }
          Row(verticalAlignment = Alignment.Bottom) {
            Text(title, color = contentColor, fontFamily = exposure, fontWeight = FontWeight.Medium, fontSize = if (wide) 54.sp else 46.sp, lineHeight = 58.sp)
            Text(" ${activeChapter.toString().padStart(2, '0')}", color = Faint, fontFamily = exposure, fontSize = 22.sp, modifier = Modifier.padding(bottom = 6.dp))
          }
          Spacer(Modifier.height(34.dp))
          LanguageSlider(activeMode, onMode)
          Spacer(Modifier.height(18.dp))
          Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
          Spacer(Modifier.height(18.dp))
        }
        if (loading) item { Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Ink, strokeWidth = 2.dp) } }
        itemsIndexed(verses, key = { _, verse -> verse.number }) { index, verse ->
          VerseRow(verse, activeMode, selected = index == 0, contentColor = contentColor, openRunde = openRunde)
        }
        item {
          Row(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            ChapterButton(false, activeBook.chinese, activeChapter - 1, onPrevious)
            ChapterButton(true, activeBook.chinese, activeChapter + 1, onNext)
          }
        }
      }
    }
  }
}

@Composable
private fun LanguageSlider(mode: ReadingMode, onMode: (ReadingMode) -> Unit) {
  val index = mode.ordinal
  val x by animateDpAsState(62.dp * index, spring(dampingRatio = .78f, stiffness = 420f), label = "language-slider")
  Box(Modifier.width(186.dp).height(40.dp).clip(RoundedCornerShape(12.dp)).background(Surface).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(3.dp)) {
    Box(Modifier.offset(x = x).width(56.dp).fillMaxHeight().clip(RoundedCornerShape(9.dp)).background(Ink))
    Row(Modifier.fillMaxSize()) {
      listOf("中文", "英文", "雙語").forEachIndexed { i, label ->
        Box(Modifier.weight(1f).fillMaxHeight().clickable { onMode(ReadingMode.entries[i]) }, contentAlignment = Alignment.Center) {
          Text(label, color = if (i == index) Canvas else Muted, fontSize = 11.sp)
        }
      }
    }
  }
}

@Composable
private fun VerseRow(verse: VersePair, mode: ReadingMode, selected: Boolean, contentColor: Color, openRunde: FontFamily) {
  Row(
    Modifier.fillMaxWidth().background(if (selected) Surface else Color.Transparent, RoundedCornerShape(5.dp)).border(width = 0.dp, color = Color.Transparent)
      .padding(vertical = 18.dp, horizontal = 8.dp),
  ) {
    if (selected) Box(Modifier.width(2.dp).height(24.dp).background(Ink)) else Spacer(Modifier.width(2.dp))
    Text(verse.number.toString().padStart(2, '0'), color = Faint, fontSize = 10.sp, modifier = Modifier.width(48.dp).padding(top = 6.dp), textAlign = TextAlign.Center)
    Column(Modifier.weight(1f)) {
      if (mode != ReadingMode.English) Text(verse.chinese, color = contentColor, fontSize = 18.sp, lineHeight = 34.sp)
      if (mode != ReadingMode.Chinese) {
        if (mode == ReadingMode.Bilingual) Spacer(Modifier.height(8.dp))
        Text(verse.english, color = if (mode == ReadingMode.English) contentColor else Muted, fontFamily = openRunde, fontSize = if (mode == ReadingMode.English) 17.sp else 14.sp, lineHeight = if (mode == ReadingMode.English) 30.sp else 24.sp)
      }
    }
  }
  Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF20201E)))
}

@Composable private fun OutlinedIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
  IconButton(onClick, Modifier.size(40.dp).border(1.dp, Line, RoundedCornerShape(11.dp))) { Icon(icon, label, tint = Muted, modifier = Modifier.size(19.dp)) }
}

@Composable private fun ChapterButton(next: Boolean, name: String, number: Int, onClick: () -> Unit) {
  Row(Modifier.clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
    if (!next) Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Muted)
    Column(Modifier.padding(horizontal = 10.dp), horizontalAlignment = if (next) Alignment.End else Alignment.Start) {
      Text(if (next) "下一章" else "上一章", color = Faint, fontSize = 10.sp)
      Text(if (number > 0) "$name $number" else "—", color = Ink, fontSize = 13.sp)
    }
    if (next) Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Muted)
  }
}

@Composable private fun BookSidebar(book: BibleBook, chapter: Int, onOpen: () -> Unit) {
  Column(Modifier.width(260.dp).fillMaxHeight().padding(start = 36.dp, top = 104.dp, end = 24.dp)) {
    Text("正在閱讀", color = Faint, fontSize = 10.sp, letterSpacing = 2.sp)
    Spacer(Modifier.height(16.dp))
    Text(book.chinese, color = Ink, fontSize = 20.sp, modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(bottom = 16.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
    LazyVerticalGrid(GridCells.Fixed(5), modifier = Modifier.padding(top = 16.dp).height(215.dp)) {
      items((1..book.chapters).toList()) { item ->
        Box(Modifier.padding(3.dp).size(34.dp).clip(RoundedCornerShape(9.dp)).background(if (item == chapter) Ink else Color.Transparent), contentAlignment = Alignment.Center) {
          Text(item.toString(), color = if (item == chapter) Canvas else Faint, fontSize = 10.sp)
        }
      }
    }
  }
}

@Composable private fun BookDrawer(selected: Int, onBook: (Int) -> Unit, onClose: () -> Unit) {
  var old by remember { mutableStateOf(BibleBooks[selected].isOldTestament) }
  Column(Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues()).padding(24.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text("選擇書卷", color = Ink, fontSize = 32.sp, modifier = Modifier.weight(1f)); OutlinedIcon(Icons.Rounded.Close, "關閉", onClose) }
    Spacer(Modifier.height(28.dp))
    TestamentSlider(old) { old = it }
    Spacer(Modifier.height(18.dp))
    AnimatedContent(old, label = "testament") { showOld ->
      Column(Modifier.verticalScroll(rememberScrollState())) {
        BibleBooks.mapIndexed { index, book -> index to book }.filter { it.second.isOldTestament == showOld }.forEach { (index, book) ->
          Row(Modifier.fillMaxWidth().clickable { onBook(index) }.padding(vertical = 15.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text((index + 1).toString().padStart(2, '0'), color = Faint, fontSize = 10.sp, modifier = Modifier.width(42.dp))
            Text(book.chinese, color = Ink, fontSize = 15.sp, fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
            Text("${book.chapters} 章", color = Faint, fontSize = 10.sp)
          }
          Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        }
      }
    }
  }
}

@Composable private fun TestamentSlider(old: Boolean, onChange: (Boolean) -> Unit) {
  val x by animateDpAsState(if (old) 0.dp else 152.dp, spring(dampingRatio = .78f), label = "testament-slider")
  Box(Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp)).background(Surface).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(3.dp)) {
    Box(Modifier.offset(x = x).width(146.dp).fillMaxHeight().clip(RoundedCornerShape(9.dp)).background(Ink))
    Row(Modifier.fillMaxSize()) {
      listOf(true to "舊約 · 39", false to "新約 · 27").forEach { (value, text) ->
        Box(Modifier.weight(1f).fillMaxHeight().clickable { onChange(value) }, contentAlignment = Alignment.Center) { Text(text, color = if (value == old) Canvas else Muted, fontSize = 11.sp) }
      }
    }
  }
}

@Composable private fun ReflectionPane() { Box(Modifier.width(290.dp).fillMaxHeight().padding(top = 108.dp, end = 32.dp)) { Text("「神說：要有光，\n就有了光。」", color = Ink, fontSize = 23.sp, lineHeight = 38.sp) } }

@Preview(widthDp = 390, heightDp = 844) @Composable private fun PhonePreview() { MainScreen() }
@Preview(widthDp = 1280, heightDp = 800) @Composable private fun TabletPreview() { MainScreen() }
