import 'package:flutter/material.dart';

const springCurve = Cubic(0.16, 1, 0.3, 1);

class AppColors extends ThemeExtension<AppColors> {
  const AppColors({
    required this.canvas,
    required this.surface,
    required this.surfaceRaised,
    required this.ink,
    required this.muted,
    required this.faint,
    required this.line,
  });

  final Color canvas, surface, surfaceRaised, ink, muted, faint, line;

  static const dark = AppColors(
    canvas: Color(0xFF090909),
    surface: Color(0xFF111111),
    surfaceRaised: Color(0xFF171717),
    ink: Color(0xFFF1EFE9),
    muted: Color(0xFF8C8B86),
    faint: Color(0xFF5E5E5A),
    line: Color(0xFF292927),
  );

  static const light = AppColors(
    canvas: Color(0xFFF5F2EA),
    surface: Color(0xFFFEFBF4),
    surfaceRaised: Color(0xFFECE8DE),
    ink: Color(0xFF191918),
    muted: Color(0xFF696760),
    faint: Color(0xFF969289),
    line: Color(0xFFD8D3C8),
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
  }) => AppColors(
    canvas: canvas ?? this.canvas,
    surface: surface ?? this.surface,
    surfaceRaised: surfaceRaised ?? this.surfaceRaised,
    ink: ink ?? this.ink,
    muted: muted ?? this.muted,
    faint: faint ?? this.faint,
    line: line ?? this.line,
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
    );
  }
}
