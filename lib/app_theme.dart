import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

const springCurve = Cubic(0.16, 1, 0.3, 1);

class AppRadii extends ThemeExtension<AppRadii> {
  const AppRadii({
    required this.compact,
    required this.control,
    required this.surface,
    required this.screen,
    double? topLeft,
    double? topRight,
    double? bottomLeft,
    double? bottomRight,
  }) : topLeft = topLeft ?? screen,
       topRight = topRight ?? screen,
       bottomLeft = bottomLeft ?? screen,
       bottomRight = bottomRight ?? screen;

  static const fallback = AppRadii(
    compact: 8,
    control: 12,
    surface: 18,
    screen: 28,
    topLeft: 28,
    topRight: 28,
    bottomLeft: 28,
    bottomRight: 28,
  );

  static const _android = MethodChannel('bible/android');

  final double compact;
  final double control;
  final double surface;
  final double screen;
  final double topLeft;
  final double topRight;
  final double bottomLeft;
  final double bottomRight;

  static Future<AppRadii> load() async {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
      return fallback;
    }
    try {
      final response = await _android.invokeMapMethod<String, dynamic>(
        'getDeviceRoundedCorners',
      );
      if (response?['available'] != true) {
        return fallback;
      }
      // Accept the former single-radius response during staggered native/app
      // upgrades, while keeping square corners as real zero values.
      final legacyRadius = _nonNegativeRadius(response?['radiusDp']);
      final topLeft = _nonNegativeRadius(response?['topLeftDp']) ?? legacyRadius;
      final topRight =
          _nonNegativeRadius(response?['topRightDp']) ?? legacyRadius;
      final bottomLeft =
          _nonNegativeRadius(response?['bottomLeftDp']) ?? legacyRadius;
      final bottomRight =
          _nonNegativeRadius(response?['bottomRightDp']) ?? legacyRadius;
      if (topLeft == null ||
          topRight == null ||
          bottomLeft == null ||
          bottomRight == null) {
        return fallback;
      }
      final radius = math.max(
        math.max(topLeft, topRight),
        math.max(bottomLeft, bottomRight),
      );
      if (radius <= 0) return fallback;
      return AppRadii(
        compact: (radius * .24).clamp(7, 11).toDouble(),
        control: (radius * .34).clamp(10, 16).toDouble(),
        surface: (radius * .48).clamp(14, 22).toDouble(),
        screen: radius,
        topLeft: topLeft,
        topRight: topRight,
        bottomLeft: bottomLeft,
        bottomRight: bottomRight,
      );
    } on MissingPluginException {
      return fallback;
    } on PlatformException {
      return fallback;
    }
  }

  factory AppRadii.of(BuildContext context) =>
      Theme.of(context).extension<AppRadii>() ?? fallback;

  @override
  AppRadii copyWith({
    double? compact,
    double? control,
    double? surface,
    double? screen,
    double? topLeft,
    double? topRight,
    double? bottomLeft,
    double? bottomRight,
  }) => AppRadii(
    compact: compact ?? this.compact,
    control: control ?? this.control,
    surface: surface ?? this.surface,
    screen: screen ?? this.screen,
    topLeft: topLeft ?? this.topLeft,
    topRight: topRight ?? this.topRight,
    bottomLeft: bottomLeft ?? this.bottomLeft,
    bottomRight: bottomRight ?? this.bottomRight,
  );

  @override
  AppRadii lerp(covariant AppRadii? other, double t) {
    if (other == null) return this;
    return AppRadii(
      compact: lerpDouble(compact, other.compact, t),
      control: lerpDouble(control, other.control, t),
      surface: lerpDouble(surface, other.surface, t),
      screen: lerpDouble(screen, other.screen, t),
      topLeft: lerpDouble(topLeft, other.topLeft, t),
      topRight: lerpDouble(topRight, other.topRight, t),
      bottomLeft: lerpDouble(bottomLeft, other.bottomLeft, t),
      bottomRight: lerpDouble(bottomRight, other.bottomRight, t),
    );
  }

  static double? _nonNegativeRadius(Object? value) {
    if (value is! num) return null;
    return math.max(0, value.toDouble());
  }

  static double lerpDouble(double a, double b, double t) => a + (b - a) * t;
}

class AppColors extends ThemeExtension<AppColors> {
  const AppColors({
    required this.canvas,
    required this.surface,
    required this.surfaceRaised,
    required this.ink,
    required this.muted,
    required this.faint,
    required this.line,
    required this.danger,
    required this.dangerSurface,
  });

  final Color canvas,
      surface,
      surfaceRaised,
      ink,
      muted,
      faint,
      line,
      danger,
      dangerSurface;

  static const dark = AppColors(
    canvas: Color(0xFF090909),
    surface: Color(0xFF111111),
    surfaceRaised: Color(0xFF171717),
    ink: Color(0xFFF1EFE9),
    muted: Color(0xFF8C8B86),
    faint: Color(0xFF5E5E5A),
    line: Color(0xFF292927),
    danger: Color(0xFFE3A6A6),
    dangerSurface: Color(0xFF241717),
  );

  static const light = AppColors(
    canvas: Color(0xFFF5F2EA),
    surface: Color(0xFFFEFBF4),
    surfaceRaised: Color(0xFFECE8DE),
    ink: Color(0xFF191918),
    muted: Color(0xFF696760),
    faint: Color(0xFF969289),
    line: Color(0xFFD8D3C8),
    danger: Color(0xFF8F4141),
    dangerSurface: Color(0xFFF2DEDA),
  );

  factory AppColors.of(BuildContext context) =>
      Theme.of(context).extension<AppColors>() ??
      (Theme.of(context).brightness == Brightness.dark ? dark : light);

  @override
  AppColors copyWith({
    Color? canvas,
    Color? surface,
    Color? surfaceRaised,
    Color? ink,
    Color? muted,
    Color? faint,
    Color? line,
    Color? danger,
    Color? dangerSurface,
  }) => AppColors(
    canvas: canvas ?? this.canvas,
    surface: surface ?? this.surface,
    surfaceRaised: surfaceRaised ?? this.surfaceRaised,
    ink: ink ?? this.ink,
    muted: muted ?? this.muted,
    faint: faint ?? this.faint,
    line: line ?? this.line,
    danger: danger ?? this.danger,
    dangerSurface: dangerSurface ?? this.dangerSurface,
  );

  @override
  AppColors lerp(covariant AppColors? other, double t) {
    if (other == null) return this;
    return AppColors(
      canvas: Color.lerp(canvas, other.canvas, t)!,
      surface: Color.lerp(surface, other.surface, t)!,
      surfaceRaised: Color.lerp(surfaceRaised, other.surfaceRaised, t)!,
      ink: Color.lerp(ink, other.ink, t)!,
      muted: Color.lerp(muted, other.muted, t)!,
      faint: Color.lerp(faint, other.faint, t)!,
      line: Color.lerp(line, other.line, t)!,
      danger: Color.lerp(danger, other.danger, t)!,
      dangerSurface: Color.lerp(dangerSurface, other.dangerSurface, t)!,
    );
  }
}
