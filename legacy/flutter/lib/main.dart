import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;
import 'dart:ui';

import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'ai_chat_page.dart';
import 'ai_service.dart';
import 'app_markdown.dart';
import 'app_navbar.dart';
import 'app_settings.dart';
import 'app_theme.dart';
import 'app_ui.dart';
import 'bible_data.dart';
import 'devotion_page.dart';
import 'l10n/app_localizations.dart';
import 'localization.dart';

const canvas = Color(0xFF090909);
const surface = Color(0xFF111111);
const surface2 = Color(0xFF171717);
const ink = Color(0xFFF1EFE9);
const muted = Color(0xFF8C8B86);
const faint = Color(0xFF5E5E5A);
const line = Color(0xFF292927);

enum ReadingMode { chinese, english, bilingual }

bool _usesEnglishUi(BuildContext context) =>
    AppSettingsScope.maybeOf(context)?.state.locale == AppLocale.en;

/// Book names follow the reading mode first: English/bilingual reading shows
/// English names even when the app UI language is Chinese.
String _bookName(BuildContext context, BibleBook book, [ReadingMode? readingMode]) =>
    (readingMode != null && readingMode != ReadingMode.chinese) ||
            _usesEnglishUi(context)
        ? book.en
        : book.zh;

class ReaderLocation {
  const ReaderLocation({
    required this.bookId,
    required this.chapter,
    required this.mode,
    required this.scrollOffset,
  });

  final String bookId;
  final int chapter;
  final ReadingMode mode;
  final double scrollOffset;
}

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      systemNavigationBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.light,
      systemNavigationBarIconBrightness: Brightness.light,
      systemStatusBarContrastEnforced: false,
      systemNavigationBarContrastEnforced: false,
    ),
  );
  final settings = AppSettingsController.instance;
  await settings.initialize();
  final radii = await AppRadii.load();
  runApp(BibleApp(settings: settings, radii: radii));
}

class BibleApp extends StatefulWidget {
  const BibleApp({
    super.key,
    required this.settings,
    this.radii = AppRadii.fallback,
  });
  final AppSettingsController settings;
  final AppRadii radii;
  @override
  State<BibleApp> createState() => _BibleAppState();
}

class _BibleAppState extends State<BibleApp> {
  @override
  void initState() {
    super.initState();
    widget.settings.addListener(_settingsChanged);
    BibleAiController.instance.setResponseLocale(widget.settings.state.locale);
  }

  @override
  void dispose() {
    widget.settings.removeListener(_settingsChanged);
    super.dispose();
  }

  void _settingsChanged() {
    BibleAiController.instance.setResponseLocale(widget.settings.state.locale);
    if (mounted) setState(() {});
  }

  ThemeData _theme(Brightness brightness, AppColors colors) {
    final theme = ThemeData(
      brightness: brightness,
      scaffoldBackgroundColor: colors.canvas,
      colorScheme: ColorScheme.fromSeed(
        seedColor: colors.ink,
        brightness: brightness,
        surface: colors.surface,
        onSurface: colors.ink,
      ),
      extensions: [colors, widget.radii],
      fontFamily: 'OpenRunde',
      fontFamilyFallback: const [
        'PingFang TC',
        'Noto Sans CJK TC',
        'Microsoft JhengHei',
        'sans-serif',
      ],
      textSelectionTheme: TextSelectionThemeData(
        cursorColor: colors.ink,
        selectionColor: const Color(0xFF376996),
      ),
    );
    return theme.copyWith(
      textTheme: theme.textTheme.apply(
        bodyColor: colors.ink,
        displayColor: colors.ink,
      ),
      iconTheme: IconThemeData(color: colors.ink),
    );
  }

  @override
  Widget build(BuildContext context) {
    final settings = widget.settings.state;
    final launchRoute = PlatformDispatcher.instance.defaultRouteName;
    final chatLaunch = launchRoute.startsWith('/ai-chat');
    final chatPayload = chatLaunch
        ? _decodeChatPayload(launchRoute)
        : const <String, dynamic>{};
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Bible',
      locale: settings.locale.locale,
      supportedLocales: AppLocalizations.supportedLocales,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      themeAnimationDuration: const Duration(milliseconds: 420),
      themeAnimationCurve: springCurve,
      theme: _theme(Brightness.light, AppColors.light),
      darkTheme: _theme(Brightness.dark, AppColors.dark),
      themeMode: settings.themeMode,
      builder: (context, child) => AppSettingsScope(
        controller: widget.settings,
        child: Material(
          type: MaterialType.transparency,
          child: child ?? const SizedBox.shrink(),
        ),
      ),
      home: chatLaunch
          ? AiChatPage(
              initialQuestion: chatPayload['initialQuestion'] as String?,
              scriptureContext: chatPayload['scriptureContext'] as String?,
              scriptureReference: chatPayload['scriptureReference'] as String?,
              scriptureAttachment:
                  chatPayload['scriptureAttachment'] as String?,
              autoSend: chatPayload['autoSend'] == true,
              settingsController: widget.settings,
            )
          : BibleHome(settingsController: widget.settings),
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
    this.settingsController,
    this.repository,
    this.aiController,
  });
  final AppSettingsController? settingsController;
  final BibleRepository? repository;
  final BibleAiController? aiController;
  @override
  State<BibleHome> createState() => _BibleHomeState();
}

class _BibleHomeState extends State<BibleHome> with WidgetsBindingObserver {
  late final repository = widget.repository ?? BibleRepository();
  late final ai = widget.aiController ?? BibleAiController.instance;
  late final settings =
      widget.settingsController ?? AppSettingsController.instance;
  final readerScroll = ScrollController(keepScrollOffset: false);
  Timer? scrollSaveTimer;
  Future<void> scrollSaveQueue = Future<void>.value();
  final Map<String, double> scrollOffsets = {};
  final Map<int, GlobalKey> verseKeys = {};
  bool applyingSavedScroll = false;
  int scrollRestoreEpoch = 0;
  int positionRestoreEpoch = 0;
  int bookIndex = 0;
  int chapter = 1;
  ReadingMode mode = ReadingMode.chinese;
  Future<List<VersePair>>? verses;
  ReaderLocation? searchOrigin;
  int tab = 0;
  final Set<int> visitedTabs = {0};

