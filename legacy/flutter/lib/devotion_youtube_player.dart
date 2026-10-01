import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:youtube_player_flutter/youtube_player_flutter.dart';

import 'app_theme.dart';
import 'app_ui.dart';
import 'localization.dart';

/// Pure helper for YouTube-app-style stacked double-tap seek.
///
/// Each double-tap seeks relative to the position at tap time, so stacking
/// (-10 → -20) falls out naturally; this only clamps into `[0, total]`.
/// Extracted for unit testing — the widget itself needs a real WebView.
double clampSeekTarget({
  required double current,
  required double total,
  required double delta,
}) {
  if (total <= 0) return 0;
  return (current + delta).clamp(0, total).toDouble();
}

/// Inline YouTube player with layered fallbacks:
/// 1. [YoutubePlayer] (youtube_player_flutter) with the package's own themed
///    controls overlay. YouTube's native controls are always hidden by the
///    wrapper, so in-video annotations/endscreens are disabled via params to
///    leave exactly one controllable UI layer.
/// 2. Thumbnail + external browser – always works (web & fallback).
class DevotionYoutubePlayer extends StatefulWidget {
  const DevotionYoutubePlayer({super.key, required this.videoId});

  final String videoId;

  @override
  State<DevotionYoutubePlayer> createState() => _DevotionYoutubePlayerState();
}

class _DevotionYoutubePlayerState extends State<DevotionYoutubePlayer> {
  YoutubePlayerController? _controller;

  /// Mirrors `controller.value.fullScreenOption.enabled` via
  /// [YoutubePlayerController.setFullScreenListener]. Drives the back-button
  /// interceptor: while fullscreen, Android back must exit fullscreen instead
  /// of popping the app route — popping underneath a live fullscreen overlay
  /// is what left the WebView detached and the screen black until process
  /// kill.
  bool _isFullScreen = false;

  /// Accumulated YouTube-app-style double-tap feedback. Negative = rewind,
  /// positive = forward; both reset to 0 shortly after the last tap.
  double _shownDelta = 0;
  Timer? _feedbackTimer;

  @override
  void initState() {
    super.initState();
    if (!kIsWeb) {
      // autoPlay: false — a freshly built player must never start on its own.
      _controller = YoutubePlayerController.fromVideoId(
        videoId: widget.videoId,
        autoPlay: false,
        params: const YoutubePlayerParams(
          enableCaption: true,
          // The wrapper hides YouTube's native controls, but in-video
          // annotations and endscreens would still render an uncontrollable
          // second UI layer on top of the video — disable both.
          showVideoAnnotations: false,
          strictRelatedVideos: true,
        ),
      );
      _controller!.setFullScreenListener((isFullScreen) {
        if (mounted && isFullScreen != _isFullScreen) {
          setState(() => _isFullScreen = isFullScreen);
        }
      });
    }
  }

  @override
  void didUpdateWidget(covariant DevotionYoutubePlayer oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (_controller == null || oldWidget.videoId == widget.videoId) return;
    // cue (not load): swaps in the new video without playing it. The ValueKey
    // at the call site normally recreates this state instead; this only
    // guards against in-place videoId updates.
    unawaited(_controller!.cueVideoById(videoId: widget.videoId));
  }

  @override
  void dispose() {
    _feedbackTimer?.cancel();
    // Exit fullscreen before releasing the controller so no fullscreen
    // overlay outlives this widget when the date changes or the tab hides.
    if (_isFullScreen) _controller?.exitFullScreen();
    unawaited(_controller?.close());
    super.dispose();
  }

  String get _watchUrl => 'https://www.youtube.com/watch?v=${widget.videoId}';

