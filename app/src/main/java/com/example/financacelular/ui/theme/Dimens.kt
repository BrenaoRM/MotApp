package com.example.financacelular.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Dimensions(
    val paddingSmall: Dp,
    val paddingMedium: Dp,
    val paddingScreen: Dp,
    val spaceBetweenCards: Dp,
    val cardCornerRadius: Dp
)

// Telas extremamente estreitas / Zoom Máximo do Android (< 350dp)
val UltraCompactDimensions = Dimensions(
    paddingSmall = 4.dp,
    paddingMedium = 10.dp,
    paddingScreen = 12.dp,
    spaceBetweenCards = 8.dp,
    cardCornerRadius = 14.dp
)

// Telas compactas / Zoom Padrão do Android (350dp a 380dp)
val CompactDimensions = Dimensions(
    paddingSmall = 6.dp,
    paddingMedium = 12.dp,
    paddingScreen = 12.dp,
    spaceBetweenCards = 10.dp,
    cardCornerRadius = 16.dp
)

// Telas normais e largas / Zoom Mínimo (>= 380dp)
val MediumDimensions = Dimensions(
    paddingSmall = 10.dp,
    paddingMedium = 16.dp,
    paddingScreen = 14.dp,
    spaceBetweenCards = 16.dp,
    cardCornerRadius = 22.dp
)

val MaterialTheme.dimens: Dimensions
    @Composable
    @ReadOnlyComposable
    @Suppress("UnusedReceiverParameter")
    get() {
        val width = LocalConfiguration.current.screenWidthDp
        return when {
            width < 350 -> UltraCompactDimensions
            width < 380 -> CompactDimensions
            else -> MediumDimensions
        }
    }