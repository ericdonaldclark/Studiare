package net.ericclark.studiare.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import net.ericclark.studiare.R

// Define the single variable font and explicitly set the weights
@OptIn(ExperimentalTextApi::class)
val RobotoFlexFamily = FontFamily(
    Font(
        resId = R.font.robotoflex_variable,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400) // Normal
        )
    ),
    Font(
        resId = R.font.robotoflex_variable,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500) // Medium
        )
    ),
    Font(
        resId = R.font.robotoflex_variable,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(600) // SemiBold
        )
    ),
    Font(
        resId = R.font.robotoflex_variable,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700) // Bold
        )
    )
)

// Grab the baseline Material 3 default typography sizes
private val baseline = Typography()

// Apply Roboto Flex to ALL Material 3 text styles
val Typography = Typography(
    displayLarge = baseline.displayLarge.copy(fontFamily = RobotoFlexFamily),
    displayMedium = baseline.displayMedium.copy(fontFamily = RobotoFlexFamily),
    displaySmall = baseline.displaySmall.copy(fontFamily = RobotoFlexFamily),

    headlineLarge = baseline.headlineLarge.copy(fontFamily = RobotoFlexFamily),
    headlineMedium = baseline.headlineMedium.copy(fontFamily = RobotoFlexFamily),
    headlineSmall = baseline.headlineSmall.copy(fontFamily = RobotoFlexFamily),

    titleLarge = baseline.titleLarge.copy(fontFamily = RobotoFlexFamily),
    titleMedium = baseline.titleMedium.copy(fontFamily = RobotoFlexFamily),
    titleSmall = baseline.titleSmall.copy(fontFamily = RobotoFlexFamily),

    bodyLarge = baseline.bodyLarge.copy(fontFamily = RobotoFlexFamily),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = RobotoFlexFamily),
    bodySmall = baseline.bodySmall.copy(fontFamily = RobotoFlexFamily),

    labelLarge = baseline.labelLarge.copy(fontFamily = RobotoFlexFamily),
    labelMedium = baseline.labelMedium.copy(fontFamily = RobotoFlexFamily),
    labelSmall = baseline.labelSmall.copy(fontFamily = RobotoFlexFamily)
)