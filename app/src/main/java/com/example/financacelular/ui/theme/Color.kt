package com.example.financacelular.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Paleta clara — fundo bege claro, cartões brancos flutuando, verde como accent.
// Baseado na referência enviada pelo usuário (app de finanças com tema claro).
val FundoBege = Color(0xFFF4F1EA)
val CartaoBranco = Color(0xFFFFFFFF)
val BordaClara = Color(0xFFE9E5D8)
val TextoEscuro = Color(0xFF1E1F1C)
val TextoSecundario = Color(0xFF8B8A82)

val FundoEscuro = Color(0xFF16171A)
val CartaoEscuro = Color(0xFF212226)
val BordaEscura = Color(0xFF2E2F33)
val TextoClaro = Color(0xFFF2F1EC)
val TextoSecundarioEscuro = Color(0xFF9B9A94)

val Verde = Color(0xFF2FAE60)
val VerdeEscuro = Color(0xFF1F8C4A)
val Coral = Color(0xFFEF6C5C)
val Laranja = Color(0xFFF2A93B)

// Tons mais luminosos, usados só no tema escuro — o texto/ícones verde e coral
// originais ficam com contraste baixo demais contra o fundo quase preto.
val VerdeContrasteEscuro = Color(0xFF4ADE80)
val CoralContrasteEscuro = Color(0xFFFF8A75)

val EsquemaClaro = lightColorScheme(
    primary = Verde,
    onPrimary = Color.White,
    secondary = Verde,
    onSecondary = Color.White,
    tertiary = Coral,
    onTertiary = Color.White,
    background = FundoBege,
    onBackground = TextoEscuro,
    surface = CartaoBranco,
    onSurface = TextoEscuro,
    surfaceVariant = CartaoBranco,
    onSurfaceVariant = TextoSecundario,
    outline = BordaClara
)

val EsquemaEscuro = darkColorScheme(
    primary = VerdeContrasteEscuro,
    onPrimary = Color.Black,
    secondary = VerdeContrasteEscuro,
    onSecondary = Color.Black,
    tertiary = CoralContrasteEscuro,
    onTertiary = Color.Black,
    background = FundoEscuro,
    onBackground = TextoClaro,
    surface = CartaoEscuro,
    onSurface = TextoClaro,
    surfaceVariant = CartaoEscuro,
    onSurfaceVariant = TextoSecundarioEscuro,
    outline = BordaEscura
)
