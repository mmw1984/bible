import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'ai_chat_page.dart';
import 'ai_service.dart';
import 'app_theme.dart';
import 'bible_data.dart';

const canvas = Color(0xFF090909);
const surface = Color(0xFF111111);
const surface2 = Color(0xFF171717);
const ink = Color(0xFFF1EFE9);
const muted = Color(0xFF8C8B86);
const faint = Color(0xFF5E5E5A);
const line = Color(0xFF292927);

enum ReadingMode { chinese, english, bilingual }

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      systemNavigationBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.light,
      systemNavigationBarIconBrightness: Brightness.light,
      systemNavigationBarContrastEnforced: false,
    ),
  );
  final preferences = await SharedPreferences.getInstance();
  final savedDark = preferences.getBool('appearance_dark');
  final initialDark =
      savedDark ??
      PlatformDispatcher.instance.platformBrightness == Brightness.dark;
  runApp(BibleApp(initialDark: initialDark));
}

class BibleApp extends StatefulWidget {
  const BibleApp({super.key, required this.initialDark});
  final bool initialDark;
  @override
  State<BibleApp> createState() => _BibleAppState();
}

class _BibleAppState extends State<BibleApp> {
  late bool dark = widget.initialDark;

  Future<void> _toggleTheme() async {
    final next = !dark;
    setState(() => dark = next);
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool('appearance_dark', next);
  }

  ThemeData _theme(Brightness brightness, AppColors colors) => ThemeData(
    brightness: brightness,
    scaffoldBackgroundColor: colors.canvas,
    colorScheme: ColorScheme.fromSeed(
      seedColor: colors.ink,
      brightness: brightness,
      surface: colors.surface,
      onSurface: colors.ink,
    ),
    extensions: [colors],
    fontFamily: 'OpenRunde',
    textSelectionTheme: TextSelectionThemeData(
      cursorColor: colors.ink,
      selectionColor: const Color(0xFF376996),
    ),
  );

  @override
  Widget build(BuildContext context) {
    final launchRoute = PlatformDispatcher.instance.defaultRouteName;
    final chatLaunch = launchRoute.startsWith('/ai-chat');
    final chatPayload = chatLaunch
        ? _decodeChatPayload(launchRoute)
        : const <String, dynamic>{};
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Bible',
      themeAnimationDuration: const Duration(milliseconds: 420),
      themeAnimationCurve: springCurve,
      theme: _theme(Brightness.light, AppColors.light),
      darkTheme: _theme(Brightness.dark, AppColors.dark),
      themeMode: dark ? ThemeMode.dark : ThemeMode.light,
      home: chatLaunch
          ? AiChatPage(
              initialQuestion: chatPayload['initialQuestion'] as String?,
              scriptureContext: chatPayload['scriptureContext'] as String?,
              scriptureReference: chatPayload['scriptureReference'] as String?,
              autoSend: chatPayload['autoSend'] == true,
            )
          : BibleHome(dark: dark, onTheme: _toggleTheme),
    );
  }

  Map<String, dynamic> _decodeChatPayload(String route) {
    try {
      final encoded = Uri.parse(route).queryParameters['payload'];
      if (encoded == null) return const {};
      return (jsonDecode(utf8.decode(base64Url.decode(encoded))) as Map)
          .cast<String, dynamic>();
    } catch (_) {
      return const {};
    }
  }
}

class BibleHome extends StatefulWidget {
  const BibleHome({
    super.key,
    required this.dark,
    required this.onTheme,
    this.repository,
  });
  final bool dark;
  final VoidCallback onTheme;
  final BibleRepository? repository;
  @override
  State<BibleHome> createState() => _BibleHomeState();
}

class _BibleHomeState extends State<BibleHome> with WidgetsBindingObserver {
  late final repository = widget.repository ?? BibleRepository();
  final ai = BibleAiController.instance;
  final readerScroll = ScrollController(keepScrollOffset: false);
  Timer? scrollSaveTimer;
  Future<void> scrollSaveQueue = Future<void>.value();
  final Map<String, double> scrollOffsets = {};
  bool applyingSavedScroll = false;
  int scrollRestoreEpoch = 0;
  int positionRestoreEpoch = 0;
  int bookIndex = 0;
  int chapter = 1;
  ReadingMode mode = ReadingMode.chinese;
  Future<List<VersePair>>? verses;

