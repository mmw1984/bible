import 'package:flutter/widgets.dart';

import 'l10n/app_localizations.dart';
import 'l10n/app_localizations_zh.dart';

extension AppLocalizationContext on BuildContext {
  AppLocalizations get l10n =>
      Localizations.of<AppLocalizations>(this, AppLocalizations) ??
      AppLocalizationsZh();
}
