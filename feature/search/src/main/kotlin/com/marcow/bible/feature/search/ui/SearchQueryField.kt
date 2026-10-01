package com.marcow.bible.feature.search.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.components.AppControlSurface
import com.marcow.bible.core.designsystem.components.AppGlyphView
import com.marcow.bible.core.designsystem.icons.AppGlyph
import com.marcow.bible.core.designsystem.theme.AppFonts
import com.marcow.bible.core.designsystem.theme.appColors

/**
 * The search sheet's query box, the part of `AppTextInput` (`legacy/flutter/lib/app_ui.dart`) the
 * sheet used: the raised surface, the glyph prefix, the floating hint, and a search key that submits.
 *
 * `AppTextInput` itself is not in `core:design-system` yet and that module is outside this feature's
 * scope, so this is the same widget reduced to what `_SearchDialog` passed it — no header, helper,
 * error or trailing slot. When the design-system module is next in scope this should be deleted in
 * favour of it rather than kept alongside.
 *
 * The box is fully controlled ([value] in, [onValueChange] out) where Flutter held a
 * `TextEditingController` and rebuilt from a listener: the sheet's state value already carries the
 * text, so a second copy of it here would be a second thing to keep in step.
 */
@Composable
internal fun SearchQueryField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    autofocus: Boolean = true,
) {
    val colors = appColors
    var focused by remember { mutableStateOf(false) }
    val outline by animateColorAsState(
        targetValue = if (focused) colors.ink else colors.line,
        animationSpec = tween(OUTLINE_MILLIS),
        label = "searchQueryOutline",
    )

    AppControlSurface(
        modifier = modifier.heightIn(min = 48.dp),
        color = colors.surfaceRaised.copy(alpha = FILL_ALPHA),
        borderColor = outline,
        horizontalPadding = 13.dp,
        verticalPadding = 5.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppGlyphView(glyph = AppGlyph.SEARCH, color = colors.muted, size = 18.dp)
            Spacer(Modifier.width(10.dp))
            QueryText(
                value = value,
                onValueChange = onValueChange,
                onSearch = onSearch,
                onFocusChange = { focused = it },
                autofocus = autofocus,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The hint behind the text and the text itself, which is the `Stack` of Flutter's `AppTextInput`.
 *
 * `BasicTextField` rather than Material's `TextField` because Flutter drew an `EditableText` inside
 * the app's own surface: Material's field would add its own container, label and indicator on top of
 * the one this composable already draws. The selection colours still come from the theme, which is
 * where `AppTheme` puts the Flutter build's pinned `0xFF376996`.
 */
@Composable
private fun QueryText(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    autofocus: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = appColors
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Flutter asked for focus on the first frame; a `FocusRequester` can only be used from a frame
    // after the field it belongs to is composed, so this is the earliest it can be granted.
    LaunchedEffect(autofocus) {
        if (autofocus) focusRequester.requestFocus()
    }

    Box(modifier = modifier) {
        if (value.isEmpty()) {
            // The `IgnorePointer` in Flutter: the hint is drawn under the field, and only the field
            // takes touches.
            Text(
                text = stringResource(R.string.search_whole_bible),
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = queryTextStyle(colors.muted),
            )
        }
        // Selection colours are left to `AppTheme`, which pins the Flutter build's `0xFF376996` in
        // `LocalTextSelectionColors` — where `BasicTextField` reads them from, as Flutter's
        // `EditableText` read `selectionColor` from the theme.
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChange(it.isFocused) },
            textStyle = queryTextStyle(colors.ink),
            singleLine = true,
            cursorBrush = SolidColor(colors.ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            // Flutter's `onSubmitted`, which the sheet also wired to its forward button.
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSearch()
                    focusManager.clearFocus()
                },
            ),
        )
    }
}

/** `fontFamily: 'OpenRunde', fontSize: 14, height: 1.45` in the Flutter input. */
private fun queryTextStyle(color: Color): TextStyle = TextStyle(
    color = color,
    fontFamily = AppFonts.OpenRunde,
    fontSize = 14.sp,
    lineHeight = INPUT_LINE_HEIGHT,
)

/** `AnimatedContainer(duration: Duration(milliseconds: 160))` around the Flutter input's outline. */
private const val OUTLINE_MILLIS = 160

/** `colors.surfaceRaised.withValues(alpha: .72)` on the Flutter input's surface. */
private const val FILL_ALPHA = 0.72f

/** `height: 1.45` at `fontSize: 14`, which is what Flutter's `TextStyle.height` means. */
private val INPUT_LINE_HEIGHT = 20.3.sp