  BibleBook get book => bibleBooks[bookIndex];
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    ai.addListener(_aiChanged);
    settings.addListener(_settingsChanged);
    ai.setResponseLocale(settings.state.locale);
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
    settings.removeListener(_settingsChanged);
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

  void _settingsChanged() {
    ai.setResponseLocale(settings.state.locale);
    if (mounted) {
      setState(() {
        // A just-hidden devotion tab must never stay selected.
        if (!settings.state.showDevotion && tab == 2) {
          tab = 0;
          FocusManager.instance.primaryFocus?.unfocus();
        }
      });
    }
  }

  void _load({bool persist = true, int? targetVerse}) {
    verseKeys.clear();
    final chapterFuture = repository.chapter(book, chapter);
    verses = chapterFuture;
    if (targetVerse == null) {
      _restoreScrollOffset(_scrollKey, chapterFuture);
    } else {
      _scrollToVerse(chapterFuture, targetVerse);
    }
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
    if (applyingSavedScroll) return;
    final offset = readerScroll.hasClients
        ? readerScroll.offset
        : scrollOffsets[_scrollKey];
    if (offset == null) return;
    _saveScrollOffset(_scrollKey, offset);
  }

  void _beforeUserNavigation({bool clearSearchOrigin = true}) {
    positionRestoreEpoch++;
    scrollSaveTimer?.cancel();
    _saveCurrentScrollOffset();
    if (clearSearchOrigin) searchOrigin = null;
  }

  ReaderLocation _currentReaderLocation() => ReaderLocation(
    bookId: book.id,
    chapter: chapter,
    mode: mode,
    scrollOffset: readerScroll.hasClients
        ? readerScroll.offset
        : scrollOffsets[_scrollKey] ?? 0,
  );

