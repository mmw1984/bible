import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'app_navbar.dart';
import 'app_settings.dart';
import 'app_theme.dart';
import 'devotion_image.dart';
import 'devotion_soundcloud_player.dart';
import 'devotion_youtube_player.dart';
import 'app_ui.dart';
import 'devotion_content.dart';
import 'devotion_web_reader.dart';
import 'localization.dart';

export 'devotion_content.dart';

class DevotionPage extends StatefulWidget {
  const DevotionPage({super.key});

  @override
  State<DevotionPage> createState() => _DevotionPageState();
}

class _DevotionPageState extends State<DevotionPage>
    with WidgetsBindingObserver {
  bool loading = true;
  String? error;

  /// Short technical cause shown under the generic failure message so
  /// on-device network problems are diagnosable from a screenshot.
  String? errorDetail;
  List<DevotionPost> posts = const [];
  int selected = 0;
  DateTime? _lastFetchTime;
  bool _wasActive = false;
  Timer? _pollTimer;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _pollTimer?.cancel();
    super.dispose();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // ignore: deprecated_member_use
    final active = TickerMode.of(context);
    if (active && !_wasActive) {
      _wasActive = true;
      _pollTimer?.cancel();
      _pollTimer = Timer.periodic(const Duration(minutes: 30), (_) {
        if (!mounted) return;
        // ignore: deprecated_member_use
        if (!TickerMode.of(context)) return;
        if (_shouldRefresh()) _refreshIfStale(silent: true);
      });
      if (_shouldRefresh()) _refreshIfStale(silent: true);
    } else if (!active && _wasActive) {
      _wasActive = false;
      _pollTimer?.cancel();
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && mounted) {
      // ignore: deprecated_member_use
      final active = TickerMode.of(context);
      if (active && _shouldRefresh()) _refreshIfStale(silent: true);
    }
  }

  bool _shouldRefresh() {
    if (_lastFetchTime == null) return true;
    final now = DateTime.now();
    if (now.difference(_lastFetchTime!) > const Duration(minutes: 30)) {
      return true;
    }
    if (now.day != _lastFetchTime!.day ||
        now.month != _lastFetchTime!.month ||
        now.year != _lastFetchTime!.year) {
      return true;
    }
    return false;
  }

  Future<void> _refreshIfStale({bool silent = true}) => _load(silent: silent);

  /// Index of the entry for *today*: prefer an exact date match, otherwise
  /// the newest entry that isn't scheduled in the future. Posts arrive sorted
  /// newest-first, so this skips future-dated scheduled items instead of
  /// opening them by default.
  int _todayIndex(List<DevotionPost> list) {
    if (list.isEmpty) return 0;
    final now = DateTime.now();
    final exact = list.indexWhere((p) {
      final d = p.devotionDate;
      return d.year == now.year && d.month == now.month && d.day == now.day;
    });
    if (exact >= 0) return exact;
    final latestPast = list.indexWhere((p) => !p.devotionDate.isAfter(now));
    return latestPast >= 0 ? latestPast : list.length - 1;
  }

  Future<void> _load({bool silent = false}) async {
    final hasContent = posts.isNotEmpty;
    if (!silent || !hasContent) {
      setState(() {
        loading = true;
        error = null;
        errorDetail = null;
      });
    }
    // Cold start: paint the last cached fetch instantly so the page is
    // readable even before (or without) a successful network round-trip.
    if (!hasContent && !silent) {
      final cached = await readDevotionCache();
      if (cached.isNotEmpty && mounted && posts.isEmpty) {
        setState(() {
          posts = cached;
          selected = _todayIndex(cached);
          loading = false;
        });
      }
    }
    try {
      final fetched = await fetchDevotionPosts();
      if (!mounted) return;
      unawaited(writeDevotionCache(fetched));
      final previousId =
          hasContent ? posts[selected.clamp(0, posts.length - 1)].id : null;
      var nextSelected = _todayIndex(fetched);
      if (silent && previousId != null) {
        final idx = fetched.indexWhere((p) => p.id == previousId);
        if (idx >= 0) nextSelected = idx;
      }
      setState(() {
        posts = fetched;
        selected =
            nextSelected.clamp(0, fetched.isEmpty ? 0 : fetched.length - 1);
        loading = false;
        error = null;
        errorDetail = null;
        _lastFetchTime = DateTime.now();
      });
    } catch (loadError) {
      if (!mounted) return;
      // Network failed but we have cached devotions — keep them visible
      // instead of showing a dead end; the cache refreshes next success.
      final cached = await readDevotionCache();
      if (cached.isNotEmpty) {
        if (!hasContent) {
          setState(() {
            posts = cached;
            selected = _todayIndex(cached);
            loading = false;
            error = null;
          });
        } else if (!silent) {
          setState(() {
            loading = false;
            errorDetail = loadError.toString();
          });
        }
        return;
      }
      if (silent && hasContent) return;
      setState(() {
        loading = false;
        error = context.l10n.devotionLoadFailed;
        errorDetail = loadError.toString();
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final dark = Theme.of(context).brightness == Brightness.dark;
    final locale = AppSettingsScope.maybeOf(context)?.state.locale ?? AppLocale.zhHant;
    final overlayStyle = SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      systemNavigationBarColor: Colors.transparent,
      statusBarIconBrightness: dark ? Brightness.light : Brightness.dark,
      systemNavigationBarIconBrightness: dark ? Brightness.light : Brightness.dark,
      systemStatusBarContrastEnforced: false,
      systemNavigationBarContrastEnforced: false,
    );
    final post = posts.isEmpty ? null : posts[selected.clamp(0, posts.length - 1)];
    final wide = MediaQuery.sizeOf(context).width >= 920;
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: overlayStyle,
      child: ColoredBox(
        color: colors.canvas,
        child: Stack(
          fit: StackFit.expand,
          children: [
            RefreshIndicator(
              onRefresh: () => _load(silent: false),
              color: colors.ink,
              backgroundColor: colors.surface,
              child: _maybeWrapWithAndroidBlur(
                CustomScrollView(
                physics: const AlwaysScrollableScrollPhysics(
                  parent: ClampingScrollPhysics(),
                ),
                slivers: [
                  // Large reader-style masthead: oversized title with the
                  // controls docked to its baseline, mirroring 聖經 page.
                  SliverPadding(
                    padding: EdgeInsets.fromLTRB(
                      wide ? 46 : 20,
                      MediaQuery.paddingOf(context).top + (wide ? 8 : 14),
                      wide ? 46 : 20,
                      0,
                    ),
                    sliver: SliverToBoxAdapter(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          SizedBox(
                            height: wide ? 94 : 104,
                            child: Row(
                              crossAxisAlignment: CrossAxisAlignment.end,
                              children: [
                                Expanded(
                                  child: Align(
                                    alignment: Alignment.bottomLeft,
                                    child: Text(
                                      context.l10n.tabDevotion,
                                      maxLines: 1,
                                      overflow: TextOverflow.fade,
                                      style: TextStyle(
                                        fontFamily: 'Exposure',
                                        fontFamilyFallback: const ['NotoSerifTC'],
                                        color: colors.ink,
                                        fontSize: wide ? 68 : 45,
                                        height: 1.04,
                                        fontWeight: FontWeight.w500,
                                      ),
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(height: 16),
                          Row(
                            children: [
                              _buildRefreshButton(
                                context,
                                loading: loading,
                                onTap: loading
                                    ? null
                                    : () => _load(silent: false),
                              ),
                              const SizedBox(width: 9),
                              AppGlyphButton(
                                glyph: AppGlyph.book,
                                label: context.l10n.devotionOpenWebReader,
                                onTap: post == null
                                    ? null
                                    : () => showDevotionWebReader(
                                          context,
                                          url: post.link,
                                          title: post.title,
                                        ),
                              ),
                              const Spacer(),
                              if (_lastFetchTime != null)
                                Text(
                                  formatDevotionDate(
                                    DateTime.now(),
                                    locale,
                                  ),
                                  style: TextStyle(
                                    color: colors.faint,
                                    fontSize: 10,
                                  ),
                                ),
                            ],
                          ),
                          const SizedBox(height: 18),
                          Container(height: 1, color: colors.line),
                          const SizedBox(height: 14),
                          if (posts.length > 1)
                            SizedBox(
                              height: 40,
                              child: ListView.separated(
                                scrollDirection: Axis.horizontal,
                                itemCount: posts.length,
                                separatorBuilder: (_, _) =>
                                    const SizedBox(width: 7),
                                itemBuilder: (context, index) => _buildDateChip(
                                  context,
                                  posts[index],
                                  index == selected,
                                  locale,
                                  () => setState(() => selected = index),
                                ),
                              ),
                            ),
                          if (posts.length > 1) const SizedBox(height: 10),
                        ],
                      ),
                    ),
                  ),
                if (loading)
                  SliverFillRemaining(
                    hasScrollBody: false,
                    child: Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          AppSpinner(color: colors.muted),
                          const SizedBox(height: 12),
                          Text(
                            context.l10n.devotionLoading,
                            style: TextStyle(color: colors.muted, fontSize: 12),
                          ),
                        ],
                      ),
                    ),
                  )
                else if (error != null || post == null)
                  SliverFillRemaining(
                    hasScrollBody: false,
                    child: Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(LucideIcons.cloudOff, size: 22, color: colors.muted),
                          const SizedBox(height: 10),
                          Text(
                            error ?? context.l10n.devotionLoadFailed,
                            textAlign: TextAlign.center,
                            style: TextStyle(color: colors.muted, fontSize: 12),
                          ),
                          if (errorDetail != null) ...[
                            const SizedBox(height: 8),
                            Text(
                              errorDetail!,
                              textAlign: TextAlign.center,
                              maxLines: 5,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                color: colors.faint,
                                fontSize: 10,
                                height: 1.4,
                              ),
                            ),
                          ],
                          const SizedBox(height: 16),
                          Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              AppButton(
                                label: context.l10n.devotionRetry,
                                onTap: _load,
                                emphasized: true,
                                child: Padding(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 18,
                                    vertical: 9,
                                  ),
                                  child: Text(
                                    context.l10n.devotionRetry,
                                    style: TextStyle(
                                      color: colors.ink,
                                      fontSize: 12,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                ),
                              ),
                              const SizedBox(width: 10),
                              AppButton(
                                label: context.l10n.devotionOpenWebReader,
                                onTap: () => showDevotionWebReader(
                                  context,
                                  url:
                                      post?.link ?? devotionOrigin,
                                ),
                                child: Padding(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 18,
                                    vertical: 9,
                                  ),
                                  child: Text(
                                    context.l10n.devotionOpenWebReader,
                                    style: TextStyle(
                                      color: colors.ink,
                                      fontSize: 12,
                                    ),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  )
                else
                  SliverPadding(
                    padding: EdgeInsets.fromLTRB(
                      20,
                      4,
                      20,
                      appNavBottomClearance(context) + 28,
                    ),
                    sliver: SliverList.builder(
                      itemCount: post.blocks.length + 2,
                      itemBuilder: (context, index) {
                        if (index == 0) {
                          return Padding(
                            padding: const EdgeInsets.only(bottom: 4),
                            child: Text(
                              formatDevotionDate(post.devotionDate, locale),
                              style: TextStyle(color: colors.faint, fontSize: 11),
                            ),
                          );
                        }
                        if (index == 1) {
                          return Padding(
                            padding: const EdgeInsets.only(bottom: 18),
                            child: Text(
                              post.title,
                              style: TextStyle(
                                fontFamily: 'NotoSerifTC',
                                fontFamilyFallback: const ['Noto Serif CJK TC', 'serif'],
                                color: colors.ink,
                                fontSize: 21,
                                height: 1.5,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          );
                        }
                        final block = post.blocks[index - 2];
                        return _buildBlock(context, block);
                      },
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

  Widget _maybeWrapWithAndroidBlur(Widget child) => child;

  /// Refresh control — now icon-only to match the webview button.
  Widget _buildRefreshButton(
    BuildContext context, {
    required bool loading,
    required VoidCallback? onTap,
  }) {
    final colors = AppColors.of(context);
    return AppControlSurface(
      color: colors.surfaceRaised.withValues(alpha: .6),
      borderColor: colors.line,
      child: AppTap(
        label: context.l10n.devotionRefresh,
        enabled: !loading,
        onTap: onTap,
        child: SizedBox(
          width: 40,
          height: 40,
          child: Center(
            child: loading
                ? SizedBox(
                    width: 16,
                    height: 16,
                    child: AppSpinner(color: colors.ink, size: 16),
                  )
                : Icon(LucideIcons.refreshCw, size: 18, color: colors.ink),
          ),
        ),
      ),
    );
  }

  Widget _buildDateChip(
    BuildContext context,
    DevotionPost post,
    bool active,
    AppLocale locale,
    VoidCallback onTap,
  ) {
    final colors = AppColors.of(context);
    return AppControlSurface(
      color: active ? colors.surfaceRaised : colors.surfaceRaised.withValues(alpha: .6),
      borderColor: active ? colors.ink.withValues(alpha: .55) : colors.line,
      selected: active,
      child: AppTap(
        label: formatDevotionDate(post.devotionDate, locale),
        selected: active,
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12),
          child: Center(
            child: Text(
              formatDevotionDateShort(post.devotionDate, locale),
              maxLines: 1,
              style: TextStyle(
                color: active ? colors.ink : colors.muted,
                fontSize: 11,
                fontWeight: active ? FontWeight.w600 : FontWeight.w500,
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildBlock(BuildContext context, DevotionBlock block) {
    final colors = AppColors.of(context);
    final radii = AppRadii.of(context);
    final width = MediaQuery.sizeOf(context).width;
    final wide = width >= 920;
    final indent = wide ? 46.0 : 0.0;
    switch (block) {
      case DevotionHeading():
        return Padding(
          padding: EdgeInsets.fromLTRB(indent, 18, indent, 8),
          child: Text(
            block.text,
            style: TextStyle(
              fontFamily: 'NotoSerifTC',
              fontFamilyFallback: const ['Noto Serif CJK TC', 'serif'],
              color: colors.ink,
              fontSize: 17,
              height: 1.55,
              fontWeight: FontWeight.w600,
            ),
          ),
        );
      case DevotionQuote():
        return Padding(
          padding: EdgeInsets.fromLTRB(indent, 6, indent, 6),
          child: Container(
            padding: const EdgeInsets.fromLTRB(13, 10, 13, 10),
            decoration: BoxDecoration(
              color: colors.surfaceRaised.withValues(alpha: .5),
              border: Border(left: BorderSide(color: colors.ink.withValues(alpha: .5), width: 2)),
              borderRadius: BorderRadius.circular(radii.compact),
            ),
            child: Text(
              block.text,
              style: TextStyle(
                fontFamily: 'NotoSerifTC',
                fontFamilyFallback: const ['Noto Serif CJK TC', 'serif'],
                color: colors.ink,
                fontSize: 15,
                height: 1.75,
                fontStyle: FontStyle.italic,
              ),
            ),
          ),
        );
      case DevotionImage():
        return Padding(
          padding: EdgeInsets.fromLTRB(indent, 10, indent, 10),
          child: buildDevotionImage(
            context,
            url: block.url,
            borderRadius: radii.surface,
          ),
        );
      case DevotionEmbed():
        return Padding(
          padding: EdgeInsets.fromLTRB(indent, 10, indent, 14),
          child: DevotionSoundCloudPlayer(embedUrl: block.url),
        );
      case DevotionVideo():
        return Padding(
          padding: EdgeInsets.fromLTRB(indent, 10, indent, 14),
          // Keyed per video: switching dates disposes the old player
          // (stopping its audio) and mounts a fresh, paused one instead of
          // reusing a live webview that would keep playing or auto-play.
          child: DevotionYoutubePlayer(
            key: ValueKey(block.videoId),
            videoId: block.videoId,
          ),
        );
      case DevotionSection():
        return Padding(
          padding: EdgeInsets.fromLTRB(indent, 8, indent, 4),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 3,
                    height: 13,
                    decoration: BoxDecoration(
                      color: colors.ink,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    block.title,
                    style: TextStyle(
                      fontFamily: 'NotoSerifTC',
                      fontFamilyFallback: const ['Noto Serif CJK TC', 'serif'],
                      color: colors.ink,
                      fontSize: 14,
                      fontWeight: FontWeight.w700,
                      letterSpacing: .5,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 6),
              for (final child in block.blocks) _buildBlock(context, child),
            ],
          ),
        );
      case DevotionParagraph():
        return Padding(
          padding: EdgeInsets.fromLTRB(indent, 0, indent, 14),
          child: Text(
            block.text,
            style: TextStyle(
              fontFamily: 'NotoSerifTC',
              fontFamilyFallback: const ['Noto Serif CJK TC', 'serif'],
              color: colors.ink,
              fontSize: 16,
              height: 1.85,
            ),
          ),
        );
    }
  }

}
