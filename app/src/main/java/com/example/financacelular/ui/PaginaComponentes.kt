package com.example.financacelular.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Mostra uma mensagem curta ao usuário (Snackbar); provido pelo AppNavigation. */
val LocalMensagem = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Cor usada no cartão principal quando algo está estourado/negativo (igual à home). */
val CorHeroAlerta = Color(0xFFA85560)

/** Locale único do app para moeda, datas e nomes de mês. */
val LocalePtBr: Locale = Locale.Builder().setLanguage("pt").setRegion("BR").build()

/** "Novembro": o mês por extenso, com a inicial maiúscula. */
fun YearMonth.nomeDoMes(): String =
    month.getDisplayName(TextStyle.FULL, LocalePtBr)
        .replaceFirstChar { it.uppercase() }
