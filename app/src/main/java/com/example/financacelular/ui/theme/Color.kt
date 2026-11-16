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
// 4.8:1 sobre o bege e 5.4:1 sobre o branco (antes 3.07:1 / 3.47:1)
val TextoSecundario = Color(0xFF6B6A63)

val FundoEscuro = Color(0xFF16171A)
val CartaoEscuro = Color(0xFF212226)
val BordaEscura = Color(0xFF2E2F33)
val TextoClaro = Color(0xFFF2F1EC)
val TextoSecundarioEscuro = Color(0xFF9B9A94)

val Verde = Color(0xFF2FAE60)
val VerdeEscuro = Color(0xFF1F8C4A)
// Verde para fundo de botão com texto branco no tema claro (5.4:1; o Verde da marca dá 2.86:1)
val VerdeBotaoClaro = Color(0xFF187A3E)
val Coral = Color(0xFFEF6C5C)
val Laranja = Color(0xFFF2A93B)

// Tons mais luminosos, usados só no tema escuro — o texto/ícones verde e coral
// originais ficam com contraste baixo demais contra o fundo quase preto.
val VerdeContrasteEscuro = Color(0xFF4ADE80)
val CoralContrasteEscuro = Color(0xFFFF8A75)

// Texto de gasto no tema claro: 5.29:1 sobre branco, 4.69:1 sobre o bege
val CoralTextoClaro = Color(0xFFBF3F30)
// Texto de alerta no tema claro: 5.22:1 sobre branco, 4.63:1 sobre o bege
val AmbarTextoClaro = Color(0xFFA35A00)

// Cores de página (a mesma do atalho na home)
val AmareloInvestimento = Laranja
val AmareloEscuroGradiente = Color(0xFFD97706)
val AmareloAlerta = Color(0xFFF5A524)
val AmareloAgenda = Color(0xFFF59E0B)
val AzulAgenda = Color(0xFF3B82F6)
val AzulAgendaClaro = Color(0xFF7EA6F7)
val AzulCartao = Color(0xFF5B8DEF)
val RoxoMeta = Color(0xFF8B5CF6)
val RoxoMetaEscuro = Color(0xFF6D3FD6)
val RoxoHero = Color(0xFF7B5CF5)
val AuroraIndigo = Color(0xFF6366F1)
val TealParcelados = Color(0xFF2EC4B6)
val VerdeOrcamento = Color(0xFF5FB36B)
val LilasRecorrente = Color(0xFFB07CE8)
val CorConfiguracoes = Color(0xFF64748B)

val CoresCategoria = listOf(Coral, AzulCartao, LilasRecorrente, Laranja, Verde, TealParcelados)
val CoresCategoriaGanhos = listOf(
    Verde,
    TealParcelados,
    Color(0xFF81C784),
    Color(0xFF00B4D8),
    Color(0xFF4CAF50),
    Color(0xFF009688)
)

val CoresClaras = CoresApp(textoGanho = VerdeBotaoClaro, textoGasto = CoralTextoClaro, alerta = AmbarTextoClaro)
val CoresEscuras = CoresApp(textoGanho = VerdeContrasteEscuro, textoGasto = CoralContrasteEscuro, alerta = Laranja)

val EsquemaClaro = lightColorScheme(
    primary = VerdeBotaoClaro,
    onPrimary = Color.White,
    secondary = VerdeBotaoClaro,
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

// Tema AMOLED: preto puro no fundo, cartões quase pretos, bordas finas.
val FundoAmoled = Color(0xFF000000)
val CartaoAmoled = Color(0xFF0D0D0F)
val BordaAmoled = Color(0xFF1F1F23)

// Acento violeta (como na referência); verde/coral ficam só para ganho/gasto.
val EsquemaAmoled = darkColorScheme(
    primary = RoxoHero,
    onPrimary = Color.White,
    secondary = RoxoHero,
    onSecondary = Color.White,
    tertiary = CoralContrasteEscuro,
    onTertiary = Color.Black,
    background = FundoAmoled,
    onBackground = TextoClaro,
    surface = CartaoAmoled,
    onSurface = TextoClaro,
    surfaceVariant = CartaoAmoled,
    onSurfaceVariant = TextoSecundarioEscuro,
    outline = BordaAmoled
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
