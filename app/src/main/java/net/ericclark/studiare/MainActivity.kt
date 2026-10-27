package net.ericclark.studiare

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.FirebaseApp
import net.ericclark.studiare.data.DESKTOP_MIN_WIDTH_DP
import net.ericclark.studiare.components.parseHexColor
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.ui.theme.CompactDimensions
import net.ericclark.studiare.ui.theme.ComfortableDimensions
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.ui.theme.NormalDimensions
import net.ericclark.studiare.ui.theme.StudiareTheme
import net.ericclark.studiare.ui.theme.generateCustomScheme
import net.ericclark.studiare.components.AppLogger
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.windowInsetsPadding
import net.ericclark.studiare.components.WindowSizeOverlay
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Home
import net.ericclark.studiare.screens.Dialogs.TutorialDialog
import net.ericclark.studiare.screens.Screens.CollectionManagerScreen
import net.ericclark.studiare.screens.Screens.DeckEditorScreen
import net.ericclark.studiare.screens.Screens.DeckListScreen
import net.ericclark.studiare.screens.Screens.RecentsScreen
import net.ericclark.studiare.screens.Screens.SetManagerScreen
import net.ericclark.studiare.screens.Screens.SettingsScreen
import net.ericclark.studiare.screens.Screens.SimpleEditorScreen
import net.ericclark.studiare.screens.Screens.StudyModeSelectionScreen

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }
val LocalWindowWidthSizeClass = compositionLocalOf<WindowWidthSizeClass> { error("No Width Size provided") }
val LocalWindowHeightSizeClass = compositionLocalOf<WindowHeightSizeClass> { error("No Height Size provided") }
// The one shared "is this wide enough for the desktop multi-pane treatment" breakpoint —
// previously recomputed independently (and inconsistently) in MainActivity and DecksScreen.
// Deliberately a custom threshold (DESKTOP_MIN_WIDTH_DP, above WindowWidthSizeClass.Expanded's
// own 840dp) rather than reusing a stock size class, so it needs its own CompositionLocal.
val LocalIsDesktopWidth = compositionLocalOf<Boolean> { error("No desktop-width flag provided") }

// Define a High Contrast Black & White Color Scheme
internal val BlackAndWhiteColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color.White,
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFFCCCCCC),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFAAAAAA),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF999999),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF666666),
    onTertiaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color.Black,
    onSurfaceVariant = Color.White,
    // The surface-container ladder was previously left unset here, silently falling back to
    // stock Material's purple-tinted dark defaults in a theme whose whole point is black/white/
    // gray. Continues the grayscale ramp already used above for secondary/tertiary.
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF404040),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0D0D0D),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF262626),
    surfaceContainerHighest = Color(0xFF333333),
    outline = Color.White,
    error = Color.White,
    onError = Color.Black
)