  BibleBook get book => bibleBooks[bookIndex];
  Color get foreground => widget.dark ? ink : const Color(0xFF191918);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    ai.addListener(_aiChanged);
    ai.initialize();
    readerScroll.addListener(_rememberScrollOffset);
    _load(persist: false);
    _restorePosition();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    positionRestoreEpoch++;
    scrollRestoreEpoch++;
    scrollSaveTimer?.cancel();
    _saveCurrentScrollOffset();
    readerScroll
      ..removeListener(_rememberScrollOffset)
      ..dispose();
    ai.removeListener(_aiChanged);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      ai.reloadSharedSettings();
      return;
    }
    if (state == AppLifecycleState.inactive ||
        state == AppLifecycleState.paused ||
        state == AppLifecycleState.detached) {
      scrollSaveTimer?.cancel();
      _saveCurrentScrollOffset();
      _persistPosition();
    }
  }

  void _aiChanged() {
    if (mounted) setState(() {});
  }

  void _load({bool persist = true}) {
    final chapterFuture = repository.chapter(book, chapter);
    verses = chapterFuture;
    _restoreScrollOffset(_scrollKey, chapterFuture);
    if (persist) _persistPosition();
  }

  String get _scrollKey => '${book.id}-$chapter';

  void _rememberScrollOffset() {
    if (applyingSavedScroll || !readerScroll.hasClients) return;
    final key = _scrollKey;
    final offset = readerScroll.offset;
    scrollOffsets[key] = offset;
    scrollSaveTimer?.cancel();
    scrollSaveTimer = Timer(
      const Duration(milliseconds: 180),
      () => _saveScrollOffset(key, offset),
    );
  }

  Future<void> _saveScrollOffset(String key, double offset) async {
    final safeOffset = offset.clamp(0, double.infinity).toDouble();
    scrollOffsets[key] = safeOffset;

    Future<void> write() async {
      final preferences = await SharedPreferences.getInstance();
      await preferences.setDouble('reader_scroll_$key', safeOffset);
    }

    scrollSaveQueue = scrollSaveQueue.then(
      (_) => write(),
      onError: (_) => write(),
    );
    await scrollSaveQueue;
  }

  void _saveCurrentScrollOffset() {
    final offset = readerScroll.hasClients
        ? readerScroll.offset
        : scrollOffsets[_scrollKey];
    if (offset == null) return;
    _saveScrollOffset(_scrollKey, offset);
  }

  void _beforeUserNavigation() {
    positionRestoreEpoch++;
    scrollSaveTimer?.cancel();
    _saveCurrentScrollOffset();
  }

  Future<void> _restoreScrollOffset(
    String key,
    Future<List<VersePair>> chapterFuture,
  ) async {
    final epoch = ++scrollRestoreEpoch;
    applyingSavedScroll = true;
    if (readerScroll.hasClients) readerScroll.jumpTo(0);
    final preferences = await SharedPreferences.getInstance();
    final savedOffset =
        scrollOffsets[key] ?? preferences.getDouble('reader_scroll_$key') ?? 0;
    scrollOffsets[key] = savedOffset;
    try {
      try {
        await chapterFuture;
      } catch (_) {
        // FutureBuilder owns the visible load error; scrolling must still unlock.
      }
      // FutureBuilder receives the same completed future after this listener.
      // Give it one frame to publish data and another to lay out the slivers
      // before clamping the persisted offset to the real scroll extent.
      await WidgetsBinding.instance.endOfFrame;
      await WidgetsBinding.instance.endOfFrame;
      if (!mounted || epoch != scrollRestoreEpoch || key != _scrollKey) return;
      if (readerScroll.hasClients) {
        readerScroll.jumpTo(
          savedOffset.clamp(0, readerScroll.position.maxScrollExtent),
        );
      }
    } finally {
      if (mounted && epoch == scrollRestoreEpoch) {
        applyingSavedScroll = false;
      }
    }
  }

  Future<void> _restorePosition() async {
    final epoch = ++positionRestoreEpoch;
    final preferences = await SharedPreferences.getInstance();
    final savedBook = preferences.getString('reader_book');
    final savedChapter = preferences.getInt('reader_chapter');
    final savedMode = preferences.getString('reader_mode');
    final index = bibleBooks.indexWhere((item) => item.id == savedBook);
    if (!mounted || epoch != positionRestoreEpoch || index < 0) return;
    setState(() {
      bookIndex = index;
      chapter = (savedChapter ?? 1).clamp(1, bibleBooks[index].chapters);
      mode =
          ReadingMode.values
              .where((item) => item.name == savedMode)
              .firstOrNull ??
          ReadingMode.chinese;
      _load(persist: false);
    });
  }

  Future<void> _persistPosition() async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString('reader_book', book.id);
    await preferences.setInt('reader_chapter', chapter);
    await preferences.setString('reader_mode', mode.name);
  }

  void _selectBook(int index) {
    _beforeUserNavigation();
    setState(() {
      bookIndex = index;
      chapter = 1;
      mode = ReadingMode.chinese;
      _load();
    });
  }

  void _selectChapter(int value) {
    _beforeUserNavigation();
    setState(() {
      chapter = value;
      _load();
    });
  }

  void _next() {
    _beforeUserNavigation();
    setState(() {
      if (chapter < book.chapters) {
        chapter++;
      } else if (bookIndex < bibleBooks.length - 1) {
        bookIndex++;
        chapter = 1;
      }
      _load();
    });
  }

  void _previous() {
    _beforeUserNavigation();
    setState(() {
      if (chapter > 1) {
        chapter--;
      } else if (bookIndex > 0) {
        bookIndex--;
        chapter = bibleBooks[bookIndex].chapters;
      }
      _load();
    });
  }

  @override
  Widget build(BuildContext context) {
    final overlayStyle = SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      systemNavigationBarColor: Colors.transparent,
      statusBarIconBrightness: widget.dark ? Brightness.light : Brightness.dark,
      systemNavigationBarIconBrightness: widget.dark
          ? Brightness.light
          : Brightness.dark,
      systemNavigationBarContrastEnforced: false,
    );
    return LayoutBuilder(
      builder: (context, constraints) {
        final wide = constraints.maxWidth >= 920;
        return AnnotatedRegion<SystemUiOverlayStyle>(
          value: overlayStyle,
          child: Scaffold(
            extendBody: true,
            extendBodyBehindAppBar: true,
            backgroundColor: Colors.transparent,
            body: Stack(
              children: [
                Positioned.fill(
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 300),
                    curve: springCurve,
                    color: widget.dark ? canvas : const Color(0xFFF5F2EA),
                  ),
                ),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (wide)
                      SizedBox(
                        width: 270,
                        child: _Sidebar(
                          book: book,
                          chapter: chapter,
                          onLibrary: _openLibrary,
                          onChapter: _selectChapter,
                        ),
                      ),
                    Expanded(
                      child: _Reader(
                        book: book,
                        chapter: chapter,
                        mode: mode,
                        verses: verses!,
                        foreground: foreground,
                        wide: wide,
                        onMode: (value) => setState(() {
                          positionRestoreEpoch++;
                          mode = value;
                          _persistPosition();
                        }),
                        onPrevious: _previous,
                        onNext: _next,
                        onLongPressVerse: _openVerseActions,
                        scrollController: readerScroll,
                        onScrollEnd: _saveCurrentScrollOffset,
                      ),
                    ),
                  ],
                ),
                Positioned(
                  top: MediaQuery.paddingOf(context).top + 4,
                  left: 20,
                  right: 20,
                  child: Row(
                    children: [
                      if (!wide)
                        _ToolButton(
                          icon: Icons.menu_rounded,
                          label: '選擇書卷',
                          onTap: _openLibrary,
                        ),
                      const Spacer(),
                      if (ai.isSupported) ...[
                        _ToolButton(
                          icon: Icons.chat_bubble_outline_rounded,
                          label: 'Bible AI 對話',
                          onTap: _openAiChat,
                        ),
                        const SizedBox(width: 9),
                      ],
                      _ToolButton(
                        icon: Icons.search_rounded,
                        label: '搜尋全本聖經',
                        onTap: _openSearch,
                      ),
                      const SizedBox(width: 9),
                      _ToolButton(
                        icon: widget.dark
                            ? Icons.light_mode_outlined
                            : Icons.dark_mode_outlined,
                        label: '外觀',
                        onTap: widget.onTheme,
                      ),
                    ],
                  ),
                ),
                Positioned(
                  top: 0,
                  left: 0,
                  right: 0,
                  height: MediaQuery.paddingOf(context).top + 4,
                  child: IgnorePointer(
                    child: ClipRect(
                      child: ShaderMask(
                        blendMode: BlendMode.dstIn,
                        shaderCallback: (bounds) => const LinearGradient(
                          begin: Alignment.topCenter,
                          end: Alignment.bottomCenter,
                          colors: [
                            Colors.white,
                            Colors.white,
                            Colors.transparent,
                          ],
                          stops: [0, .62, 1],
                        ).createShader(bounds),
                        child: BackdropFilter(
                          filter: ImageFilter.blur(sigmaX: 14, sigmaY: 14),
                          child: ColoredBox(
                            color:
                                (widget.dark ? canvas : const Color(0xFFF5F2EA))
                                    .withValues(alpha: .58),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
                Positioned(
                  left: 0,
                  right: 0,
                  bottom: 0,
                  height: MediaQuery.paddingOf(context).bottom + 42,
                  child: IgnorePointer(
                    child: ClipRect(
                      child: ShaderMask(
                        blendMode: BlendMode.dstIn,
                        shaderCallback: (bounds) => const LinearGradient(
                          begin: Alignment.topCenter,
                          end: Alignment.bottomCenter,
                          colors: [
                            Colors.transparent,
                            Colors.white,
                            Colors.white,
                          ],
                          stops: [0, .62, 1],
                        ).createShader(bounds),
                        child: BackdropFilter(
                          filter: ImageFilter.blur(sigmaX: 16, sigmaY: 16),
                          child: ColoredBox(
                            color: AppColors.of(
                              context,
                            ).canvas.withValues(alpha: .5),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  void _openLibrary() => Navigator.of(context).push(
    _LibraryRoute(
      builder: (context) => Align(
        alignment: Alignment.centerLeft,
        child: _LibraryPanel(
          selected: bookIndex,
          onSelect: (index) {
            Navigator.pop(context);
            _selectBook(index);
          },
        ),
      ),
    ),
  );

  Future<void> _openSearch() async {
    await ai.reloadSharedSettings();
    if (!mounted) return;
    await showDialog<void>(
      context: context,
      barrierColor: Colors.black.withValues(alpha: .72),
      builder: (_) => _SearchDialog(
        repository: repository,
        ai: ai,
        onVerse: (hit) {
          Navigator.pop(context);
          final index = bibleBooks.indexWhere((item) => item.id == hit.book.id);
          if (index >= 0) {
            setState(() {
              bookIndex = index;
              chapter = hit.chapter;
              _load();
            });
          }
        },
      ),
    );
  }

  Future<void> _openVerseActions(VersePair verse) async {
    final chapterVerses = await verses!;
    if (!mounted) return;
    final reference = '${book.zh} $chapter:${verse.number}';
    final selected = '$reference\n${verse.zh}\n${verse.en}';
    final fullChapter = chapterVerses
        .map(
          (item) =>
              '${book.zh} $chapter:${item.number}\n中文：${item.zh}\nEnglish: ${item.en}',
        )
        .join('\n\n');
    await showModalBottomSheet<void>(
      context: context,
      backgroundColor: AppColors.of(context).surfaceRaised,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              _ActionTile(
                icon: Icons.copy_rounded,
                label: '複製經文',
                onTap: () async {
                  await Clipboard.setData(ClipboardData(text: selected));
                  if (sheetContext.mounted) Navigator.pop(sheetContext);
                },
              ),
              if (ai.isSupported)
                _ActionTile(
                  icon: Icons.chat_bubble_outline_rounded,
                  label: '問 AI',
                  onTap: () {
                    Navigator.pop(sheetContext);
                    _openAiChat(
                      initialQuestion:
                          '關於 $reference：\n中文：${verse.zh}\nEnglish: ${verse.en}\n\n',
                      scriptureContext: fullChapter,
                      scriptureReference: reference,
                    );
                  },
                ),
              if (ai.isSupported)
                _ActionTile(
                  icon: Icons.menu_book_rounded,
                  label: '解釋經文',
                  onTap: () {
                    Navigator.pop(sheetContext);
                    _openAiChat(
                      initialQuestion:
                          '請解釋以下經文，並說明上下文、主旨及今天可以如何理解。\n\n$selected',
                      scriptureContext: fullChapter,
                      scriptureReference: reference,
                      autoSend: true,
                    );
                  },
                ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _openAiChat({
    String? initialQuestion,
    String? scriptureContext,
    String? scriptureReference,
    bool autoSend = false,
  }) async {
    if (kIsWeb) {
      await Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => AiChatPage(
            initialQuestion: initialQuestion,
            scriptureContext: scriptureContext,
            scriptureReference: scriptureReference,
            autoSend: autoSend,
          ),
        ),
      );
      return;
    }
    await ai.openChatActivity(
      initialQuestion: initialQuestion,
      scriptureContext: scriptureContext,
      scriptureReference: scriptureReference,
      autoSend: autoSend,
    );
  }
}

class _Reader extends StatelessWidget {
  const _Reader({
    required this.book,
    required this.chapter,
    required this.mode,
    required this.verses,
    required this.foreground,
    required this.wide,
    required this.onMode,
    required this.onPrevious,
    required this.onNext,
    required this.onLongPressVerse,
    required this.scrollController,
    required this.onScrollEnd,
  });
  final BibleBook book;
  final int chapter;
  final ReadingMode mode;
  final Future<List<VersePair>> verses;
  final Color foreground;
  final bool wide;
  final ValueChanged<ReadingMode> onMode;
  final VoidCallback onPrevious, onNext;
  final ValueChanged<VersePair> onLongPressVerse;
  final ScrollController scrollController;
  final VoidCallback onScrollEnd;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final title = switch (mode) {
      ReadingMode.chinese => book.zh,
      ReadingMode.english => book.en,
      ReadingMode.bilingual => '${book.zh} / ${book.en}',
    };
    final top = MediaQuery.paddingOf(context).top + (wide ? 72 : 108);
    return FutureBuilder<List<VersePair>>(
      future: verses,
      builder: (context, snapshot) {
        // FutureBuilder keeps the previous future's data while a replacement
        // future is waiting. Never paint that stale chapter under the new title.
        final loaded =
            snapshot.connectionState == ConnectionState.done &&
            !snapshot.hasError;
        final data = loaded
            ? snapshot.data ?? const <VersePair>[]
            : const <VersePair>[];
        return ColoredBox(
          color: colors.canvas,
          child: NotificationListener<ScrollEndNotification>(
            onNotification: (notification) {
              onScrollEnd();
              return false;
            },
            child: CustomScrollView(
              key: ValueKey('${book.id}-$chapter'),
              controller: scrollController,
              scrollCacheExtent: const ScrollCacheExtent.pixels(1200),
              physics: const ClampingScrollPhysics(),
              slivers: [
                SliverPadding(
                  padding: EdgeInsets.fromLTRB(
                    wide ? 46 : 20,
                    top,
                    wide ? 46 : 20,
                    0,
                  ),
                  sliver: SliverList.list(
                    children: [
                      SizedBox(
                        height: wide ? 94 : 112,
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            Expanded(
                              child: Align(
                                alignment: Alignment.bottomLeft,
                                child: AnimatedSwitcher(
                                  duration: const Duration(milliseconds: 240),
                                  reverseDuration: const Duration(
                                    milliseconds: 140,
                                  ),
                                  switchInCurve: Curves.easeOutCubic,
                                  switchOutCurve: Curves.easeOutCubic,
                                  layoutBuilder:
                                      (currentChild, previousChildren) => Stack(
                                        alignment: Alignment.bottomLeft,
                                        children: [
                                          ...previousChildren,
                                          ?currentChild,
                                        ],
                                      ),
                                  transitionBuilder: (child, animation) =>
                                      FadeTransition(
                                        opacity: animation,
                                        child: SlideTransition(
                                          position: Tween<Offset>(
                                            begin: const Offset(.025, 0),
                                            end: Offset.zero,
                                          ).animate(animation),
                                          child: child,
                                        ),
                                      ),
                                  child: Text(
                                    title,
                                    key: ValueKey('${book.id}-${mode.name}'),
                                    maxLines: 2,
                                    overflow: TextOverflow.fade,
                                    style: TextStyle(
                                      fontFamily: 'Exposure',
                                      fontFamilyFallback: const ['NotoSerifTC'],
                                      color: foreground,
                                      fontSize: wide ? 68 : 45,
                                      height: 1.04,
                                      fontWeight: FontWeight.w500,
                                      letterSpacing: 0,
                                    ),
                                  ),
                                ),
                              ),
                            ),
                            Padding(
                              padding: const EdgeInsets.only(
                                left: 9,
                                bottom: 5,
                              ),
                              child: AnimatedSwitcher(
                                duration: const Duration(milliseconds: 420),
                                switchInCurve: springCurve,
                                transitionBuilder: (child, animation) =>
                                    FadeTransition(
                                      opacity: animation,
                                      child: SlideTransition(
                                        position: Tween<Offset>(
                                          begin: const Offset(0, .35),
                                          end: Offset.zero,
                                        ).animate(animation),
                                        child: child,
                                      ),
                                    ),
                                child: Text(
                                  chapter.toString().padLeft(2, '0'),
                                  key: ValueKey('${book.id}-$chapter'),
                                  style: TextStyle(
                                    fontFamily: 'Exposure',
                                    fontSize: 23,
                                    fontStyle: FontStyle.italic,
                                    color: colors.faint,
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 24),
                      Align(
                        alignment: Alignment.centerLeft,
                        child: _Segmented(mode: mode, onChanged: onMode),
                      ),
                      const SizedBox(height: 18),
                      Divider(height: 1, color: colors.line),
                      const SizedBox(height: 18),
                      if (snapshot.connectionState == ConnectionState.waiting)
                        const _VerseSkeleton(),
                      if (snapshot.hasError)
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 70),
                          child: Text(
                            '經文暫時無法載入',
                            style: TextStyle(color: colors.muted),
                          ),
                        ),
                    ],
                  ),
                ),
                SliverPadding(
                  padding: EdgeInsets.symmetric(horizontal: wide ? 46 : 20),
                  sliver: SliverList.builder(
                    itemCount: data.length,
                    itemBuilder: (context, index) => _ScrollAwareEntrance(
                      key: ValueKey(data[index].number),
                      staggerIndex: index,
                      child: _VerseRow(
                        verse: data[index],
                        mode: mode,
                        onLongPress: () => onLongPressVerse(data[index]),
                      ),
                    ),
                  ),
                ),
                SliverPadding(
                  padding: EdgeInsets.fromLTRB(
                    wide ? 46 : 20,
                    28,
                    wide ? 46 : 20,
                    MediaQuery.paddingOf(context).bottom + 32,
                  ),
                  sliver: SliverToBoxAdapter(
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        _ChapterLink(
                          next: false,
                          label: chapter > 1
                              ? '${mode == ReadingMode.english ? book.en : book.zh} ${chapter - 1}'
                              : '—',
                          onTap: onPrevious,
                        ),
                        _ChapterLink(
                          next: true,
                          label: chapter < book.chapters
                              ? '${mode == ReadingMode.english ? book.en : book.zh} ${chapter + 1}'
                              : '—',
                          onTap: onNext,
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _VerseRow extends StatelessWidget {
  const _VerseRow({
    required this.verse,
    required this.mode,
    required this.onLongPress,
  });
  final VersePair verse;
  final ReadingMode mode;
  final VoidCallback onLongPress;
  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onLongPress: onLongPress,
      child: Container(
        margin: const EdgeInsets.only(bottom: 4),
        decoration: BoxDecoration(
          color: Colors.transparent,
          borderRadius: BorderRadius.circular(12),
        ),
        // The verse number and copy share the same left edge.  The old desktop
        // gutter made every verse look indented compared with the chapter title.
        padding: const EdgeInsets.symmetric(vertical: 18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              verse.number.toString().padLeft(2, '0'),
              style: TextStyle(color: colors.faint, fontSize: 10),
            ),
            const SizedBox(height: 6),
            AnimatedSize(
              duration: const Duration(milliseconds: 240),
              curve: Curves.easeOutCubic,
              alignment: Alignment.topCenter,
              clipBehavior: Clip.none,
              child: AnimatedSwitcher(
                duration: const Duration(milliseconds: 210),
                reverseDuration: const Duration(milliseconds: 140),
                switchInCurve: Curves.easeOutCubic,
                switchOutCurve: Curves.easeOutCubic,
                layoutBuilder: (currentChild, previousChildren) => Stack(
                  alignment: Alignment.topLeft,
                  children: [...previousChildren, ?currentChild],
                ),
                transitionBuilder: (child, animation) => FadeTransition(
                  opacity: animation,
                  child: SlideTransition(
                    position: Tween(
                      begin: const Offset(.018, 0),
                      end: Offset.zero,
                    ).animate(animation),
                    child: child,
                  ),
                ),
                child: Column(
                  key: ValueKey(mode),
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (mode != ReadingMode.english)
                      Text(
                        verse.zh,
                        style: TextStyle(
                          color: colors.ink,
                          fontFamily: 'NotoSerifTC',
                          fontSize: MediaQuery.sizeOf(context).width >= 920
                              ? 20
                              : 17,
                          height: 1.9,
                        ),
                      ),
                    if (mode == ReadingMode.bilingual)
                      const SizedBox(height: 8),
                    if (mode != ReadingMode.chinese)
                      Text(
                        verse.en,
                        style: TextStyle(
                          fontFamily: 'OpenRunde',
                          color: mode == ReadingMode.english
                              ? colors.ink
                              : colors.muted,
                          fontSize: mode == ReadingMode.english ? 17 : 14,
                          height: mode == ReadingMode.english ? 1.78 : 1.7,
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ScrollAwareEntrance extends StatelessWidget {
  const _ScrollAwareEntrance({
    super.key,
    required this.child,
    this.staggerIndex = 0,
  });
  final Widget child;
  final int staggerIndex;

  @override
  Widget build(BuildContext context) {
    if (Scrollable.recommendDeferredLoadingForContext(context)) return child;
    final delay = staggerIndex.clamp(0, 10) * .035;
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: 1),
      duration: Duration(milliseconds: 260 + staggerIndex.clamp(0, 12) * 18),
      curve: Interval(delay, 1, curve: Curves.easeOutCubic),
      child: child,
      builder: (context, value, child) => Opacity(
        opacity: value,
        child: Transform.translate(
          offset: Offset(0, 8 * (1 - value)),
          child: child,
        ),
      ),
    );
  }
}

class _Segmented extends StatelessWidget {
  const _Segmented({required this.mode, required this.onChanged});
  final ReadingMode mode;
  final ValueChanged<ReadingMode> onChanged;
  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return Semantics(
      label: '語言',
      child: Container(
        width: 180,
        height: 36,
        padding: const EdgeInsets.all(3),
        decoration: BoxDecoration(
          color: colors.surface,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: colors.line),
        ),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final itemWidth = constraints.maxWidth / ReadingMode.values.length;
            return Stack(
              children: [
                AnimatedPositioned(
                  duration: const Duration(milliseconds: 420),
                  curve: springCurve,
                  left: mode.index * itemWidth,
                  top: 0,
                  bottom: 0,
                  width: itemWidth,
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      color: colors.ink,
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                ),
                Row(
                  children: List.generate(3, (index) {
                    final labels = ['中文', '英文', '雙語'];
                    final active = index == mode.index;
                    return Expanded(
                      child: InkWell(
                        borderRadius: BorderRadius.circular(9),
                        onTap: () => onChanged(ReadingMode.values[index]),
                        child: Center(
                          child: AnimatedDefaultTextStyle(
                            duration: const Duration(milliseconds: 180),
                            style: TextStyle(
                              color: active ? colors.canvas : colors.muted,
                              fontSize: 11,
                              fontFamily: 'OpenRunde',
                            ),
                            child: Text(labels[index]),
                          ),
                        ),
                      ),
                    );
                  }),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _VerseSkeleton extends StatefulWidget {
  const _VerseSkeleton();
  @override
  State<_VerseSkeleton> createState() => _VerseSkeletonState();
}

class _VerseSkeletonState extends State<_VerseSkeleton>
    with SingleTickerProviderStateMixin {
  late final AnimationController controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1100),
  )..repeat(reverse: true);
  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) => Padding(
        padding: const EdgeInsets.only(bottom: 24),
        child: Column(
          children: List.generate(
            6,
            (index) => Container(
              height: 54,
              width: double.infinity,
              margin: const EdgeInsets.only(bottom: 14),
              decoration: BoxDecoration(
                color: colors.surfaceRaised.withValues(
                  alpha: .38 + controller.value * .22,
                ),
                borderRadius: BorderRadius.circular(12),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _Sidebar extends StatelessWidget {
  const _Sidebar({
    required this.book,
    required this.chapter,
    required this.onLibrary,
    required this.onChapter,
  });
  final BibleBook book;
  final int chapter;
  final VoidCallback onLibrary;
  final ValueChanged<int> onChapter;
  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return Padding(
      padding: EdgeInsets.fromLTRB(
        36,
        MediaQuery.paddingOf(context).top + 102,
        22,
        36,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '正在閱讀',
            style: TextStyle(
              color: colors.faint,
              fontSize: 9,
              letterSpacing: 2,
            ),
          ),
          const SizedBox(height: 15),
          InkWell(
            onTap: onLibrary,
            borderRadius: BorderRadius.circular(12),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(8, 8, 8, 16),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      book.zh,
                      style: TextStyle(color: colors.ink, fontSize: 20),
                    ),
                  ),
                  Icon(
                    Icons.keyboard_arrow_down_rounded,
                    color: colors.muted,
                    size: 18,
                  ),
                ],
              ),
            ),
          ),
          Divider(height: 1, color: colors.line),
          const SizedBox(height: 17),
          Expanded(
            child: GridView.builder(
              padding: EdgeInsets.zero,
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 5,
                mainAxisSpacing: 5,
                crossAxisSpacing: 5,
              ),
              itemCount: book.chapters,
              itemBuilder: (_, i) {
                final value = i + 1;
                final active = value == chapter;
                return InkWell(
                  onTap: () => onChapter(value),
                  borderRadius: BorderRadius.circular(9),
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 220),
                    decoration: BoxDecoration(
                      color: active ? colors.ink : Colors.transparent,
                      borderRadius: BorderRadius.circular(9),
                    ),
                    alignment: Alignment.center,
                    child: Text(
                      '$value',
                      style: TextStyle(
                        color: active ? colors.canvas : colors.faint,
                        fontSize: 10,
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _ToolButton extends StatelessWidget {
  const _ToolButton({
    required this.icon,
    required this.label,
    required this.onTap,
  });
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return Semantics(
      label: label,
      button: true,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 13, sigmaY: 13),
          child: Material(
            color: colors.surface.withValues(alpha: .72),
            child: InkWell(
              onTap: onTap,
              child: Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  border: Border.all(color: colors.line.withValues(alpha: .9)),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: AnimatedSwitcher(
                  duration: const Duration(milliseconds: 240),
                  switchInCurve: springCurve,
                  transitionBuilder: (child, animation) => FadeTransition(
                    opacity: animation,
                    child: RotationTransition(
                      turns: Tween(begin: -.12, end: 0.0).animate(animation),
                      child: ScaleTransition(
                        scale: Tween(begin: .82, end: 1.0).animate(animation),
                        child: child,
                      ),
                    ),
                  ),
                  child: Icon(
                    icon,
                    key: ValueKey(icon),
                    color: colors.muted,
                    size: 19,
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _ChapterLink extends StatelessWidget {
  const _ChapterLink({
    required this.next,
    required this.label,
    required this.onTap,
  });
  final bool next;
  final String label;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(10),
      child: Padding(
        padding: const EdgeInsets.all(10),
        child: Row(
          children: [
            if (!next)
              Icon(Icons.arrow_back_rounded, color: colors.muted, size: 18),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 10),
              child: Column(
                crossAxisAlignment: next
                    ? CrossAxisAlignment.end
                    : CrossAxisAlignment.start,
                children: [
                  Text(
                    next ? '下一章' : '上一章',
                    style: TextStyle(color: colors.faint, fontSize: 9),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    label,
                    style: TextStyle(color: colors.ink, fontSize: 13),
                  ),
                ],
              ),
            ),
            if (next)
              Icon(Icons.arrow_forward_rounded, color: colors.muted, size: 18),
          ],
        ),
      ),
    );
  }
}

class _LibraryRoute extends PageRoute<void> {
  _LibraryRoute({required this.builder});

  final WidgetBuilder builder;

  @override
  bool get opaque => false;

  @override
  Color? get barrierColor => Colors.black.withValues(alpha: .62);

  @override
  bool get barrierDismissible => true;

  @override
  String? get barrierLabel => '關閉書卷';

  @override
  bool get maintainState => true;

  @override
  Duration get transitionDuration => const Duration(milliseconds: 420);

  @override
  Duration get reverseTransitionDuration => const Duration(milliseconds: 300);

  @override
  Widget buildPage(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
  ) => builder(context);

  @override
  Widget buildTransitions(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) {
    if (popGestureInProgress) {
      return const PredictiveBackFullscreenPageTransitionsBuilder(
        fallbackColor: Colors.transparent,
      ).buildTransitions(this, context, animation, secondaryAnimation, child);
    }
    final entrance = CurvedAnimation(
      parent: animation,
      curve: springCurve,
      reverseCurve: Curves.easeInOutCubic,
    );
    return FadeTransition(
      opacity: entrance,
      child: SlideTransition(
        position: Tween(
          begin: const Offset(-.12, 0),
          end: Offset.zero,
        ).animate(entrance),
        child: child,
      ),
    );
  }
}

class _LibraryPanel extends StatefulWidget {
  const _LibraryPanel({required this.selected, required this.onSelect});
  final int selected;
  final ValueChanged<int> onSelect;
  @override
  State<_LibraryPanel> createState() => _LibraryPanelState();
}

class _LibraryPanelState extends State<_LibraryPanel> {
  late bool old = bibleBooks[widget.selected].old;
  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final width = math.min(MediaQuery.sizeOf(context).width * .92, 440.0);
    final books = bibleBooks.indexed
        .where((entry) => entry.$2.old == old)
        .toList();
    return Material(
      color: Colors.transparent,
      child: ClipRRect(
        borderRadius: const BorderRadius.horizontal(right: Radius.circular(24)),
        child: ColoredBox(
          color: colors.canvas,
          child: SafeArea(
            bottom: false,
            child: SizedBox(
              width: width,
              height: double.infinity,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(28, 28, 28, 0),
                child: Column(
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            '選擇書卷',
                            style: TextStyle(
                              fontFamily: 'Exposure',
                              color: colors.ink,
                              fontSize: 34,
                            ),
                          ),
                        ),
                        _ToolButton(
                          icon: Icons.close,
                          label: '關閉',
                          onTap: () => Navigator.pop(context),
                        ),
                      ],
                    ),
                    const SizedBox(height: 26),
                    _TestamentSegmented(
                      old: old,
                      onChanged: (value) => setState(() => old = value),
                    ),
                    const SizedBox(height: 14),
                    Expanded(
                      child: AnimatedSwitcher(
                        duration: const Duration(milliseconds: 430),
                        reverseDuration: const Duration(milliseconds: 240),
                        switchInCurve: springCurve,
                        switchOutCurve: Curves.easeInCubic,
                        transitionBuilder: (child, animation) => FadeTransition(
                          opacity: animation,
                          child: SlideTransition(
                            position: Tween(
                              begin: const Offset(0, .025),
                              end: Offset.zero,
                            ).animate(animation),
                            child: child,
                          ),
                        ),
                        layoutBuilder: (currentChild, previousChildren) =>
                            currentChild ?? const SizedBox.shrink(),
                        child: ListView.separated(
                          key: ValueKey(old),
                          itemCount: books.length,
                          separatorBuilder: (context, index) =>
                              Divider(height: 1, color: colors.line),
                          itemBuilder: (_, i) {
                            final index = books[i].$1;
                            final book = books[i].$2;
                            return _ScrollAwareEntrance(
                              staggerIndex: i,
                              child: InkWell(
                                onTap: () => widget.onSelect(index),
                                borderRadius: BorderRadius.circular(12),
                                child: Padding(
                                  padding: const EdgeInsets.symmetric(
                                    vertical: 15,
                                    horizontal: 7,
                                  ),
                                  child: Row(
                                    children: [
                                      SizedBox(
                                        width: 42,
                                        child: Text(
                                          '${index + 1}'.padLeft(2, '0'),
                                          style: TextStyle(
                                            color: colors.faint,
                                            fontSize: 10,
                                          ),
                                        ),
                                      ),
                                      Expanded(
                                        child: Text(
                                          book.zh,
                                          style: TextStyle(
                                            color: colors.ink,
                                            fontSize: 15,
                                            fontWeight: index == widget.selected
                                                ? FontWeight.w600
                                                : FontWeight.w400,
                                          ),
                                        ),
                                      ),
                                      Text(
                                        '${book.chapters} 章',
                                        style: TextStyle(
                                          color: colors.faint,
                                          fontSize: 10,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                            );
                          },
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _TestamentSegmented extends StatelessWidget {
  const _TestamentSegmented({required this.old, required this.onChanged});
  final bool old;
  final ValueChanged<bool> onChanged;
  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return Container(
      height: 42,
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        color: colors.surface,
        border: Border.all(color: colors.line),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Stack(
        children: [
          AnimatedAlign(
            duration: const Duration(milliseconds: 420),
            curve: springCurve,
            alignment: old ? Alignment.centerLeft : Alignment.centerRight,
            child: FractionallySizedBox(
              widthFactor: .5,
              heightFactor: 1,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: colors.ink,
                  borderRadius: BorderRadius.circular(9),
                ),
              ),
            ),
          ),
          Row(
            children: [
              for (final option in [(true, '舊約 · 39'), (false, '新約 · 27')])
                Expanded(
                  child: InkWell(
                    onTap: () => onChanged(option.$1),
                    child: Center(
                      child: Text(
                        option.$2,
                        style: TextStyle(
                          color: option.$1 == old
                              ? colors.canvas
                              : colors.muted,
                          fontSize: 11,
                        ),
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class _SearchDialog extends StatefulWidget {
  const _SearchDialog({
    required this.repository,
    required this.ai,
    required this.onVerse,
  });
  final BibleRepository repository;
  final BibleAiController ai;
  final ValueChanged<ScriptureHit> onVerse;
  @override
  State<_SearchDialog> createState() => _SearchDialogState();
}

class _SearchDialogState extends State<_SearchDialog> {
  final input = TextEditingController();
  String query = '';
  bool searching = false;
  String? error;
  List<ScriptureHit> traditional = const [];
  AiSearchResponse? overview;
  List<(ScriptureHit, String)> aiHits = const [];
  bool pendingCloudSearch = false;
  _SearchMode mode = _SearchMode.traditional;

  @override
  void initState() {
    super.initState();
    widget.ai.addListener(_aiChanged);
  }

  @override
  void dispose() {
    widget.ai.removeListener(_aiChanged);
    input.dispose();
    super.dispose();
  }

  void _aiChanged() {
    if (!mounted) return;
    setState(() {});
    if (pendingCloudSearch && widget.ai.openRouterSignedIn && !searching) {
      pendingCloudSearch = false;
      _searchAi();
    }
  }

  Future<void> _search() async {
    final value = input.text.trim();
    if (value.isEmpty || searching) return;
    setState(() {
      query = value;
      searching = true;
      error = null;
      traditional = const [];
      overview = null;
      aiHits = const [];
    });
    try {
      final localFuture = widget.repository.search(value);
      final aiFuture = widget.ai.isReady ? _resolveAi(value) : null;
      final local = await localFuture;
      if (mounted) setState(() => traditional = local);
      if (aiFuture != null) {
        final (response, resolved) = await aiFuture;
        if (mounted) {
          setState(() {
            overview = response;
            aiHits = resolved;
          });
        }
      }
    } catch (_) {
      if (mounted) setState(() => error = '搜尋暫時未能完成，請再試一次。');
    } finally {
      if (mounted) {
        setState(() => searching = false);
      }
    }
  }

  Future<(AiSearchResponse, List<(ScriptureHit, String)>)> _resolveAi(
    String value,
  ) async {
    final response = await widget.ai.search(value);
    final resolved = <(ScriptureHit, String)>[];
    for (final reference in response.scriptures) {
      final book = bibleBooks
          .where((item) => item.id == reference.bookId)
          .firstOrNull;
      if (book == null ||
          reference.chapter < 1 ||
          reference.chapter > book.chapters) {
        continue;
      }
      final verses = await widget.repository.chapter(book, reference.chapter);
      for (final verse in verses.where(
        (item) =>
            item.number >= reference.verseStart &&
            item.number <= reference.verseEnd,
      )) {
        resolved.add((
          ScriptureHit(book: book, chapter: reference.chapter, verse: verse),
          reference.reason,
        ));
      }
    }
    return (response, resolved);
  }

  Future<void> _searchAi() async {
    final value = input.text.trim();
    if (value.isEmpty || searching || !widget.ai.isReady) return;
    setState(() {
      searching = true;
      error = null;
      overview = null;
      aiHits = const [];
    });
    try {
      final (response, resolved) = await _resolveAi(value);
      if (mounted) {
        setState(() {
          query = value;
          overview = response;
          aiHits = resolved;
        });
      }
    } catch (_) {
      if (mounted) setState(() => error = 'AI 搜尋暫時未能完成，請再試一次。');
    } finally {
      if (mounted) setState(() => searching = false);
    }
  }

  Future<void> _beginOpenRouterLogin() async {
    pendingCloudSearch = input.text.trim().isNotEmpty;
    await widget.ai.beginOpenRouterLogin();
  }

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return Dialog(
      insetPadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 16),
      backgroundColor: colors.canvas,
      shape: RoundedRectangleBorder(
        side: BorderSide(color: colors.line),
        borderRadius: BorderRadius.circular(22),
      ),
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: 760,
          maxHeight: MediaQuery.sizeOf(context).height * .92,
        ),
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(10, 10, 8, 10),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: input,
                      autofocus: true,
                      textInputAction: TextInputAction.search,
                      onSubmitted: (_) => _search(),
                      style: TextStyle(color: colors.ink),
                      decoration: InputDecoration(
                        prefixIcon: Icon(
                          Icons.search_rounded,
                          color: colors.muted,
                        ),
                        hintText: '搜尋全本聖經',
                        hintStyle: TextStyle(color: colors.faint),
                        filled: true,
                        fillColor: colors.surface,
                        contentPadding: const EdgeInsets.symmetric(
                          vertical: 13,
                        ),
                        border: OutlineInputBorder(
                          borderSide: BorderSide(color: colors.line),
                          borderRadius: BorderRadius.circular(16),
                        ),
                        enabledBorder: OutlineInputBorder(
                          borderSide: BorderSide(color: colors.line),
                          borderRadius: BorderRadius.circular(16),
                        ),
                        focusedBorder: OutlineInputBorder(
                          borderSide: BorderSide(color: colors.ink),
                          borderRadius: BorderRadius.circular(16),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 7),
                  _ToolButton(
                    icon: Icons.arrow_forward_rounded,
                    label: '搜尋',
                    onTap: _search,
                  ),
                  const SizedBox(width: 3),
                  _ToolButton(
                    icon: Icons.close_rounded,
                    label: '關閉',
                    onTap: () => Navigator.pop(context),
                  ),
                ],
              ),
            ),
            if (widget.ai.isSupported)
              Padding(
                padding: const EdgeInsets.fromLTRB(10, 0, 10, 8),
                child: _SearchModeSegmented(
                  mode: mode,
                  onChanged: (value) => setState(() => mode = value),
                ),
              ),
            Expanded(
              child: AnimatedSwitcher(
                duration: const Duration(milliseconds: 360),
                switchInCurve: springCurve,
                transitionBuilder: (child, animation) => FadeTransition(
                  opacity: animation,
                  child: SlideTransition(
                    position: Tween(
                      begin: const Offset(0, .025),
                      end: Offset.zero,
                    ).animate(animation),
                    child: child,
                  ),
                ),
                child: _results(colors),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _results(AppColors colors) {
    if (query.isEmpty) {
      return Center(
        key: const ValueKey('empty'),
        child: Text('輸入字詞、人物、事件或主題', style: TextStyle(color: colors.faint)),
      );
    }
    return ListView(
      key: ValueKey(
        '${mode.name}-$query-$searching-${traditional.length}-${aiHits.length}',
      ),
      padding: const EdgeInsets.fromLTRB(10, 8, 10, 20),
      children: [
        if (searching)
          ClipRRect(
            borderRadius: BorderRadius.circular(999),
            child: LinearProgressIndicator(
              minHeight: 4,
              color: colors.ink,
              backgroundColor: colors.surfaceRaised,
            ),
          ),
        if (error != null)
          _SearchSection(
            title: '搜尋狀態',
            child: Text(error!, style: TextStyle(color: colors.muted)),
          ),
        if (mode == _SearchMode.ai &&
            widget.ai.provider == AiProvider.openRouter &&
            widget.ai.requiresLogin)
          _SearchSection(
            title: 'AI Overview',
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    '完成 OpenRouter 登入後會自動繼續 AI 搜尋；傳統全文結果毋須登入。',
                    style: TextStyle(color: colors.muted, height: 1.5),
                  ),
                ),
                _InlineAction(
                  icon: Icons.login_rounded,
                  label: '登入',
                  onTap: _beginOpenRouterLogin,
                ),
              ],
            ),
          ),
        if (mode == _SearchMode.ai && overview != null)
          _SearchSection(
            title: 'AI Overview',
            child: Text(
              overview!.overview,
              style: TextStyle(color: colors.ink, fontSize: 15, height: 1.6),
            ),
          ),
        if (mode == _SearchMode.ai && aiHits.isNotEmpty) ...[
          _SectionLabel(label: 'AI 經文結果 · ${aiHits.length}'),
          ...aiHits
              .take(80)
              .map(
                (item) => _SearchHitTile(
                  hit: item.$1,
                  reason: item.$2,
                  onTap: () => widget.onVerse(item.$1),
                ),
              ),
        ],
        if (mode == _SearchMode.traditional && traditional.isNotEmpty) ...[
          _SectionLabel(
            label:
                '傳統全文結果 · ${traditional.length}${traditional.length == 80 ? '+' : ''}',
          ),
          ...traditional.map(
            (hit) => _SearchHitTile(hit: hit, onTap: () => widget.onVerse(hit)),
          ),
        ],
        if (!searching &&
            !(mode == _SearchMode.ai && !widget.ai.isReady) &&
            (mode == _SearchMode.traditional
                ? traditional.isEmpty
                : aiHits.isEmpty && overview == null))
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 54),
            child: Center(
              child: Text('找不到相符經文', style: TextStyle(color: colors.faint)),
            ),
          ),
      ],
    );
  }
}

enum _SearchMode { traditional, ai }

class _SearchModeSegmented extends StatelessWidget {
  const _SearchModeSegmented({required this.mode, required this.onChanged});
  final _SearchMode mode;
  final ValueChanged<_SearchMode> onChanged;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return Container(
      height: 40,
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        color: colors.surface,
        border: Border.all(color: colors.line),
        borderRadius: BorderRadius.circular(13),
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final width = constraints.maxWidth / 2;
          return Stack(
            children: [
              AnimatedPositioned(
                duration: const Duration(milliseconds: 360),
                curve: springCurve,
                left: mode == _SearchMode.traditional ? 0 : width,
                top: 0,
                bottom: 0,
                width: width,
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: colors.ink,
                    borderRadius: BorderRadius.circular(10),
                  ),
                ),
              ),
              Row(
                children: [
                  for (final option in [
                    (_SearchMode.traditional, '傳統搜尋'),
                    (_SearchMode.ai, 'AI 搜尋'),
                  ])
                    Expanded(
                      child: InkWell(
                        onTap: () => onChanged(option.$1),
                        borderRadius: BorderRadius.circular(10),
                        child: Center(
                          child: Text(
                            option.$2,
                            style: TextStyle(
                              color: mode == option.$1
                                  ? colors.canvas
                                  : colors.muted,
                              fontSize: 11,
                            ),
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ],
          );
        },
      ),
    );
  }
}

class _SearchSection extends StatelessWidget {
  const _SearchSection({required this.title, required this.child});
  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: colors.surface,
        border: Border.all(color: colors.line),
        borderRadius: BorderRadius.circular(18),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: TextStyle(
              color: colors.faint,
              fontSize: 10,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 10),
          child,
        ],
      ),
    );
  }
}

class _SectionLabel extends StatelessWidget {
  const _SectionLabel({required this.label});
  final String label;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(4, 12, 4, 9),
    child: Text(
      label,
      style: TextStyle(
        color: AppColors.of(context).faint,
        fontSize: 10,
        fontWeight: FontWeight.w600,
      ),
    ),
  );
}

class _SearchHitTile extends StatelessWidget {
  const _SearchHitTile({required this.hit, required this.onTap, this.reason});
  final ScriptureHit hit;
  final String? reason;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(15),
        child: Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            border: Border.all(color: colors.line),
            borderRadius: BorderRadius.circular(15),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '${hit.book.zh} ${hit.chapter}:${hit.verse.number}',
                style: TextStyle(color: colors.faint, fontSize: 10),
              ),
              const SizedBox(height: 6),
              Text(
                hit.verse.zh,
                style: TextStyle(
                  color: colors.ink,
                  fontFamily: 'NotoSerifTC',
                  fontSize: 14,
                  height: 1.65,
                ),
              ),
              if (reason?.isNotEmpty == true) ...[
                const SizedBox(height: 7),
                Text(
                  reason!,
                  style: TextStyle(
                    color: colors.muted,
                    fontSize: 11,
                    height: 1.45,
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _ActionTile extends StatelessWidget {
  const _ActionTile({
    required this.icon,
    required this.label,
    required this.onTap,
  });
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 7),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Container(
          height: 54,
          padding: const EdgeInsets.symmetric(horizontal: 15),
          decoration: BoxDecoration(
            color: colors.surface,
            border: Border.all(color: colors.line),
            borderRadius: BorderRadius.circular(16),
          ),
          child: Row(
            children: [
              Icon(icon, color: colors.ink, size: 19),
              const SizedBox(width: 13),
              Text(label, style: TextStyle(color: colors.ink)),
            ],
          ),
        ),
      ),
    );
  }
}

class _InlineAction extends StatelessWidget {
  const _InlineAction({
    required this.icon,
    required this.label,
    required this.onTap,
  });
  final IconData icon;
  final String label;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return Opacity(
      opacity: onTap == null ? .5 : 1,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Container(
          height: 38,
          padding: const EdgeInsets.symmetric(horizontal: 12),
          decoration: BoxDecoration(
            color: colors.ink,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, size: 16, color: colors.canvas),
              const SizedBox(width: 7),
              Text(
                label,
                style: TextStyle(
                  color: colors.canvas,
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
