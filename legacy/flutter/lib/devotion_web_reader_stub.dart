import 'package:flutter/widgets.dart';
import 'package:url_launcher/url_launcher.dart';

/// Web (and other platforms without an embeddable browser) degrade to the
/// external browser so the article still opens — just outside the app.
Future<void> showDevotionWebReader(
  BuildContext context, {
  required String url,
  String? title,
}) {
  return launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
}
