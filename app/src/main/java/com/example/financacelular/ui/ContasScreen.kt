package com.example.financacelular.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.financacelular.ui.theme.AzulCartao
import com.example.financacelular.ui.theme.dimens

@Composable
fun ContasScreen(
    aoVoltar: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = MaterialTheme.dimens.paddingScreen)
            .padding(top = 16.dp, bottom = espacoParaBarraFlutuante())
    ) {
        CabecalhoDePagina("Contas", AzulCartao, aoVoltar)
        Spacer(modifier = Modifier.height(32.dp))
        EstadoVazio(
            icone = Icons.Filled.AccountBalanceWallet,
            mensagem = "Contas chegam em breve"
        )
    }
}