  /// YouTube-app-style double-tap seek: ±10 s per tap, stacked while tapping.
  Future<void> _seekBy(double delta) async {
    final controller = _controller;
    if (controller == null) return;
    try {
      final results = await Future.wait<double>([
        controller.currentTime,
        controller.duration,
      ]);
      final target = clampSeekTarget(
        current: results[0],
        total: results[1],
        delta: delta,
      );
      await controller.seekTo(seconds: target, allowSeekAhead: true);
      if (!mounted) return;
      setState(() => _shownDelta += delta);
      _feedbackTimer?.cancel();
      _feedbackTimer = Timer(const Duration(milliseconds: 900), () {
        if (mounted) setState(() => _shownDelta = 0);
      });
    } catch (_) {
      // A seek that races dispose/webview teardown is harmless — ignore it.
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final radii = AppRadii.of(context);

    // Web keeps the always-working thumbnail: iframes are unreliable inside
    // a scrollable CustomScrollView (grey placeholder + null-check errors).
    if (_controller == null) {
      return _ThumbnailFallback(
        videoId: widget.videoId,
        colors: colors,
        radii: radii,
        watchUrl: _watchUrl,
      );
    }

    return PopScope(
      // While fullscreen, Android back exits fullscreen instead of popping
      // the route underneath the fullscreen overlay (the black-screen bug).
      canPop: !_isFullScreen,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && _isFullScreen) _controller?.exitFullScreen();
      },
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(radii.surface),
            child: AspectRatio(
              aspectRatio: 16 / 9,
              child: Stack(
                children: [
                  YoutubePlayer(
                    controller: _controller!,
                    // Vertical drags belong to the page scroll; letting them
                    // toggle fullscreen causes accidental entries (and every
                    // entry is a chance to hit the back-button bug).
                    enableFullScreenOnVerticalDrag: false,
                  ),
                  // Double-tap zones: left/right edges, middle vertical band
                  Positioned(
                    left: 0,
                    top: 64,
                    bottom: 56,
                    width: 160,
                    child: _DoubleTapZone(
                      onDoubleTap: () => _seekBy(-10),
                    ),
                  ),
                  Positioned(
                    right: 0,
                    top: 64,
                    bottom: 56,
                    width: 160,
                    child: _DoubleTapZone(
                      onDoubleTap: () => _seekBy(10),
                    ),
                  ),
                  if (_shownDelta < 0)
                    Positioned(
                      left: 24,
                      top: 0,
                      bottom: 0,
                      child: Center(
                        child: _SeekFlash(
                          rewind: true,
                          seconds: _shownDelta.abs().toInt(),
                        ),
                      ),
                    ),
                  if (_shownDelta > 0)
                    Positioned(
                      right: 24,
                      top: 0,
                      bottom: 0,
                      child: Center(
                        child: _SeekFlash(
                          rewind: false,
                          seconds: _shownDelta.toInt(),
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
          // External-open affordance lives below the video now: the old
          // floating chip covered the top-right of the picture, ate touches
          // and read as yet another control layer.
          Padding(
            padding: const EdgeInsets.only(top: 6),
            child: Align(
              alignment: Alignment.centerRight,
              child: AppTap(
                label: context.l10n.devotionOpenInBrowser,
                onTap: () => launchUrl(
                  Uri.parse(_watchUrl),
                  mode: LaunchMode.externalApplication,
                ),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 6,
                    vertical: 4,
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        LucideIcons.externalLink,
                        size: 12,
                        color: colors.faint,
                      ),
                      const SizedBox(width: 5),
                      Text(
                        context.l10n.devotionOpenInBrowser,
                        style: TextStyle(
                          color: colors.faint,
                          fontSize: 11,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Transparent double-tap catcher. Only [onDoubleTap] is set, so single taps
/// lose the gesture arena and fall through to the package controls below.
class _DoubleTapZone extends StatelessWidget {
  const _DoubleTapZone({required this.onDoubleTap});

  final VoidCallback onDoubleTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.translucent,
      onDoubleTap: onDoubleTap,
      child: const SizedBox.expand(),
    );
  }
}

/// YouTube-app-style seek feedback: translucent circle with rewind/forward
/// glyph and the stacked seconds (10 → 20 → …).
class _SeekFlash extends StatelessWidget {
  const _SeekFlash({required this.rewind, required this.seconds});

  final bool rewind;
  final int seconds;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 64,
      height: 64,
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: .55),
        shape: BoxShape.circle,
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            rewind ? Icons.fast_rewind : Icons.fast_forward,
            size: 22,
            color: Colors.white,
          ),
          const SizedBox(height: 2),
          Text(
            '$seconds',
            style: const TextStyle(
              color: Colors.white,
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

class _ThumbnailFallback extends StatelessWidget {
  const _ThumbnailFallback({
    required this.videoId,
    required this.colors,
    required this.radii,
    required this.watchUrl,
  });

  final String videoId;
  final AppColors colors;
  final AppRadii radii;
  final String watchUrl;

  @override
  Widget build(BuildContext context) {
    final thumbnailUrl = 'https://img.youtube.com/vi/$videoId/hqdefault.jpg';
    return AppTap(
      label: context.l10n.devotionWatchVideo,
      onTap: () =>
          launchUrl(Uri.parse(watchUrl), mode: LaunchMode.externalApplication),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(radii.surface),
        child: AspectRatio(
          aspectRatio: 16 / 9,
          child: Stack(
            fit: StackFit.expand,
            children: [
              Image.network(
                thumbnailUrl,
                fit: BoxFit.cover,
                errorBuilder: (_, _, _) => ColoredBox(
                  color: colors.surfaceRaised.withValues(alpha: .5),
                  child: const SizedBox.expand(),
                ),
              ),
              DecoratedBox(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [
                      Colors.transparent,
                      Colors.black.withValues(alpha: .45)
                    ],
                  ),
                ),
              ),
              Center(
                child: Container(
                  width: 52,
                  height: 52,
                  decoration: BoxDecoration(
                    color: Colors.black.withValues(alpha: .55),
                    shape: BoxShape.circle,
                    border: Border.all(color: Colors.white, width: 1.6),
                  ),
                  child: const Icon(Icons.play_arrow,
                      size: 24, color: Colors.white),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
