package com.teleprompter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teleprompter.model.TextDisplayStyle

val FontFamilyOptions = listOf("Default", "Sans Serif", "Serif", "Monospace", "Cursive")

fun fontFamilyFor(name: String): FontFamily = when (name) {
    "Sans Serif" -> FontFamily.SansSerif
    "Serif" -> FontFamily.Serif
    "Monospace" -> FontFamily.Monospace
    "Cursive" -> FontFamily.Cursive
    else -> FontFamily.Default
}

fun parseHexColor(hex: String): Color {
    val cleaned = hex.removePrefix("#")
    val argb = when (cleaned.length) {
        6 -> 0xFF000000L or cleaned.toLong(16)
        8 -> cleaned.toLong(16)
        else -> return Color.White
    }
    return Color(argb)
}

@Composable
fun TextDisplaySample(
    style: TextDisplayStyle,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { style.borderWidthDp.dp.toPx() }
    val textStyle = TextStyle(
        fontSize = style.fontSizeSp.sp,
        fontFamily = fontFamilyFor(style.fontFamily),
        lineHeight = (style.fontSizeSp * 1.2f).sp
    )
    Box(modifier = modifier) {
        if (style.borderWidthDp > 0f) {
            Text(
                text = "AaBbCcDd",
                style = textStyle.copy(
                    color = parseHexColor(style.borderColorHex),
                    drawStyle = Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            )
        }
        Text(
            text = "AaBbCcDd",
            style = textStyle.copy(color = parseHexColor(style.textColorHex))
        )
    }
}
