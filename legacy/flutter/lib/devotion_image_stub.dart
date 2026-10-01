import 'package:flutter/widgets.dart';

/// Fallback for platforms without a specialised image view (e.g. tests).
Widget buildDevotionImage(
  BuildContext context, {
  required String url,
  required double borderRadius,
}) {
  return Image.network(url, fit: BoxFit.cover);
}