/**
 * The main and only activity in the application.
 * It sets up the Jetpack Compose content, including the theme and navigation.
 */
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Pass the debug flag to the logger
        AppLogger.init(BuildConfig.DEBUG)

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val widthSizeClass = windowSizeClass.widthSizeClass
            val heightSizeClass = windowSizeClass.heightSizeClass
            val isDesktopWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= DESKTOP_MIN_WIDTH_DP &&
                heightSizeClass > WindowHeightSizeClass.Compact

            val context = LocalContext.current
            val viewModel: FlashcardViewModel =
                viewModel(factory = FlashcardViewModelFactory(context.applicationContext as Application))

            val themeMode by viewModel.themeMode.collectAsState()
            val customColors by viewModel.customThemeColors.collectAsState()

            splashScreen.setKeepOnScreenCondition {
                !viewModel.hasStartedLoading
            }

            val spacingMode by viewModel.spacingMode.collectAsState()
            val studiareDimensions = when (spacingMode) {
                SpacingMode.COMPACT -> CompactDimensions
                SpacingMode.NORMAL -> NormalDimensions
                else -> ComfortableDimensions
            }

            // Respect the OS-level "remove animations" setting, or the app's own opt-in toggle.
            val systemReducedMotion by net.ericclark.studiare.util.rememberSystemReducedMotion()
            val appReduceMotion by viewModel.reduceMotion.collectAsState()
            val reducedMotionActive = systemReducedMotion || appReduceMotion
            val disableCardFlip by viewModel.disableCardFlipAnimations.collectAsState()

            val content = @Composable {
                // Initialize our Shortcut Engine States
                var isHintMode by remember { mutableStateOf(false) }
                val shortcutRegistry = remember { ShortcutRegistry() }
                val hintOverlay = remember { HintOverlayState() }
                val focusRequester = remember { FocusRequester() }
                val shortcutRemaps by viewModel.shortcutRemaps.collectAsState()
                val showSizeOverlay by viewModel.showSizeOverlay.collectAsState()
                val showTutorial by viewModel.showTutorial.collectAsState()
                val hasSeenTutorial by viewModel.hasSeenTutorial.collectAsState()
                // null means "not yet loaded from DataStore" — wait for the real value so a
                // returning user never sees the tutorial flash open for a frame first.
                LaunchedEffect(hasSeenTutorial) {
                    if (hasSeenTutorial == false) viewModel.requestShowTutorial()
                }

                CompositionLocalProvider(
                    LocalStudiareDimensions provides studiareDimensions,
                    LocalWindowWidthSizeClass provides widthSizeClass,
                    LocalWindowHeightSizeClass provides heightSizeClass,
                    LocalIsDesktopWidth provides isDesktopWidth,
                    LocalHintMode provides isHintMode,
                    LocalShortcutRegistry provides shortcutRegistry,
                    LocalHintOverlay provides hintOverlay,
                    LocalShortcutRemaps provides shortcutRemaps,
                    net.ericclark.studiare.ui.theme.LocalReducedMotion provides reducedMotionActive,
                    net.ericclark.studiare.ui.theme.LocalCardFlipAnimated provides !disableCardFlip
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .autoFocusable(focusRequester, initialDelayMs = 100) // wait for window attach
                            .onFocusChanged { if (!it.hasFocus) isHintMode = false }
                            .onPreviewKeyEvent { event ->
                                if (event.key == Key.AltLeft || event.key == Key.AltRight) {
                                    if (event.type == KeyEventType.KeyDown) isHintMode = true
                                    if (event.type == KeyEventType.KeyUp) isHintMode = false
                                }
                                if (event.type == KeyEventType.KeyDown && isHintMode) {
                                    if (shortcutRegistry.trigger(event.key)) {
                                        return@onPreviewKeyEvent true
                                    }
                                }
                                false
                            },
                        color = MaterialTheme.colorScheme.surfaceContainerLowest
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            AppNavigation(viewModel = viewModel)
                            ShortcutHintOverlay(state = hintOverlay, visible = isHintMode)
                            if (showSizeOverlay) {
                                WindowSizeOverlay(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.captionBar))
                            }
                            if (showTutorial) {
                                TutorialDialog(onDismiss = { viewModel.dismissTutorial() })
                            }
                        }
                    }
                }
            }

            val isDark = isSystemInDarkTheme()
            val resolvedColorScheme = when (themeMode) {
                ThemeMode.BLACK_AND_WHITE -> BlackAndWhiteColorScheme
                ThemeMode.CUSTOM -> remember(customColors) {
                    val customBackground = parseHexColor(customColors.background)
                    generateCustomScheme(
                        primary = parseHexColor(customColors.primary),
                        secondary = parseHexColor(customColors.secondary),
                        tertiary = parseHexColor(customColors.tertiary),
                        background = customBackground,
                        // MaterialKolor only uses these RGB inputs as HCT hue/chroma seeds — a
                        // separate isDark flag actually picks the tonal (light-vs-dark) direction
                        // of the generated surfaces. That has to come from the background color the
                        // user actually chose, not the device's system dark-mode setting, or picking
                        // a black background here could still render as a light scheme.
                        isDark = customBackground.luminance() < 0.5f
                    )
                }
                else -> null
            }

            StudiareTheme(
                darkTheme = themeMode == ThemeMode.DARK,
                customColorScheme = resolvedColorScheme,
                reducedMotion = reducedMotionActive,
                content = content
            )
        }
    }
}

