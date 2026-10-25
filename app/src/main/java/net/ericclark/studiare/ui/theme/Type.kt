package net.ericclark.studiare.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import net.ericclark.studiare.R

// Roboto Flex is a true variable font (confirmed via its fvar table: wght 100-1000, wdth 25-151,
// opsz 8-144, plus GRAD/slnt/parametric axes). Each Font() entry must carry an explicit `weight`
// so Compose's font matcher can actually tell the entries apart — `variationSettings` alone only
// controls how a face renders *after* one is selected, not which one gets picked for a requested
// TextStyle.fontWeight. Without `weight=`, every entry below defaulted to FontWeight.Normal and
// was indistinguishable to the matcher, so weight variation never actually worked.
@OptIn(ExperimentalTextApi::class)
private fun flexFont(weight: Int, width: Int? = null) = Font(
    resId = R.font.robotoflex_variable,
    weight = FontWeight(weight),
    variationSettings = if (width != null) {
        FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width.toFloat()))
    } else {
        FontVariation.Settings(FontVariation.weight(weight))
    }
)

// Normal width (100%) — safe everywhere, including fixed-width single-character tiles (study
// modes render single letters in exactly-sized boxes; a weight-only change keeps advance width
// stable, by variable-font design).
@OptIn(ExperimentalTextApi::class)
val RobotoFlexFamily = FontFamily(
    flexFont(400), // Normal
    flexFont(500), // Medium
    flexFont(600), // SemiBold
    flexFont(700), // Bold
    flexFont(900), // Black
)

// Expanded width (125%) — only for roles confirmed to render in free-flowing (non-fixed-width)
// contexts: top app bar titles, section headers, display/score text.
@OptIn(ExperimentalTextApi::class)
val RobotoFlexExpandedFamily = FontFamily(
    flexFont(400, width = 125),
    flexFont(500, width = 125),
    flexFont(600, width = 125),
    flexFont(700, width = 125),
    flexFont(900, width = 125),
)

// Grab the baseline Material 3 default typography sizes
private val baseline = Typography()

private val emphasizedTitleLarge = baseline.titleLarge.copy(
    fontFamily = RobotoFlexExpandedFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.2).sp,
)

private val emphasizedHeadlineSmall = baseline.headlineSmall.copy(
    fontFamily = RobotoFlexFamily,
    fontWeight = FontWeight.Bold,
)

private val emphasizedHeadlineMedium = baseline.headlineMedium.copy(
    fontFamily = RobotoFlexExpandedFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 30.sp,
    lineHeight = 38.sp,
    letterSpacing = (-0.2).sp,
)

private val emphasizedHeadlineLarge = baseline.headlineLarge.copy(
    fontFamily = RobotoFlexExpandedFamily,
    fontWeight = FontWeight.Black,
    fontSize = 36.sp,
    lineHeight = 44.sp,
    letterSpacing = (-0.3).sp,
)

private val emphasizedDisplayLarge = baseline.displayLarge.copy(
    fontFamily = RobotoFlexExpandedFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 63.sp,
    lineHeight = 70.sp,
    letterSpacing = (-0.5).sp,
)

private val emphasizedDisplayMedium = baseline.displayMedium.copy(
    fontFamily = RobotoFlexExpandedFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 50.sp,
    lineHeight = 56.sp,
    letterSpacing = (-0.3).sp,
)

private val emphasizedDisplaySmall = baseline.displaySmall.copy(
    fontFamily = RobotoFlexExpandedFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 40.sp,
    lineHeight = 46.sp,
    letterSpacing = (-0.2).sp,
)

// Apply Roboto Flex to ALL Material 3 text styles. Display/headline/title get the deliberate
// heavier+wider(+bigger) "emphasized" treatment per M3's own guidance that these roles are for
// "short, important text... choose an expressive font... help attract the eye" — this cascades
// to every top app bar (via titleLarge), ~35 dialog/section/game-tile call sites (headlineSmall),
// and the two score screens (display*), with no per-call-site edits needed. Body/label keep their
// stock weight/size (long-form, small, legibility-first per M3's own guidance) and only pick up
// the font-matching bug fix above.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Typography = Typography(
    displayLarge = emphasizedDisplayLarge,
    displayMedium = emphasizedDisplayMedium,
    displaySmall = emphasizedDisplaySmall,

    headlineLarge = emphasizedHeadlineLarge,
    headlineMedium = emphasizedHeadlineMedium,
    headlineSmall = emphasizedHeadlineSmall,

    titleLarge = emphasizedTitleLarge,
    titleMedium = baseline.titleMedium.copy(fontFamily = RobotoFlexFamily),
    titleSmall = baseline.titleSmall.copy(fontFamily = RobotoFlexFamily),

    bodyLarge = baseline.bodyLarge.copy(fontFamily = RobotoFlexFamily),
    bodyMedium = baseline.bodyMedium.copy(fontFamily = RobotoFlexFamily),
    bodySmall = baseline.bodySmall.copy(fontFamily = RobotoFlexFamily),

    labelLarge = baseline.labelLarge.copy(fontFamily = RobotoFlexFamily),
    labelMedium = baseline.labelMedium.copy(fontFamily = RobotoFlexFamily),
    labelSmall = baseline.labelSmall.copy(fontFamily = RobotoFlexFamily),

    displayLargeEmphasized = emphasizedDisplayLarge,
    displayMediumEmphasized = emphasizedDisplayMedium,
    displaySmallEmphasized = emphasizedDisplaySmall,

    headlineLargeEmphasized = emphasizedHeadlineLarge,
    headlineMediumEmphasized = emphasizedHeadlineMedium,
    headlineSmallEmphasized = emphasizedHeadlineSmall,

    titleLargeEmphasized = emphasizedTitleLarge,
    titleMediumEmphasized = baseline.titleMedium.copy(fontFamily = RobotoFlexFamily, fontWeight = FontWeight.Bold),
    titleSmallEmphasized = baseline.titleSmall.copy(fontFamily = RobotoFlexFamily, fontWeight = FontWeight.Bold),

    bodyLargeEmphasized = baseline.bodyLarge.copy(fontFamily = RobotoFlexFamily, fontWeight = FontWeight.Medium),
    bodyMediumEmphasized = baseline.bodyMedium.copy(fontFamily = RobotoFlexFamily, fontWeight = FontWeight.Medium),
    bodySmallEmphasized = baseline.bodySmall.copy(fontFamily = RobotoFlexFamily, fontWeight = FontWeight.Medium),

    labelLargeEmphasized = baseline.labelLarge.copy(fontFamily = RobotoFlexFamily, fontWeight = FontWeight.Bold),
    labelMediumEmphasized = baseline.labelMedium.copy(fontFamily = RobotoFlexFamily, fontWeight = FontWeight.Bold),
    labelSmallEmphasized = baseline.labelSmall.copy(fontFamily = RobotoFlexFamily, fontWeight = FontWeight.Bold),
)
