package com.marcow.bible

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.marcow.bible.core.designsystem.R
import com.marcow.bible.core.designsystem.components.AppControlSurface
import com.marcow.bible.core.designsystem.components.AppGlyphView
import com.marcow.bible.core.designsystem.components.AppTap
import com.marcow.bible.core.designsystem.icons.AppGlyph
import com.marcow.bible.core.designsystem.theme.AppTheme
import com.marcow.bible.core.designsystem.theme.appColors
import com.marcow.bible.core.model.AppSettings
import com.marcow.bible.core.model.ReadingMode
import com.marcow.bible.feature.aichat.AiChatRoute
import com.marcow.bible.feature.aichat.ScriptureHandoff
import com.marcow.bible.feature.devotion.DevotionRoute
import com.marcow.bible.feature.library.LibraryDestination
import com.marcow.bible.feature.library.LibrarySidebar
import com.marcow.bible.feature.library.SidebarWidth
import com.marcow.bible.feature.library.libraryRoute
import com.marcow.bible.feature.navigation.AppFloatingNavBar
import com.marcow.bible.feature.navigation.AppNavBarBottomGap
import com.marcow.bible.feature.navigation.AppNavTab
import com.marcow.bible.feature.navigation.ReaderTopBar
import com.marcow.bible.feature.navigation.ReaderTopBarVisibility
import com.marcow.bible.feature.navigation.appNavBarClearance
import com.marcow.bible.feature.navigation.navBarItems
import com.marcow.bible.feature.reader.ReaderRoute
import com.marcow.bible.feature.reader.ReaderViewModel
import com.marcow.bible.feature.reader.WideThreshold
import com.marcow.bible.feature.search.SearchHost
import com.marcow.bible.feature.settings.SettingsRoute

/** The reader tab, mirroring `AppNavTab.bible` in the Flutter shell. */
const val BIBLE_ROUTE = "bible"

/** The AI chat tab, mirroring `AppNavTab.ask`. */
const val ASK_ROUTE = "ask"

/** The devotion tab, mirroring `AppNavTab.devotion`. */
const val DEVOTION_ROUTE = "devotion"

/** The settings page, mirroring `_openSettings` pushing `AiSettingsPage`. */
const val SETTINGS_ROUTE = "settings"

private fun AppNavTab.route(): String = when (this) {
    AppNavTab.BIBLE -> BIBLE_ROUTE
    AppNavTab.ASK -> ASK_ROUTE
    AppNavTab.DEVOTION -> DEVOTION_ROUTE
}

/**
 * The app, replacing `BibleApp` in `legacy/flutter/lib/main.dart:79`.
 *
 * The theme comes from the stored settings exactly as Flutter's `themeMode` did, and the home is
 * the tab shell below. The OpenRouter callback (`bible://openrouter/callback`) intentionally stays
 * out of this graph: its intent-filter lives on `MainActivity` in `AndroidManifest.xml` and the
 * activity forwards it to `OpenRouterCallbackForwarder` from `onCreate`/`onNewIntent`, which is the
 * leg the sign-in needs. Registering it here as well would give the same link two consumers.
 */
@Composable
fun BibleApp(
    shellViewModel: BibleShellViewModel = hiltViewModel(),
    readerViewModel: ReaderViewModel = hiltViewModel(),
) {
    val settings by shellViewModel.settings.collectAsStateWithLifecycle()
    AppTheme.BibleTheme(settings = settings, isSystemDark = isSystemInDarkTheme()) {
        BibleHome(
            settings = settings,
            shellViewModel = shellViewModel,
            readerViewModel = readerViewModel,
        )
    }
}

