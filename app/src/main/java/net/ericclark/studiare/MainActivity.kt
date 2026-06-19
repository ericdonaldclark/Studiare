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
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.FirebaseApp
import net.ericclark.studiare.components.parseHexColor
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.navigation.compose.currentBackStackEntryAsState
import net.ericclark.studiare.screens.AppNavigationDrawer
import androidx.compose.material3.DismissibleNavigationDrawer
import androidx.compose.material3.DismissibleDrawerSheet
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.focusable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.isCtrlPressed

val LocalDrawerState = compositionLocalOf<DrawerState?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }
val LocalWindowWidthSizeClass = compositionLocalOf<WindowWidthSizeClass> { error("No Width Size provided") }
val LocalWindowHeightSizeClass = compositionLocalOf<WindowHeightSizeClass> { error("No Height Size provided") }

// Define a High Contrast Black & White Color Scheme
private val BlackAndWhiteColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color.White,
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFFCCCCCC),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFAAAAAA),
    onSecondaryContainer = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color.Black,
    onSurfaceVariant = Color.White,
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

            val context = LocalContext.current
            val viewModel: FlashcardViewModel =
                viewModel(factory = FlashcardViewModelFactory(context.applicationContext as Application))

            val themeMode by viewModel.themeMode.collectAsState()
            val customColors by viewModel.customThemeColors.collectAsState()

            splashScreen.setKeepOnScreenCondition {
                !viewModel.hasStartedLoading
            }

            val content = @Composable {
                // Initialize our Shortcut Engine States
                var isHintMode by remember { mutableStateOf(false) }
                val shortcutRegistry = remember { ShortcutRegistry() }
                val focusRequester = remember { FocusRequester() }

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                CompositionLocalProvider(
                    LocalWindowWidthSizeClass provides widthSizeClass,
                    LocalWindowHeightSizeClass provides heightSizeClass,
                    LocalHintMode provides isHintMode,
                    LocalShortcutRegistry provides shortcutRegistry
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester)
                            .focusable()
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
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigation(viewModel = viewModel)
                    }
                }
            }

            val isDark = isSystemInDarkTheme()
            val resolvedColorScheme = when (themeMode) {
                ThemeMode.BLACK_AND_WHITE -> BlackAndWhiteColorScheme
                ThemeMode.CUSTOM -> generateCustomScheme(
                    primary = parseHexColor(customColors.primary),
                    secondary = parseHexColor(customColors.secondary),
                    tertiary = parseHexColor(customColors.tertiary),
                    background = parseHexColor(customColors.background),
                    isDark = isDark
                )
                else -> null
            }

            StudiareTheme(
                darkTheme = themeMode == ThemeMode.DARK,
                customColorScheme = resolvedColorScheme,
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
    val activeSessions by viewModel.allActiveSessions.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isDecksScreen = currentRoute == "deckList" || currentRoute == null
    val gesturesEnabled = isDecksScreen // FIX: Enable gestures on the home screen
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val windowHeightSizeClass = LocalWindowHeightSizeClass.current
    val isWideScreen = windowWidthSizeClass > WindowWidthSizeClass.Compact && windowHeightSizeClass > WindowHeightSizeClass.Compact

    val isPersistentDrawerOpen by viewModel.isLargeScreenDrawerOpen.collectAsState()

    // The core state. Acts as the real drawer on phones, and an interceptor on desktops.
    val phoneDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // 1. Initial State Sync & Breakpoint Handoff
    val hasInitializedDrawer = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(isWideScreen) {
        if (!hasInitializedDrawer.value) {
            if (isWideScreen) {
                viewModel.setLargeScreenDrawerOpen(true)
            }
            hasInitializedDrawer.value = true
        } else {
            if (isWideScreen && phoneDrawerState.isOpen) {
                // Phone -> Desktop: Move the modal state into the persistent sidebar
                viewModel.setLargeScreenDrawerOpen(true)
                phoneDrawerState.snapTo(DrawerValue.Closed)
            } else if (!isWideScreen && isPersistentDrawerOpen) {
                // Desktop -> Phone: Pop the modal drawer open so the user doesn't lose context
                phoneDrawerState.snapTo(DrawerValue.Open)
            }
        }
    }

    // 2. The Interceptor: Catches hamburger menu clicks on Desktop
    LaunchedEffect(phoneDrawerState.currentValue) {
        if (isWideScreen && phoneDrawerState.currentValue == DrawerValue.Open) {
            viewModel.setLargeScreenDrawerOpen(true)
            phoneDrawerState.snapTo(DrawerValue.Closed)
        }
    }

    if (isWideScreen) {
        // --- DESKTOP: Dynamic Squishing Row Layout ---
        Row(modifier = Modifier.fillMaxSize()) {
            val drawerVisibilityState = androidx.compose.runtime.remember {
                androidx.compose.animation.core.MutableTransitionState(
                    initialState = if (!hasInitializedDrawer.value && isWideScreen) true else isPersistentDrawerOpen
                )
            }
            drawerVisibilityState.targetState = if (!hasInitializedDrawer.value && isWideScreen) true else isPersistentDrawerOpen

            androidx.compose.animation.AnimatedVisibility(
                visibleState = drawerVisibilityState,
                enter = androidx.compose.animation.expandHorizontally(expandFrom = Alignment.Start),
                exit = androidx.compose.animation.shrinkHorizontally(shrinkTowards = Alignment.Start)
            ) {
                AppNavigationDrawer(
                    decks = decks,
                    sessions = activeSessions,
                    isLoading = viewModel.isLoading,
                    navController = navController,
                    onCloseAction = { viewModel.setLargeScreenDrawerOpen(false) },
                    onNavigateAction = { /* Do nothing, leave the persistent drawer open! */ }
                )
            }

            // Wrap the NavGraph in an invisible modal to safely provide LocalDrawerState
            ModalNavigationDrawer(
                drawerState = phoneDrawerState,
                gesturesEnabled = false,
                drawerContent = { Box(Modifier.width(0.dp)) },
                scrimColor = Color.Transparent,
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                StudiareNavGraph(navController, viewModel, phoneDrawerState, decks)
            }
        }
    } else {
        // --- PHONE: Standard Overlay Drawer ---
        ModalNavigationDrawer(
            drawerState = phoneDrawerState,
            gesturesEnabled = gesturesEnabled,
            drawerContent = {
                AppNavigationDrawer(
                    decks = decks,
                    sessions = activeSessions,
                    isLoading = viewModel.isLoading,
                    navController = navController,
                    onCloseAction = { scope.launch { phoneDrawerState.close() } },
                    onNavigateAction = { scope.launch { phoneDrawerState.close() } }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) {
            StudiareNavGraph(navController, viewModel, phoneDrawerState, decks)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun StudiareNavGraph(
    navController: androidx.navigation.NavHostController,
    viewModel: FlashcardViewModel,
    drawerState: DrawerState,
    decks: List<net.ericclark.studiare.data.DeckWithCards>
) {
    val scope = rememberCoroutineScope()
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val windowHeightSizeClass = LocalWindowHeightSizeClass.current
    val isWideScreen = windowWidthSizeClass > WindowWidthSizeClass.Compact && windowHeightSizeClass > WindowHeightSizeClass.Compact

    SharedTransitionLayout(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    // Global Back (Escape)
                    if (event.key == Key.Escape) {
                        // navigateUp() securely handles popping the backstack
                        if (navController.navigateUp()) {
                            return@onPreviewKeyEvent true
                        }
                    }

                    val isModifierPressed = event.isCtrlPressed || event.isMetaPressed

                    // Global Shortcuts with Ctrl/Cmd or Alt
                    if (isModifierPressed) {
                        when (event.key) {
                            Key.H -> {
                                navController.navigate("deckList") { popUpTo(0) }
                                return@onPreviewKeyEvent true
                            }
                            Key.Comma, Key.S -> {
                                navController.navigate("settings")
                                return@onPreviewKeyEvent true
                            }
                            Key.B, Key.D -> {
                                if (isWideScreen) {
                                    viewModel.setLargeScreenDrawerOpen(!viewModel.isLargeScreenDrawerOpen.value)
                                } else {
                                    scope.launch {
                                        if (drawerState.isOpen) drawerState.close() else drawerState.open()
                                    }
                                }
                                return@onPreviewKeyEvent true
                            }
                        }
                    } else if (event.isAltPressed) {
                        if (event.key == Key.M) {
                            if (isWideScreen) {
                                viewModel.setLargeScreenDrawerOpen(!viewModel.isLargeScreenDrawerOpen.value)
                            } else {
                                scope.launch {
                                    if (drawerState.isOpen) drawerState.close() else drawerState.open()
                                }
                            }
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                false
            }
    ) {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
            LocalDrawerState provides drawerState
        ) {
            NavHost(
                navController = navController,
                startDestination = "deckList",
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    androidx.compose.animation.scaleIn(
                        initialScale = 0.95f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                        )
                    ) + androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(200)
                    )
                },
                exitTransition = {
                    androidx.compose.animation.scaleOut(
                        targetScale = 1.05f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                        )
                    ) + androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(200)
                    )
                },
                popEnterTransition = {
                    androidx.compose.animation.scaleIn(
                        initialScale = 1.05f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                        )
                    ) + androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(200)
                    )
                },
                popExitTransition = {
                    androidx.compose.animation.scaleOut(
                        targetScale = 0.95f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                        )
                    ) + androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(200)
                    )
                }
            ) {

                composable("deckList") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        val deckGroups by viewModel.groupedAndSortedDecks.collectAsState()
                        net.ericclark.studiare.screens.DeckListScreen(
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
                        net.ericclark.studiare.screens.DeckEditorScreen(
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

                            net.ericclark.studiare.screens.SetManagerScreen(
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
                composable("quizStudy") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        net.ericclark.studiare.studymodes.QuizScreen(
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
                        net.ericclark.studiare.screens.SettingsScreen(
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
                composable("freeformStudy") {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                        net.ericclark.studiare.studymodes.FreeformScreen(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }
                }
                composable("collectionManager") {
                    net.ericclark.studiare.screens.CollectionManagerScreen(
                        navController = navController,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}