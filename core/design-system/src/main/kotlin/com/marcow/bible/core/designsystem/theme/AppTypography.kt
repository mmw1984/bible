package com.marcow.bible.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import com.marcow.bible.core.designsystem.R

/**
 * The font families, matching the families declared in `legacy/flutter/pubspec.yaml`.
 *
 * The CJK families Flutter listed as a fallback chain (`PingFang TC`, `Noto Sans CJK TC`,
 * `Microsoft JhengHei`) are all iOS/Windows/Play-store fonts that Android resolves on its own for a
 * code point these families do not cover, so they are not bundled here. `NotoSerifTC` is bundled
 * because it is the family the devotion articles and the scripture text are *set* in, not a fallback.
 */
object AppFonts {
    /** `OpenRunde`: every UI label. Weight 400/500 map to the two static instances. */
    val OpenRunde = FontFamily(
        Font(R.font.openrunde_regular, FontWeight.Normal),
        Font(R.font.openrunde_medium, FontWeight.Medium),
    )

    /**
     * `NotoSerifTC`: scripture and devotion body text.
     *
     * The shipped font is a subset of the variable original, so the `wght` axis is still live and
     * [Scripture] selects the weight through `FontVariation` — `FontWeight` alone would only reach
     * the axis' default instance of 200, which is lighter than anything the Flutter build rendered.
     */
    val NotoSerifTC = FontFamily(Font(R.font.noto_serif_tc_variable, FontWeight.Normal))

    /** `Exposure`: display type, the variable font used for chapter headings. */
    val Exposure = FontFamily(Font(R.font.exposure_variable, FontWeight.Normal))
}

/** The `wght` values the variable families are rendered at in the Flutter build. */
object AppFontWeights {
    const val SERIF_REGULAR = 400
    const val SERIF_SEMIBOLD = 600
}

/**
 * Material 3's type scale, set in [AppFonts.OpenRunde].
 *
 * The Flutter build set `fontFamily` on `ThemeData` and let Material 3's own scale apply, so the
 * sizes below are the platform defaults rather than hand-tuned numbers. Colours are applied per
 * theme in [AppTheme], the same way `theme.textTheme.apply(bodyColor: …, displayColor: …)` did it.
 */
internal fun appTypography(): Typography = Typography().run {
    copy(
        displayLarge = displayLarge.withFamily(AppFonts.OpenRunde),
        displayMedium = displayMedium.withFamily(AppFonts.OpenRunde),
        displaySmall = displaySmall.withFamily(AppFonts.OpenRunde),
        headlineLarge = headlineLarge.withFamily(AppFonts.OpenRunde),
        headlineMedium = headlineMedium.withFamily(AppFonts.OpenRunde),
        headlineSmall = headlineSmall.withFamily(AppFonts.OpenRunde),
        titleLarge = titleLarge.withFamily(AppFonts.OpenRunde),
        titleMedium = titleMedium.withFamily(AppFonts.OpenRunde),
        titleSmall = titleSmall.withFamily(AppFonts.OpenRunde),
        bodyLarge = bodyLarge.withFamily(AppFonts.OpenRunde),
        bodyMedium = bodyMedium.withFamily(AppFonts.OpenRunde),
        bodySmall = bodySmall.withFamily(AppFonts.OpenRunde),
        labelLarge = labelLarge.withFamily(AppFonts.OpenRunde),
        labelMedium = labelMedium.withFamily(AppFonts.OpenRunde),
        labelSmall = labelSmall.withFamily(AppFonts.OpenRunde),
    )
}

/**
 * Body text in [AppFonts.NotoSerifTC] at [weight].
 *
 * Used for scripture and devotion, where the Flutter build named `NotoSerifTC` explicitly and
 * relied on the font's own weight axis rather than on a bold face.
 */
fun scriptureStyle(
    size: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    weight: Int = AppFontWeights.SERIF_REGULAR,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
): TextStyle = TextStyle.Default.copy(
    fontFamily = notoSerifTcAt(weight),
    fontSize = size,
    lineHeight = lineHeight,
    color = color,
    // `FontWeight.Normal` keeps Compose from synthesising a bold face; the axis does the work.
    fontWeight = FontWeight.Normal,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

/**
 * [AppFonts.NotoSerifTC] rendered at the font's own `wght` [weight].
 *
 * A variation setting belongs to a [Font] and not to a [TextStyle] — `copy` takes no such parameter —
 * so the family is built for the weight that was asked for. `FontWeight` could not reach the axis:
 * the bundled font is the one variable file, and Compose resolves it at 400 whatever the style asks
 * for.
 */
private fun notoSerifTcAt(weight: Int): FontFamily = FontFamily(
    Font(
        resId = R.font.noto_serif_tc_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    ),
)

private fun TextStyle.withFamily(family: FontFamily): TextStyle = copy(
    fontFamily = family,
    // Flutter's Material text styles have no extra font padding on Android, and the Compose default
    // adds top/bottom padding that shifts every baseline against the Flutter layout.
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