/**
 * Composable function that defines the app's navigation graph using Jetpack Navigation Compose.
 * It sets up all the possible screens and the routes to navigate between them.
 * @param viewModel The shared ViewModel instance passed to each screen.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavigation(
    viewModel: FlashcardViewModel
) {
    val navController = rememberNavController()
    val decks by viewModel.allDecks.observeAsState(initial = emptyList())

    // State needed for the widescreen Collection dropdown
    val selectedCollectionId by viewModel.selectedCollectionId.collectAsState()
    val allCollections by viewModel.allCollectionsWithDecks.collectAsState()

    val contentFocusRequester = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isWideScreen = LocalIsDesktopWidth.current

    val navigateTo = { route: String ->
        navController.navigate(route) {
            popUpTo("deckList") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Recents and Settings from the dock are pushed on top of wherever you were, so Home can return
    // you there. Switching between the two replaces the one already open instead of stacking them.
    val dockRoutes = setOf("recents", "settings")
    val openDockRoute = { route: String ->
        val replacing = currentRoute?.takeIf { it in dockRoutes }
        navController.navigate(route) {
            if (replacing != null) popUpTo(replacing) { inclusive = true }
            launchSingleTop = true
        }
    }

    // Drilling into sets/study happens as panes inside the deckList route, so "home"
    // means the deckList route AND only the root pane showing.
    val paneStack by viewModel.paneStack.collectAsState()
    val isAtHome = (currentRoute == "deckList" || currentRoute == null) && paneStack.size <= 1
    val goHome = {
        if (currentRoute in dockRoutes) {
            // First press returns to the screen the dock was opened from; the next press (from there) goes home
            if (!navController.popBackStack()) {
                navController.navigate("deckList") { popUpTo(currentRoute!!) { inclusive = true } }
            }
        } else if (!isAtHome) {
            viewModel.popToPane("deckList")
            if (currentRoute != "deckList" && currentRoute != null) {
                // deckList is always already on the back stack beneath whatever full-screen
                // study route we're on (those are pushed with a plain navigate(), never
                // popUpTo/launchSingleTop), so popping back to it is correct and, unlike
                // navigate(route) { popUpTo(route) { saveState = true }; restoreState = true },
                // reliably works here — that combo was a no-op when the target route was
                // already further down the same back stack instead of the current top.
                if (!navController.popBackStack("deckList", inclusive = false)) {
                    navigateTo("deckList")
                }
            }
        }
    }

    val goHomeKey = resolveShortcutKey(LocalShortcutRemaps.current, "global.go_home", Key.H)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val isModifierPressed = event.isCtrlPressed || event.isMetaPressed
                val isRepeat = (event.nativeKeyEvent as android.view.KeyEvent).repeatCount > 0

                if (event.type == KeyEventType.KeyDown && !isRepeat) {
                    if (isModifierPressed) {
                        when (event.key) {
                            goHomeKey -> { goHome(); return@onPreviewKeyEvent true }
                            Key.Comma, Key.S -> { openDockRoute("settings"); return@onPreviewKeyEvent true }
                        }
                    }
                } else if (event.type == KeyEventType.KeyUp) {
                    if (event.key == Key.Escape) {
                        // Panes live inside the deck list route, so close the deepest one first.
                        if ((currentRoute == "deckList" || currentRoute == null) && paneStack.size > 1) {
                            viewModel.popPane()
                            return@onPreviewKeyEvent true
                        }
                        if (navController.navigateUp()) return@onPreviewKeyEvent true
                    }
                }
                false
            }
    ) {
        if (isWideScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Navigation Rail for Desktop/Tablet
                var showCollectionDialog by remember { mutableStateOf(false) }

                if (showCollectionDialog) {
                    net.ericclark.studiare.CollectionPickerDialog(
                        selectedCollectionId = selectedCollectionId,
                        allCollections = allCollections,
                        onSelectCollection = { collectionId ->
                            viewModel.selectCollection(collectionId)
                            showCollectionDialog = false
                        },
                        onEditCollections = {
                            showCollectionDialog = false
                            navController.navigate("collectionManager")
                        },
                        onDismiss = { showCollectionDialog = false }
                    )
                }

                NavigationRail(
                    modifier = Modifier.width(90.dp),
                    header = {
                        val currentName = if (selectedCollectionId == null || selectedCollectionId == "UNINITIALIZED") getText(R.string.decks_all)
                        else allCollections.find { it.collection.id == selectedCollectionId }?.collection?.name ?: getText(R.string.decks_all)

                        Box(modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)) {
                            TooltipIconButton(description = currentName, onClick = { showCollectionDialog = true }) {
                                Icon(Icons.Default.AutoAwesomeMotion, contentDescription = currentName)
                            }
                        }
                    }
                ) {
                    NavigationRailItem(
                        selected = isAtHome,
                        onClick = { goHome() },
                        icon = { Icon(Icons.Default.Home, contentDescription = getText(R.string.home)) },
                        label = { Text(getText(R.string.home)) }
                    )
                    NavigationRailItem(
                        selected = currentRoute == "recents",
                        onClick = { openDockRoute("recents") },
                        icon = { Icon(Icons.Default.History, contentDescription = getText(R.string.recents)) },
                        label = { Text(getText(R.string.recents)) }
                    )
                    NavigationRailItem(
                        selected = currentRoute == "settings",
                        onClick = { openDockRoute("settings") },
                        icon = { Icon(Icons.Default.Settings, contentDescription = getText(R.string.settings)) },
                        label = { Text(getText(R.string.settings)) }
                    )
                }

                // Main Content Area
                Box(modifier = Modifier.weight(1f).fillMaxHeight()
                    .focusGroup()
                    .autoFocusable(contentFocusRequester)
                ) {
                    StudiareNavGraph(navController, viewModel, decks)
                }
            }
        } else {
            // Compact Screen
            Box(modifier = Modifier.fillMaxSize()) {
                // Add the setManager and studyModeSelection routes to the visible list
                val showBottomBar = currentRoute in listOf("deckList", "recents", "settings", null) ||
                        currentRoute?.startsWith("setManager/") == true ||
                        currentRoute?.startsWith("studyModeSelection/") == true

                val motionScheme = MaterialTheme.motionScheme

                // The flush bar is 80dp tall (NavigationBarTokens.TallContainerHeight) plus
                // whatever system gesture inset it pads itself by — content needs to clear both.
                // We use animateDpAsState so the padding smoothly adjusts as the nav bar enters/exits.
                val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val bottomPadding by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (showBottomBar) 80.dp + navBottomInset else 0.dp,
                    animationSpec = motionScheme.defaultSpatialSpec(),
                    label = "navBarPadding"
                )

                // The spatial spring backing bottomPadding is bouncy (non-critically-damped), so it
                // legitimately overshoots past its 0.dp target while settling — both padding() and
                // PaddingValues() throw on a negative value, which crashed every screen transition
                // that hides the bottom bar (e.g. tapping "Create Deck"). Clamp the sampled value
                // once; the overshoot is invisible at this magnitude, only its raw sign was the problem.
                val safeBottomPadding = bottomPadding.coerceAtLeast(0.dp)

                Box(modifier = Modifier.fillMaxSize()
                    // 1. Push the graph up to save the FAB
                    .padding(bottom = safeBottomPadding)
                    // 2. Consume the insets so the inner Scaffolds don't double-pad the lists!
                    .consumeWindowInsets(PaddingValues(bottom = safeBottomPadding))
                    .focusGroup()
                    .autoFocusable(contentFocusRequester)
                ) {
                    StudiareNavGraph(navController, viewModel, decks)
                }

                // Bottom Navigation
                androidx.compose.animation.AnimatedVisibility(
                    visible = showBottomBar,
                    enter = androidx.compose.animation.slideInVertically(
                        // Start slightly further down to ensure it drops in smoothly from off-screen
                        initialOffsetY = { it + 50 },
                        animationSpec = motionScheme.defaultSpatialSpec()
                    ),
                    exit = androidx.compose.animation.slideOutVertically(
                        // Slide fully off the screen
                        targetOffsetY = { it + 50 },
                        animationSpec = motionScheme.defaultSpatialSpec()
                    ),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    // Flush, full-bleed bar — no margin, no rounded clip, no shadow. Those all
                    // read as a hard "permanent separator" (a drawn edge/box around the bar)
                    // instead of the plain color-tone difference the user asked for. The bar's
                    // own default windowInsets (unset here) already pads its content away from
                    // the system gesture area while letting containerColor fill behind it, so it
                    // sits flush against the true bottom of the screen with square corners.
                    NavigationBar(
                        // A full tier above the screen canvas (surfaceContainerLowest) is enough
                        // contrast on its own with no shape/elevation effects needed.
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        NavigationBarItem(
                            selected = isAtHome,
                            onClick = { goHome() },
                            icon = { Icon(Icons.Default.Home, contentDescription = getText(R.string.home)) },
                            label = { Text(getText(R.string.home)) }
                        )
                        NavigationBarItem(
                            selected = currentRoute == "recents",
                            onClick = { openDockRoute("recents") },
                            icon = { Icon(Icons.Default.History, contentDescription = getText(R.string.recents)) },
                            label = { Text(getText(R.string.recents)) }
                        )
                        NavigationBarItem(
                            selected = currentRoute == "settings",
                            onClick = { openDockRoute("settings") },
                            icon = { Icon(Icons.Default.Settings, contentDescription = getText(R.string.settings)) },
                            label = { Text(getText(R.string.settings)) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun StudiareNavGraph(
    navController: androidx.navigation.NavHostController,
    viewModel: FlashcardViewModel,
    decks: List<net.ericclark.studiare.data.DeckWithCards>
) {
    SharedTransitionLayout(
        modifier = Modifier.fillMaxSize()
    ) {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this
        ) {
            // Transition lambdas below aren't @Composable, so reducedMotion must be read here and
            // captured, not read inside the lambdas.
            //
            // NOT motionScheme here, unlike every other animated thing in this app: M3's own
            // Transitions doc says explicitly "M3 transitions use the legacy easing and duration
            // system" — the physics-based MotionScheme is for component-level motion (buttons,
            // dialogs, etc.), not page transitions. Confirmed the hard way: the spring specs
            // this used before settle in ~70-150ms (do the math on MotionScheme.expressive()'s
            // real spring tokens — critically-damped effects springs settle in well under
            // 100ms), which a frame-by-frame screen-recording comparison showed was genuinely
            // imperceptible in practice, not just "fast" — swapping to an exaggerated 2-second
            // tween confirmed the transition mechanism itself was working fine the whole time,
            // just too quick to read as motion at the spring's real settle time. Plain
            // tween+easing (M3's actual prescribed approach for this pattern) gives a duration
            // that's reliably visible instead of guessing at spring tokens tuned for other things.
            val reducedMotion = net.ericclark.studiare.ui.theme.LocalReducedMotion.current
            // "Top level" (M3's "Top level" pattern: quick fade, no persistent elements, since
            // these destinations aren't related to each other) = Decks/Recents/Settings, the
            // three peer destinations reachable from the bottom dock — vs "Forward and backward"
            // (M3's "fade as screens slide" pattern) for genuine hierarchy navigation within one
            // of those three (deck -> editor, deck -> set manager, deck -> study hub -> a study
            // mode screen, etc.).
            //
            // Classified by GROUP, not exact route, so a top-level switch is still recognized no
            // matter how deep you've drilled into the Decks hierarchy first — e.g. Study Hub ->
            // Recents, or a Settings category -> Home, are both top-level switches even though
            // neither endpoint is literally "deckList"/"recents"/"settings".
            val topLevelGroups = setOf("recents", "settings")
            fun groupOf(route: String?) = if (route in topLevelGroups) route else "decks"
            fun androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.isTopLevelSwitch() =
                groupOf(initialState.destination.route) != groupOf(targetState.destination.route)
            val enterEasing = androidx.compose.animation.core.FastOutSlowInEasing
            val exitEasing = androidx.compose.animation.core.FastOutLinearInEasing
            // Every fade in this NavHost (top-level and the fade component of forward/backward
            // alike) uses the same 300ms enter / 200ms exit timing — exit shorter than enter per
            // M3's "clean fades" guidance (fade the outgoing content out quickly rather than let
            // it cross-fade with the incoming content at equal, full duration).
            val fadeEnterSpec = androidx.compose.animation.core.tween<Float>(300, easing = enterEasing)
            val fadeExitSpec = androidx.compose.animation.core.tween<Float>(200, easing = exitEasing)
            // Top-level switches only (see isTopLevelSwitch() below): AnimatedContent's enter and
            // exit normally start at the same instant and run concurrently — confirmed via an
            // exaggerated 3-second test that this NavHost's fade mechanism itself was never broken
            // or swallowed, it was genuinely cross-fading, just too fast at 300ms to clearly read
            // as "both screens fading at once." M3's own "clean fades" guidance is explicit here:
            // fully fade the outgoing screen out before fading the new one in, not an overlapping
            // cross-fade — so the enter fade is delayed by the exit's own duration (200ms) to make
            // it genuinely sequential. Hierarchy nav's slide+fade keeps the default concurrent
            // timing — overlap while sliding is the normal, expected look for that pattern.
            val topLevelFadeEnterSpec = androidx.compose.animation.core.tween<Float>(300, delayMillis = 200, easing = enterEasing)
            val slideEnterSpec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(300, easing = enterEasing)
            val slideExitSpec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(200, easing = exitEasing)
            NavHost(
                navController = navController,
                startDestination = "deckList",
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    if (reducedMotion) {
                        // Subtle fade, not None — M3's reduced-motion guidance (see Motion.kt's
                        // ReducedMotionScheme doc comment for the full reasoning).
                        androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(100))
                    } else if (isTopLevelSwitch()) {
                        androidx.compose.animation.fadeIn(animationSpec = topLevelFadeEnterSpec)
                    } else {
                        // Forward and backward (M3: "Android uses a fade as screens slide" —
                        // confirmed against Google's own GM3 transition-pattern reference video).
                        // Enters from the right.
                        androidx.compose.animation.fadeIn(animationSpec = fadeEnterSpec) +
                            androidx.compose.animation.slideInHorizontally(animationSpec = slideEnterSpec) { fullWidth -> fullWidth / 4 }
                    }
                },
                exitTransition = {
                    // Exit uses a shorter duration, enter uses the full one — M3 warns against a
                    // full-duration cross fade ("fully fade out before fading in... if a cross
                    // fade needs to occur, keep it quick"); this asymmetry clears the outgoing
                    // screen quickly so the overlap window shrinks instead of the two screens
                    // cross-fading at full, equal duration.
                    if (reducedMotion) {
                        androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(100))
                    } else if (isTopLevelSwitch()) {
                        androidx.compose.animation.fadeOut(animationSpec = fadeExitSpec)
                    } else {
                        // Outgoing screen slides further left while fading, mirroring the entrance.
                        androidx.compose.animation.fadeOut(animationSpec = fadeExitSpec) +
                            androidx.compose.animation.slideOutHorizontally(animationSpec = slideExitSpec) { fullWidth -> -fullWidth / 4 }
                    }
                },
                popEnterTransition = {
                    if (reducedMotion) {
                        androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(100))
                    } else if (isTopLevelSwitch()) {
                        androidx.compose.animation.fadeIn(animationSpec = topLevelFadeEnterSpec)
                    } else {
                        // Reverse of the forward entrance: enters from the left.
                        androidx.compose.animation.fadeIn(animationSpec = fadeEnterSpec) +
                            androidx.compose.animation.slideInHorizontally(animationSpec = slideEnterSpec) { fullWidth -> -fullWidth / 4 }
                    }
                },
                popExitTransition = {
                    if (reducedMotion) {
                        androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(100))
                    } else if (isTopLevelSwitch()) {
                        androidx.compose.animation.fadeOut(animationSpec = fadeExitSpec)
                    } else {
                        // Reverse of the forward exit: slides out further right while fading.
                        androidx.compose.animation.fadeOut(animationSpec = fadeExitSpec) +
                            androidx.compose.animation.slideOutHorizontally(animationSpec = slideExitSpec) { fullWidth -> fullWidth / 4 }
                    }
                }
            ) {

                composable("deckList") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        val deckGroups by viewModel.groupedAndSortedDecks.collectAsState()
                        net.ericclark.studiare.screens.Screens.DeckListScreen(
                            navController = navController,
                            deckGroups = deckGroups,
                            viewModel = viewModel
                        )
                    }
                }
                composable("deckEditor?deckId={deckId}") { backStackEntry ->
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        val deckId = backStackEntry.arguments?.getString("deckId")
                        val deck = decks.find { it.deck.id == deckId }
                        net.ericclark.studiare.screens.Screens.DeckEditorScreen(
                            navController = navController,
                            deckWithCards = deck,
                            viewModel = viewModel
                        )
                    }
                }
                composable("simpleEditor?deckId={deckId}") { backStackEntry ->
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        val deckId = backStackEntry.arguments?.getString("deckId")
                        val deck = decks.find { it.deck.id == deckId }
                        net.ericclark.studiare.screens.Screens.SimpleEditorScreen(
                            navController = navController,
                            deckWithCards = deck,
                            viewModel = viewModel
                        )
                    }
                }
                composable("setManager/{deckId}") { backStackEntry ->
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        val deckId = backStackEntry.arguments?.getString("deckId")

                        // FIX: Remove the parentDeckId == null restriction so any deck/set can act as the parent
                        val parentDeck = decks.find { it.deck.id == deckId }

                        if (parentDeck != null) {
                            // FIX: Filter the raw decks list directly to find children, allowing infinite nesting
                            // rather than relying on the top-level-only groupedAndSortedDecks flow.
                            val sets = decks
                                .filter { it.deck.parentDeckId == deckId }
                                .map { net.ericclark.studiare.data.DeckSummary(
                                    deck = it.deck,
                                    totalCards = it.cards.size
                                )}

                            net.ericclark.studiare.screens.Screens.SetManagerScreen(
                                navController = navController,
                                parentDeck = parentDeck,
                                sets = sets,
                                viewModel = viewModel
                            )
                        }
                    }
                }
                composable(
                    route = "studyModeSelection/{deckId}?autoOpen={autoOpen}",
                    arguments = listOf(
                        navArgument("deckId") { type = NavType.StringType },
                        navArgument("autoOpen") {
                            type = NavType.StringType; nullable = true; defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        val deckId = backStackEntry.arguments?.getString("deckId")!!
                        val autoOpen = backStackEntry.arguments?.getString("autoOpen")
                        val deck = decks.find { it.deck.id == deckId }
                        if (deck != null) {
                            StudyModeSelectionScreen(
                                navController = navController,
                                deck = deck,
                                viewModel = viewModel,
                                autoOpen = autoOpen
                            )
                        }
                    }
                }
                composable("studyModeSelection/{deckId}") { backStackEntry ->
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        val deckId = backStackEntry.arguments?.getString("deckId")!!
                        val deck = decks.find { it.deck.id == deckId }
                        if (deck != null) {
                            StudyModeSelectionScreen(
                                navController = navController,
                                deck = deck,
                                viewModel = viewModel
                            )
                        }
                    }
                }
                composable("flashcardStudy") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        net.ericclark.studiare.studymodes.FlashcardScreen(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                }
                composable("flashcardQuizStudy") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        net.ericclark.studiare.studymodes.FlashcardQuizScreen(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                }
                composable("mcStudy") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        net.ericclark.studiare.studymodes.MultipleChoiceScreen(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                }
                composable("typingScoredStudy") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        net.ericclark.studiare.studymodes.TypingScoredScreen(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                }
                composable("matchingStudy") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        net.ericclark.studiare.studymodes.MatchingScreen(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                }
                composable("typingStudy") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        net.ericclark.studiare.studymodes.TypingScreen(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                }
                composable("settings") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        net.ericclark.studiare.screens.Screens.SettingsScreen(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                }
                composable("audioStudy") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        net.ericclark.studiare.studymodes.AudioStudyScreen(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                }
                composable("typedListenStudy") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        net.ericclark.studiare.studymodes.TypedListenScreen(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                }
                composable("spokenListenStudy") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        net.ericclark.studiare.studymodes.SpokenListenScreen(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                }
                composable("hearMatchStudy") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        net.ericclark.studiare.studymodes.HearMatchScreen(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                }
                composable("anagramStudy") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        net.ericclark.studiare.studymodes.AnagramScreen(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                }
                composable("hangmanStudy") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        net.ericclark.studiare.studymodes.HangmanScreen(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                }
                composable("memoryStudy") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        net.ericclark.studiare.studymodes.MemoryScreen(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                }
                composable("crosswordStudy") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        net.ericclark.studiare.studymodes.CrosswordScreen(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                }
                composable("wordSearchStudy") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        net.ericclark.studiare.studymodes.WordSearchMode(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                }
                composable("freeformStudy") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        net.ericclark.studiare.studymodes.FreeformScreen(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                }
                composable("collectionManager") {
                    net.ericclark.studiare.screens.Screens.CollectionManagerScreen(
                        navController = navController,
                        viewModel = viewModel
                    )
                }
                composable("recents") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        net.ericclark.studiare.screens.Screens.RecentsScreen(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}