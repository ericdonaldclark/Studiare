package net.ericclark.studiare.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.toFontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.ericclark.studiare.R

/**
 * Codepoints into the Material Symbols Outlined variable font (res/font/material_symbols_outlined.ttf),
 * from Google's MaterialSymbolsOutlined[FILL,GRAD,opsz,wght].codepoints.
 */
object MaterialSymbols {
    const val HOME = ''
    const val PUSH_PIN = ''
    const val HISTORY = ''
    const val SETTINGS = ''
}

/**
 * Renders one glyph from the Material Symbols variable font. [fill] (0f outline -> 1f filled) and
 * [weight] are continuous axes on the font itself, so a single glyph can morph between states
 * instead of swapping between two separate icon assets.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun MaterialSymbol(
    codepoint: Char,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    fill: Float = 0f,
    weight: Int = 400,
    tint: Color = LocalContentColor.current
) {
    val fontFamily = remember(fill, weight) {
        Font(
            resId = R.font.material_symbols_outlined,
            variationSettings = FontVariation.Settings(
                FontVariation.Setting("FILL", fill),
                FontVariation.weight(weight),
                FontVariation.grade(0),
            )
        ).toFontFamily()
    }
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = codepoint.toString(),
            color = tint,
            style = TextStyle(
                fontFamily = fontFamily,
                fontSize = sizeToFontSize(size),
                textAlign = TextAlign.Center
            )
        )
    }
}

private fun sizeToFontSize(size: Dp): TextUnit = (size.value).sp
