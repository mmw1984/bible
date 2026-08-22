import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';

import 'app_theme.dart';
import 'app_ui.dart';
import 'localization.dart';

/// Inline SoundCloud player.
///
/// The requested `minikin/soundcloud_audio_player` is a demo app
/// (`publish_to: none`, Dart SDK <3.0, `audioplayers` 0.17) and cannot be
/// added as a library dependency on Flutter 3.12+. Instead this widget embeds
/// the same `w.soundcloud.com/player/?url=...` widget the site already ships
/// (via `webview_flutter`), which plays without an API key.
///
/// Fallback chain: WebView → external browser.
class DevotionSoundCloudPlayer extends StatefulWidget {
  const DevotionSoundCloudPlayer({super.key, required this.embedUrl});

  /// The iframe `src` from the WordPress content, e.g.
  /// `https://w.soundcloud.com/player/?url=https%3A//api.soundcloud.com/tracks/...`
  final String embedUrl;

  @override
  State<DevotionSoundCloudPlayer> createState() => _DevotionSoundCloudPlayerState();
}

class _DevotionSoundCloudPlayerState extends State<DevotionSoundCloudPlayer> {
  late final WebViewController _controller;
  bool _hasError = false;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setBackgroundColor(Colors.transparent)
      ..setNavigationDelegate(
        NavigationDelegate(
          onPageFinished: (_) {
            if (mounted) setState(() => _isLoading = false);
          },
          onWebResourceError: (error) {
            if (error.isForMainFrame ?? false) {
              if (mounted) setState(() => _hasError = true);
            }
          },
          onNavigationRequest: (request) async {
            final uri = Uri.tryParse(request.url);
            if (uri == null) return NavigationDecision.navigate;
            // Keep the SoundCloud widget inside the WebView; open everything
            // else externally so the frame cannot navigate away.
            if (uri.host.contains('soundcloud.com') ||
                uri.host.contains('sndcdn.com')) {
              return NavigationDecision.navigate;
            }
            if (uri.scheme == 'http' || uri.scheme == 'https') {
              await launchUrl(uri, mode: LaunchMode.externalApplication);
              return NavigationDecision.prevent;
            }
            return NavigationDecision.navigate;
          },
        ),
      )
      ..loadRequest(Uri.parse(widget.embedUrl));
  }

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final radii = AppRadii.of(context);

    if (_hasError) {
      return _ExternalFallback(
        colors: colors,
        radii: radii,
        url: widget.embedUrl,
      );
    }

    return ClipRRect(
      borderRadius: BorderRadius.circular(radii.surface),
      child: Container(
        color: colors.surfaceRaised.withValues(alpha: .5),
        child: Stack(
          children: [
            SizedBox(
              height: 166,
              child: WebViewWidget(controller: _controller),
            ),
            if (_isLoading)
              SizedBox(
                height: 166,
                child: Center(child: AppSpinner(color: colors.muted)),
              ),
            Positioned(
              right: 6,
              top: 6,
              child: _ExternalChip(
                label: context.l10n.devotionOpenInBrowser,
                onTap: () => launchUrl(
                  Uri.parse(widget.embedUrl),
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

class _ExternalFallback extends StatelessWidget {
  const _ExternalFallback({required this.colors, required this.radii, required this.url});
  final AppColors colors;
  final AppRadii radii;
  final String url;
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 12),
      decoration: BoxDecoration(
        color: colors.surfaceRaised.withValues(alpha: .5),
        border: Border.all(color: colors.line),
        borderRadius: BorderRadius.circular(radii.surface),
      ),
      child: Row(
        children: [
          Icon(LucideIcons.audioLines, size: 18, color: colors.ink),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(context.l10n.devotionOpenEmbed,
                    style: TextStyle(color: colors.ink, fontSize: 13, fontWeight: FontWeight.w600)),
                const SizedBox(height: 2),
                Text(Uri.tryParse(url)?.host ?? url,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(color: colors.faint, fontSize: 11)),
              ],
            ),
          ),
          const SizedBox(width: 8),
          AppTap(
            label: context.l10n.devotionOpenInBrowser,
            onTap: () => launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              decoration: BoxDecoration(
                color: colors.surface.withValues(alpha: .9),
                border: Border.all(color: colors.line),
                borderRadius: BorderRadius.circular(radii.compact),
              ),
              child: Icon(LucideIcons.externalLink, size: 14, color: colors.ink),
            ),
          ),
        ],
      ),
    );
  }
}

class _ExternalChip extends StatelessWidget {
  const _ExternalChip({required this.label, required this.onTap});
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