  void _restoreSearchOrigin() {
    final origin = searchOrigin;
    if (origin == null) return;
    final index = bibleBooks.indexWhere((item) => item.id == origin.bookId);
    if (index < 0) {
      setState(() => searchOrigin = null);
      return;
    }
    _beforeUserNavigation(clearSearchOrigin: false);
    final originKey = '${origin.bookId}-${origin.chapter}';
    scrollOffsets[originKey] = origin.scrollOffset;
    setState(() {
      searchOrigin = null;
      bookIndex = index;
      chapter = origin.chapter.clamp(1, bibleBooks[index].chapters);
      mode = origin.mode;
      _load();
    });
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

  Future<void> _scrollToVerse(
    Future<List<VersePair>> chapterFuture,
    int targetVerse,
  ) async {
    final epoch = ++scrollRestoreEpoch;
    applyingSavedScroll = true;
    if (readerScroll.hasClients) readerScroll.jumpTo(0);
    try {
      final data = await chapterFuture;
      final targetIndex = data.indexWhere(
        (verse) => verse.number == targetVerse,
      );
      if (targetIndex < 0) return;

      await WidgetsBinding.instance.endOfFrame;
      await WidgetsBinding.instance.endOfFrame;
      if (!mounted || epoch != scrollRestoreEpoch || !readerScroll.hasClients) {
        return;
      }

      final position = readerScroll.position;
      final estimated =
          position.maxScrollExtent * ((targetIndex + 1) / (data.length + 1));
      readerScroll.jumpTo(
        estimated.clamp(position.minScrollExtent, position.maxScrollExtent),
      );

      for (var attempt = 0; attempt < 32; attempt++) {
        await WidgetsBinding.instance.endOfFrame;
        if (!mounted ||
            epoch != scrollRestoreEpoch ||
            !readerScroll.hasClients) {
          return;
        }

        final targetContext = verseKeys[targetVerse]?.currentContext;
        if (targetContext != null && targetContext.mounted) {
          await Scrollable.ensureVisible(
            targetContext,
            alignment: .18,
            duration: const Duration(milliseconds: 280),
            curve: Curves.easeOutCubic,
          );
          if (mounted && epoch == scrollRestoreEpoch) {
            _saveScrollOffset(_scrollKey, readerScroll.offset);
          }
          return;
        }

        final built = verseKeys.entries
            .where((entry) => entry.value.currentContext != null)
            .map((entry) => entry.key)
            .toList(growable: false);
        if (built.isEmpty) continue;
        final first = built.reduce(math.min);
        final last = built.reduce(math.max);
        final direction = targetVerse < first
            ? -1.0
            : targetVerse > last
            ? 1.0
            : 0.0;
        if (direction == 0) continue;

        final current = readerScroll.position;
        final next =
            (readerScroll.offset + direction * current.viewportDimension * .72)
                .clamp(current.minScrollExtent, current.maxScrollExtent);
        if ((next - readerScroll.offset).abs() < 1) return;
        readerScroll.jumpTo(next);
      }
    } catch (_) {
      // The reader's FutureBuilder owns chapter load errors.
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
    final colors = AppColors.of(context);
    final dark = Theme.of(context).brightness == Brightness.dark;
    final overlayStyle = SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      systemNavigationBarColor: Colors.transparent,
      statusBarIconBrightness: dark ? Brightness.light : Brightness.dark,
      systemNavigationBarIconBrightness: dark
          ? Brightness.light
          : Brightness.dark,
      systemStatusBarContrastEnforced: false,
      systemNavigationBarContrastEnforced: false,
    );
    final effectiveStyle = settings.state.navbarStyle;
    final showNavbar = settings.state.showNavbar;
    final showDevotion = settings.state.showDevotion;
    // Devotion is the last item so filtering it keeps bible/ask indices —
    // and therefore tab state and _shellPage lookups — unchanged.
    final items = navBarItems(
      context,
    ).where((item) => showDevotion || item.tab != AppNavTab.devotion).toList();
    return LayoutBuilder(
      builder: (context, constraints) {
        final wide = constraints.maxWidth >= 920;
        final readerBackground = Stack(
          fit: StackFit.expand,
          children: [
            Positioned.fill(child: ColoredBox(color: colors.canvas)),
            Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (wide)
                  SizedBox(
                    width: 270,
                    child: _Sidebar(
                      book: book,
                      chapter: chapter,
                      readingMode: mode,
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
                    foreground: colors.ink,
                    wide: wide,
                    onMode: (value) {
                      _beforeUserNavigation();
                      setState(() {
                        mode = value;
                        _persistPosition();
                      });
                    },
                    onChapter: _selectChapter,
                    onPrevious: _previous,
                    onNext: _next,
                    onLongPressVerse: _openVerseActions,
                    scrollController: readerScroll,
                    verseKeys: verseKeys,
                    onScrollEnd: _saveCurrentScrollOffset,
                  ),
                ),
              ],
            ),
          ],
        );
        final readerControls = Stack(
          fit: StackFit.expand,
          children: [
            Positioned(
              top: MediaQuery.paddingOf(context).top + 4,
              left: 20,
              right: 20,
              child: Row(
                children: [
                  if (!wide)
                    AppGlyphButton(
                      glyph: AppGlyph.menu,
                      label: context.l10n.selectBook,
                      onTap: _openLibrary,
                    ),
                  const Spacer(),
                  if (!showNavbar) ...[
                    _IconControlButton(
                      icon: LucideIcons.sparkles,
                      label: context.l10n.tabAsk,
                      onTap: () => _selectTab(1),
                    ),
                    if (showDevotion) ...[
                      const SizedBox(width: 9),
                      _IconControlButton(
                        icon: LucideIcons.sunrise,
                        label: context.l10n.tabDevotion,
                        onTap: () => _selectTab(2),
                      ),
                    ],
                    const SizedBox(width: 9),
                  ],
                  AppGlyphButton(
                    glyph: AppGlyph.search,
                    label: context.l10n.searchWholeBible,
                    onTap: _openSearch,
                  ),
                  const SizedBox(width: 9),
                  // Standard gear icon (matches the chat page) so settings
                  // reads clearly and opens reliably.
                  AppControlSurface(
                    child: AppTap(
                      label: context.l10n.settings,
                      onTap: _openSettings,
                      child: SizedBox(
                        width: 40,
                        height: 40,
                        child: Center(
                          child: Icon(
                            LucideIcons.settings,
                            size: 19,
                            color: colors.ink,
                          ),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            if (searchOrigin != null)
              Positioned(
                top: MediaQuery.paddingOf(context).top + 56,
                left: 20,
                child: _ReturnToSearchOrigin(
                  label: context.l10n.returnToSearchOrigin,
                  onTap: _restoreSearchOrigin,
                ),
              ),
          ],
        );
        final reader = Stack(
          fit: StackFit.expand,
          children: [readerBackground, readerControls],
        );
        final pages = Stack(
          fit: StackFit.expand,
          children: [
            for (var index = 0; index < 3; index++) Positioned.fill(
              child: _shellPage(index, reader),
            ),
          ],
        );
        final shell = Stack(
          fit: StackFit.expand,
          children: [
            pages,
            if (showNavbar)
              Positioned(
                left: kAppNavBarHorizontalInset,
                right: kAppNavBarHorizontalInset,
                bottom:
                    MediaQuery.paddingOf(context).bottom + kAppNavBarBottomGap,
                height: kAppNavBarHeight,
                child: AppNavBar(
                  items: items,
                  index: tab,
                  onChanged: _selectTab,
                  style: effectiveStyle,
                ),
              ),
          ],
        );
        return PopScope(
          canPop: tab == 0 && searchOrigin == null,
          onPopInvokedWithResult: (didPop, result) {
            if (didPop) return;
            if (tab != 0) {
              setState(() => tab = 0);
              return;
            }
            _restoreSearchOrigin();
          },
          child: AnnotatedRegion<SystemUiOverlayStyle>(
            key: const ValueKey('reader-system-ui-overlay'),
            value: overlayStyle,
            child: shell,
          ),
        );
      },
    );
  }

  void _selectTab(int value) {
    if (value == tab) return;
    // Drop any active focus (e.g. the chat composer) so the keyboard closes
    // and cannot be re-summoned by a hidden tab's text field.
    FocusManager.instance.primaryFocus?.unfocus();
    setState(() {
      tab = value;
      visitedTabs.add(value);
    });
  }

  /// Tabs mount lazily on first visit, then stay alive offstage. Hidden tabs
  /// run with tickers muted and are removed from the focus chain so their
  /// text fields can never summon the keyboard.
  Widget _shellPage(int index, Widget reader) {
    final visited = visitedTabs.contains(index);
    final active = tab == index;
    Widget page = const SizedBox.expand();
    if (visited) {
      page = switch (index) {
        0 => reader,
        1 => AiChatPage(
          embedded: true,
          aiController: ai,
          settingsController: settings,
        ),
        _ => const DevotionPage(),
      };
    }
    return Offstage(
      offstage: !active,
      child: TickerMode(
        enabled: active,
        child: Focus(
          canRequestFocus: active,
          descendantsAreFocusable: active,
          child: page,
        ),
      ),
    );
  }

  void _openLibrary() {
    // Close any focus first so dismissing the library sheet cannot hand
    // focus back to a text field and pop the keyboard.
    FocusManager.instance.primaryFocus?.unfocus();
    Navigator.of(context).push(
      _LibraryRoute(
        barrierLabel: context.l10n.closeLibrary,
        builder: (context) => Align(
          alignment: Alignment.centerLeft,
          child: _LibraryPanel(
            selected: bookIndex,
            readingMode: mode,
            onSelect: (index) {
              Navigator.pop(context);
              _selectBook(index);
            },
          ),
        ),
      ),
    );
  }

  Future<void> _openSettings() => Navigator.of(context).push(
    AppPageRoute<void>(
      builder: (_) =>
          AiSettingsPage(aiController: ai, settingsController: settings),
    ),
  );

  Future<void> _openSearch() async {
    final origin = _currentReaderLocation();
    await ai.reloadSharedSettings();
    if (!mounted) return;
    await showGeneralDialog<void>(
      context: context,
      barrierDismissible: true,
      barrierLabel: context.l10n.closeSearch,
      barrierColor: Colors.black.withValues(alpha: .72),
      transitionDuration: const Duration(milliseconds: 180),
      pageBuilder: (dialogContext, animation, secondaryAnimation) =>
          _SearchDialog(
            repository: repository,
            ai: ai,
            readingMode: mode,
            onVerse: (hit) {
              Navigator.pop(dialogContext);
              final index = bibleBooks.indexWhere(
                (item) => item.id == hit.book.id,
              );
              if (index >= 0) {
                _beforeUserNavigation(clearSearchOrigin: false);
                setState(() {
                  searchOrigin = origin;
                  bookIndex = index;
                  chapter = hit.chapter;
                  _load(targetVerse: hit.verse.number);
                });
              }
            },
          ),
      transitionBuilder: (context, animation, secondaryAnimation, child) =>
          FadeTransition(opacity: animation, child: child),
    );
  }

  Future<void> _openVerseActions(VersePair verse) async {
    final chapterVerses = await verses!;
    if (!mounted) return;
    final reference =
        '${_bookName(context, book, mode)} $chapter:${verse.number}';
    final selected = '$reference\n${verse.zh}\n${verse.en}';
    final fullChapter = chapterVerses
        .map(
          (item) =>
              '${book.zh} $chapter:${item.number}\n中文：${item.zh}\nEnglish: ${item.en}',
        )
        .join('\n\n');
    await showGeneralDialog<void>(
      context: context,
      barrierDismissible: true,
      barrierLabel: context.l10n.closeScriptureActions,
      barrierColor: Colors.black.withValues(alpha: .62),
      transitionDuration: const Duration(milliseconds: 200),
      pageBuilder: (sheetContext, animation, secondaryAnimation) => Align(
        alignment: Alignment.bottomCenter,
        child: SafeArea(
          top: false,
          child: Container(
            width: double.infinity,
            margin: const EdgeInsets.fromLTRB(10, 0, 10, 10),
            padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
            decoration: BoxDecoration(
              color: AppColors.of(sheetContext).surfaceRaised,
              border: Border.all(color: AppColors.of(sheetContext).line),
              borderRadius: BorderRadius.circular(
                AppRadii.of(sheetContext).surface,
              ),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 32,
                  height: 3,
                  margin: const EdgeInsets.only(bottom: 10),
                  color: AppColors.of(sheetContext).faint,
                ),
                _ActionTile(
                  glyph: AppGlyph.copy,
                  label: context.l10n.copyScripture,
                  onTap: () async {
                    await Clipboard.setData(ClipboardData(text: selected));
                    if (sheetContext.mounted) Navigator.pop(sheetContext);
                  },
                ),
                if (ai.isSupported)
                  _ActionTile(
                    glyph: AppGlyph.chat,
                    label: context.l10n.askAi,
                    onTap: () {
                      Navigator.pop(sheetContext);
                      _openAiChat(
                        scriptureContext: fullChapter,
                        scriptureReference: reference,
                        scriptureAttachment: selected,
                      );
                    },
                  ),
                if (ai.isSupported)
                  _ActionTile(
                    glyph: AppGlyph.book,
                    label: context.l10n.explainScripture,
                    onTap: () {
                      Navigator.pop(sheetContext);
                      _openAiChat(
                        initialQuestion: context.l10n.explainScripturePrompt(
                          reference,
                        ),
                        scriptureContext: fullChapter,
                        scriptureReference: reference,
                        scriptureAttachment: selected,
                        autoSend: true,
                      );
                    },
                  ),
              ],
            ),
          ),
        ),
      ),
      transitionBuilder: (context, animation, secondaryAnimation, child) =>
          SlideTransition(
            position:
                Tween<Offset>(
                  begin: const Offset(0, .12),
                  end: Offset.zero,
                ).animate(
                  CurvedAnimation(
                    parent: animation,
                    curve: Curves.easeOutCubic,
                  ),
                ),
            child: FadeTransition(opacity: animation, child: child),
          ),
    );
  }

  Future<void> _openAiChat({
    String? initialQuestion,
    String? scriptureContext,
    String? scriptureReference,
    String? scriptureAttachment,
    bool autoSend = false,
  }) async {
    await Navigator.of(context).push(
      AppPageRoute<void>(
        builder: (_) => AiChatPage(
          initialQuestion: initialQuestion,
          scriptureContext: scriptureContext,
          scriptureReference: scriptureReference,
          scriptureAttachment: scriptureAttachment,
          autoSend: autoSend,
          aiController: ai,
          settingsController: settings,
        ),
      ),
    );
  }
}

/// Top-bar icon control sharing the settings-gear styling; used for the
/// Ask/Devotion shortcuts that replace the hidden bottom navbar.
class _IconControlButton extends StatelessWidget {
  const _IconControlButton({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return AppControlSurface(
      child: AppTap(
        label: label,
        onTap: onTap,
        child: SizedBox(
          width: 40,
          height: 40,
          child: Center(
            child: Icon(icon, size: 19, color: AppColors.of(context).ink),
          ),
        ),
      ),
    );
  }
}

class _ReturnToSearchOrigin extends StatelessWidget {
  const _ReturnToSearchOrigin({required this.label, required this.onTap});

  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return AppControlSurface(
      child: AppTap(
        label: label,
        onTap: onTap,
        child: SizedBox(
          height: 40,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 11),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                AppGlyphView(AppGlyph.back, color: colors.ink, size: 17),
                const SizedBox(width: 7),
                Text(
                  label,
                  style: TextStyle(
                    color: colors.ink,
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
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
    required this.onChapter,
    required this.onPrevious,
    required this.onNext,
    required this.onLongPressVerse,
    required this.scrollController,
    required this.verseKeys,
    required this.onScrollEnd,
  });
  final BibleBook book;
  final int chapter;
  final ReadingMode mode;
  final Future<List<VersePair>> verses;
  final Color foreground;
  final bool wide;
  final ValueChanged<ReadingMode> onMode;
  final ValueChanged<int> onChapter;
  final VoidCallback onPrevious, onNext;
  final ValueChanged<VersePair> onLongPressVerse;
  final ScrollController scrollController;
  final Map<int, GlobalKey> verseKeys;
  final VoidCallback onScrollEnd;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final chapterAnchorKey = GlobalKey();
    final title = _bookName(context, book, mode);
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
            child: _maybeWrapWithAndroidBlur(
              context,
              CustomScrollView(
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
                            AppControlSurface(
                              key: const ValueKey('chapter-anchor'),
                              color: colors.surfaceRaised.withValues(alpha: .5),
                              child: AppTap(
                                key: chapterAnchorKey,
                                label: context.l10n.selectChapterCurrent(
                                  chapter,
                                ),
                                onTap: () => _showChapterPicker(
                                  context,
                                  chapterAnchorKey,
                                ),
                                child: Padding(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 12,
                                    vertical: 8,
                                  ),
                                  child: Row(
                                    mainAxisSize: MainAxisSize.min,
                                    crossAxisAlignment: CrossAxisAlignment.center,
                                    children: [
                                      AnimatedSwitcher(
                                        duration: const Duration(
                                          milliseconds: 420,
                                        ),
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
                                          textAlign: TextAlign.center,
                                          style: TextStyle(
                                            fontFamily: 'Exposure',
                                            fontSize: 23,
                                            height: 1,
                                            fontStyle: FontStyle.italic,
                                            color: colors.faint,
                                          ),
                                        ),
                                      ),
                                    ],
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
                      Container(height: 1, color: colors.line),
                      const SizedBox(height: 18),
                      if (snapshot.connectionState == ConnectionState.waiting)
                        const _VerseSkeleton(),
                      if (snapshot.hasError)
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 70),
                          child: Text(
                            context.l10n.scriptureLoadFailed,
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
                      key: verseKeys.putIfAbsent(
                        data[index].number,
                        GlobalKey.new,
                      ),
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
                    appNavBottomClearance(context) + 32,
                  ),
                  sliver: SliverToBoxAdapter(
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        _ChapterLink(
                          next: false,
                          label: chapter > 1
                              ? '${_bookName(context, book, mode)} ${chapter - 1}'
                              : '—',
                          onTap: onPrevious,
                        ),
                        _ChapterLink(
                          next: true,
                          label: chapter < book.chapters
                              ? '${_bookName(context, book, mode)} ${chapter + 1}'
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
           ),
         );
       },
     );
   }

  Widget _maybeWrapWithAndroidBlur(BuildContext context, Widget child) => child;

  Future<void> _showChapterPicker(
    BuildContext context,
    GlobalKey anchorKey,
  ) async {
    final anchorBox =
        anchorKey.currentContext?.findRenderObject() as RenderBox?;
    if (anchorBox == null || !anchorBox.hasSize) return;

    final anchorTopLeft = anchorBox.localToGlobal(Offset.zero);
    final anchorRect = anchorTopLeft & anchorBox.size;
    final selected = await showGeneralDialog<int>(
      context: context,
      barrierDismissible: true,
      barrierLabel: context.l10n.closeChapterPicker,
      barrierColor: Colors.black.withValues(alpha: .16),
      transitionDuration: const Duration(milliseconds: 320),
      pageBuilder: (dialogContext, animation, secondaryAnimation) {
        final size = MediaQuery.sizeOf(dialogContext);
        final padding = MediaQuery.paddingOf(dialogContext);
        final width = math.min(330.0, size.width - 32);
        final right = (size.width - anchorRect.right).clamp(16.0, 28.0);
        final top = math.min(anchorRect.bottom + 10, size.height * .36);
        final maxHeight = math.min(
          400.0,
          size.height - top - padding.bottom - 16,
        );
        return Stack(
          children: [
            Positioned(
              top: top,
              right: right,
              width: width,
              child: _ChapterPickerBubble(
                book: book,
                chapter: chapter,
                maxHeight: maxHeight,
                onSelected: (value) => Navigator.pop(dialogContext, value),
              ),
            ),
          ],
        );
      },
      transitionBuilder: (context, animation, secondaryAnimation, child) {
        final curved = CurvedAnimation(parent: animation, curve: springCurve);
        return FadeTransition(
          opacity: CurvedAnimation(
            parent: animation,
            curve: const Interval(0, .65, curve: Curves.easeOut),
          ),
          child: ScaleTransition(
            alignment: Alignment.topRight,
            scale: Tween(begin: .82, end: 1.0).animate(curved),
            child: child,
          ),
        );
      },
    );
    if (selected != null && selected != chapter) onChapter(selected);
  }
}

class _ChapterPickerBubble extends StatelessWidget {
  const _ChapterPickerBubble({
    required this.book,
    required this.chapter,
    required this.maxHeight,
    required this.onSelected,
  });

  final BibleBook book;
  final int chapter;
  final double maxHeight;
  final ValueChanged<int> onSelected;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return ConstrainedBox(
      constraints: BoxConstraints(maxHeight: maxHeight),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(AppRadii.of(context).screen),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 24, sigmaY: 24),
          child: Container(
            decoration: BoxDecoration(
              color: colors.surface.withValues(alpha: .82),
              border: Border.all(color: colors.line.withValues(alpha: .84)),
              borderRadius: BorderRadius.circular(AppRadii.of(context).screen),
            ),
            padding: const EdgeInsets.fromLTRB(14, 14, 14, 16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(6, 2, 3, 12),
                  child: Row(
                    children: [
                      Expanded(
                        child: Text(
                          context.l10n.selectChapter,
                          style: TextStyle(
                            color: colors.ink,
                            fontSize: 15,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                      Material(
                        color: Colors.transparent,
                        shape: const CircleBorder(),
                        child: InkWell(
                          customBorder: const CircleBorder(),
                          onTap: () => Navigator.pop(context),
                          child: Container(
                            width: 32,
                            height: 32,
                            decoration: BoxDecoration(
                              color: colors.surfaceRaised.withValues(alpha: .55),
                              shape: BoxShape.circle,
                              border: Border.all(color: colors.line),
                            ),
                            child: Center(
                              child: Icon(
                                Icons.close,
                                size: 16,
                                color: colors.ink,
                              ),
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                Flexible(
                  child: LayoutBuilder(
                    builder: (context, gridConstraints) {
                      final cellWidth =
                          (gridConstraints.maxWidth - 28) / 5;
                      return GridView.builder(
                        shrinkWrap: true,
                        padding: EdgeInsets.zero,
                        physics: const ClampingScrollPhysics(),
                        gridDelegate:
                            SliverGridDelegateWithFixedCrossAxisCount(
                          crossAxisCount: 5,
                          mainAxisSpacing: 7,
                          crossAxisSpacing: 7,
                          childAspectRatio: cellWidth / 46,
                        ),
                        itemCount: book.chapters,
                        itemBuilder: (context, index) {
                          final value = index + 1;
                          final active = value == chapter;
                          return AppTap(
                            key: ValueKey('chapter-picker-$value'),
                            label: context.l10n.chapterNumber(value),
                            selected: active,
                            onTap: () => onSelected(value),
                            child: Container(
                              alignment: Alignment.center,
                              decoration: BoxDecoration(
                                color: active
                                    ? colors.ink
                                    : colors.surfaceRaised
                                        .withValues(alpha: .55),
                                border: Border.all(
                                  color: active ? colors.ink : colors.line,
                                ),
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Text(
                                '$value',
                                textAlign: TextAlign.center,
                                style: TextStyle(
                                  color: active ? colors.canvas : colors.ink,
                                  fontSize: 13,
                                  height: 1,
                                  fontWeight: active
                                      ? FontWeight.w600
                                      : FontWeight.w400,
                                ),
                              ),
                            ),
                          );
                        },
                      );
                    },
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
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
          borderRadius: BorderRadius.circular(AppRadii.of(context).control),
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
                          fontFamilyFallback: const [
                            'Noto Serif CJK TC',
                            'serif',
                          ],
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
    return Semantics(
      label: context.l10n.readingLanguage,
      child: SizedBox(
        width: 180,
        child: AppSegmented<ReadingMode>(
          height: 36,
          choices: [
            AppChoice(value: ReadingMode.chinese, label: context.l10n.chinese),
            AppChoice(value: ReadingMode.english, label: context.l10n.english),
            AppChoice(
              value: ReadingMode.bilingual,
              label: context.l10n.bilingual,
            ),
          ],
          selected: mode,
          onChanged: onChanged,
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
                borderRadius: BorderRadius.circular(
                  AppRadii.of(context).control,
                ),
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
    required this.readingMode,
    required this.onLibrary,
    required this.onChapter,
  });
  final BibleBook book;
  final int chapter;
  final ReadingMode readingMode;
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
            context.l10n.currentlyReading,
            style: TextStyle(
              color: colors.faint,
              fontSize: 9,
              letterSpacing: 2,
            ),
          ),
          const SizedBox(height: 15),
          AppTap(
            label: context.l10n.selectBook,
            onTap: onLibrary,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(8, 8, 8, 16),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      _bookName(context, book, readingMode),
                      style: TextStyle(color: colors.ink, fontSize: 20),
                    ),
                  ),
                  AppGlyphView(
                    AppGlyph.chevronDown,
                    color: colors.muted,
                    size: 18,
                  ),
                ],
              ),
            ),
          ),
          Container(height: 1, color: colors.line),
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
                return AppTap(
                  label: context.l10n.chapterNumber(value),
                  onTap: () => onChapter(value),
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 220),
                    decoration: BoxDecoration(
                      color: active ? colors.ink : Colors.transparent,
                      borderRadius: BorderRadius.circular(
                        AppRadii.of(context).compact,
                      ),
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
    final navigationLabel = next
        ? context.l10n.nextChapter
        : context.l10n.previousChapter;
    return AppTap(
      label: navigationLabel,
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.all(10),
        child: Row(
          children: [
            if (!next)
              AppGlyphView(AppGlyph.back, color: colors.muted, size: 18),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 10),
              child: Column(
                crossAxisAlignment: next
                    ? CrossAxisAlignment.end
                    : CrossAxisAlignment.start,
                children: [
                  Text(
                    navigationLabel,
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
              AppGlyphView(AppGlyph.forward, color: colors.muted, size: 18),
          ],
        ),
      ),
    );
  }
}

class _LibraryRoute extends PageRoute<void> {
  _LibraryRoute({required this.builder, required this.barrierLabel});

  final WidgetBuilder builder;
  @override
  final String barrierLabel;

  @override
  bool get opaque => false;

  @override
  Color? get barrierColor => Colors.black.withValues(alpha: .62);

  @override
  bool get barrierDismissible => true;

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
  const _LibraryPanel({
    required this.selected,
    required this.readingMode,
    required this.onSelect,
  });
  final int selected;
  final ReadingMode readingMode;
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
    return ClipRRect(
      borderRadius: BorderRadius.horizontal(
        right: Radius.circular(AppRadii.of(context).screen),
      ),
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
                          context.l10n.selectBook,
                          style: TextStyle(
                            fontFamily: 'Exposure',
                            color: colors.ink,
                            fontSize: 34,
                          ),
                        ),
                      ),
                      AppGlyphButton(
                        glyph: AppGlyph.close,
                        label: context.l10n.close,
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
                            Container(height: 1, color: colors.line),
                        itemBuilder: (_, i) {
                          final index = books[i].$1;
                          final book = books[i].$2;
                          return _ScrollAwareEntrance(
                            staggerIndex: i,
                            child: AppTap(
                              label: _bookName(context, book, widget.readingMode),
                              onTap: () => widget.onSelect(index),
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
                                        _bookName(context, book, widget.readingMode),
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
                                      context.l10n.chapterCount(book.chapters),
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
    );
  }
}

class _TestamentSegmented extends StatelessWidget {
  const _TestamentSegmented({required this.old, required this.onChanged});
  final bool old;
  final ValueChanged<bool> onChanged;
  @override
  Widget build(BuildContext context) {
    return AppSegmented<bool>(
      choices: [
        AppChoice(value: true, label: context.l10n.oldTestamentCount),
        AppChoice(value: false, label: context.l10n.newTestamentCount),
      ],
      selected: old,
      onChanged: onChanged,
    );
  }
}

class _SearchDialog extends StatefulWidget {
  const _SearchDialog({
    required this.repository,
    required this.ai,
    required this.readingMode,
    required this.onVerse,
  });
  final BibleRepository repository;
  final BibleAiController ai;
  final ReadingMode readingMode;
  final ValueChanged<ScriptureHit> onVerse;
  @override
  State<_SearchDialog> createState() => _SearchDialogState();
}

class _SearchDialogState extends State<_SearchDialog> {
  final input = TextEditingController();
  String query = '';
  bool localSearching = false;
  bool overviewSearching = false;
  bool referencesSearching = false;
  bool localFailed = false;
  bool overviewFailed = false;
  _ReferenceFailure? referencesFailure;
  List<ScriptureHit> traditional = const [];
  String? overview;
  List<(ScriptureHit, String)> aiHits = const [];
  bool pendingCloudSearch = false;
  _SearchMode mode = _SearchMode.traditional;

  bool get searching =>
      localSearching || overviewSearching || referencesSearching;

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
    if (mode == _SearchMode.ai) {
      setState(() {
        query = value;
        localFailed = false;
        overviewFailed = false;
        referencesFailure = null;
        overview = null;
        aiHits = const [];
      });
      if (widget.ai.isReady) await _searchAi(value);
      return;
    }
    setState(() {
      query = value;
      localSearching = true;
      localFailed = false;
      traditional = const [];
      overview = null;
      aiHits = const [];
    });
    try {
      final local = await widget.repository.search(value);
      if (mounted) setState(() => traditional = local);
    } catch (_) {
      if (mounted) setState(() => localFailed = true);
    } finally {
      if (mounted) {
        setState(() => localSearching = false);
      }
    }
  }

  Future<List<(ScriptureHit, String)>> _resolveReferences(
    List<AiScriptureReference> references,
  ) async {
    final resolved = <(ScriptureHit, String)>[];
    for (final reference in references) {
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
    return resolved;
  }

  Future<void> _searchAi([String? supplied]) async {
    final value = (supplied ?? input.text).trim();
    if (value.isEmpty || searching || !widget.ai.isReady) return;
    setState(() {
      query = value;
      overviewSearching = true;
      referencesSearching = true;
      localFailed = false;
      overviewFailed = false;
      referencesFailure = null;
      overview = null;
      aiHits = const [];
    });
    Future<void>? resolving;
    try {
      await widget.ai.search(
        value,
        onOverview: (result) {
          if (!mounted || query != value) return;
          setState(() {
            overview = result;
            overviewSearching = false;
          });
        },
        onReferences: (result) {
          resolving = _resolveReferences(result.scriptures)
              .then((resolved) {
                if (!mounted || query != value) return;
                setState(() {
                  aiHits = resolved;
                  referencesSearching = false;
                });
              })
              .catchError((Object _) {
                if (!mounted || query != value) return;
                setState(() {
                  referencesFailure = _ReferenceFailure.verses;
                  referencesSearching = false;
                });
              });
        },
        onOverviewError: (_) {
          if (!mounted || query != value) return;
          setState(() {
            overviewFailed = true;
            overviewSearching = false;
          });
        },
        onReferencesError: (_) {
          if (!mounted || query != value) return;
          setState(() {
            referencesFailure = _ReferenceFailure.request;
            referencesSearching = false;
          });
        },
      );
    } catch (_) {
      // Each request publishes its own error without hiding a successful peer.
    } finally {
      await resolving;
      if (mounted && query == value) {
        setState(() {
          if (overviewSearching) {
            overviewFailed = true;
            overviewSearching = false;
          }
          if (referencesSearching) {
            referencesFailure ??= _ReferenceFailure.request;
            referencesSearching = false;
          }
        });
      }
    }
  }

  void _changeMode(_SearchMode value) {
    if (mode == value || searching) return;
    setState(() {
      mode = value;
      query = '';
      localFailed = false;
      overviewFailed = false;
      referencesFailure = null;
      traditional = const [];
      overview = null;
      aiHits = const [];
    });
  }

  Future<void> _beginOpenRouterLogin() async {
    pendingCloudSearch = input.text.trim().isNotEmpty;
    await widget.ai.beginOpenRouterLogin();
  }

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final keyboard = MediaQuery.viewInsetsOf(context).bottom;
    return SafeArea(
      minimum: const EdgeInsets.all(10),
      child: AnimatedPadding(
        duration: const Duration(milliseconds: 220),
        curve: Curves.easeOutCubic,
        padding: EdgeInsets.only(bottom: keyboard),
        child: LayoutBuilder(
          builder: (context, constraints) => Center(
            child: Container(
              key: const ValueKey('search-dialog-surface'),
              width: double.infinity,
              constraints: BoxConstraints(
                maxWidth: 760,
                maxHeight: constraints.maxHeight,
              ),
              decoration: BoxDecoration(
                color: colors.canvas,
                border: Border.all(color: colors.line),
                borderRadius: BorderRadius.circular(
                  AppRadii.of(context).surface,
                ),
              ),
              child: Column(
                children: [
                  Padding(
                    padding: const EdgeInsets.fromLTRB(10, 10, 8, 10),
                    child: Row(
                      children: [
                        Expanded(
                          child: AppTextInput(
                            controller: input,
                            hint: context.l10n.searchWholeBible,
                            prefix: AppGlyph.search,
                            autofocus: true,
                            textInputAction: TextInputAction.search,
                            onSubmitted: (_) => _search(),
                          ),
                        ),
                        const SizedBox(width: 7),
                        AppGlyphButton(
                          glyph: AppGlyph.forward,
                          label: context.l10n.search,
                          onTap: _search,
                        ),
                        const SizedBox(width: 3),
                        AppGlyphButton(
                          glyph: AppGlyph.close,
                          label: context.l10n.close,
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
                        onChanged: _changeMode,
                      ),
                    ),
                  ClipRect(
                    child: AnimatedSize(
                      duration: const Duration(milliseconds: 240),
                      curve: Curves.easeOutCubic,
                      alignment: Alignment.topCenter,
                      child: searching
                          ? Padding(
                              padding: const EdgeInsets.fromLTRB(10, 0, 10, 7),
                              child: AppProgressLine(
                                key: const ValueKey('search-progress'),
                                color: colors.ink,
                              ),
                            )
                          : const SizedBox(width: double.infinity),
                    ),
                  ),
                  Expanded(
                    child: AnimatedSwitcher(
                      duration: const Duration(milliseconds: 220),
                      switchInCurve: Curves.easeOutCubic,
                      transitionBuilder: (child, animation) =>
                          FadeTransition(opacity: animation, child: child),
                      child: _results(colors),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _results(AppColors colors) {
    if (query.isEmpty) {
      return Center(
        key: const ValueKey('empty'),
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(
            context.l10n.searchHintBody,
            textAlign: TextAlign.center,
            style: TextStyle(
              color: colors.faint,
              fontSize: 14,
              height: 1.5,
              fontWeight: FontWeight.w500,
            ),
          ),
        ),
      );
    }
    return ListView(
      key: ValueKey('${mode.name}-$query'),
      padding: const EdgeInsets.fromLTRB(10, 8, 10, 20),
      children: [
        if (localFailed)
          _SearchSection(
            title: context.l10n.searchStatus,
            child: Text(
              context.l10n.searchFailed,
              style: TextStyle(color: colors.muted),
            ),
          ),
        if (mode == _SearchMode.ai && widget.ai.requiresLogin)
          _SearchSection(
            title: context.l10n.aiOverview,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        context.l10n.loginToSearch,
                        style: TextStyle(color: colors.muted, height: 1.5),
                      ),
                    ),
                    _InlineAction(
                      glyph: AppGlyph.login,
                      label: context.l10n.login,
                      onTap: _beginOpenRouterLogin,
                    ),
                  ],
                ),
                if (widget.ai.openRouterAuthError != null) ...[
                  const SizedBox(height: 10),
                  SelectableText(
                    widget.ai.openRouterAuthError!,
                    style: TextStyle(
                      color: colors.danger,
                      fontSize: 11,
                      height: 1.45,
                    ),
                  ),
                ],
              ],
            ),
          ),
        if (mode == _SearchMode.ai && overviewSearching)
          _SearchSection(
            title: context.l10n.aiOverview,
            child: _AiSearchLoading(label: context.l10n.searchingOverview),
          ),
        if (mode == _SearchMode.ai && overviewFailed)
          _SearchSection(
            title: context.l10n.aiOverview,
            child: Text(
              context.l10n.overviewFailed,
              style: TextStyle(color: colors.muted),
            ),
          ),
        if (mode == _SearchMode.ai && overview != null)
          _SearchSection(
            title: context.l10n.aiOverview,
            child: AppMarkdown(
              data: overview!,
              foreground: colors.ink,
              secondary: colors.muted,
              border: colors.line,
            ),
          ),
        if (mode == _SearchMode.ai && referencesSearching)
          _SearchSection(
            title: context.l10n.aiScriptureResults,
            child: _AiSearchLoading(label: context.l10n.searchingScripture),
          ),
        if (mode == _SearchMode.ai && referencesFailure != null)
          _SearchSection(
            title: context.l10n.aiScriptureResults,
            child: Text(
              referencesFailure == _ReferenceFailure.verses
                  ? context.l10n.verseResultsFailed
                  : context.l10n.referencesFailed,
              style: TextStyle(color: colors.muted),
            ),
          ),
        if (mode == _SearchMode.ai && aiHits.isNotEmpty) ...[
          _SectionLabel(label: context.l10n.aiResultCount(aiHits.length)),
          ...aiHits
              .take(80)
              .map(
                (item) => _SearchHitTile(
                  hit: item.$1,
                  readingMode: widget.readingMode,
                  reason: item.$2,
                  onTap: () => widget.onVerse(item.$1),
                ),
              ),
        ],
        if (mode == _SearchMode.traditional && traditional.isNotEmpty) ...[
          _SectionLabel(
            label: context.l10n.traditionalResultCount(
              '${traditional.length}${traditional.length == 80 ? '+' : ''}',
            ),
          ),
          ...traditional.map(
            (hit) => _SearchHitTile(
              hit: hit,
              readingMode: widget.readingMode,
              onTap: () => widget.onVerse(hit),
            ),
          ),
        ],
        if (!searching &&
            !(mode == _SearchMode.ai && !widget.ai.isReady) &&
            !overviewFailed &&
            referencesFailure == null &&
            (mode == _SearchMode.traditional
                ? traditional.isEmpty
                : aiHits.isEmpty && overview == null))
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 54),
            child: Center(
              child: Text(
                context.l10n.noResults,
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: colors.faint,
                  fontSize: 14,
                  height: 1.5,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          ),
      ],
    );
  }
}

enum _SearchMode { traditional, ai }

enum _ReferenceFailure { request, verses }

class _SearchModeSegmented extends StatelessWidget {
  const _SearchModeSegmented({required this.mode, required this.onChanged});
  final _SearchMode mode;
  final ValueChanged<_SearchMode> onChanged;

  @override
  Widget build(BuildContext context) => AppSegmented<_SearchMode>(
    choices: [
      AppChoice(
        value: _SearchMode.traditional,
        label: context.l10n.traditionalSearch,
      ),
      AppChoice(value: _SearchMode.ai, label: context.l10n.aiSearch),
    ],
    selected: mode,
    onChanged: onChanged,
    height: 40,
  );
}

class _AiSearchLoading extends StatelessWidget {
  const _AiSearchLoading({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return Row(
      children: [
        AppSpinner(color: colors.muted, size: 15),
        const SizedBox(width: 9),
        Text(label, style: TextStyle(color: colors.muted, fontSize: 12)),
      ],
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
        borderRadius: BorderRadius.circular(AppRadii.of(context).surface),
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
  const _SearchHitTile({
    required this.hit,
    required this.readingMode,
    required this.onTap,
    this.reason,
  });
  final ScriptureHit hit;
  final ReadingMode readingMode;
  final String? reason;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final reference =
        '${_bookName(context, hit.book, readingMode)} ${hit.chapter}:${hit.verse.number}';
    final verseText = _usesEnglishUi(context) ? hit.verse.en : hit.verse.zh;
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: AppTap(
        label: reference,
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            border: Border.all(color: colors.line),
            borderRadius: BorderRadius.circular(AppRadii.of(context).control),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                reference,
                style: TextStyle(color: colors.faint, fontSize: 10),
              ),
              const SizedBox(height: 6),
              Text(
                verseText,
                style: TextStyle(
                  color: colors.ink,
                  fontFamily: 'NotoSerifTC',
                  fontFamilyFallback: const ['Noto Serif CJK TC', 'serif'],
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
    required this.glyph,
    required this.label,
    required this.onTap,
  });
  final AppGlyph glyph;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 7),
      child: AppTap(
        label: label,
        onTap: onTap,
        child: Container(
          height: 54,
          padding: const EdgeInsets.symmetric(horizontal: 15),
          decoration: BoxDecoration(
            color: colors.surface,
            border: Border.all(color: colors.line),
            borderRadius: BorderRadius.circular(AppRadii.of(context).surface),
          ),
          child: Row(
            children: [
              AppGlyphView(glyph, color: colors.ink, size: 19),
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
    required this.glyph,
    required this.label,
    required this.onTap,
  });
  final AppGlyph glyph;
  final String label;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    return Opacity(
      opacity: onTap == null ? .5 : 1,
      child: AppTap(
        label: label,
        onTap: onTap,
        enabled: onTap != null,
        child: Container(
          height: 38,
          padding: const EdgeInsets.symmetric(horizontal: 12),
          decoration: BoxDecoration(
            color: colors.ink,
            borderRadius: BorderRadius.circular(AppRadii.of(context).control),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              AppGlyphView(glyph, size: 16, color: colors.canvas),
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
