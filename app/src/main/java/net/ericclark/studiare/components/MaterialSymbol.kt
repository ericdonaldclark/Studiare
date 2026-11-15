package net.ericclark.studiare.components

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val FONT_ASSET_PATH = "fonts/material_symbols_outlined.ttf"

/**
 * Codepoints into the Material Symbols Outlined variable font (assets/fonts/material_symbols_outlined.ttf),
 * from Google's MaterialSymbolsOutlined[FILL,GRAD,opsz,wght].codepoints.
 */
object MaterialSymbols {
    const val HOME = ''
    const val PUSH_PIN = ''
    const val SCHEDULE = ''
    const val SETTINGS = ''
}

/**
 * Renders one glyph from the Material Symbols variable font. [fill] (0f outline -> 1f filled) and
 * [weight] are continuous axes on the font itself, so a single glyph can morph between states
 * instead of swapping between two separate icon assets.
 *
 * Drawn with a raw [android.graphics.Paint] on a [Canvas] instead of Compose's [androidx.compose.material3.Text]:
 * this font's own ascent/descent metrics (reserved for accents/descenders that a real line of text
 * needs but a single icon glyph doesn't) put noticeably more empty space above the glyph than below
 * it when laid out as text — confirmed on-device, and `includeFontPadding = false` plus a tight,
 * centered `lineHeightStyle` didn't change it, since that only trims Android's extra line-layout
 * padding, not the font's own metrics. Measuring the glyph's actual ink bounds with
 * [Paint.getTextBounds] and centering that box directly sidesteps the font metrics entirely.
 */
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
    val context = LocalContext.current
    // Variable-font axes need API 26's Typeface.Builder.setFontVariationSettings (loaded from the
    // asset path directly, since that overload is what exposes it — below API 26 the glyph just
    // renders at the font's built-in default axis values instead of morphing fill/weight).
    val typeface = remember(context, fill, weight) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Typeface.Builder(context.assets, FONT_ASSET_PATH)
                .setFontVariationSettings("'FILL' $fill, 'wght' $weight, 'GRAD' 0")
                .build()
        } else {
            Typeface.createFromAsset(context.assets, FONT_ASSET_PATH)
        }
    }
    val text = codepoint.toString()
    val tintArgb = tint.toArgb()
    // Computed here, outside the DrawScope lambda below, where plain `size` still means this
    // function's Dp parameter rather than DrawScope's own same-named Size property.
    val fontSizePx = with(LocalDensity.current) { size.toPx() }

    Canvas(
        modifier = modifier
            .size(size)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else Modifier
            )
    ) {
        drawIntoCanvas { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = fontSizePx
                color = tintArgb
                textAlign = Paint.Align.CENTER
            }
            val bounds = Rect()
            paint.getTextBounds(text, 0, text.length, bounds)
            // Centers the glyph's actual ink (bounds, relative to the baseline) in the canvas,
            // rather than trusting the font's ascent/descent to place it there on its own.
            val baselineY = this.size.height / 2f - bounds.exactCenterY()
            canvas.nativeCanvas.drawText(text, this.size.width / 2f, baselineY, paint)
        }
    }
}
