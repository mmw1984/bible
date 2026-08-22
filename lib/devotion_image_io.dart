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
      headers: const {
        'User-Agent':
            'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36',
      },
      loadingBuilder: (context, child, progress) => AspectRatio(
        aspectRatio: 3 / 2,
        child: ColoredBox(
          color: colors.surfaceRaised.withValues(alpha: .4),
          child: progress?.expectedTotalBytes != null
              ? Center(child: AppSpinner(color: colors.muted))
              : child,
        ),
      ),
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