/**
 * The tab shell, replacing `BibleHome` in `legacy/flutter/lib/main.dart:199`.
 *
 * Three tabs under one floating pill (`bible` / `ask` / `devotion`, Devotion filtered exactly as
 * Flutter filtered it, and a hidden Devotion never stays selected), the reader's top bar over the
 * bible tab (Ask/Devotion shortcuts while the bar is hidden, Search and Settings always), the wide
 * sidebar beside the reader, the library as a destination over the reader, the search as a dialog
 * over everything with its return chip, and settings as a full page.
 *
 * Tab state is kept with `saveState`/`restoreState`, which is the native shape of Flutter's lazy
 * mount: the first visit builds the page and later visits return to it. The reader's view model is
 * shared with the shell (activity scope) rather than scoped to its destination, because the library
 * and the search both read the reader's mode and book from outside the destination.
 */
@Composable
fun BibleHome(
    settings: AppSettings,
    shellViewModel: BibleShellViewModel,
    readerViewModel: ReaderViewModel,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val readerState by readerViewModel.state.collectAsStateWithLifecycle()
    var searchOpen by remember { mutableStateOf(false) }
    var handoff by remember { mutableStateOf<ScriptureHandoff?>(null) }
    var searchOrigin by remember { mutableStateOf<SearchOrigin?>(null) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val items = navBarItems(showDevotion = settings.showDevotion)
    val selectedIndex = items.indexOfFirst { it.tab.route() == currentRoute }.takeIf { it >= 0 } ?: 0
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val clearance = appNavBarClearance(showNavbar = settings.showNavbar, bottomInset = bottomInset)
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val wide = screenWidth >= WideThreshold
    val onSelectTab: (AppNavTab) -> Unit = { tab ->
        navController.navigate(tab.route()) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val restoreSearchOrigin: () -> Unit = {
        val origin = searchOrigin
        searchOrigin = null
        if (origin != null) {
            if (readerState.mode != origin.mode) readerViewModel.selectMode(origin.mode)
            readerViewModel.openLocation(origin.bookId, origin.chapter)
            onSelectTab(AppNavTab.BIBLE)
        }
    }
    LaunchedEffect(settings.showDevotion, currentRoute) {
        if (!settings.showDevotion && currentRoute == DEVOTION_ROUTE) onSelectTab(AppNavTab.BIBLE)
    }
    BackHandler(enabled = searchOrigin != null && currentRoute == BIBLE_ROUTE && !searchOpen) {
        restoreSearchOrigin()
    }

    Box(modifier = modifier.fillMaxSize().background(appColors.canvas)) {
        NavHost(
            navController = navController,
            startDestination = BIBLE_ROUTE,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(
                route = BIBLE_ROUTE,
                // The library's own KDoc asks for this: the sheet is pushed over a reader that
                // must stay exactly where it is, so no transition runs on the way out or back.
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                val book = readerState.book
                if (wide && book != null) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        LibrarySidebar(
                            book = book,
                            chapter = readerState.chapter,
                            readingMode = readerState.mode,
                            usesEnglishUi = readerState.usesEnglishUi,
                            onLibraryClick = { navController.navigate(LibraryDestination) },
                            onChapterSelected = readerViewModel::selectChapter,
                            modifier = Modifier.width(SidebarWidth),
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            ReaderRoute(
                                bottomClearance = clearance,
                                onVerseAction = { _, request ->
                                    handoff = ScriptureHandoff.of(
                                        reference = request.reference,
                                        text = request.text,
                                        chapterContext = request.chapterContext,
                                        question = request.question,
                                    )
                                    onSelectTab(AppNavTab.ASK)
                                },
                                viewModel = readerViewModel,
                            )
                        }
                    }
                } else {
                    ReaderRoute(
                        bottomClearance = clearance,
                        onVerseAction = { _, request ->
                            handoff = ScriptureHandoff.of(
                                reference = request.reference,
                                text = request.text,
                                chapterContext = request.chapterContext,
                                question = request.question,
                            )
                            onSelectTab(AppNavTab.ASK)
                        },
                        viewModel = readerViewModel,
                    )
                }
            }
            composable(route = ASK_ROUTE) {
                AiChatRoute(
                    bottomClearance = clearance,
                    embedded = true,
                    handoff = handoff,
                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                )
            }
            composable(route = DEVOTION_ROUTE) {
                DevotionRoute(bottomClearance = clearance)
            }
            composable(route = SETTINGS_ROUTE) {
                SettingsRoute(onBack = { navController.popBackStack() })
            }
            libraryRoute(
                navController = navController,
                readingMode = readerState.mode,
                selectedBookId = readerState.book?.id,
                onBookSelected = { bookId ->
                    searchOrigin = null
                    readerViewModel.selectBook(bookId)
                },
            )
        }

        if (currentRoute == BIBLE_ROUTE) {
            ReaderTopBar(
                visibility = ReaderTopBarVisibility(
                    libraryButton = !wide,
                    navBar = settings.showNavbar,
                    devotion = settings.showDevotion,
                ),
                onOpenLibrary = { navController.navigate(LibraryDestination) },
                onSearch = { searchOpen = true },
                onSettings = { navController.navigate(SETTINGS_ROUTE) },
                onAsk = { onSelectTab(AppNavTab.ASK) },
                onDevotion = { onSelectTab(AppNavTab.DEVOTION) },
            )
        }

        if (searchOrigin != null && currentRoute == BIBLE_ROUTE && !searchOpen) {
            ReturnToSearchOrigin(
                onTap = restoreSearchOrigin,
                modifier = Modifier.align(Alignment.TopStart)
                    .padding(top = topInset + SearchOriginTopGap, start = SearchOriginStart),
            )
        }

        if (settings.showNavbar && currentRoute in setOf(BIBLE_ROUTE, ASK_ROUTE, DEVOTION_ROUTE)) {
            AppFloatingNavBar(
                items = items,
                selectedIndex = selectedIndex,
                onSelected = { index -> items.getOrNull(index)?.let { item -> onSelectTab(item.tab) } },
                style = settings.navbarStyle,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = bottomInset + AppNavBarBottomGap),
            )
        }

        if (searchOpen) {
            SearchHost(
                readingMode = readerState.mode,
                locale = settings.locale,
                onOpenVerse = { hit ->
                    val currentBookId = readerState.book?.id
                    searchOrigin = currentBookId?.let {
                        SearchOrigin(it, readerState.chapter, readerState.mode)
                    }
                    searchOpen = false
                    readerViewModel.openLocation(hit.book.id, hit.chapter)
                    onSelectTab(AppNavTab.BIBLE)
                },
                onDismiss = { searchOpen = false },
                signIn = shellViewModel.searchSignIn,
            )
        }
    }
}

