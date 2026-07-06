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
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.compose.currentBackStackEntryAsState
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
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Home

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
                    kotlinx.coroutines.delay(100) // Ensure window is fully attached before requesting
                    runCatching { focusRequester.requestFocus() }
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

    // State needed for the widescreen Collection dropdown
    val selectedCollectionId by viewModel.selectedCollectionId.collectAsState()
    val allCollections by viewModel.allCollectionsWithDecks.collectAsState()

    val contentFocusRequester = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val windowHeightSizeClass = LocalWindowHeightSizeClass.current
    val isWideScreen = windowWidthSizeClass > WindowWidthSizeClass.Compact && windowHeightSizeClass > WindowHeightSizeClass.Compact

    val navigateTo = { route: String ->
        navController.navigate(route) {
            popUpTo("deckList") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val isModifierPressed = event.isCtrlPressed || event.isMetaPressed
                val isRepeat = (event.nativeKeyEvent as android.view.KeyEvent).repeatCount > 0

                if (event.type == KeyEventType.KeyDown && !isRepeat) {
                    if (isModifierPressed) {
                        when (event.key) {
                            Key.H -> { navigateTo("deckList"); return@onPreviewKeyEvent true }
                            Key.Comma, Key.S -> { navigateTo("settings"); return@onPreviewKeyEvent true }
                        }
                    }
                } else if (event.type == KeyEventType.KeyUp) {
                    if (event.key == Key.Escape) {
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
                        val currentName = if (selectedCollectionId == null || selectedCollectionId == "UNINITIALIZED") "All Decks"
                        else allCollections.find { it.collection.id == selectedCollectionId }?.collection?.name ?: "All Decks"

                        Box(modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)) {
                            IconButton(onClick = { showCollectionDialog = true }) {
                                Icon(Icons.Default.AutoAwesomeMotion, contentDescription = currentName)
                            }
                        }
                    }
                ) {
                    NavigationRailItem(
                        selected = currentRoute == "deckList" || currentRoute == null,
                        onClick = { navigateTo("deckList") },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") }
                    )
                    NavigationRailItem(
                        selected = currentRoute == "recents",
                        onClick = { navigateTo("recents") },
                        icon = { Icon(Icons.Default.History, contentDescription = "Recents") },
                        label = { Text("Recents") }
                    )
                    NavigationRailItem(
                        selected = currentRoute == "settings",
                        onClick = { navigateTo("settings") },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                }

                // Main Content Area
                Box(modifier = Modifier.weight(1f).fillMaxHeight()
                    .focusRequester(contentFocusRequester)
                    .focusGroup()
                    .focusable()
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

                // 88dp perfectly clears the 64dp bar + 16dp margin + 8dp of breathing room for the FAB
                // We use animateDpAsState so the padding smoothly adjusts as the nav bar enters/exits
                val bottomPadding by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (showBottomBar) 88.dp else 0.dp,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    ),
                    label = "navBarPadding"
                )

                Box(modifier = Modifier.fillMaxSize()
                    // 1. Push the graph up to save the FAB
                    .padding(bottom = bottomPadding)
                    // 2. Consume the insets so the inner Scaffolds don't double-pad the lists!
                    .consumeWindowInsets(PaddingValues(bottom = bottomPadding))
                    .focusRequester(contentFocusRequester)
                    .focusGroup()
                    .focusable()
                ) {
                    StudiareNavGraph(navController, viewModel, decks)
                }

                // Floating Bottom Navigation
                androidx.compose.animation.AnimatedVisibility(
                    visible = showBottomBar,
                    enter = androidx.compose.animation.slideInVertically(
                        // Start slightly further down to ensure it drops in smoothly from off-screen
                        initialOffsetY = { it + 50 },
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                        )
                    ),
                    exit = androidx.compose.animation.slideOutVertically(
                        // Slide fully off the screen
                        targetOffsetY = { it + 50 },
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                        )
                    ),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    NavigationBar(
                        modifier = Modifier
                            .padding(16.dp)
                            .height(64.dp) // Make the pill shorter
                            .clip(RoundedCornerShape(24.dp)),
                        // Strip the default system gesture padding from inside the bar
                        windowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp),
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = currentRoute == "deckList" || currentRoute == null,
                            onClick = { navigateTo("deckList") },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == "recents",
                            onClick = { navigateTo("recents") },
                            icon = { Icon(Icons.Default.History, contentDescription = "Recents") },
                            label = { Text("Recents") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == "settings",
                            onClick = { navigateTo("settings") },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("Settings") }
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
                    net.ericclark.studiare.screens.CollectionManagerScreen(
                        navController = navController,
                        viewModel = viewModel
                    )
                }
                composable("recents") {
                    net.ericclark.studiare.screens.RecentsScreen(
                        navController = navController,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}