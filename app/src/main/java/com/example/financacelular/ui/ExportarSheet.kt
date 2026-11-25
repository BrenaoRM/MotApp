package com.example.financacelular.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.financacelular.data.ExportarLancamentos
import com.example.financacelular.data.ExportarLancamentos.Periodo
import com.example.financacelular.ui.theme.RoxoHero
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Folha "Exportar lançamentos": escolhe o período e compartilha a planilha CSV. */
@Composable
fun FolhaExportar(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val mensagem = LocalMensagem.current
    val escopo = rememberCoroutineScope()
    var periodo by remember { mutableStateOf(Periodo.ESTE_MES) }
    var gerando by remember { mutableStateOf(false) }
    val datas by produceState<List<LocalDate>?>(null) {
        value = withContext(Dispatchers.IO) { ExportarLancamentos.datas(context) }
    }
    val hoje = remember { LocalDate.now() }
    val quantidade = datas?.count { periodo.contem(it, hoje) }

    FolhaApp(onDismiss = onDismiss) {
        CabecalhoDeFolha("Exportar lançamentos", aoFechar = onDismiss)
        Spacer(modifier = Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Periodo.entries.chunked(2).forEach { linha ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    linha.forEach { opcao ->
                        OpcaoDePeriodo(
                            texto = opcao.rotulo,
                            ativo = opcao == periodo,
                            onClick = { periodo = opcao },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            when (quantidade) {
                null -> " "
                0 -> "Nenhum lançamento"
                1 -> "1 lançamento"
                else -> "$quantidade lançamentos"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        BotaoPrincipal(
            texto = "Compartilhar planilha",
            onClick = {
                gerando = true
                escopo.launch {
                    val enviados = runCatching {
                        withContext(Dispatchers.IO) { ExportarLancamentos.compartilhar(context, periodo) }
                    }
                    gerando = false
                    enviados.onSuccess { if (it > 0) onDismiss() }
                        .onFailure { mensagem("Não foi possível gerar a planilha", erro = true) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            cor = RoxoHero,
            corConteudo = Color.White,
            icone = Icons.Outlined.Share,
            enabled = !gerando && (quantidade ?: 0) > 0
        )
        Text(
            "Abre a lista do Android: WhatsApp, Drive, e-mail…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp)
        )
    }
}

@Composable
private fun OpcaoDePeriodo(texto: String, ativo: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (ativo) RoxoHero.copy(alpha = 0.16f) else MaterialTheme.colorScheme.background,
        border = if (ativo) BorderStroke(1.5.dp, RoxoHero) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                texto,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (ativo) FontWeight.Bold else FontWeight.Medium,
                color = if (ativo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
