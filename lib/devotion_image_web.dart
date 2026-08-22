// ignore: avoid_web_libraries_in_flutter, deprecated_member_use
import 'dart:html' as html;
// ignore: avoid_web_libraries_in_flutter
import 'dart:ui_web' as ui_web;

import 'package:flutter/material.dart';

final Set<String> _registeredViews = <String>{};

Widget buildDevotionImage(
  BuildContext context, {
  required String url,
  required double borderRadius,
}) {
  // Each image needs a unique viewType; use the URL hash.
  final viewType = 'devotion-img-${url.hashCode}';
  if (!_registeredViews.contains(viewType)) {
    _registeredViews.add(viewType);
    // ignore: undefined_prefixed_name
    ui_web.platformViewRegistry.registerViewFactory(viewType, (int viewId) {
      final img = html.ImageElement()
        ..src = url
        ..style.width = '100%'
        ..style.height = '100%'
        ..style.objectFit = 'cover';
      return img;
    });
  }

  return ClipRRect(
    borderRadius: BorderRadius.circular(borderRadius),
    child: AspectRatio(
      aspectRatio: 3 / 2,
      child: HtmlElementView(viewType: viewType),
    ),
  );
}
