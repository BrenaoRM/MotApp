package com.example.financacelular.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
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

/** Cores usadas como texto que mudam com o tema (Verde/Coral seguem para preenchimentos). */
data class CoresApp(
    val textoGanho: Color,
    val textoGasto: Color,
    val alerta: Color
)

val LocalCoresApp = staticCompositionLocalOf { CoresClaras }

val MaterialTheme.cores: CoresApp
    @Composable
    @ReadOnlyComposable
    get() = LocalCoresApp.current

@Composable
fun FinanceAPPTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme && amoled -> EsquemaAmoled
        darkTheme -> EsquemaEscuro
        else -> EsquemaClaro
    }

    CompositionLocalProvider(LocalCoresApp provides if (darkTheme) CoresEscuras else CoresClaras) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FinancaTypography,
            shapes = FinancaShapes,
            content = content
        )
    }
}
