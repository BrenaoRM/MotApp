package com.example.financacelular.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

@Composable
fun ProvideResponsiveDensity(content: @Composable () -> Unit) {
    val currentDensity = LocalDensity.current

    // Trava a escala de fonte entre 0.85x e 1.15x
    // Evita que fontes gigantes quebrem botões e cartões em telas compactas
    val clampedFontScale = currentDensity.fontScale.coerceIn(0.85f, 1.15f)

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = currentDensity.density,
            fontScale = clampedFontScale
        )
    ) {
        content()
    }
}