/**
 * Where the reader was when a search hit moved it, mirroring `ReaderLocation` in
 * `legacy/flutter/lib/main.dart:46` for the fields a return needs.
 *
 * Flutter kept the pixel offset too; the native reader persists a scroll ratio per book instead,
 * and that row is written on navigation already, so the book, chapter and mode are what a return
 * has to name.
 */
private data class SearchOrigin(val bookId: String, val chapter: Int, val mode: ReadingMode)

/**
 * The chip below the top bar that takes the reader back, mirroring `_ReturnToSearchOrigin` in
 * `legacy/flutter/lib/main.dart:1024`.
 */
@Composable
private fun ReturnToSearchOrigin(onTap: () -> Unit, modifier: Modifier = Modifier) {
    AppControlSurface(modifier = modifier) {
        AppTap(
            label = stringResource(R.string.return_to_search_origin),
            onClick = onTap,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppGlyphView(glyph = AppGlyph.BACK, color = appColors.ink, size = SearchOriginGlyph)
                Spacer(Modifier.width(SearchOriginGap))
                Text(
                    text = stringResource(R.string.return_to_search_origin),
                    color = appColors.ink,
                    fontSize = SearchOriginLabelSize,
                    fontWeight = FontWeight.W600,
                )
            }
        }
    }
}

private val SearchOriginTopGap = 56.dp
private val SearchOriginStart = 20.dp
private val SearchOriginGlyph = 17.dp
private val SearchOriginGap = 7.dp
private val SearchOriginLabelSize = 11.sp
