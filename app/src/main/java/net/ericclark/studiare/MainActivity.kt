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
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Pass the debug flag to the logger
        AppLogger.init(BuildConfig.DEBUG)

        setContent {
            val context = LocalContext.current
            val viewModel: FlashcardViewModel =
                viewModel(factory = FlashcardViewModelFactory(context.applicationContext as Application))

            val themeMode by viewModel.themeMode.collectAsState()
            val customColors by viewModel.customThemeColors.collectAsState()

            splashScreen.setKeepOnScreenCondition { viewModel.isLoading }

            val content = @Composable {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel = viewModel)
                }
            }

            when (themeMode) {
                ThemeMode.BLACK_AND_WHITE -> {
                    MaterialTheme(colorScheme = BlackAndWhiteColorScheme, content = content)
                }
                ThemeMode.CUSTOM -> {
                    // Calculate the custom scheme using our local helper
                    val isDark = isSystemInDarkTheme()
                    val customScheme = generateCustomScheme(
                        primary = parseHexColor(customColors.primary),
                        secondary = parseHexColor(customColors.secondary),
                        tertiary = parseHexColor(customColors.tertiary),
                        background = parseHexColor(customColors.background),
                        isDark = isDark
                    )

                    StudiareTheme(
                        customColorScheme = customScheme,
                        content = content
                    )
                }
                else -> {
                    StudiareTheme(
                        darkTheme = themeMode == ThemeMode.DARK,
                        content = content
                    )
                }
            }
        }
    }
}

/**
 * Composable function that defines the app's navigation graph using Jetpack Navigation Compose.
 * It sets up all the possible screens and the routes to navigate between them.
 * @param viewModel The shared ViewModel instance passed to each screen.
 */
@Composable
fun AppNavigation(viewModel: FlashcardViewModel) {
    val navController = rememberNavController()
    val decks by viewModel.allDecks.observeAsState(initial = emptyList())

    NavHost(
        navController = navController,
        startDestination = "deckList",
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
            net.ericclark.studiare.screens.DeckListScreen(
                navController = navController,
                decks = decks,
                viewModel = viewModel
            )
        }
        composable("deckEditor?deckId={deckId}") { backStackEntry ->
            val deckId = backStackEntry.arguments?.getString("deckId")
            val deck = decks.find { it.deck.id == deckId }
            net.ericclark.studiare.screens.DeckEditorScreen(
                navController = navController,
                deckWithCards = deck,
                viewModel = viewModel
            )
        }
        composable("setManager/{deckId}") { backStackEntry ->
            val deckId = backStackEntry.arguments?.getString("deckId")
            val parentDeck = decks.find { it.deck.id == deckId && it.deck.parentDeckId == null }
            if (parentDeck != null) {
                val sets = decks.filter { it.deck.parentDeckId == deckId }
                net.ericclark.studiare.screens.SetManagerScreen(
                    navController = navController,
                    parentDeck = parentDeck,
                    sets = sets,
                    viewModel = viewModel
                )
            }
        }
        composable("studyModeSelection/{deckId}") { backStackEntry ->
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
        composable("flashcardStudy") {
            net.ericclark.studiare.studymodes.FlashcardScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
        composable("flashcardQuizStudy") {
            net.ericclark.studiare.studymodes.FlashcardQuizScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
        composable("mcStudy") {
            net.ericclark.studiare.studymodes.MultipleChoiceScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
        composable("quizStudy") {
            net.ericclark.studiare.studymodes.QuizScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
        composable("matchingStudy") {
            net.ericclark.studiare.studymodes.MatchingScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
        composable("typingStudy") {
            net.ericclark.studiare.studymodes.TypingScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
        composable("settings") {
            net.ericclark.studiare.screens.SettingsScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
        composable("audioStudy") {
            net.ericclark.studiare.studymodes.AudioStudyScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
        composable("anagramStudy") {
            net.ericclark.studiare.studymodes.AnagramScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
        composable("hangmanStudy") {
            net.ericclark.studiare.studymodes.HangmanScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
        composable("memoryStudy") {
            net.ericclark.studiare.studymodes.MemoryScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
        composable("crosswordStudy") {
            net.ericclark.studiare.studymodes.CrosswordScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
    }
}