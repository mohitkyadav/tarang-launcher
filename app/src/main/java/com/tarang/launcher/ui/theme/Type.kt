package com.tarang.launcher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import com.tarang.launcher.R

@OptIn(ExperimentalTextApi::class)
private fun interFont(weight: Int) = Font(
    resId = R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Inter (SIL OFL) — a clean SF-style sans used as the SF substitute (plan §5.4). */
val InterFamily = FontFamily(
    interFont(400),
    interFont(500),
    interFont(600),
    interFont(700),
)

/** Wraps the TV MaterialTheme and makes Inter the default font for all text. */
@Composable
fun TarangTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = InterFamily),
            content = content,
        )
    }
}
