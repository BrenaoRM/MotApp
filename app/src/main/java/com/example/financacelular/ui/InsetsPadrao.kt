package com.example.financacelular.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Altura "própria" da barra de navegação flutuante, sem contar a inset do
 * sistema: 10dp de respiro acima + 50dp da NavigationBar + 10dp de respiro
 * abaixo. Ver AppNavigation.kt -> Scaffold -> bottomBar.
 */
private val ALTURA_BARRA_FLUTUANTE = 70.dp

/** Respiro extra para o último item nunca ficar colado na barra. */
private val RESPIRO_EXTRA = 6.dp

/**
 * Espaço padrão que toda lista/coluna rolável deve reservar no final do seu
 * contentPadding para que o conteúdo passe por trás da barra de navegação
 * flutuante sem que o último item fique escondido por ela.
 *
 * Já soma a inset real de navegação do sistema (gestos ou 3 botões), então
 * funciona igual em qualquer aparelho sem precisar ajustar valores na mão.
 * Use este valor em vez de números fixos (ex.: 82.5.dp, 100.dp) no
 * `bottom` do `contentPadding` de cada tela.
 */
@Composable
fun espacoParaBarraFlutuante(): Dp {
    val insetSistema = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return insetSistema + ALTURA_BARRA_FLUTUANTE + RESPIRO_EXTRA
}
