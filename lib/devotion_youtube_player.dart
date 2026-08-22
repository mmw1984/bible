import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:youtube_player_flutter/youtube_player_flutter.dart';

import 'app_theme.dart';
import 'app_ui.dart';
import 'localization.dart';

/// Inline YouTube player with layered fallbacks:
/// 1. [YoutubePlayer] (youtube_player_flutter) – themed controls overlay
///    (play/pause, seek bar, fullscreen) rendered above the iframe webview,
///    so playback stays controllable even where native YouTube controls
///    collapse inside an Android WebView.
/// 2. Thumbnail + external browser – always works (web & fallback).
class DevotionYoutubePlayer extends StatefulWidget {
  const DevotionYoutubePlayer({super.key, required this.videoId});

  final String videoId;

  @override
  State<DevotionYoutubePlayer> createState() => _DevotionYoutubePlayerState();
}

class _DevotionYoutubePlayerState extends State<DevotionYoutubePlayer> {
  YoutubePlayerController? _controller;

  @override
  void initState() {
    super.initState();
    if (!kIsWeb) {
      // autoPlay: false — a freshly built player must never start on its own.
      _controller = YoutubePlayerController.fromVideoId(
        videoId: widget.videoId,
        autoPlay: false,
        params: const YoutubePlayerParams(
          showControls: true,
          showFullscreenButton: true,
          enableCaption: true,
        ),
      );
    }
  }

  @override
  void didUpdateWidget(covariant DevotionYoutubePlayer oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (_controller == null || oldWidget.videoId == widget.videoId) return;
    // cue (not load): swaps in the new video without playing it. The ValueKey
    // at the call site normally recreates this state instead; this only
    // guards against in-place videoId updates.
    _controller!.cueVideoById(videoId: widget.videoId);
  }

  @override
  void dispose() {
    _controller?.close();
    super.dispose();
  }

  String get _watchUrl => 'https://www.youtube.com/watch?v=${widget.videoId}';

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

    return ClipRRect(
      borderRadius: BorderRadius.circular(radii.surface),
      child: AspectRatio(
        aspectRatio: 16 / 9,
        child: Stack(
          children: [
            YoutubePlayer(controller: _controller!),
            Positioned(
              right: 6,
              top: 6,
              child: _FallbackChip(
                label: context.l10n.devotionOpenInBrowser,
                onTap: () => launchUrl(
                  Uri.parse(_watchUrl),
                  mode: LaunchMode.externalApplication,
                ),
              ),
            ),
          ],
        ),
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
      onTap: () => launchUrl(Uri.parse(watchUrl), mode: LaunchMode.externalApplication),
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
                    colors: [Colors.transparent, Colors.black.withValues(alpha: .45)],
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
                  child: const Icon(Icons.play_arrow, size: 24, color: Colors.white),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _FallbackChip extends StatelessWidget {
  const _FallbackChip({required this.label, required this.onTap});
  final String label;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.black54,
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          child: Text(label, style: const TextStyle(color: Colors.white, fontSize: 11)),
        ),
      ),
    );
  }
}
