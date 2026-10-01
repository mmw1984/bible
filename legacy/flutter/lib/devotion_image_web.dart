import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';

import 'app_theme.dart';
import 'app_ui.dart';

Widget buildDevotionImage(
  BuildContext context, {
  required String url,
  required double borderRadius,
}) {
  final colors = AppColors.of(context);
  return ClipRRect(
    borderRadius: BorderRadius.circular(borderRadius),
    child: Image.network(
      url,
      fit: BoxFit.cover,
      loadingBuilder: (context, child, progress) {
        if (progress == null) return child;
        return AspectRatio(
          aspectRatio: 3 / 2,
          child: ColoredBox(
            color: colors.surfaceRaised.withValues(alpha: .4),
            child: Center(child: AppSpinner(color: colors.muted)),
          ),
        );
      },
      errorBuilder: (_, _, _) => AspectRatio(
        aspectRatio: 3 / 2,
        child: ColoredBox(
          color: colors.surfaceRaised.withValues(alpha: .4),
          child: Icon(LucideIcons.imageOff, size: 20, color: colors.faint),
        ),
      ),
    ),
  );
}
