package com.extratube.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary      = DarkPrimary,
    onPrimary    = DarkOnPrimary,
    background   = DarkBackground,
    surface      = DarkSurface,
    onBackground = DarkOnBackground,
    onSurface    = DarkOnSurface,
    error        = DarkError
)

@Composable
fun ExtraTubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = ExtraTubeTypography,
        shapes      = ExtraTubeShapes,
        content     = content
    )
}
