package com.example.financacelular.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.financacelular.ui.theme.dimens

/** Aba do assistente com IA: por enquanto só o estado vazio, a tela de verdade vem depois. */
@Composable
fun AssistenteScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MaterialTheme.dimens.paddingScreen)
            .padding(bottom = espacoParaBarraFlutuante())
    ) {
        Spacer(modifier = Modifier.height(LocalEspacoCabecalho.current + 16.dp))
        EstadoVazio(
            icone = Icons.Outlined.AutoAwesome,
            mensagem = "Assistente chega em breve"
        )
    }
}
