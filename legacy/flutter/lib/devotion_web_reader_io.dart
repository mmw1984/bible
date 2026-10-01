import 'dart:async';

import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';

import 'app_theme.dart';
import 'app_ui.dart';
import 'localization.dart';

/// The most robust way to read a devotion: render the article's own page in
/// an embedded browser. It bypasses the WordPress REST API, RSS and the HTML
/// parser entirely, so every post stays readable — text, images and embedded
/// players alike — even when the JSON/feed endpoints are unreachable on a
/// device.
Future<void> showDevotionWebReader(
  BuildContext context, {
  required String url,
  String? title,
}) {
  return Navigator.of(context, rootNavigator: true).push(
    MaterialPageRoute<void>(
      builder: (_) => DevotionWebReaderPage(url: url, title: title),
    ),
  );
}

class DevotionWebReaderPage extends StatefulWidget {
  const DevotionWebReaderPage({super.key, required this.url, this.title});

  final String url;
  final String? title;

  @override
  State<DevotionWebReaderPage> createState() => _DevotionWebReaderPageState();
}

class _DevotionWebReaderPageState extends State<DevotionWebReaderPage> {
  late final WebViewController _controller;
  var _progress = 0.0;
  var _failed = false;
  Brightness? _appliedBrightness;

  @override
  void initState() {
    super.initState();
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(
        NavigationDelegate(
          onProgress: (value) => mounted
              ? setState(() {
                  _progress = value / 100;
                })
              : null,
          onPageFinished: (_) => mounted
              ? setState(() {
                  _progress = 1;
                })
              : null,
          // Keep http(s) pages inside the reader; hand anything else
          // (mailto:, intent:, play store…) to the operating system.
          onNavigationRequest: (request) async {
            final uri = Uri.tryParse(request.url);
            final scheme = uri?.scheme.toLowerCase() ?? '';
            if (scheme == 'http' || scheme == 'https') {
              return NavigationDecision.navigate;
            }
            if (uri != null) {
              await launchUrl(uri, mode: LaunchMode.externalApplication);
            }
            return NavigationDecision.prevent;
          },
          onWebResourceError: (error) {
            if (error.isForMainFrame ?? false) {
              if (mounted) setState(() => _failed = true);
            }
          },
        ),
      )
      ..loadRequest(Uri.parse(widget.url));
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final brightness = Theme.of(context).brightness;
    if (_appliedBrightness != brightness) {
      _appliedBrightness = brightness;
      unawaited(
        _controller.setBackgroundColor(
          brightness == Brightness.dark ? Colors.black : Colors.white,
        ),
      );
    }
  }

  Future<void> _retry() async {
    setState(() => _failed = false);
    await _controller.reload();
  }

  Future<void> _openExternally() =>
      launchUrl(Uri.parse(widget.url), mode: LaunchMode.externalApplication);

  @override
  Widget build(BuildContext context) {
    final colors = AppColors.of(context);
    final dark = Theme.of(context).brightness == Brightness.dark;
    final title = widget.title ?? Uri.parse(widget.url).host;
    return Scaffold(
      backgroundColor: colors.canvas,
      body: SafeArea(
        bottom: false,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(10, 6, 10, 6),
              child: Row(
                children: [
                  AppControlSurface(
                    color: colors.surfaceRaised.withValues(alpha: .6),
                    borderColor: colors.line,
                    child: BackButton(color: colors.ink),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: colors.ink,
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  AppControlSurface(
                    color: colors.surfaceRaised.withValues(alpha: .6),
                    borderColor: colors.line,
                    child: IconButton(
                      onPressed: _retry,
                      tooltip: context.l10n.devotionRefresh,
                      icon: Icon(LucideIcons.rotateCw,
                          size: 18, color: colors.ink),
                    ),
                  ),
                  const SizedBox(width: 6),
                  AppControlSurface(
                    color: colors.surfaceRaised.withValues(alpha: .6),
                    borderColor: colors.line,
                    child: IconButton(
                      onPressed: _openExternally,
                      tooltip: context.l10n.devotionOpenInBrowser,
                      icon: Icon(LucideIcons.externalLink,
                          size: 18, color: colors.ink),
                    ),
                  ),
                ],
              ),
            ),
            if (!_failed && _progress < 1)
              LinearProgressIndicator(
                value: _progress <= 0 ? null : _progress,
                minHeight: 2,
                backgroundColor: colors.line.withValues(alpha: .4),
                valueColor: AlwaysStoppedAnimation(colors.ink),
              ),
            Expanded(
              child: _failed
                  ? _ErrorView(
                      colors: colors,
                      onRetry: _retry,
                      onOpenExternally: _openExternally,
                    )
                  : ColoredBox(
                      color: dark ? Colors.black : Colors.white,
                      child: WebViewWidget(controller: _controller),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({
    required this.colors,
    required this.onRetry,
    required this.onOpenExternally,
  });

  final AppColors colors;
  final Future<void> Function() onRetry;
  final Future<void> Function() onOpenExternally;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(LucideIcons.cloudOff, size: 22, color: colors.muted),
          const SizedBox(height: 10),
          Text(
            context.l10n.devotionWebLoadFailed,
            textAlign: TextAlign.center,
            style: TextStyle(color: colors.muted, fontSize: 12),
          ),
          const SizedBox(height: 16),
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              AppButton(
                label: context.l10n.devotionRetry,
                emphasized: true,
                onTap: onRetry,
                child: Padding(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 18, vertical: 9),
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
                label: context.l10n.devotionOpenInBrowser,
                onTap: onOpenExternally,
                child: Padding(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 18, vertical: 9),
                  child: Text(
                    context.l10n.devotionOpenInBrowser,
                    style: TextStyle(color: colors.ink, fontSize: 12),
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
