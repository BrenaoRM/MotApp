package com.example.financacelular.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Cantos bem suaves e generosos — cartões arredondados "flutuando",
// toggles e botões em formato de pílula, como na referência.
private val FinancaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun FinanceAPPTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) EsquemaEscuro else EsquemaClaro

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FinancaTypography,
        shapes = FinancaShapes,
        content = content
    )
}
