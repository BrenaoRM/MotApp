package com.example.financacelular.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OpenInNew
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
    val podeAgir = !gerando && (quantidade ?: 0) > 0

    /** Roda [acao] fora da tela principal; [aoTerminar] recebe o resultado, e falha vira mensagem de erro. */
    fun executar(acao: suspend () -> Boolean, aoTerminar: (Boolean) -> Unit) {
        gerando = true
        escopo.launch {
            val resultado = runCatching { withContext(Dispatchers.IO) { acao() } }
            gerando = false
            resultado.onSuccess(aoTerminar).onFailure { mensagem("Não foi possível gerar a planilha", erro = true) }
        }
    }

    val salvarComo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) executar({ ExportarLancamentos.salvarEm(context, uri, periodo) }) {
            if (it) mensagem("Planilha salva")
        }
    }

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
            onClick = { executar({ ExportarLancamentos.compartilhar(context, periodo) }) { if (it) onDismiss() } },
            modifier = Modifier.fillMaxWidth(),
            cor = RoxoHero,
            corConteudo = Color.White,
            icone = Icons.Outlined.Share,
            enabled = podeAgir
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            BotaoSecundario(
                texto = "Abrir",
                onClick = {
                    executar({ ExportarLancamentos.abrir(context, periodo) }) {
                        if (!it) mensagem("Nenhum app deste celular abre planilhas", erro = true)
                    }
                },
                modifier = Modifier.weight(1f),
                icone = Icons.Outlined.OpenInNew,
                enabled = podeAgir
            )
            BotaoSecundario(
                texto = "Salvar",
                onClick = {
                    if (ExportarLancamentos.salvaDireto()) {
                        executar({ ExportarLancamentos.salvarEmDownloads(context, periodo) }) {
                            if (it) mensagem("Salvo em Downloads")
                        }
                    } else {
                        salvarComo.launch(ExportarLancamentos.nomeDoArquivo())
                    }
                },
                modifier = Modifier.weight(1f),
                icone = Icons.Outlined.Download,
                enabled = podeAgir
            )
        }
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
